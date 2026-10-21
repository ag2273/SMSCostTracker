package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class CategoryTotal(val category: Category, val amountMinor: Long)
data class MonthlyTotal(val month: YearMonth, val amountMinor: Long)
data class MerchantTotal(val merchant: String, val amountMinor: Long)
data class AccountTotal(val account: String, val amountMinor: Long)
data class SpendingSummary(
    val totalMinor: Long,
    val currency: String,
    val byCategory: List<CategoryTotal>,
    val byAccount: List<AccountTotal>,
    val byMonth: List<MonthlyTotal>,
    val topMerchants: List<MerchantTotal>
)

object SpendingAnalysis {
    const val MAX_MONTHS = 12

    fun summarizeByCurrency(
        transactions: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        maxMonths: Int = MAX_MONTHS,
        topMerchantCount: Int = 5
    ): List<SpendingSummary> =
        transactions.groupBy { it.currency }
            .map { (_, rows) ->
                summarize(rows, zone, maxMonths, topMerchantCount)
            }
            .sortedByDescending { it.totalMinor }

    fun collapseIntoOther(rows: List<CategoryTotal>, maxRows: Int): List<CategoryTotal> {
        require(maxRows > 0) { "maxRows must be positive" }
        val sorted = rows.sortedByDescending { it.amountMinor }
        if (sorted.size <= maxRows) return sorted

        val head = sorted.take(maxRows - 1)
        val tailTotal = sorted.drop(maxRows - 1).sumOf { it.amountMinor }
        return if (head.any { it.category == Category.OTHER }) {
            head.map {
                if (it.category == Category.OTHER) {
                    it.copy(amountMinor = it.amountMinor + tailTotal)
                } else {
                    it
                }
            }
        } else {
            head + CategoryTotal(Category.OTHER, tailTotal)
        }
    }

    fun monthlyTrend(
        transactions: List<TransactionEntity>,
        months: Int,
        now: YearMonth = YearMonth.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<MonthlyTotal> {
        require(months > 0) { "months must be positive" }
        val totals = transactions.monthTotals(zone)
        val start = now.minusMonths((months - 1).toLong())
        return generateSequence(start) { it.plusMonths(1) }
            .take(months)
            .map { MonthlyTotal(it, totals[it] ?: 0L) }
            .toList()
    }

    fun summarize(
        transactions: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        maxMonths: Int = MAX_MONTHS,
        topMerchantCount: Int = 5
    ): SpendingSummary {
        val byCategory = transactions
            .totalsBy { Category.fromStorage(it.category).displayName }
            .mapNotNull { (name, amount) ->
                Category.entries.firstOrNull { it.displayName == name }
                    ?.let { CategoryTotal(it, amount) }
            }

        val topMerchants = transactions
            .totalsBy { it.merchant }
            .map { (merchant, amount) -> MerchantTotal(merchant, amount) }
            .take(topMerchantCount)

        val byAccount = transactions
            .totalsBy { it.account.ifBlank { "Unknown account" } }
            .map { (account, amount) -> AccountTotal(account, amount) }

        val totals = transactions.monthTotals(zone)
        val latest = totals.keys.maxOrNull()
        val byMonth = if (latest == null) {
            emptyList()
        } else {
            val earliest = totals.keys.min()
            val start = maxOf(earliest, latest.minusMonths((maxMonths - 1).toLong()))
            generateSequence(start) { it.plusMonths(1) }
                .takeWhile { it <= latest }
                .map { MonthlyTotal(it, totals[it] ?: 0L) }
                .toList()
        }

        return SpendingSummary(
            totalMinor = transactions.sumOf { it.amountMinor },
            currency = transactions.firstOrNull()?.currency ?: "",
            byCategory = byCategory,
            byAccount = byAccount,
            byMonth = byMonth,
            topMerchants = topMerchants
        )
    }

    private fun List<TransactionEntity>.monthTotals(zone: ZoneId): Map<YearMonth, Long> =
        groupingBy { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }
            .fold(0L) { sum, transaction -> sum + transaction.amountMinor }

    private fun List<TransactionEntity>.totalsBy(
        key: (TransactionEntity) -> String
    ): List<Pair<String, Long>> =
        groupBy(key)
            .map { (group, rows) -> group to rows.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
}
