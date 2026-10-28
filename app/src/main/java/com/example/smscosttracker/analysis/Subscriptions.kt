package com.example.smscosttracker.analysis

import java.time.LocalDate

/** The status pill on a subscription row. Late and Ended outrank a price change. */
enum class SubscriptionPill(val label: String) {
    ACTIVE("Active"),
    LATE("Late"),
    ENDED("Ended"),
    PRICE_CHANGED("Price changed")
}

data class SubscriptionTotal(val currency: String, val monthlyMinor: Long, val count: Int)

/** Turns detected recurring series into what the Subscriptions screen and Home card show. */
object Subscriptions {
    const val DUE_SOON_DAYS = 7L
    const val ENDED_SHOWN_MONTHS = 12L

    /** What a series costs per month: yearly and quarterly spread out, weekly times 52 / 12. */
    fun monthlyMinor(series: RecurringSeries): Long = when (series.cycle) {
        RecurringCycle.WEEKLY -> series.typicalMinor * 52 / 12
        RecurringCycle.MONTHLY -> series.typicalMinor
        RecurringCycle.QUARTERLY -> series.typicalMinor / 3
        RecurringCycle.YEARLY -> series.typicalMinor / 12
    }

    /** A price change shows until a payment after the first new-price one comes in. */
    fun pill(series: RecurringSeries): SubscriptionPill = when (series.status) {
        RecurringStatus.LATE -> SubscriptionPill.LATE
        RecurringStatus.ENDED -> SubscriptionPill.ENDED
        RecurringStatus.ACTIVE -> {
            val change = series.priceChange
            val recent = change != null && !change.on.isBefore(series.cycle.advance(series.lastDate, -1))
            if (recent) SubscriptionPill.PRICE_CHANGED else SubscriptionPill.ACTIVE
        }
    }

    /** Series that are still being paid: active or late, not ended. */
    fun current(series: List<RecurringSeries>): List<RecurringSeries> =
        series.filter { it.status != RecurringStatus.ENDED }.sortedWith(compareBy({ it.nextExpected }, { it.merchant }))

    /** Series that stopped within the last year; older ones are history, not subscriptions. */
    fun ended(series: List<RecurringSeries>, today: LocalDate): List<RecurringSeries> {
        val since = today.minusMonths(ENDED_SHOWN_MONTHS)
        return series.filter { it.status == RecurringStatus.ENDED && !it.lastDate.isBefore(since) }
            .sortedByDescending { it.lastDate }
    }

    /** Monthly totals of current series, one per currency and never mixed, largest first. */
    fun monthlyTotals(series: List<RecurringSeries>): List<SubscriptionTotal> =
        current(series).groupBy { it.currency }
            .map { (currency, rows) -> SubscriptionTotal(currency, rows.sumOf { monthlyMinor(it) }, rows.size) }
            .sortedWith(compareByDescending<SubscriptionTotal> { it.monthlyMinor }.thenBy { it.currency })

    /** Current series whose next payment falls in the coming week, today included. */
    fun dueSoon(series: List<RecurringSeries>, today: LocalDate): List<RecurringSeries> {
        val end = today.plusDays(DUE_SOON_DAYS - 1)
        return current(series).filter { !it.nextExpected.isBefore(today) && !it.nextExpected.isAfter(end) }
    }
}
