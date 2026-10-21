package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFiltersTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 10, 21).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun transaction(
        merchant: String = "Carrefour",
        category: Category = Category.GROCERIES,
        date: LocalDate = LocalDate.of(2026, 10, 5),
        account: String = "SAB 001"
    ) = TransactionEntity(
        amountMinor = 1000L,
        currency = "EGP",
        merchant = merchant,
        occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        sourceAddress = "BANK",
        category = category.displayName,
        account = account
    )

    @Test
    fun `this month keeps only current month transactions`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 10, 1)),
            transaction(date = LocalDate.of(2026, 9, 30))
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(preset = DatePreset.THIS_MONTH),
            now,
            zone
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `this quarter keeps only current quarter transactions`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 10, 1)),
            transaction(date = LocalDate.of(2026, 7, 1))
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(preset = DatePreset.THIS_QUARTER),
            now,
            zone
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `custom range includes both endpoints`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 10, 1)),
            transaction(date = LocalDate.of(2026, 10, 5)),
            transaction(date = LocalDate.of(2026, 10, 6))
        )
        val state = TransactionFilterState(
            preset = DatePreset.CUSTOM,
            customStart = LocalDate.of(2026, 10, 1),
            customEnd = LocalDate.of(2026, 10, 5)
        )

        val result = TransactionFilters.apply(transactions, state, now, zone)

        assertEquals(2, result.size)
    }

    @Test
    fun `last month keeps only previous month transactions`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 9, 30)),
            transaction(date = LocalDate.of(2026, 10, 1))
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(preset = DatePreset.LAST_MONTH),
            now,
            zone
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `last quarter keeps only previous quarter transactions`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 7, 1)),
            transaction(date = LocalDate.of(2026, 10, 1))
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(preset = DatePreset.LAST_QUARTER),
            now,
            zone
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `custom range without an end date is open-ended`() {
        val transactions = listOf(
            transaction(date = LocalDate.of(2026, 10, 1)),
            transaction(date = LocalDate.of(2026, 12, 1))
        )
        val state = TransactionFilterState(
            preset = DatePreset.CUSTOM,
            customStart = LocalDate.of(2026, 10, 1)
        )

        val result = TransactionFilters.apply(transactions, state, now, zone)

        assertEquals(2, result.size)
    }

    @Test
    fun `filters by category`() {
        val transactions = listOf(
            transaction(category = Category.GROCERIES),
            transaction(merchant = "Uber", category = Category.TRANSPORT)
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(category = Category.TRANSPORT),
            now,
            zone
        )

        assertEquals(1, result.size)
        assertEquals("Uber", result.first().merchant)
    }

    @Test
    fun `filters by account`() {
        val transactions = listOf(
            transaction(account = "SAB 001"),
            transaction(account = "SAB 060")
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(account = "SAB 060"),
            now,
            zone
        )

        assertEquals(1, result.size)
        assertEquals("SAB 060", result.first().account)
    }

    @Test
    fun `filters by merchant ignoring case`() {
        val transactions = listOf(
            transaction(merchant = "Carrefour"),
            transaction(merchant = "Uber", category = Category.TRANSPORT)
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(merchantQuery = "carre"),
            now,
            zone
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `currency filter isolates one currency`() {
        val transactions = listOf(
            transaction(),
            transaction().copy(currency = "USD")
        )

        val result = TransactionFilters.apply(
            transactions,
            TransactionFilterState(currency = "EGP"),
            now,
            zone
        )

        assertEquals(1, result.size)
        assertEquals("EGP", result.single().currency)
    }

}
