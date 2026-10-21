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
    fun `monthly trend ends at the current month including empty months`() {
        val trend = SpendingAnalysis.monthlyTrend(
            listOf(
                transaction(1000L, Category.GROCERIES, "Carrefour", LocalDate.of(2026, 8, 1)),
                transaction(2000L, Category.GROCERIES, "Panda", LocalDate.of(2026, 10, 1))
            ),
            months = 6,
            now = YearMonth.of(2026, 10),
            zone = zone
        )

        assertEquals(
            listOf(
                YearMonth.of(2026, 5),
                YearMonth.of(2026, 6),
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 10)
            ),
            trend.map { it.month }
        )
        assertEquals(0L, trend.first().amountMinor)
        assertEquals(1000L, trend[3].amountMinor)
        assertEquals(2000L, trend.last().amountMinor)
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
    fun `collapses the smaller categories into other`() {
        val rows = listOf(
            CategoryTotal(Category.GROCERIES, 5000L),
            CategoryTotal(Category.DINING, 4000L),
            CategoryTotal(Category.TRANSPORT, 3000L),
            CategoryTotal(Category.SHOPPING, 2000L),
            CategoryTotal(Category.HEALTH, 1000L)
        )

        val collapsed = SpendingAnalysis.collapseIntoOther(rows, maxRows = 4)

        assertEquals(4, collapsed.size)
        assertEquals(Category.GROCERIES, collapsed[0].category)
        assertEquals(Category.TRANSPORT, collapsed[2].category)
        assertEquals(Category.OTHER, collapsed.last().category)
        assertEquals(3000L, collapsed.last().amountMinor)
    }

    @Test
    fun `collapsing merges the tail into an existing other row`() {
        val rows = listOf(
            CategoryTotal(Category.GROCERIES, 5000L),
            CategoryTotal(Category.OTHER, 4000L),
            CategoryTotal(Category.DINING, 3000L),
            CategoryTotal(Category.TRANSPORT, 2000L),
            CategoryTotal(Category.SHOPPING, 1000L)
        )

        val collapsed = SpendingAnalysis.collapseIntoOther(rows, maxRows = 3)

        assertEquals(2, collapsed.size)
        assertEquals(1, collapsed.count { it.category == Category.OTHER })
        assertEquals(10000L, collapsed.first { it.category == Category.OTHER }.amountMinor)
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
