package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.InstrumentCandidate
import com.example.smscosttracker.data.ParsedTransaction
import com.example.smscosttracker.data.RawSms
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ml.SmsModel
import com.example.smscosttracker.ml.SmsText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface ParseOutcome {
    data class Parsed(val transaction: ParsedTransaction) : ParseOutcome
    data class Skipped(val reason: String) : ParseOutcome
    data class Unparsed(val reason: String) : ParseOutcome
}

/**
 * Reads a bank SMS by structure rather than by template: [SmsFieldReader] splits it into labelled
 * values using the shared [SmsLexicon], each value's shape decides what it is, and weighted cues
 * decide the message type. No rule depends on which bank sent the message.
 */
object TransactionParser {
    const val PARSER_VERSION = 8

    private const val UNKNOWN_MERCHANT = "Unknown"
    private const val ATM_MERCHANT = "ATM withdrawal"
    private const val TRANSFER_MERCHANT = "Transfer"
    private const val MIN_SKIP_WEIGHT = 5

    private val plainAmount = Regex(
        "(?i)(?:amount|debit(?:ed)?|paid|payment|purchase|withdrawal|deducted|spent|charged)" +
            "[^0-9]{0,20}([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val explicitPurchase = Regex("(?i)\\b(purchase|pos)\\b|شراء|نقاط البيع")
    private val cardCue = Regex("(?i)\\b(pos|purchase|card|mada|visa|mastercard|pay)\\b|شراء|نقاط البيع|بطاقة|مدى")
    private val compactDateMerchant = Regex(
        "(?i)\\b[0-9]{2}(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[0-9]{2}\\s+" +
            "(\\p{L}[\\p{L}0-9&.'*/ \\-]{1,40}?)\\s+(?:purchase\\s+)?(?:from\\s+)?[0-9]{3}-"
    )
    private val maskedAnywhere = Regex("[*xX#•]{2,}[ \\t-]*([0-9]{3,6})(?![0-9])")
    private val datePatterns = listOf(
        Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b") to DateTimeFormatter.ISO_LOCAL_DATE,
        Regex("\\b(\\d{1,2}/(\\d{1,2})/(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d/M/yyyy"),
        Regex("\\b(\\d{1,2}-(\\d{1,2})-(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d-M-yyyy")
    )

    fun parse(
        message: RawSms,
        defaultCurrency: String = "EGP",
        instruments: Map<String, TransactionChannel> = emptyMap(),
        model: SmsModel? = null
    ): ParsedTransaction? =
        (analyze(message, defaultCurrency, instruments, model) as? ParseOutcome.Parsed)?.transaction

    fun analyze(
        message: RawSms,
        defaultCurrency: String = "EGP",
        instruments: Map<String, TransactionChannel> = emptyMap(),
        model: SmsModel? = null
    ): ParseOutcome {
        val body = SmsText.clean(message.body)
        val fields = SmsFieldReader.read(body)
        val rule = classify(body, fields, defaultCurrency)
        val prediction = model?.predict(message.address, body, rule.type)
        val type = prediction?.type ?: rule.type
        if (!type.isSpending) {
            return ParseOutcome.Skipped(
                if (prediction == null) rule.skipReason ?: type.label else "${type.label} (learned)"
            )
        }
        val amount = prediction?.amount?.let { learnedAmount(it, body, fields, defaultCurrency) }
            ?: extractAmount(body, fields, defaultCurrency)
            ?: return ParseOutcome.Unparsed("Amount not found")
        val instrument = prediction?.instrument
            ?.let { learnedInstrument(it, body, message.address, instruments) }
            ?: extractInstrument(body, fields, type, message.address, instruments)
        val merchant = prediction?.merchant ?: extractMerchant(body, fields, type, message.address)
        return ParseOutcome.Parsed(
            ParsedTransaction(
                amountMinor = amount.first,
                currency = amount.second,
                merchant = merchant,
                occurredAt = extractDate(body, message.date),
                sourceAddress = message.address,
                account = instrument.first,
                channel = instrument.second
            )
        )
    }

    /** The rule-based message type, also the weak label the [SmsModel] trains on. */
    fun ruleType(body: String): MessageType {
        val clean = SmsText.clean(body)
        return classify(clean, SmsFieldReader.read(clean), "EGP").type
    }

    fun instrumentKey(address: String, digits: String): String = "$address|$digits"

    fun candidates(rawBody: String): List<InstrumentCandidate> {
        val body = SmsText.clean(rawBody)
        return instrumentCandidates(body, SmsFieldReader.read(body))
    }

    private class RuleDecision(val type: MessageType, val skipReason: String? = null)

    private fun classify(body: String, fields: List<SmsField>, defaultCurrency: String): RuleDecision {
        val present = SmsLexicon.typeCues
            .filter { (_, cue) -> cue.second.containsMatchIn(body) }
            .associate { (type, cue) -> type to cue.first }
        val skip = SmsLexicon.typePrecedence
            .filter { !it.isSpending && (present[it] ?: 0) >= MIN_SKIP_WEIGHT }
            .maxByOrNull { present[it] ?: 0 }
        if (skip != null) {
            val reason = if (skip == MessageType.CREDIT) "Credit, refund or income" else "Not a spending message"
            return RuleDecision(skip, reason)
        }
        val spendingCue = present.keys.any { it.isSpending }
        val hasAmount = extractAmount(body, fields, defaultCurrency) != null
        if (!hasAmount && (!spendingCue || SmsLexicon.advisory.containsMatchIn(body))) {
            return RuleDecision(MessageType.NOTICE, "No amount or spending hint")
        }
        return RuleDecision(
            when {
                MessageType.WITHDRAWAL in present -> MessageType.WITHDRAWAL
                MessageType.BILL_OR_TRANSFER in present && !explicitPurchase.containsMatchIn(body) ->
                    MessageType.BILL_OR_TRANSFER
                else -> MessageType.PURCHASE
            }
        )
    }

    private fun extractAmount(
        body: String,
        fields: List<SmsField>,
        defaultCurrency: String
    ): Pair<Long, String>? {
        val excluded = fields.filter {
            it.role in setOf(FieldRole.BALANCE, FieldRole.FEE, FieldRole.IGNORE, FieldRole.REFERENCE)
        }.map { it.range }
        val bodyCurrency = SmsFieldReader.amounts(body)
            .firstOrNull { (_, _, range) -> excluded.none { range.first in it } }?.second
        for (field in fields.filter { it.role == FieldRole.AMOUNT }) {
            SmsFieldReader.amounts(field.value).firstOrNull()?.let { (number, currency, _) ->
                return toAmount(number, currency)
            }
            Regex("^[0-9][0-9,]*(?:\\.[0-9]{1,3})?").find(field.value.trim())?.let { number ->
                return toAmount(number.value, bodyCurrency ?: defaultCurrency)
            }
        }
        SmsFieldReader.amounts(body)
            .firstOrNull { (_, _, range) -> excluded.none { range.first in it } }
            ?.let { (number, currency, _) -> return toAmount(number, currency) }
        val plain = plainAmount.find(body)?.groupValues?.get(1) ?: return null
        return toAmount(plain, defaultCurrency)
    }

    private fun toAmount(number: String, currency: String): Pair<Long, String>? {
        val amount = number.replace(",", "").toBigDecimalOrNull() ?: return null
        return amount.toMinorUnits() to currencyCode(currency)
    }

    private fun extractMerchant(
        body: String,
        fields: List<SmsField>,
        type: MessageType,
        sender: String
    ): String {
        val byRole = { role: FieldRole -> fields.filter { it.role == role && it.shape == ValueShape.TEXT } }
        val ordered = if (type == MessageType.BILL_OR_TRANSFER) {
            byRole(FieldRole.MERCHANT) + byRole(FieldRole.TO) + byRole(FieldRole.FROM) + byRole(FieldRole.IN)
        } else {
            byRole(FieldRole.MERCHANT) + byRole(FieldRole.FROM) + byRole(FieldRole.IN) + byRole(FieldRole.TO)
        }
        val fromFields = ordered.asSequence().map { it.value }
        val fromLayout = compactDateMerchant.findAll(body).map { it.groupValues[1] }
        for (candidate in fromFields + fromLayout) {
            cleanMerchant(candidate, sender)?.let { return it }
        }
        return when (type) {
            MessageType.WITHDRAWAL -> ATM_MERCHANT
            MessageType.BILL_OR_TRANSFER -> TRANSFER_MERCHANT
            else -> UNKNOWN_MERCHANT
        }
    }

    private fun cleanMerchant(raw: String, sender: String): String? {
        val cleaned = raw.replace(Regex("×+"), " ")
            .replace(Regex("^[0-9]+\\s+"), "")
            .replace(Regex("(?i)\\s+(?:is|was|were|has|have|will|had)\\s.*$"), "")
            .replace(Regex("\\s+[0-9][0-9/:.-]*.*$"), "")
            .replace(Regex("\\s+"), " ")
            .take(40)
            .trim()
            .trim('.', ',', '-', '*', '/', ':')
        if (cleaned.length < 2 || cleaned.none { it.isLetter() }) return null
        if (cleaned.lowercase() in SmsLexicon.genericText) return null
        if (SmsLexicon.genericText.any { cleaned.startsWith("$it ") }) return null
        if (cleaned.equals(sender.trim(), ignoreCase = true)) return null
        return cleaned
    }

    private fun instrumentCandidates(body: String, fields: List<SmsField>): List<InstrumentCandidate> {
        val found = LinkedHashMap<String, TransactionChannel>()
        fun add(digits: String?, channel: TransactionChannel) {
            if (digits == null || digits.length !in 3..6) return
            if (channel == TransactionChannel.CARD && digits.length > 4) return
            found.putIfAbsent(digits, channel)
        }
        fields.forEach { field ->
            when {
                field.role == FieldRole.CARD ->
                    add(SmsFieldReader.instrumentDigits(field.value), TransactionChannel.CARD)
                field.role == FieldRole.ACCOUNT ->
                    add(SmsFieldReader.instrumentDigits(field.value), TransactionChannel.ACCOUNT)
                field.role in setOf(FieldRole.FROM, FieldRole.TO) && field.shape == ValueShape.MASKED_DIGITS ->
                    add(SmsFieldReader.digitsOf(field.value), TransactionChannel.ACCOUNT)
            }
        }
        if (found.isEmpty()) {
            val channel = if (cardCue.containsMatchIn(body)) TransactionChannel.CARD else TransactionChannel.ACCOUNT
            maskedAnywhere.findAll(body).forEach { add(it.groupValues[1], channel) }
        }
        return found.map { InstrumentCandidate(it.key, it.value) }
    }

    private fun extractInstrument(
        body: String,
        fields: List<SmsField>,
        type: MessageType,
        sender: String,
        instruments: Map<String, TransactionChannel>
    ): Pair<String, TransactionChannel> {
        val candidates = instrumentCandidates(body, fields).map { candidate ->
            val configured = instruments[instrumentKey(sender, candidate.digits)]
            InstrumentCandidate(candidate.digits, configured ?: candidate.channel)
        }
        if (candidates.isEmpty()) return sender to TransactionChannel.OTHER
        val target = if (type == MessageType.BILL_OR_TRANSFER) TransactionChannel.ACCOUNT else TransactionChannel.CARD
        val chosen = candidates.firstOrNull { it.channel == target }
            ?: candidates.firstOrNull { it.channel != TransactionChannel.OTHER }
            ?: candidates.first()
        return "$sender ${chosen.digits}" to chosen.channel
    }

    private fun learnedAmount(
        token: String,
        body: String,
        fields: List<SmsField>,
        defaultCurrency: String
    ): Pair<Long, String>? {
        val currency = extractAmount(body, fields, defaultCurrency)?.second ?: defaultCurrency.uppercase()
        return toAmount(token, currency)
    }

    private fun learnedInstrument(
        digits: String,
        body: String,
        sender: String,
        instruments: Map<String, TransactionChannel>
    ): Pair<String, TransactionChannel> {
        val channel = instruments[instrumentKey(sender, digits)]
            ?: candidates(body).firstOrNull { it.digits == digits }?.channel
            ?: TransactionChannel.OTHER
        return "$sender $digits" to channel
    }

    private fun extractDate(body: String, fallbackMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val fallbackTime = Instant.ofEpochMilli(fallbackMillis).atZone(zone).toLocalTime()
        for ((pattern, formatter) in datePatterns) {
            val candidate = pattern.find(body)?.value ?: continue
            runCatching {
                val date = LocalDate.parse(candidate, formatter)
                date.atTime(fallbackTime).atZone(zone).toInstant().toEpochMilli()
            }.onSuccess { return it }
        }
        return fallbackMillis
    }

    private fun BigDecimal.toMinorUnits(): Long =
        movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    private fun currencyCode(token: String): String = when (token.uppercase()) {
        "EGP", "جنيه", "ج.م", "جم" -> "EGP"
        "SAR", "ريال" -> "SAR"
        "AED", "درهم" -> "AED"
        else -> token.uppercase()
    }
}
