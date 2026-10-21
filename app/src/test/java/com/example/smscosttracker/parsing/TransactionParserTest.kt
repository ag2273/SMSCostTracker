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

    @Test
    fun `ignores balance-only messages`() {
        assertNull(
            TransactionParser.parse(
                message("Available Balance: SAR 55,143.25\nCredit Limit: SAR 60,000.00")
            )
        )
    }

    @Test
    fun `ignores refunds and other credits`() {
        assertNull(
            TransactionParser.parse(
                message(
                    "Credit Card: Refund\n" +
                        "An Amount of SAR 1,259.00 from SAUDI AIRLINES to SAB Mastercard " +
                        "Premier Credit Card (0542)\nDate: 2026-10-20 13:01:19\n" +
                        "Balance: SAR 52945.21"
                )
            )
        )
    }

    @Test
    fun `prefers the purchase amount over the balance`() {
        val parsed = TransactionParser.parse(
            message("Purchase of SAR 120.50 at Carrefour\nBalance: SAR 5,000.00")
        )!!

        assertEquals(12050L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("Carrefour", parsed.merchant)
    }

    @Test
    fun `parses transfer amount from its labelled line`() {
        val parsed = TransactionParser.parse(
            message(
                "Funds Transfer Accepted (sarie)\nFrom: 3060\n" +
                    "To: BARQ SAFE AND DEPOSIT CLIENT MONEY\nAmount: SAR 10000.00"
            )
        )!!

        assertEquals(1000000L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("BANK 3060", parsed.account)
    }

    @Test
    fun `ignores transfers between own accounts`() {
        assertNull(
            TransactionParser.parse(
                message(
                    "Debit transfer: Between Your Accounts\nFrom: ***001\n" +
                        "Amount: SAR 15,000.00\nTo: ***060\nDate: 2026-09-22 09:53:49"
                )
            )
        )
        assertNull(
            TransactionParser.parse(
                message(
                    "Credit transfer: Between Your Accounts\nFrom: ***001\n" +
                        "Amount: SAR 15,000.00\nTo: ***060\nDate: 2026-09-22 09:53:49"
                )
            )
        )
    }

    @Test
    fun `extracts card account over funding account`() {
        val parsed = TransactionParser.parse(
            message(
                "PoS Purchase\nSAB mada(Google Pay) Card (1214) from (***001) was used at " +
                    "CITY FRESH KITCHEN for SAR 98.00 on 2026-09-10 13:18:37."
            )
        )!!

        assertEquals("BANK 1214", parsed.account)
    }

    @Test
    fun `extracts masked card from online purchase`() {
        val parsed = TransactionParser.parse(
            message(
                "Online Purchase\nBy: ***1214;mada\nfor SAR 181.00 on 2026-09-10 20:22:00"
            )
        )!!

        assertEquals("BANK 1214", parsed.account)
    }
}
