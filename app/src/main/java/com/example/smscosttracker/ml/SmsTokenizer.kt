package com.example.smscosttracker.ml

import java.util.Locale

object SmsText {
    private val bidiControls = Regex("[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    fun clean(body: String): String {
        val stripped = bidiControls.replace(body, "")
        val out = StringBuilder(stripped.length)
        for (char in stripped) {
            out.append(
                when (char) {
                    in '٠'..'٩' -> '0' + (char - '٠')
                    in '۰'..'۹' -> '0' + (char - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> char
                }
            )
        }
        return out.toString()
    }

    fun senderKey(address: String): String = address.trim().lowercase(Locale.ROOT)
}

enum class TokenKind { WORD, NUMBER, AMOUNT, MASKED, DATE, TIME, SYMBOL }

data class SmsToken(val text: String, val kind: TokenKind, val start: Int, val end: Int) {
    val feature: String
        get() = when (kind) {
            TokenKind.WORD -> text.lowercase(Locale.ROOT)
            TokenKind.NUMBER, TokenKind.AMOUNT -> "<num>"
            TokenKind.MASKED -> "<mask>"
            TokenKind.DATE -> "<date>"
            TokenKind.TIME -> "<time>"
            TokenKind.SYMBOL -> text
        }

    val digits: String
        get() = text.filter { it in '0'..'9' }
}

object SmsTokenizer {
    private val pattern = Regex(
        "(\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})" +
            "|(\\d{1,2}:\\d{2}(?::\\d{2})?)" +
            "|([*xX#•]{2,}[ \\t]?\\d{2,6})" +
            "|(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+\\.\\d{1,2})" +
            "|(\\d+)" +
            "|([\\p{L}\\p{M}][\\p{L}\\p{M}'&]*)" +
            "|([^\\s\\p{L}\\p{N}])"
    )
    private val kinds = listOf(
        TokenKind.DATE,
        TokenKind.TIME,
        TokenKind.MASKED,
        TokenKind.AMOUNT,
        TokenKind.NUMBER,
        TokenKind.WORD,
        TokenKind.SYMBOL
    )

    fun tokenize(cleanBody: String): List<SmsToken> =
        pattern.findAll(cleanBody).map { match ->
            val group = (1..kinds.size).first { match.groups[it] != null }
            SmsToken(match.value, kinds[group - 1], match.range.first, match.range.last + 1)
        }.toList()

    fun features(sender: String, tokens: List<SmsToken>): List<String> {
        val words = tokens.filter { it.kind != TokenKind.SYMBOL }.map { it.feature }
        return buildList {
            add("@${SmsText.senderKey(sender)}")
            addAll(words)
            words.zipWithNext { a, b -> add("$a $b") }
        }
    }
}
