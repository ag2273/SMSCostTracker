package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingAnalysisTest {
    private val zone = ZoneId.of("UTC")

    private fun transaction(
        amount: Long,
        category: Category,
        merchant: String,
        date: LocalDate,
        account: String = "SAB 001"
    ) = TransactionEntity(
        amountMinor = amount,
        currency = "EGP",
        merchant = merchant,
        occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        sourceAddress = "BANK",
        category = category.displayName,
        account = account
    )

    @Test
    fun `sums totals per category and sorts descending`() {
        val summary = SpendingAnalysis.summarize(
            listOf(
                transaction(1000L, Category.GROCERIES, "Carrefour", LocalDate.of(2026, 10, 1)),
                transaction(500L, Category.GROCERIES, "Panda", LocalDate.of(2026, 10, 2)),
                transaction(2000L, Category.TRANSPORT, "Uber", LocalDate.of(2026, 10, 3))
            ),
            zone
        )

        assertEquals(3500L, summary.totalMinor)
        assertEquals("EGP", summary.currency)
        assertEquals(Category.TRANSPORT, summary.byCategory.first().category)
        assertEquals(2000L, summary.byCategory.first().amountMinor)
        assertEquals(1500L, summary.byCategory[1].amountMinor)
    }

    @Test
    fun `monthly trend spans the filtered data capped at twelve months`() {
        val summary = SpendingAnalysis.summarize(
            listOf(
                transaction(1000L, Category.GROCERIES, "Carrefour", LocalDate.of(2026, 10, 1)),
                transaction(2000L, Category.GROCERIES, "Panda", LocalDate.of(2026, 8, 1)),
                transaction(9000L, Category.GROCERIES, "Old", LocalDate.of(2025, 1, 1))
            ),
            zone
        )

        assertEquals(12, summary.byMonth.size)
        assertEquals(YearMonth.of(2026, 10), summary.byMonth.last().month)
        assertEquals(1000L, summary.byMonth.last().amountMinor)
        assertEquals(3000L, summary.byMonth.sumOf { it.amountMinor })
    }

    @Test
    fun `monthly trend only covers months with transactions`() {
        val summary = SpendingAnalysis.summarize(
            listOf(
                transaction(1000L, Category.GROCERIES, "Carrefour", LocalDate.of(2026, 10, 1)),
                transaction(2000L, Category.GROCERIES, "Panda", LocalDate.of(2026, 8, 1))
            ),
            zone
        )

        assertEquals(3, summary.byMonth.size)
        assertEquals(YearMonth.of(2026, 8), summary.byMonth.first().month)
        assertEquals(2000L, summary.byMonth.first().amountMinor)
        assertEquals(0L, summary.byMonth[1].amountMinor)
    }

    @Test
    fun `splits totals per account`() {
        val summary = SpendingAnalysis.summarize(
            listOf(
                transaction(1000L, Category.GROCERIES, "Carrefour", LocalDate.of(2026, 10, 1), "SAB 001"),
                transaction(3000L, Category.GROCERIES, "Panda", LocalDate.of(2026, 10, 2), "SAB 060")
            ),
            zone
        )

        assertEquals(2, summary.byAccount.size)
        assertEquals("SAB 060", summary.byAccount.first().account)
        assertEquals(3000L, summary.byAccount.first().amountMinor)
    }

    @Test
    fun `lists top merchants by spend`() {
        val summary = SpendingAnalysis.summarize(
            listOf(
                transaction(1000L, Category.SHOPPING, "Amazon", LocalDate.of(2026, 10, 1)),
                transaction(3000L, Category.SHOPPING, "Noon", LocalDate.of(2026, 10, 2)),
                transaction(2000L, Category.SHOPPING, "Amazon", LocalDate.of(2026, 10, 3))
            ),
            zone,
            topMerchantCount = 2
        )

        assertEquals(2, summary.topMerchants.size)
        assertEquals("Amazon", summary.topMerchants.first().merchant)
        assertEquals(3000L, summary.topMerchants.first().amountMinor)
    }

    @Test
    fun `splits summaries per currency instead of adding them`() {
        val summaries = SpendingAnalysis.summarizeByCurrency(
            listOf(
                transaction(1000L, Category.SHOPPING, "Amazon", LocalDate.of(2026, 10, 1)),
                transaction(2000L, Category.SHOPPING, "Noon", LocalDate.of(2026, 10, 2))
                    .copy(currency = "USD")
            ),
            zone
        )

        assertEquals(2, summaries.size)
        assertEquals(2000L, summaries[0].totalMinor)
        assertEquals("USD", summaries[0].currency)
        assertEquals(1000L, summaries[1].totalMinor)
        assertEquals("EGP", summaries[1].currency)
    }

}
