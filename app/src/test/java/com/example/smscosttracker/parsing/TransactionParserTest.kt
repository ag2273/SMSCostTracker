package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.RawSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {
    private val fallbackDate = 1_700_000_000_000L

    private fun message(body: String) = RawSms(
        address = "BANK",
        body = body,
        date = fallbackDate,
        threadId = 1L
    )

    @Test
    fun `parses amount currency merchant and date`() {
        val parsed = TransactionParser.parse(
            message("Purchase of EGP 125.50 at Carrefour on 20/10/2026")
        )!!

        assertEquals(12550L, parsed.amountMinor)
        assertEquals("EGP", parsed.currency)
        assertEquals("Carrefour", parsed.merchant)
        assertEquals("BANK", parsed.sourceAddress)
    }

    @Test
    fun `parses amount written after currency with thousands separator`() {
        val parsed = TransactionParser.parse(
            message("SAR 1,234.00 debited from STC")
        )!!

        assertEquals(123400L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("STC", parsed.merchant)
    }

    @Test
    fun `parses arabic currency word and merchant`() {
        val parsed = TransactionParser.parse(message("خصم 250.00 جنيه لدى كارفور"))!!

        assertEquals(25000L, parsed.amountMinor)
        assertEquals("EGP", parsed.currency)
        assertEquals("كارفور", parsed.merchant)
    }

    @Test
    fun `parses plain amount without currency using default currency`() {
        val parsed = TransactionParser.parse(
            message("Debit 99.00 at Pharmacy"),
            defaultCurrency = "SAR"
        )!!

        assertEquals(9900L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("Pharmacy", parsed.merchant)
    }

    @Test
    fun `falls back to message date when body has no date`() {
        val parsed = TransactionParser.parse(message("EGP 10.00 at Pharmacy"))!!

        assertEquals(fallbackDate, parsed.occurredAt)
    }

    @Test
    fun `returns null when no amount is present`() {
        assertNull(TransactionParser.parse(message("Your OTP is 123456")))
    }
}
