package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.InstrumentCandidate
import com.example.smscosttracker.data.RawSms
import com.example.smscosttracker.data.TransactionChannel
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
        assertEquals(TransactionChannel.ACCOUNT, parsed.channel)
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
        assertEquals(TransactionChannel.CARD, parsed.channel)
    }

    @Test
    fun `extracts masked card from online purchase`() {
        val parsed = TransactionParser.parse(
            message(
                "Online Purchase\nBy: ***1214;mada\nFrom: ***001\n" +
                    "Amount: SAR 64.66\nAt: Hungerstation×Riya\nDate: 2026-09-03 12:18:41"
            )
        )!!

        assertEquals("BANK 1214", parsed.account)
        assertEquals(TransactionChannel.CARD, parsed.channel)
        assertEquals("Hungerstation×Riya", parsed.merchant)
    }

    @Test
    fun `assigns account debits to the account, not the card`() {
        val parsed = TransactionParser.parse(
            message(
                "Bill Payment\nFrom: ***001\nTo: ELECTRICITY CO\n" +
                    "Amount: SAR 250.00 on 2026-09-12 10:00:00"
            )
        )!!

        assertEquals("BANK 001", parsed.account)
        assertEquals(TransactionChannel.ACCOUNT, parsed.channel)
    }

    @Test
    fun `lists both card and funding account as candidates`() {
        val candidates = TransactionParser.candidates(
            "Online Purchase\nBy: ***1214;mada\nFrom: ***001\n" +
                "Amount: SAR 64.66\nAt: Hungerstation×Riya"
        )

        assertEquals(
            setOf(
                TransactionChannel.CARD to "1214",
                TransactionChannel.ACCOUNT to "001"
            ),
            candidates.map { it.channel to it.digits }.toSet()
        )
    }

    @Test
    fun `registry overrides inferred card type`() {
        val parsed = TransactionParser.parse(
            message(
                "Online Purchase\nBy: ***1214;mada\nFrom: ***001\n" +
                    "Amount: SAR 64.66\nAt: Hungerstation×Riya"
            ),
            instruments = mapOf(
                TransactionParser.instrumentKey("BANK", "1214") to
                    TransactionChannel.ACCOUNT
            )
        )!!

        assertEquals("BANK 1214", parsed.account)
        assertEquals(TransactionChannel.ACCOUNT, parsed.channel)
    }

    @Test
    fun `registry selects account for transfers`() {
        val parsed = TransactionParser.parse(
            message(
                "Funds Transfer Accepted (sarie)\nFrom: 3060\n" +
                    "To: BARQ SAFE AND DEPOSIT CLIENT MONEY\nAmount: SAR 10000.00"
            ),
            instruments = mapOf(
                TransactionParser.instrumentKey("BANK", "3060") to
                    TransactionChannel.ACCOUNT
            )
        )!!

        assertEquals("BANK 3060", parsed.account)
        assertEquals(TransactionChannel.ACCOUNT, parsed.channel)
    }

    @Test
    fun `does not treat dates or times as instruments`() {
        val candidates = TransactionParser.candidates(
            "Payment on 2026-09-10 13:18:37 to 2026-09-11 from 12:18 " +
                "Account activity only."
        )

        assertEquals(emptyList<InstrumentCandidate>(), candidates)
    }

    @Test
    fun `ignores reference numbers in running text`() {
        assertEquals(
            emptyList<InstrumentCandidate>(),
            TransactionParser.candidates("Transfer to 1507 offers, ref (1012), time 13:18")
        )
    }

    @Test
    fun `accepts a plain from line`() {
        assertEquals(
            listOf(InstrumentCandidate("3060", TransactionChannel.ACCOUNT)),
            TransactionParser.candidates(
                "Funds Transfer Accepted\nFrom 3060\nTo: BARQ SAFE\nAmount: SAR 10.00"
            )
        )
    }
}
