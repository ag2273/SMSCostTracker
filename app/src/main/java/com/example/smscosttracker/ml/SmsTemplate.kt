package com.example.smscosttracker.ml

import java.math.BigDecimal

data class TemplateMatch(
    val type: MessageType,
    val merchant: String?,
    val amount: String?,
    val instrument: String?
)

/**
 * A labelled message reduced to its fixed shape. Numbers, dates and masked digits are already
 * variable in the token features; the labelled merchant span becomes a wildcard, so the same
 * bank wording with another shop still matches while any other wording change does not.
 */
class SmsTemplate private constructor(
    private val sender: String,
    private val prefix: List<String>,
    private val suffix: List<String>?,
    private val type: MessageType,
    private val fixedMerchant: String?,
    private val amountIndex: Int?,
    private val instrumentIndex: Int?
) {
    fun match(sender: String, tokens: List<SmsToken>, cleanBody: String): TemplateMatch? {
        if (SmsText.senderKey(sender) != this.sender) return null
        val features = tokens.map { it.feature }
        val merchant: String?
        val position: (Int) -> Int
        if (suffix == null) {
            if (features != prefix) return null
            merchant = fixedMerchant
            position = { it }
        } else {
            if (features.size < prefix.size + suffix.size + 1) return null
            if (features.subList(0, prefix.size) != prefix) return null
            if (features.subList(features.size - suffix.size, features.size) != suffix) return null
            val first = tokens[prefix.size]
            val last = tokens[features.size - suffix.size - 1]
            merchant = cleanBody.substring(first.start, last.end).trim()
            val templateSize = prefix.size + 1 + suffix.size
            position = { if (it < prefix.size) it else features.size - (templateSize - it) }
        }
        return TemplateMatch(
            type = type,
            merchant = merchant?.takeIf { it.isNotEmpty() },
            amount = amountIndex?.let { tokens[position(it)].text },
            instrument = instrumentIndex?.let { tokens[position(it)].digits }
        )
    }

    companion object {
        fun learn(label: MessageLabel): SmsTemplate? {
            val body = SmsText.clean(label.body)
            val tokens = SmsTokenizer.tokenize(body)
            if (tokens.isEmpty()) return null
            val merchantRange = label.merchant?.trim()?.takeIf { it.isNotEmpty() }?.let { merchant ->
                val start = body.indexOf(merchant, ignoreCase = true)
                if (start < 0) return@let null
                val end = start + merchant.length
                val first = tokens.indexOfFirst { it.end > start }
                val last = tokens.indexOfLast { it.start < end }
                if (first < 0 || last < first) null else first..last
            }
            val outside = tokens.indices.filter { merchantRange == null || it !in merchantRange }
            val amountToken = label.amount?.toAmount()?.let { amount ->
                outside.firstOrNull {
                    tokens[it].kind in AMOUNT_KINDS && tokens[it].text.toAmount()?.compareTo(amount) == 0
                }
            }
            val instrumentToken = label.instrument?.filter { it in '0'..'9' }
                ?.takeIf { it.length >= 2 }
                ?.let { digits ->
                    outside.firstOrNull {
                        tokens[it].kind in INSTRUMENT_KINDS && tokens[it].digits.endsWith(digits)
                    }
                }
            val features = tokens.map { it.feature }
            val toTemplate: (Int) -> Int = if (merchantRange == null) {
                { it }
            } else {
                { if (it < merchantRange.first) it else it - merchantRange.last + merchantRange.first }
            }
            return SmsTemplate(
                sender = SmsText.senderKey(label.address),
                prefix = merchantRange?.let { features.subList(0, it.first) } ?: features,
                suffix = merchantRange?.let { features.subList(it.last + 1, features.size) },
                type = label.type,
                fixedMerchant = label.merchant?.trim()?.takeIf { it.isNotEmpty() && merchantRange == null },
                amountIndex = amountToken?.let(toTemplate),
                instrumentIndex = instrumentToken?.let(toTemplate)
            )
        }

        private val AMOUNT_KINDS = setOf(TokenKind.AMOUNT, TokenKind.NUMBER)
        private val INSTRUMENT_KINDS = setOf(TokenKind.MASKED, TokenKind.NUMBER)

        private fun String.toAmount(): BigDecimal? = replace(",", "").trim().toBigDecimalOrNull()
    }
}
