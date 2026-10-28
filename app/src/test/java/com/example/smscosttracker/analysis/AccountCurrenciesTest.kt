package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCurrenciesTest {
    private var nextId = 1L
    private val converter = CurrencyConverter(ExchangeRates.BUILT_IN)

    private fun transaction(account: String, amountMinor: Long, currency: String, at: Long = 0) = TransactionEntity(
        id = nextId++,
        amountMinor = amountMinor,
        currency = currency,
        merchant = "Shop",
        occurredAt = at,
        sourceAddress = account.substringBefore(' '),
        category = "Shopping",
        account = account,
        channel = TransactionChannel.CARD.name
    )

    private val card = InstrumentProfile("SAB 1214", TransactionChannel.CARD)
    private val account = InstrumentProfile("SAB 001", TransactionChannel.ACCOUNT)

    @Test
    fun `default currency is the one most transactions use`() {
        val rows = List(4) { transaction("SAB 1214", 1_000, "SAR") } +
            transaction("SAB 1214", 500, "USD") +
            List(5) { transaction("CIB 7781", 9_000, "EGP") }
        assertEquals(mapOf("SAB 1214" to "SAR", "CIB 7781" to "EGP"), AccountCurrencies.inferred(rows))
    }

    @Test
    fun `a card used a few times abroad keeps its bank's currency`() {
        val rows = List(20) { transaction("SAB 1214", 1_000, "SAR") } +
            List(3) { transaction("SAB 5527", 2_000, "USD") } +
            transaction("SAB 5527", 1_000, "SAR")
        assertEquals("SAR", AccountCurrencies.inferred(rows)["SAB 5527"])
    }

    @Test
    fun `foreign purchase is converted to the card currency and keeps its original`() {
        val local = transaction("SAB 1214", 10_000, "SAR")
        val abroad = transaction("SAB 1214", 2_500, "USD")

        val shown = AccountCurrencies.convert(listOf(local, abroad), listOf(card), converter)

        assertEquals(listOf("SAR", "SAR"), shown.transactions.map { it.currency })
        assertEquals(9_375L, shown.transactions[1].amountMinor)
        assertEquals(mapOf(abroad.id to OriginalAmount(2_500, "USD")), shown.originals)
        assertEquals(local, shown.transactions[0])
    }

    @Test
    fun `chosen currency wins over the inferred one`() {
        val rows = listOf(transaction("SAB 1214", 10_000, "SAR"), transaction("SAB 1214", 9_375, "SAR"))
        val shown = AccountCurrencies.convert(rows, listOf(card.copy(currency = "USD")), converter)

        assertEquals(listOf(2_667L, 2_500L), shown.transactions.map { it.amountMinor })
        assertTrue(shown.transactions.all { it.currency == "USD" })
    }

    @Test
    fun `linked debit card uses its account currency and is listed with the account`() {
        val egpAccount = account.copy(currency = "EGP")
        val debit = card.copy(cardKind = CardKind.DEBIT, linkedAccount = "SAB 001")
        val rows = listOf(transaction("SAB 1214", 10_000, "SAR"))

        val shown = AccountCurrencies.convert(rows, listOf(debit, egpAccount), converter)

        assertEquals("EGP", shown.transactions.single().currency)
        assertEquals(129_333L, shown.transactions.single().amountMinor)
        assertEquals(mapOf("SAB 1214" to "SAB 001"), shown.linkedAccounts)
    }

    @Test
    fun `credit cards and links to cards are never linked`() {
        val credit = card.copy(cardKind = CardKind.CREDIT, linkedAccount = "SAB 001")
        val toCard = card.copy(cardKind = CardKind.DEBIT, linkedAccount = "SAB 9999")
        val profiles = listOf(credit, account, InstrumentProfile("SAB 9999", TransactionChannel.CARD))

        assertNull(AccountCurrencies.linkedAccount(credit, profiles.associateBy { it.account }))
        assertNull(
            AccountCurrencies.linkedAccount(toCard, (profiles - credit + toCard).associateBy { it.account })
        )
    }

    @Test
    fun `display currency converts everything with each transaction's own rate`() {
        val changedAt = 1_000L
        val converter = CurrencyConverter(ExchangeRates.BUILT_IN + RateVersion("EGP", 50.0, changedAt, RateSource.MANUAL))
        val rows = listOf(
            transaction("SAB 1214", 10_000, "USD", at = 0),
            transaction("CIB 7781", 10_000, "USD", at = changedAt)
        )

        val shown = AccountCurrencies.convert(rows, emptyList(), converter, displayCurrency = "EGP")

        assertEquals(listOf(485_000L, 500_000L), shown.transactions.map { it.amountMinor })
    }

    @Test
    fun `amounts without a rate stay as they were`() {
        val qar = transaction("QNB 4410", 1_000, "QAR")
        val shown = AccountCurrencies.convert(listOf(qar), emptyList(), converter, displayCurrency = "SAR")

        assertEquals(qar, shown.transactions.single())
        assertTrue(shown.originals.isEmpty())
    }

    @Test
    fun `currencies are listed most used first`() {
        val rows = listOf(
            transaction("NBK 4410", 1_000, "KWD"),
            transaction("SAB 1214", 1_000, "SAR"),
            transaction("SAB 1214", 2_000, "SAR"),
            transaction("CIB 7781", 3_000, "EGP")
        )
        assertEquals(listOf("SAR", "EGP", "KWD"), AccountCurrencies.byUse(rows))
    }
}
