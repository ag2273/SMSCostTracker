package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.RecurringOverrideEntity
import com.example.smscosttracker.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class RecurringPaymentsTest {
    private val zone = ZoneOffset.UTC
    private var nextId = 1L

    private fun transaction(
        merchant: String,
        date: LocalDate,
        amountMinor: Long,
        currency: String = "SAR",
        account: String = "SAB 1214"
    ) = TransactionEntity(
        id = nextId++,
        amountMinor = amountMinor,
        currency = currency,
        merchant = merchant,
        occurredAt = date.atTime(10, 30).toInstant(zone).toEpochMilli(),
        sourceAddress = "SAB",
        category = "Entertainment",
        account = account
    )

    private fun monthly(
        merchant: String,
        start: LocalDate,
        amounts: List<Long>,
        account: String = "SAB 1214"
    ) = amounts.mapIndexed { i, amount -> transaction(merchant, start.plusMonths(i.toLong()), amount, account = account) }

    private fun detect(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        overrides: List<RecurringOverrideEntity> = emptyList()
    ) = RecurringPayments.detect(transactions, overrides, today, zone)

    @Test
    fun `monthly fixed subscription is found with its next date`() {
        val rows = monthly("Netflix", LocalDate.of(2026, 1, 15), List(6) { 4900L })
            .mapIndexed { i, row -> row.copy(occurredAt = row.occurredAt + (i % 3 - 1) * 86_400_000L) }

        val series = detect(rows, today = LocalDate.of(2026, 7, 1)).single()

        assertEquals("Netflix", series.merchant)
        assertEquals(RecurringCycle.MONTHLY, series.cycle)
        assertEquals(AmountPattern.FIXED, series.amountPattern)
        assertEquals(4900L, series.typicalMinor)
        assertEquals(6, series.occurrences)
        assertEquals(RecurringStatus.ACTIVE, series.status)
        assertEquals(series.lastDate.plusMonths(1), series.nextExpected)
        assertNull(series.priceChange)
        assertFalse(series.confirmed)
    }

    @Test
    fun `monthly variable bill reports typical amount and range`() {
        val amounts = listOf(31_000L, 42_500L, 58_000L, 61_200L, 47_300L, 29_900L)
        val series = detect(monthly("Saudi Electricity", LocalDate.of(2026, 1, 3), amounts), LocalDate.of(2026, 6, 20))
            .single()

        assertEquals(RecurringCycle.MONTHLY, series.cycle)
        assertEquals(AmountPattern.VARIABLE, series.amountPattern)
        assertEquals(44_900L, series.typicalMinor)
        assertEquals(29_900L, series.minMinor)
        assertEquals(61_200L, series.maxMinor)
    }

    @Test
    fun `yearly renewal needs only two fixed payments`() {
        val rows = listOf(
            transaction("iCloud Annual", LocalDate.of(2024, 3, 10), 39_900),
            transaction("iCloud Annual", LocalDate.of(2025, 3, 12), 39_900)
        )

        val series = detect(rows, LocalDate.of(2025, 9, 1)).single()

        assertEquals(RecurringCycle.YEARLY, series.cycle)
        assertEquals(LocalDate.of(2026, 3, 12), series.nextExpected)
        assertEquals(RecurringStatus.ACTIVE, series.status)
    }

    @Test
    fun `two different amounts a year apart are not a renewal`() {
        val rows = listOf(
            transaction("Hilton", LocalDate.of(2024, 7, 2), 120_000),
            transaction("Hilton", LocalDate.of(2025, 7, 1), 310_000)
        )

        assertTrue(detect(rows, LocalDate.of(2025, 8, 1)).isEmpty())
    }

    @Test
    fun `irregular merchant is not recurring`() {
        val days = listOf(0L, 3, 11, 12, 30, 44, 51, 80, 83, 97, 130, 141)
        val rows = days.mapIndexed { i, day ->
            transaction("Panda", LocalDate.of(2026, 1, 1).plusDays(day), 10_000L + i * 1_337)
        }

        assertTrue(detect(rows, LocalDate.of(2026, 6, 1)).isEmpty())
    }

    @Test
    fun `weekly shop visits with changing amounts are not a subscription`() {
        val rows = (0 until 10).map { week ->
            transaction("Tamimi Markets", LocalDate.of(2026, 1, 2).plusWeeks(week.toLong()), 15_000L + week * 2_100)
        }

        assertTrue(detect(rows, LocalDate.of(2026, 3, 15)).isEmpty())
    }

    @Test
    fun `weekly fixed payment is found`() {
        val rows = (0 until 5).map { week ->
            transaction("Careem Plus", LocalDate.of(2026, 1, 2).plusWeeks(week.toLong()), 1_900)
        }

        val series = detect(rows, LocalDate.of(2026, 2, 3)).single()
        assertEquals(RecurringCycle.WEEKLY, series.cycle)
    }

    @Test
    fun `skipped month makes the series late and two missed months end it`() {
        val rows = monthly("Shahid VIP", LocalDate.of(2026, 1, 5), List(5) { 2_500L })

        assertEquals(RecurringStatus.ACTIVE, detect(rows, LocalDate.of(2026, 6, 8)).single().status)
        assertEquals(RecurringStatus.LATE, detect(rows, LocalDate.of(2026, 6, 9)).single().status)
        assertEquals(RecurringStatus.LATE, detect(rows, LocalDate.of(2026, 7, 8)).single().status)
        assertEquals(RecurringStatus.ENDED, detect(rows, LocalDate.of(2026, 7, 9)).single().status)
    }

    @Test
    fun `one skipped month inside the history still counts as monthly`() {
        val rows = monthly("Spotify", LocalDate.of(2026, 1, 20), List(7) { 2_199L })
            .filterIndexed { i, _ -> i != 3 }

        val series = detect(rows, LocalDate.of(2026, 7, 25)).single()
        assertEquals(RecurringCycle.MONTHLY, series.cycle)
        assertEquals(6, series.occurrences)
    }

    @Test
    fun `price change keeps a fixed series and reports the new amount`() {
        val rows = monthly("Netflix", LocalDate.of(2026, 1, 15), listOf(4_900L, 4_900, 4_900, 4_900, 5_900, 5_900))

        val series = detect(rows, LocalDate.of(2026, 6, 20)).single()

        assertEquals(AmountPattern.FIXED, series.amountPattern)
        assertEquals(5_900L, series.typicalMinor)
        assertEquals(PriceChange(4_900, 5_900, LocalDate.of(2026, 5, 15)), series.priceChange)
    }

    @Test
    fun `one-off purchase at a subscription merchant is left out of the series`() {
        val rows = monthly("Apple.com/bill", LocalDate.of(2026, 1, 8), List(6) { 1_299L }) +
            transaction("Apple.com/bill", LocalDate.of(2026, 3, 22), 44_900)

        val series = detect(rows, LocalDate.of(2026, 6, 10)).single()
        assertEquals(6, series.occurrences)
        assertEquals(1_299L, series.maxMinor)
    }

    @Test
    fun `quarterly payment is found`() {
        val rows = (0 until 4).map { quarter ->
            transaction("Bupa Arabia", LocalDate.of(2025, 2, 1).plusMonths(3L * quarter), 180_000)
        }

        assertEquals(RecurringCycle.QUARTERLY, detect(rows, LocalDate.of(2026, 1, 15)).single().cycle)
    }

    @Test
    fun `merchant spelling variants and currencies are grouped separately by key`() {
        val sar = monthly("NETFLIX.COM", LocalDate.of(2026, 1, 15), List(3) { 4_900L })
        val usd = monthly("Netflix", LocalDate.of(2026, 1, 20), List(3) { 1_549L })
            .map { it.copy(currency = "USD") }

        val series = detect(sar + usd, LocalDate.of(2026, 3, 25))

        assertEquals(listOf("netflix|SAR", "netflix|USD"), series.map { it.key }.sorted())
    }

    @Test
    fun `two subscriptions at one merchant on different cards are split by account`() {
        val first = monthly("Google One", LocalDate.of(2026, 1, 3), List(4) { 1_099L }, account = "SAB 1214")
        val second = monthly("Google One", LocalDate.of(2026, 1, 18), List(4) { 3_799L }, account = "ANB 7781")

        val series = detect(first + second, LocalDate.of(2026, 4, 25))

        assertEquals(
            setOf("google one|SAR|SAB 1214", "google one|SAR|ANB 7781"),
            series.map { it.key }.toSet()
        )
    }

    @Test
    fun `dismissed series stays dismissed and confirmed series uses its new name`() {
        val netflix = monthly("Netflix", LocalDate.of(2026, 1, 15), List(4) { 4_900L })
        val gym = monthly("Fitness Time", LocalDate.of(2026, 1, 2), List(4) { 30_000L })
        val overrides = listOf(
            RecurringOverrideEntity("netflix|SAR", RecurringOverrideState.DISMISSED.name, updatedAt = 1),
            RecurringOverrideEntity("fitness time|SAR", RecurringOverrideState.CONFIRMED.name, "Gym", 2)
        )

        // A re-parse rebuilds transactions with new ids; the series key does not depend on them.
        val reparsed = (netflix + gym).map { it.copy(id = it.id + 1_000) }
        val series = detect(reparsed, LocalDate.of(2026, 4, 20), overrides).single()

        assertEquals("Gym", series.merchant)
        assertTrue(series.confirmed)
    }

    @Test
    fun `unknown merchant is never a series`() {
        val rows = monthly("Unknown", LocalDate.of(2026, 1, 15), List(6) { 4_900L })
        assertTrue(detect(rows, LocalDate.of(2026, 6, 20)).isEmpty())
    }

    private fun letters(n: Int) = "${'a' + n / 26}${'a' + n % 26}"

    @Test
    fun `runs on five thousand transactions in under 200 ms`() {
        val random = Random(42)
        val start = LocalDate.of(2024, 1, 1)
        val shopping = (0 until 4_500).map {
            transaction(
                merchant = "Shop " + letters(random.nextInt(300)),
                date = start.plusDays(random.nextLong(730)),
                amountMinor = random.nextLong(500, 50_000)
            )
        }
        val subscriptions = (0 until 21).flatMap { s ->
            monthly("Service " + letters(s), start.plusDays(s.toLong()), List(24) { 1_000L + s })
        }
        val rows = (shopping + subscriptions).take(5_000).shuffled(random)
        val today = LocalDate.of(2026, 1, 5)

        detect(rows, today)
        val best = (1..3).minOf {
            val began = System.nanoTime()
            detect(rows, today)
            (System.nanoTime() - began) / 1_000_000
        }

        assertTrue("took $best ms", best < 200)
        assertEquals(20, detect(rows, today).count { it.merchant.startsWith("Service") && it.occurrences == 24 })
    }
}
