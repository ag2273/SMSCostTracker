package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.MerchantNames
import com.example.smscosttracker.data.RecurringOverrideEntity
import com.example.smscosttracker.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class RecurringCycle(val label: String, val toleranceDays: Long, val minOccurrences: Int) {
    WEEKLY("Weekly", 1, 3),
    MONTHLY("Monthly", 3, 3),
    QUARTERLY("Quarterly", 5, 3),
    YEARLY("Yearly", 7, 2);

    fun advance(date: LocalDate, cycles: Long): LocalDate = when (this) {
        WEEKLY -> date.plusWeeks(cycles)
        MONTHLY -> date.plusMonths(cycles)
        QUARTERLY -> date.plusMonths(3 * cycles)
        YEARLY -> date.plusYears(cycles)
    }
}

/** Fixed: every charge within ±5% of its neighbours, apart from price changes. Variable: utilities and the like. */
enum class AmountPattern { FIXED, VARIABLE }

/** Active: next payment not yet overdue. Late: one expected payment missed. Ended: two or more missed. */
enum class RecurringStatus { ACTIVE, LATE, ENDED }

enum class RecurringOverrideState { CONFIRMED, DISMISSED }

data class PriceChange(val fromMinor: Long, val toMinor: Long, val on: LocalDate)

data class RecurringSeries(
    /** Stable across re-parses: merchant key, currency and, when split by card, the account. */
    val key: String,
    val merchant: String,
    val currency: String,
    val account: String,
    val cycle: RecurringCycle,
    val amountPattern: AmountPattern,
    val typicalMinor: Long,
    val minMinor: Long,
    val maxMinor: Long,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val nextExpected: LocalDate,
    val status: RecurringStatus,
    val priceChange: PriceChange?,
    val confirmed: Boolean,
    val transactionIds: List<Long>
) {
    val occurrences: Int get() = transactionIds.size
}

/**
 * Finds payments that repeat on a weekly, monthly, quarterly or yearly cycle. Transactions are
 * grouped by merchant key and currency, and split by account only when the merchant as a whole
 * does not repeat (two subscriptions at one merchant on different cards).
 */
object RecurringPayments {
    private const val FIXED_TOLERANCE = 0.05
    private const val MAX_SKIPPED_CYCLES = 2L
    private const val CHAIN_STARTS = 3

    private class Occurrence(val date: LocalDate, val transaction: TransactionEntity)

    private class Chain(val cycle: RecurringCycle, val kept: List<Occurrence>, val extras: Int)

