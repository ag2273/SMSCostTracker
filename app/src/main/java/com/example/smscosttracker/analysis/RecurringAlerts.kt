package com.example.smscosttracker.analysis

import java.time.LocalDate

enum class AlertType { LATE, PRICE_CHANGED, DUE_SOON }

data class RecurringAlert(
    val type: AlertType,
    val series: RecurringSeries,
    /** Stable per event, so one late payment or one price change is announced only once. */
    val id: String,
    /** The payment the alert is about: when it was expected, or when the new price was charged. */
    val date: LocalDate
)

/** Decides which alerts confirmed subscriptions should raise today. Unconfirmed series never alert. */
object RecurringAlerts {
    const val LATE_AFTER_DAYS = 3L

    fun due(
        series: List<RecurringSeries>,
        today: LocalDate,
        enabled: Set<AlertType>,
        lateAfterDays: Long = LATE_AFTER_DAYS
    ): List<RecurringAlert> = series.filter { it.confirmed }.flatMap { item ->
        listOfNotNull(
            late(item, today, lateAfterDays).takeIf { AlertType.LATE in enabled },
            priceChanged(item).takeIf { AlertType.PRICE_CHANGED in enabled },
            dueSoon(item, today).takeIf { AlertType.DUE_SOON in enabled }
        )
    }

    /** The expected payment is [lateAfterDays] overdue; once a second one is missed the series has ended. */
    private fun late(series: RecurringSeries, today: LocalDate, lateAfterDays: Long): RecurringAlert? {
        if (series.status == RecurringStatus.ENDED) return null
        if (today.isBefore(series.nextExpected.plusDays(lateAfterDays))) return null
        return RecurringAlert(AlertType.LATE, series, "late|${series.key}|${series.nextExpected}", series.nextExpected)
    }

    /** A fixed-amount series whose latest charges moved more than 5% from the earlier price, up or down. */
    private fun priceChanged(series: RecurringSeries): RecurringAlert? {
        val change = series.priceChange ?: return null
        if (Subscriptions.pill(series) != SubscriptionPill.PRICE_CHANGED) return null
        return RecurringAlert(AlertType.PRICE_CHANGED, series, "price|${series.key}|${change.on}", change.on)
    }

    private fun dueSoon(series: RecurringSeries, today: LocalDate): RecurringAlert? {
        if (series.status != RecurringStatus.ACTIVE || series.nextExpected != today.plusDays(1)) return null
        return RecurringAlert(AlertType.DUE_SOON, series, "due|${series.key}|${series.nextExpected}", series.nextExpected)
    }
}
