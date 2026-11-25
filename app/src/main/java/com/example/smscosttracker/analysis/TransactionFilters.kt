package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LABEL_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

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
    val currency: String? = null,
    val merchantQuery: String = "",
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val needsReview: Boolean = false
)

object TransactionFilters {
    fun labelFor(state: TransactionFilterState): String {
        val start = state.customStart
        if (state.preset != DatePreset.CUSTOM || start == null) return state.preset.label
        val startLabel = start.format(LABEL_DATE_FORMAT)
        return state.customEnd?.let { "$startLabel – ${it.format(LABEL_DATE_FORMAT)}" }
            ?: "From $startLabel"
    }

    fun apply(
        transactions: List<TransactionEntity>,
        state: TransactionFilterState,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        pendingInstrumentKeys: Set<String> = emptySet(),
        linkedAccounts: Map<String, String> = emptyMap(),
        monthStartDay: Int = 1
    ): List<TransactionEntity> {
        val range = rangeFor(state, now, zone, monthStartDay)
        return transactions.filter { transaction ->
            val inRange = range == null ||
                (transaction.occurredAt >= range.first && transaction.occurredAt < range.second)
            val inCategory = state.category == null ||
                Category.fromStorage(transaction.category) == state.category
            // An account also covers the debit cards linked to it.
            val inAccount = state.account == null || transaction.account == state.account ||
                linkedAccounts[transaction.account] == state.account
            val inCurrency = state.currency == null || transaction.currency == state.currency
            val matchesMerchant = state.merchantQuery.isBlank() ||
                transaction.merchant.contains(state.merchantQuery.trim(), ignoreCase = true)
            val reviewMatches = !state.needsReview ||
                TransactionReview.needsReview(transaction, pendingInstrumentKeys)
            inRange && inCategory && inAccount && inCurrency && matchesMerchant && reviewMatches
        }
    }

    fun rangeFor(
        state: TransactionFilterState,
        now: Long,
        zone: ZoneId,
        monthStartDay: Int = 1
    ): Pair<Long, Long>? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val cycle = MonthCycle.of(monthStartDay)
        val month = cycle.monthOf(today)
        val quarter = YearMonth.of(month.year, ((month.monthValue - 1) / 3) * 3 + 1)
        return when (state.preset) {
            DatePreset.ALL -> null
            DatePreset.THIS_MONTH -> cycle.start(month).toRange(cycle.endExclusive(month), zone)
            DatePreset.LAST_MONTH -> month.minusMonths(1).let { cycle.start(it).toRange(cycle.start(month), zone) }
            DatePreset.THIS_QUARTER -> cycle.start(quarter).toRange(cycle.start(quarter.plusMonths(3)), zone)
            DatePreset.LAST_QUARTER -> cycle.start(quarter.minusMonths(3)).toRange(cycle.start(quarter), zone)
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

    private fun LocalDate.toRange(endExclusive: LocalDate, zone: ZoneId): Pair<Long, Long> =
        atStartOfDay(zone).toInstant().toEpochMilli() to
            endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}