    fun detect(
        transactions: List<TransactionEntity>,
        overrides: List<RecurringOverrideEntity> = emptyList(),
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<RecurringSeries> {
        val overrideByKey = overrides.associateBy { it.seriesKey }
        val series = transactions
            .groupBy { MerchantNames.key(it.merchant) to it.currency }
            .filterKeys { (merchantKey, _) -> merchantKey.isNotEmpty() && merchantKey != "unknown" }
            .flatMap { (group, rows) ->
                val baseKey = "${group.first}|${group.second}"
                val occurrences = rows.map { Occurrence(it.occurredAt.toLocalDate(zone), it) }
                    .sortedWith(compareBy({ it.date }, { it.transaction.occurredAt }))
                val whole = seriesFor(occurrences, baseKey, "", today)
                if (whole != null) return@flatMap listOf(whole)
                occurrences.groupBy { it.transaction.account }
                    .filterKeys { it.isNotBlank() }
                    .takeIf { it.size > 1 }
                    ?.mapNotNull { (account, subset) -> seriesFor(subset, "$baseKey|$account", account, today) }
                    .orEmpty()
            }
        return series
            .mapNotNull { found ->
                val override = overrideByKey[found.key]
                when (RecurringOverrideState.entries.firstOrNull { it.name == override?.state }) {
                    RecurringOverrideState.DISMISSED -> null
                    RecurringOverrideState.CONFIRMED -> found.copy(
                        merchant = override?.name ?: found.merchant,
                        confirmed = true
                    )
                    null -> found
                }
            }
            .sortedWith(compareBy<RecurringSeries>({ it.status }, { it.merchant.lowercase() }, { it.key }))
    }

    private fun seriesFor(
        occurrences: List<Occurrence>,
        key: String,
        account: String,
        today: LocalDate
    ): RecurringSeries? {
        if (occurrences.size < RecurringCycle.YEARLY.minOccurrences) return null
        val chain = RecurringCycle.entries
            .mapNotNull { bestChain(occurrences, it) }
            .maxWithOrNull(compareBy<Chain>({ it.kept.size }, { -it.extras }))
            ?: return null
        val amounts = chain.kept.map { it.transaction.amountMinor }
        val runs = priceRuns(amounts)
        val fixed = runs.size <= 3 && runs.dropLast(1).all { it.size >= 2 }
        // Two charges a year apart, or a weekly shop visit, only count when the amount is fixed.
        if (!fixed && (chain.kept.size < 3 || chain.cycle == RecurringCycle.WEEKLY)) return null

        val first = chain.kept.first()
        val last = chain.kept.last()
        val next = chain.cycle.advance(last.date, 1)
        val tolerance = chain.cycle.toleranceDays
        val status = when {
            !today.isAfter(next.plusDays(tolerance)) -> RecurringStatus.ACTIVE
            !today.isAfter(chain.cycle.advance(last.date, 2).plusDays(tolerance)) -> RecurringStatus.LATE
            else -> RecurringStatus.ENDED
        }
        val priceChange = if (fixed && runs.size > 1) {
            val latest = runs.last()
            PriceChange(
                fromMinor = median(runs[runs.size - 2]),
                toMinor = median(latest),
                on = chain.kept[amounts.size - latest.size].date
            )
        } else {
            null
        }
        return RecurringSeries(
            key = key,
            merchant = last.transaction.merchant,
            currency = last.transaction.currency,
            account = account,
            cycle = chain.cycle,
            amountPattern = if (fixed) AmountPattern.FIXED else AmountPattern.VARIABLE,
            typicalMinor = if (fixed) median(runs.last()) else median(amounts),
            minMinor = amounts.min(),
            maxMinor = amounts.max(),
            firstDate = first.date,
            lastDate = last.date,
            nextExpected = next,
            status = status,
            priceChange = priceChange,
            confirmed = false,
            transactionIds = chain.kept.map { it.transaction.id }
        )
    }

    /**
     * Walks the occurrences from each of the first few as a start, keeping each one that lands a
     * cycle (or two, for a skipped payment) after the last kept one. Occurrences in between are
     * extras, such as a one-off purchase at a subscription merchant.
     */
    private fun bestChain(occurrences: List<Occurrence>, cycle: RecurringCycle): Chain? {
        var best: Chain? = null
        for (start in 0 until minOf(CHAIN_STARTS, occurrences.size)) {
            val kept = mutableListOf(occurrences[start])
            var single = 0
            var skipped = 0
            for (i in start + 1 until occurrences.size) {
                val date = occurrences[i].date
                val previous = kept.last().date
                val step = (1L..MAX_SKIPPED_CYCLES).firstOrNull { cycles ->
                    abs(ChronoUnit.DAYS.between(cycle.advance(previous, cycles), date)) <= cycle.toleranceDays
                }
                when {
                    step == 1L -> single++
                    step != null -> skipped++
                    date.isAfter(cycle.advance(previous, MAX_SKIPPED_CYCLES).plusDays(cycle.toleranceDays)) -> break
                    else -> continue
                }
                kept += occurrences[i]
            }
            val extras = occurrences.size - kept.size
            val valid = kept.size >= cycle.minOccurrences &&
                extras * 4 <= kept.size &&
                skipped <= single / 2
            if (valid && (best == null || kept.size > best.kept.size)) best = Chain(cycle, kept, extras)
        }
        return best
    }

    /** Splits amounts into runs whose members stay within ±5% of the run's first amount. */
    private fun priceRuns(amounts: List<Long>): List<List<Long>> {
        val runs = mutableListOf<MutableList<Long>>()
        for (amount in amounts) {
            val run = runs.lastOrNull()
            if (run != null && abs(amount - run.first()) <= run.first() * FIXED_TOLERANCE) {
                run += amount
            } else {
                runs += mutableListOf(amount)
            }
        }
        return runs
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
