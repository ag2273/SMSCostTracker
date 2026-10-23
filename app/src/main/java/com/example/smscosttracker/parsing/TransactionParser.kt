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

object TransactionParser {
    const val PARSER_VERSION = 4

    private const val UNKNOWN_MERCHANT = "Unknown"
    private const val CURRENCY_SEPARATOR = "[\\s\\u00A0\\u2007\\u202F:=]*"

    private val currencyFirst = Regex(
        "(?i)(EGP|SAR|AED|USD|ريال|جنيه|درهم)$CURRENCY_SEPARATOR" +
            "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val currencyLast = Regex(
        "(?i)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)$CURRENCY_SEPARATOR" +
            "(EGP|SAR|AED|USD|ريال|جنيه|درهم)"
    )
    private val plainAmount = Regex(
        "(?i)(?:amount|debit(?:ed)?|paid|payment|purchase|withdrawal|deducted|spent|charged)" +
            "[^0-9]{0,20}([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val nonTransaction = listOf(
        MessageType.DECLINED to Regex("(?i)\\b(declined|failed|not processed)\\b"),
        MessageType.NOTICE to Regex("(?i)\\b(otp|one[- ]time password|verification code)\\b"),
        MessageType.INTERNAL to Regex(
            "(?i)\\b(between your accounts|between own accounts|internal transfer|" +
                "credit card\\s*:\\s*payment|credit card payment|card\\s*:\\s*payment|" +
                "card repayment|payment to (?:your )?(?:credit )?card)\\b"
        ),
        MessageType.STATEMENT to Regex(
            "(?i)\\b(amount due|minimum due|total due|payment due|due date|" +
                "statement(?: has been)? issued|statement date|closing balance)\\b"
        )
    )
    private val withdrawal = Regex("(?i)\\b(atm|cash withdrawal|withdraw(?:al|n)?)\\b|سحب")
    private val creditOrRefund = Regex(
        "(?i)\\b(refund|refunded|credited|credit transfer|deposited|deposit received|" +
            "deposit of|salary|cashback|reversal|reversed|received from|incoming transfer)\\b"
    )
    private val balanceLine = Regex(
        "(?i)\\b(available balance|current balance|balance|credit limit|available limit|" +
            "minimum due|total due|statement).*$"
    )
    private val transactionHint = Regex(
        "(?i)\\b(amount|purchase|payment|paid|debit(?:ed)?|withdraw(?:al|n)?|deducted|" +
            "spent|charged|transfer(?:red)?|pos|cheque|bill)\\b"
    )
    private val merchantPatterns = listOf(
        Regex("(?i)\\bat\\s*:?\\s+([\\p{L}0-9&.'×\\- ]{2,40})"),
        Regex("(?i)\\bmerchant\\s*:?\\s+([\\p{L}0-9&.'×\\- ]{2,40})"),
        Regex("(?i)\\bto\\s*:?\\s+([\\p{L}0-9&.'×\\- ]{2,40})"),
        Regex("(?i)\\bfrom\\s*:?\\s+([\\p{L}0-9&.'×\\- ]{2,40})"),
        Regex("(?:لدى|من|في)\\s+([\\p{L}0-9&.'\\- ]{2,40})")
    )
    private val datePatterns = listOf(
        Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b") to DateTimeFormatter.ISO_LOCAL_DATE,
        Regex("\\b(\\d{1,2}/(\\d{1,2})/(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d/M/yyyy"),
        Regex("\\b(\\d{1,2}-(\\d{1,2})-(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d-M-yyyy")
    )
    private val cardPatterns = listOf(
        Regex(
            "(?i)\\b(?:card|mada|visa|mastercard|google pay|apple pay)" +
                "[^0-9\\n]{0,12}[*xX#]*([0-9]{3,6})(?![0-9:/-])"
        ),
        Regex("(?i)\\*{2,}[ \\t]*([0-9]{3,6})[^\\n]{0,12}\\b(?:mada|visa|mastercard)\\b"),
        Regex("(?i)\\b(?:by|via)\\s*:?\\s*\\*{2,}[ \\t]*([0-9]{3,6})")
    )
    private val accountPatterns = listOf(
        Regex(
            "(?im)(?:^|\\n)\\s*(?:from|to)\\s*" +
                "(?:no\\.?|number|ending|last)?\\s*[:#]\\s*" +
                "[*xX#]*([0-9]{3,6})(?![0-9:/-])"
        ),
        Regex(
            "(?i)\\b(?:account|a/c|acct)\\s*" +
                "(?:no\\.?|number|ending|last)?\\s*[:#]\\s*" +
                "[*xX#]*([0-9]{3,6})(?![0-9:/-])"
        ),
        Regex(
            "(?i)\\b(?:from|to|account|a/c|acct)\\s*\\(?\\s*" +
                "[*xX#]{1,}[ \\t]*([0-9]{3,6})(?![0-9:/-])"
        ),
        Regex(
            "(?i)\\b(?:account|a/c|acct|from|to)\\s+" +
                "(?:no\\.?|number|ending|last)\\s*[:#]?\\s*" +
                "[*xX#]*([0-9]{3,6})(?![0-9:/-])"
        ),
        Regex(
            "(?im)(?:^|\\n)\\s*(?:from|to)\\s+([0-9]{3,6})(?![0-9:/-])"
        )
    )
    private val maskedPattern = Regex("(?i)\\*{2,}[ \\t]*([0-9]{3,6})")
    private val merchantSentenceBreak = Regex("\\.\\s+(?=\\p{Lu})")
    private val cardContext = Regex(
        "(?i)\\b(pos|purchase|online purchase|card|mada|visa|mastercard|" +
            "google pay|apple pay|used at)\\b"
    )
    private val accountContext = Regex(
        "(?i)\\b(transfer|funds transfer|bill payment|sadad|sarie|standing order|" +
            "direct debit|cheque|salary)\\b"
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
        val rule = classify(body)
        val prediction = model?.predict(message.address, body, rule.type)
        val type = prediction?.type ?: rule.type
        if (!type.isSpending) {
            return ParseOutcome.Skipped(
                if (prediction == null) rule.skipReason ?: type.label else "${type.label} (learned)"
            )
        }
        val amount = prediction?.amount?.let { learnedAmount(it, body, defaultCurrency) }
            ?: extractAmount(body, defaultCurrency)
            ?: return ParseOutcome.Unparsed("Amount not found")
        val instrument = prediction?.instrument
            ?.let { learnedInstrument(it, body, message.address, instruments) }
            ?: extractInstrument(body, message.address, instruments)
        return ParseOutcome.Parsed(
            ParsedTransaction(
                amountMinor = amount.first,
                currency = amount.second,
                merchant = prediction?.merchant ?: extractMerchant(body),
                occurredAt = extractDate(body, message.date),
                sourceAddress = message.address,
                account = instrument.first,
                channel = instrument.second
            )
        )
    }

