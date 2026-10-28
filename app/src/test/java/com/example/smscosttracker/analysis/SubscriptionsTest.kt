package com.example.smscosttracker.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SubscriptionsTest {
    private fun series(
        merchant: String = "Netflix",
        cycle: RecurringCycle = RecurringCycle.MONTHLY,
        typicalMinor: Long = 4_900,
        currency: String = "SAR",
        lastDate: LocalDate = LocalDate.of(2026, 10, 15),
        status: RecurringStatus = RecurringStatus.ACTIVE,
        priceChange: PriceChange? = null
    ) = RecurringSeries(
        key = "${merchant.lowercase()}|$currency",
        merchant = merchant,
        currency = currency,
        account = "",
        cycle = cycle,
        amountPattern = AmountPattern.FIXED,
        typicalMinor = typicalMinor,
        minMinor = typicalMinor,
        maxMinor = typicalMinor,
        firstDate = lastDate.minusYears(1),
        lastDate = lastDate,
        nextExpected = cycle.advance(lastDate, 1),
        status = status,
        priceChange = priceChange,
        confirmed = false,
        transactionIds = listOf(1, 2, 3)
    )

    @Test
    fun `monthly equivalent spreads longer cycles and scales weekly ones`() {
        assertEquals(4_900L, Subscriptions.monthlyMinor(series()))
        assertEquals(20_000L, Subscriptions.monthlyMinor(series(cycle = RecurringCycle.YEARLY, typicalMinor = 240_000)))
        assertEquals(60_000L, Subscriptions.monthlyMinor(series(cycle = RecurringCycle.QUARTERLY, typicalMinor = 180_000)))
        assertEquals(4_333L, Subscriptions.monthlyMinor(series(cycle = RecurringCycle.WEEKLY, typicalMinor = 1_000)))
    }

    @Test
    fun `totals keep currencies separate and leave out ended series`() {
        val all = listOf(
            series("Netflix", typicalMinor = 4_900),
            series("Gym", cycle = RecurringCycle.YEARLY, typicalMinor = 240_000),
            series("Shahid", typicalMinor = 2_500, status = RecurringStatus.LATE),
            series("Anghami", typicalMinor = 2_199, status = RecurringStatus.ENDED),
            series("Vodafone", typicalMinor = 65_000, currency = "EGP")
        )

        assertEquals(
            listOf(SubscriptionTotal("EGP", 65_000, 1), SubscriptionTotal("SAR", 27_400, 3)),
            Subscriptions.monthlyTotals(all)
        )
    }

    @Test
    fun `status pill shows late and ended first and a price change only while recent`() {
        val change = PriceChange(4_900, 5_900, LocalDate.of(2026, 9, 15))

        assertEquals(SubscriptionPill.ACTIVE, Subscriptions.pill(series()))
        assertEquals(SubscriptionPill.LATE, Subscriptions.pill(series(status = RecurringStatus.LATE, priceChange = change)))
        assertEquals(SubscriptionPill.ENDED, Subscriptions.pill(series(status = RecurringStatus.ENDED)))
        assertEquals(SubscriptionPill.PRICE_CHANGED, Subscriptions.pill(series(priceChange = change)))
        assertEquals(
            SubscriptionPill.PRICE_CHANGED,
            Subscriptions.pill(series(priceChange = change.copy(on = LocalDate.of(2026, 10, 15))))
        )
        assertEquals(
            SubscriptionPill.ACTIVE,
            Subscriptions.pill(series(priceChange = change.copy(on = LocalDate.of(2026, 8, 15))))
        )
    }

    @Test
    fun `due soon counts next payments in the coming seven days`() {
        val today = LocalDate.of(2026, 10, 28)
        val all = listOf(
            series("Today", lastDate = LocalDate.of(2026, 9, 28)),
            series("In six days", lastDate = LocalDate.of(2026, 10, 3)),
            series("In seven days", lastDate = LocalDate.of(2026, 10, 4)),
            series("Overdue", lastDate = LocalDate.of(2026, 9, 20), status = RecurringStatus.LATE),
            series("Ended", lastDate = LocalDate.of(2026, 9, 30), status = RecurringStatus.ENDED)
        )

        assertEquals(listOf("Today", "In six days"), Subscriptions.dueSoon(all, today).map { it.merchant })
    }

    @Test
    fun `current list is ordered by next payment and ended list by last payment`() {
        val all = listOf(
            series("Later", lastDate = LocalDate.of(2026, 10, 20)),
            series("Sooner", lastDate = LocalDate.of(2026, 10, 2)),
            series("Old", lastDate = LocalDate.of(2026, 3, 1), status = RecurringStatus.ENDED),
            series("Recent", lastDate = LocalDate.of(2026, 6, 1), status = RecurringStatus.ENDED),
            series("Years ago", lastDate = LocalDate.of(2018, 9, 7), status = RecurringStatus.ENDED)
        )

        assertEquals(listOf("Sooner", "Later"), Subscriptions.current(all).map { it.merchant })
        assertEquals(listOf("Recent", "Old"), Subscriptions.ended(all, LocalDate.of(2026, 10, 28)).map { it.merchant })
    }
}
