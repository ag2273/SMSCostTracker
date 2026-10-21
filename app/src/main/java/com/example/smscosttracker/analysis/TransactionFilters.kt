package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DatePreset(val label: String) {
    ALL("All time"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_QUARTER("This quarter"),
    LAST_QUARTER("Last quarter"),
    CUSTOM("Custom range")
}

data class TransactionFilterState(
    val preset: DatePreset = DatePreset.ALL,
    val category: Category? = null,
    val account: String? = null,
    val merchantQuery: String = "",
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null
)

object TransactionFilters {
    fun apply(
        transactions: List<TransactionEntity>,
        state: TransactionFilterState,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TransactionEntity> {
        val range = rangeFor(state, now, zone)
        return transactions.filter { transaction ->
            val inRange = range == null ||
                (transaction.occurredAt >= range.first && transaction.occurredAt < range.second)
            val inCategory = state.category == null ||
                Category.fromStorage(transaction.category) == state.category
            val inAccount = state.account == null || transaction.account == state.account
            val matchesMerchant = state.merchantQuery.isBlank() ||
                transaction.merchant.contains(state.merchantQuery.trim(), ignoreCase = true)
            inRange && inCategory && inAccount && matchesMerchant
        }
    }

    fun rangeFor(
        state: TransactionFilterState,
        now: Long,
        zone: ZoneId
    ): Pair<Long, Long>? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (state.preset) {
            DatePreset.ALL -> null
            DatePreset.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                start.toRange(start.plusMonths(1), zone)
            }
            DatePreset.LAST_MONTH -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                start.toRange(start.plusMonths(1), zone)
            }
            DatePreset.THIS_QUARTER -> {
                val start = today.quarterStart()
                start.toRange(start.plusMonths(3), zone)
            }
            DatePreset.LAST_QUARTER -> {
                val start = today.quarterStart().minusMonths(3)
                start.toRange(start.plusMonths(3), zone)
            }
            DatePreset.CUSTOM -> {
                val start = state.customStart ?: return null
                val endExclusive = state.customEnd?.plusDays(1)
                val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = endExclusive?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                    ?: Long.MAX_VALUE
                startMillis to endMillis
            }
        }
    }

    private fun LocalDate.quarterStart(): LocalDate {
        val quarterStartMonth = ((monthValue - 1) / 3) * 3 + 1
        return LocalDate.of(year, quarterStartMonth, 1)
    }

    private fun LocalDate.toRange(endExclusive: LocalDate, zone: ZoneId): Pair<Long, Long> =
        atStartOfDay(zone).toInstant().toEpochMilli() to
            endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}
