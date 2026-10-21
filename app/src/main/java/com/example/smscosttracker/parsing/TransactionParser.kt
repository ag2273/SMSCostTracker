package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.ParsedTransaction
import com.example.smscosttracker.data.RawSms
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TransactionParser {
    private const val UNKNOWN_MERCHANT = "Unknown"

    private val currencyFirst = Regex(
        "(?i)(EGP|SAR|AED|USD|ريال|جنيه|درهم)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val currencyLast = Regex(
        "(?i)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(EGP|SAR|AED|USD|ريال|جنيه|درهم)"
    )
    private val plainAmount = Regex(
        "(?i)(?:amount|debit(?:ed)?|paid|payment|purchase|withdrawal|deducted|spent|charged)" +
            "[^0-9]{0,20}([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val nonTransaction = Regex(
        "(?i)\\b(otp|one[- ]time password|verification code|declined|failed|not processed|" +
            "between your accounts|between own accounts|internal transfer)\\b"
    )
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
        Regex("(?i)\\bat\\s+([\\p{L}0-9&.'\\- ]{2,40})"),
        Regex("(?i)\\bmerchant[:\\s]+([\\p{L}0-9&.'\\- ]{2,40})"),
        Regex("(?i)\\bto\\s+([\\p{L}0-9&.'\\- ]{2,40})"),
        Regex("(?i)\\bfrom\\s+([\\p{L}0-9&.'\\- ]{2,40})"),
        Regex("(?:لدى|من|في)\\s+([\\p{L}0-9&.'\\- ]{2,40})")
    )
    private val datePatterns = listOf(
        Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b") to DateTimeFormatter.ISO_LOCAL_DATE,
        Regex("\\b(\\d{1,2}/(\\d{1,2})/(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d/M/yyyy"),
        Regex("\\b(\\d{1,2}-(\\d{1,2})-(\\d{4}))\\b") to DateTimeFormatter.ofPattern("d-M-yyyy")
    )
    private val accountPatterns = listOf(
        Regex("(?i)\\b(?:card|account|a/c|acct)[^0-9]{0,20}[*xX#]*([0-9]{2,6})"),
        Regex("(?i)\\bfrom\\s*:?\\s*\\(?\\s*[*xX#]*([0-9]{2,6})\\s*\\)?"),
        Regex("(?i)\\*{2,}\\s*([0-9]{3,6})"),
        Regex("(?i)\\b(?:by|to)\\s*:?\\s*[*xX#]*([0-9]{2,6})")
    )

    fun parse(message: RawSms, defaultCurrency: String = "EGP"): ParsedTransaction? {
        val body = message.body
        if (nonTransaction.containsMatchIn(body)) return null
        if (creditOrRefund.containsMatchIn(body)) return null
        val amount = extractAmount(body, defaultCurrency) ?: return null
        return ParsedTransaction(
            amountMinor = amount.first,
            currency = amount.second,
            merchant = extractMerchant(body),
            occurredAt = extractDate(body, message.date),
            sourceAddress = message.address,
            account = extractAccount(body, message.address)
        )
    }

    private fun extractAccount(body: String, sender: String): String {
        for (pattern in accountPatterns) {
            val digits = pattern.find(body)?.groupValues?.get(1)?.trim() ?: continue
            if (digits.isNotEmpty()) return "$sender $digits"
        }
        return sender
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
            val cleaned = raw
                .replace(Regex("\\s+on\\s+.*$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+\\d+.*$"), "")
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
