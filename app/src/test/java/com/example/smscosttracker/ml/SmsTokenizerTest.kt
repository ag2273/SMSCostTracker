package com.example.smscosttracker.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTokenizerTest {
    @Test
    fun `classifies numbers dates times and masked digits`() {
        val tokens = SmsTokenizer.tokenize(
            "Purchase SAR1,249.00 at Jarir card **4432 on 2026-10-12 14:05 ref 778812"
        )

        assertEquals(
            listOf(
                "purchase", "sar", "<num>", "at", "jarir", "card", "<mask>",
                "on", "<date>", "<time>", "ref", "<num>"
            ),
            tokens.map { it.feature }
        )
        assertEquals("4432", tokens[6].digits)
        assertEquals(TokenKind.AMOUNT, tokens[2].kind)
        assertEquals(TokenKind.NUMBER, tokens[11].kind)
    }

    @Test
    fun `keeps offsets into the cleaned body`() {
        val body = "Paid at H&M Mall."
        val tokens = SmsTokenizer.tokenize(body)

        assertEquals("H&M", body.substring(tokens[2].start, tokens[2].end))
        assertEquals(".", tokens.last().text)
    }

    @Test
    fun `normalizes arabic digits and strips direction marks`() {
        val clean = SmsText.clean("‏خصم ١٬٢٥٠٫٥٠ ريال")

        assertEquals("خصم 1,250.50 ريال", clean)
        assertEquals(TokenKind.AMOUNT, SmsTokenizer.tokenize(clean)[1].kind)
    }

    @Test
    fun `features include sender words and bigrams but not symbols`() {
        val features = SmsTokenizer.features(
            " SAB ",
            SmsTokenizer.tokenize("OTP: 1234")
        )

        assertEquals(listOf("@sab", "otp", "<num>", "otp <num>"), features)
    }
}
