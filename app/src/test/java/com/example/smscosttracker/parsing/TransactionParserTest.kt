package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.InstrumentCandidate
import com.example.smscosttracker.data.RawSms
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.ml.MessageLabel
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ml.SmsModel
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
        assertEquals("Hungerstation Riya", parsed.merchant)
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

    @Test
    fun `does not treat long reference numbers as cards`() {
        assertEquals(
            emptyList<InstrumentCandidate>(),
            TransactionParser.candidates("Card reference 113038 for your request")
        )
    }

    @Test
    fun `allows six digit account numbers`() {
        assertEquals(
            listOf(InstrumentCandidate("154313", TransactionChannel.ACCOUNT)),
            TransactionParser.candidates("Account: 154313")
        )
    }

    @Test
    fun `ignores credit card payments`() {
        assertNull(
            TransactionParser.parse(
                message(
                    "Credit Card: Payment\nCard: ***0542;MASTER\n" +
                        "Amount: SAR 7,635.29\nFrom: ***001\nDate: 2026-09-22 09:53:07"
                )
            )
        )
    }

    @Test
    fun `keeps credit card purchases`() {
        val parsed = TransactionParser.parse(
            message(
                "Credit Card: Purchase\nCard: ***0542;MASTER\n" +
                    "Amount: SAR 50.00\nAt: CARREFOUR\nDate: 2026-09-22 10:00:00"
            )
        )!!

        assertEquals(5000L, parsed.amountMinor)
        assertEquals("BANK 0542", parsed.account)
    }


    @Test
    fun `parses labelled currency with a colon`() {
        val parsed = TransactionParser.parse(
            message(
                "Online Purchase\n" +
                    "SAB Mastercard Premier Credit Card (0542) was used at CLOUDFLARE " +
                    "for USD 8.20 in UNITED STATES\n" +
                    "Exchange rate: 3.75366\n" +
                    "Amount in SAR: 30.78\n" +
                    "International Fees in SAR: 0.70\n" +
                    "Total amount in SAR: 31.48\n" +
                    "Date: 2026-10-17 21:09:13\n" +
                    "Balance: SAR 51954.06"
            )
        )!!

        assertEquals(3078L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("CLOUDFLARE", parsed.merchant)
    }

    @Test
    fun `parses dual currency purchase using the settled SAR amount`() {
        val parsed = TransactionParser.parse(
            message(
                "Online Purchase\n" +
                    "SAB Mastercard Premier Credit Card (0542) was\n" +
                    "used at CLOUDFLARE for USD 10.46 in UNITED\n" +
                    "STATES\n" +
                    "Exchange rate: 3.75430\n" +
                    "Amount in SAR: 39.27\n" +
                    "International Fees in SAR: 0.90\n" +
                    "Total amount in SAR: 40.17\n" +
                    "Date: 2026-10-19 20:33:25\n" +
                    "Balance: SAR 51687.06"
            )
        )!!

        assertEquals(3927L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("CLOUDFLARE", parsed.merchant)
    }

    @Test
    fun `stops the merchant before the available limit sentence`() {
        val cases = listOf(
            "AMAZON. Your available limit is EGP 234609.10" to "AMAZON",
            "My Fawry. Your available limit is EGP 241763.95" to "My Fawry",
            "Talabat. Your available limit is EGP 262081.07" to "Talabat"
        )
        cases.forEach { (tail, expected) ->
            val parsed = TransactionParser.parse(
                message(
                    "Your Credit Card ending with *** 5797 has been used for " +
                        "EGP 3204.00 on 15/10/2026 at $tail"
                )
            )!!
            assertEquals(expected, parsed.merchant)
        }
    }

    @Test
    fun `ignores statement amount due messages`() {
        assertNull(
            TransactionParser.parse(
                message(
                    "Dear Customer,\n" +
                        "Your Mastercard Premier Credit Card (0542) statement has been issued\n" +
                        "Acc No: 13629290068\n" +
                        "Amount Due: SAR 401.00\n" +
                        "Due Date: 2026-11-10 23:59:59"
                )
            )
        )
    }

    @Test
    fun `strips trailing currency phrase from merchant`() {
        val parsed = TransactionParser.parse(
            message("Purchase at Amazon Now for SAR 51.59 on 2026-09-01 10:00:00")
        )!!

        assertEquals("Amazon Now", parsed.merchant)
    }

    @Test
    fun `parses amounts written in arabic digits`() {
        val parsed = TransactionParser.parse(message("خصم ١٬٢٥٠٫٥٠ ريال لدى بنده"))!!

        assertEquals(125050L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
    }

    @Test
    fun `rule types split skipped and spending messages`() {
        assertEquals(MessageType.NOTICE, TransactionParser.ruleType("Your OTP is 4412"))
        assertEquals(MessageType.DECLINED, TransactionParser.ruleType("Purchase of SAR 5.00 declined"))
        assertEquals(
            MessageType.INTERNAL,
            TransactionParser.ruleType("SAR 500.00 moved between your accounts")
        )
        assertEquals(MessageType.CREDIT, TransactionParser.ruleType("Salary SAR 9,000.00 credited"))
        assertEquals(
            MessageType.STATEMENT,
            TransactionParser.ruleType("Statement issued. Minimum due SAR 300.00")
        )
        assertEquals(MessageType.WITHDRAWAL, TransactionParser.ruleType("ATM withdrawal SAR 400.00"))
        assertEquals(
            MessageType.BILL_OR_TRANSFER,
            TransactionParser.ruleType("Bill payment SAR 230.00 to STC via SADAD")
        )
        assertEquals(MessageType.PURCHASE, TransactionParser.ruleType("Purchase of SAR 9.00 at Panda"))
    }

    @Test
    fun `learned template supplies merchant amount and instrument`() {
        val labelled = "POS\nAmt: 1,249.00\nBy: *4432\nAt: JARIR BOOKSTORE RIYADH\n12/10/26"
        val model = SmsModel.train(
            messages = emptyList(),
            labels = listOf(
                MessageLabel(
                    address = "BANK",
                    body = labelled,
                    type = MessageType.PURCHASE,
                    merchant = "JARIR BOOKSTORE RIYADH",
                    amount = "1,249.00",
                    instrument = "4432"
                )
            ),
            ruleType = TransactionParser::ruleType
        )

        val parsed = TransactionParser.parse(
            message("POS\nAmt: 86.00\nBy: *9981\nAt: AL BAIK OLAYA\n13/10/26"),
            defaultCurrency = "SAR",
            instruments = mapOf(TransactionParser.instrumentKey("BANK", "9981") to TransactionChannel.CARD),
            model = model
        )!!

        assertEquals("AL BAIK OLAYA", parsed.merchant)
        assertEquals(8600L, parsed.amountMinor)
        assertEquals("SAR", parsed.currency)
        assertEquals("BANK 9981", parsed.account)
        assertEquals(TransactionChannel.CARD, parsed.channel)
    }

    @Test
    fun `learned non-spending label skips a message the rules would parse`() {
        val body = "Purchase of SAR 1.00 at TEST verification merchant"
        val model = SmsModel.train(
            messages = emptyList(),
            labels = listOf(MessageLabel("BANK", body, MessageType.NOTICE)),
            ruleType = TransactionParser::ruleType
        )

        assertEquals(
            ParseOutcome.Skipped("OTP or notice (learned)"),
            TransactionParser.analyze(message(body), model = model)
        )
        assertEquals(
            ParseOutcome.Skipped("No amount or spending hint"),
            TransactionParser.analyze(message("Welcome to SAB"), model = model)
        )
    }
}
