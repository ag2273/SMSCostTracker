package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.RecurringOverrideEntity
import com.example.smscosttracker.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringAlertsTest {
    private val zone = ZoneOffset.UTC
    private val all = AlertType.entries.toSet()
    private var nextId = 1L

    private fun monthly(merchant: String, start: LocalDate, amounts: List<Long>) = amounts.mapIndexed { i, amount ->
        TransactionEntity(
            id = nextId++,
            amountMinor = amount,
            currency = "SAR",
            merchant = merchant,
            occurredAt = start.plusMonths(i.toLong()).atTime(10, 0).toInstant(zone).toEpochMilli(),
            sourceAddress = "SAB",
            category = "Entertainment"
        )
    }

    private fun confirmed(key: String) =
        RecurringOverrideEntity(key, RecurringOverrideState.CONFIRMED.name, updatedAt = 1)

    private fun series(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        overrides: List<RecurringOverrideEntity>
    ) = RecurringPayments.detect(transactions, overrides, today, zone)

    private fun alerts(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        overrides: List<RecurringOverrideEntity> = listOf(confirmed("netflix|SAR")),
        enabled: Set<AlertType> = all
    ) = RecurringAlerts.due(series(transactions, today, overrides), today, enabled)

    private val netflix = monthly("Netflix", LocalDate.of(2026, 5, 15), List(5) { 4_900L })

    @Test
    fun `late alert waits three days past the due date`() {
        // Last payment Sep 15, so Oct 15 is due.
        assertTrue(alerts(netflix, LocalDate.of(2026, 10, 17)).none { it.type == AlertType.LATE })

        val late = alerts(netflix, LocalDate.of(2026, 10, 18)).single { it.type == AlertType.LATE }
        assertEquals(LocalDate.of(2026, 10, 15), late.date)
        assertEquals("late|netflix|SAR|2026-10-15", late.id)
    }

    @Test
    fun `no late alert once the series has ended`() {
        assertTrue(alerts(netflix, LocalDate.of(2026, 11, 20)).none { it.type == AlertType.LATE })
    }

    @Test
    fun `price increase and decrease both alert once per change`() {
        val up = monthly("Netflix", LocalDate.of(2026, 5, 15), listOf(4_900L, 4_900, 4_900, 4_900, 5_900))
        val upAlert = alerts(up, LocalDate.of(2026, 9, 20)).single { it.type == AlertType.PRICE_CHANGED }
        assertEquals(PriceChange(4_900, 5_900, LocalDate.of(2026, 9, 15)), upAlert.series.priceChange)
        assertEquals("price|netflix|SAR|2026-09-15", upAlert.id)

        // A second payment at the new price keeps the same id, so no new notification.
        val again = monthly("Netflix", LocalDate.of(2026, 5, 15), listOf(4_900L, 4_900, 4_900, 4_900, 5_900, 5_900))
        assertEquals(upAlert.id, alerts(again, LocalDate.of(2026, 10, 18)).single { it.type == AlertType.PRICE_CHANGED }.id)

        val down = monthly("Netflix", LocalDate.of(2026, 5, 15), listOf(4_900L, 4_900, 4_900, 4_900, 3_900))
        assertEquals(1, alerts(down, LocalDate.of(2026, 9, 20)).count { it.type == AlertType.PRICE_CHANGED })
    }

    @Test
    fun `small price wobble and variable bills do not alert`() {
        val wobble = monthly("Netflix", LocalDate.of(2026, 5, 15), listOf(4_900L, 4_900, 4_900, 4_900, 5_050))
        assertTrue(alerts(wobble, LocalDate.of(2026, 9, 20)).none { it.type == AlertType.PRICE_CHANGED })

        val electricity = monthly("Electricity", LocalDate.of(2026, 5, 3), listOf(31_000L, 42_500, 58_000, 61_200, 29_900))
        assertTrue(
            alerts(electricity, LocalDate.of(2026, 9, 10), listOf(confirmed("electricity|SAR")))
                .none { it.type == AlertType.PRICE_CHANGED }
        )
    }

    @Test
    fun `due soon fires the day before`() {
        assertTrue(alerts(netflix, LocalDate.of(2026, 10, 13)).none { it.type == AlertType.DUE_SOON })
        val due = alerts(netflix, LocalDate.of(2026, 10, 14)).single { it.type == AlertType.DUE_SOON }
        assertEquals("due|netflix|SAR|2026-10-15", due.id)
    }

    @Test
    fun `unconfirmed and dismissed series never alert`() {
        val today = LocalDate.of(2026, 10, 18)
        assertTrue(alerts(netflix, today, overrides = emptyList()).isEmpty())
        assertTrue(
            alerts(
                netflix,
                today,
                overrides = listOf(RecurringOverrideEntity("netflix|SAR", RecurringOverrideState.DISMISSED.name, updatedAt = 1))
            ).isEmpty()
        )
    }

    @Test
    fun `only enabled alert types are returned`() {
        val today = LocalDate.of(2026, 10, 18)
        assertTrue(alerts(netflix, today, enabled = setOf(AlertType.DUE_SOON)).isEmpty())
        assertEquals(listOf(AlertType.LATE), alerts(netflix, today, enabled = setOf(AlertType.LATE)).map { it.type })
    }
}