    /** The rule-based message type, also the weak label the [SmsModel] trains on. */
    fun ruleType(body: String): MessageType = classify(SmsText.clean(body)).type

    private class RuleDecision(val type: MessageType, val skipReason: String? = null)

    private fun classify(body: String): RuleDecision {
        nonTransaction.firstOrNull { it.second.containsMatchIn(body) }?.let {
            return RuleDecision(it.first, "Not a spending message")
        }
        if (creditOrRefund.containsMatchIn(body)) {
            return RuleDecision(MessageType.CREDIT, "Credit, refund or income")
        }
        if (extractAmount(body, "EGP") == null && !transactionHint.containsMatchIn(body)) {
            return RuleDecision(MessageType.NOTICE, "No amount or spending hint")
        }
        return RuleDecision(
            when {
                withdrawal.containsMatchIn(body) -> MessageType.WITHDRAWAL
                accountContext.containsMatchIn(body) -> MessageType.BILL_OR_TRANSFER
                else -> MessageType.PURCHASE
            }
        )
    }

    private fun learnedAmount(
        token: String,
        body: String,
        defaultCurrency: String
    ): Pair<Long, String>? {
        val amount = token.replace(",", "").toBigDecimalOrNull() ?: return null
        val currency = extractAmount(body, defaultCurrency)?.second ?: defaultCurrency.uppercase()
        return amount.toMinorUnits() to currency
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

    fun instrumentKey(address: String, digits: String): String = "$address|$digits"

    fun candidates(rawBody: String): List<InstrumentCandidate> {
        val body = SmsText.clean(rawBody)
        val found = LinkedHashMap<String, Pair<TransactionChannel, Int>>()
        fun add(
            digits: String?,
            channel: TransactionChannel,
            priority: Int,
            rejectYear: Boolean = false
        ) {
            val value = digits?.trim()
            if (value.isNullOrEmpty() || value.length !in 3..6) return
            if (channel == TransactionChannel.CARD && value.length > 4) return
            if (rejectYear && value.length == 4 && value.toIntOrNull() in 1900..2100) return
            val existing = found[value]
            if (existing == null || priority > existing.second) {
                found[value] = channel to priority
            }
        }

        cardPatterns.forEach { pattern ->
            pattern.findAll(body).forEach { add(it.groupValues[1], TransactionChannel.CARD, 2) }
        }
        accountPatterns.forEach { pattern ->
            pattern.findAll(body).forEach {
                add(it.groupValues[1], TransactionChannel.ACCOUNT, 2, rejectYear = true)
            }
        }
        val hasCardContext = cardContext.containsMatchIn(body)
        val hasAccountContext = accountContext.containsMatchIn(body)
        val guessedCard = hasCardContext && !hasAccountContext
        if (hasCardContext || hasAccountContext) {
            maskedPattern.findAll(body).forEach {
                add(
                    it.groupValues[1],
                    if (guessedCard) TransactionChannel.CARD else TransactionChannel.ACCOUNT,
                    1,
                    rejectYear = !guessedCard
                )
            }
        }
        return found.map { InstrumentCandidate(it.key, it.value.first) }
    }

    private fun extractInstrument(
        body: String,
        sender: String,
        instruments: Map<String, TransactionChannel>
    ): Pair<String, TransactionChannel> {
        val candidates = candidates(body).map { candidate ->
            val configured = instruments[instrumentKey(sender, candidate.digits)]
            InstrumentCandidate(candidate.digits, configured ?: candidate.channel)
        }
        if (candidates.isEmpty()) return sender to TransactionChannel.OTHER
        val target = if (accountContext.containsMatchIn(body)) {
            TransactionChannel.ACCOUNT
        } else {
            TransactionChannel.CARD
        }
        val chosen = candidates.firstOrNull { it.channel == target }
            ?: candidates.firstOrNull { it.channel != TransactionChannel.OTHER }
            ?: candidates.first()
        return "$sender ${chosen.digits}" to chosen.channel
    }

    private fun extractAmount(body: String, defaultCurrency: String): Pair<Long, String>? {
        val lines = body.lines()
            .map { balanceLine.replace(it, " ") }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val hinted = lines.filter { transactionHint.containsMatchIn(it) }
        val candidates = hinted + lines.filterNot { transactionHint.containsMatchIn(it) }
        for (line in candidates) {
            val currencyMatch = currencyFirst.find(line) ?: currencyLast.find(line)
            if (currencyMatch != null) {
                val groups = currencyMatch.groupValues
                val numberFirst = groups[1].firstOrNull()?.isDigit() == true
                val currencyToken = if (numberFirst) groups[2] else groups[1]
                val numberToken = if (numberFirst) groups[1] else groups[2]
                val amount = numberToken.replace(",", "").toBigDecimalOrNull()
                if (amount != null) return amount.toMinorUnits() to currencyCode(currencyToken)
            }
            val plain = plainAmount.find(line)?.groupValues?.get(1) ?: continue
            val amount = plain.replace(",", "").toBigDecimalOrNull() ?: continue
            return amount.toMinorUnits() to defaultCurrency.uppercase()
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        for (pattern in merchantPatterns) {
            val raw = pattern.find(body)?.groupValues?.get(1)?.trim() ?: continue
            val sentenceBreak = merchantSentenceBreak.find(raw)?.range?.first ?: raw.length
            val cleaned = raw.substring(0, sentenceBreak)
                .replace(Regex("\\s+on\\s+.*$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+\\d+.*$"), "")
                .replace(
                    Regex(
                        "\\s+for\\s+(?:SAR|EGP|AED|USD|ريال|جنيه|درهم)\\s*$",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()
                .trim('.', ',', '-')
            if (cleaned.length >= 2 &&
                !cleaned.equals("your account", ignoreCase = true) &&
                !balanceLine.containsMatchIn(cleaned)
            ) {
                return cleaned
            }
        }
        return UNKNOWN_MERCHANT
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
        "EGP", "جنيه" -> "EGP"
        "SAR", "ريال" -> "SAR"
        "AED", "درهم" -> "AED"
        "USD" -> "USD"
        else -> token.uppercase()
    }
}
