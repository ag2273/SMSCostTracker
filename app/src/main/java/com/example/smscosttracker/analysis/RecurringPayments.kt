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

    private class Walk(val kept: List<Occurrence>, val single: Int, val skipped: Int)

    private class Chain(
        val cycle: RecurringCycle,
        val kept: List<Occurrence>,
        /** Off-cycle payments inside the stretches that repeat; isolated payments elsewhere are not counted. */
        val extras: List<Occurrence>,
        val single: Int,
        val skipped: Int,
        val longestSegment: Int
    )

    /** [extraWeight] is two per off-cycle payment, one when its amount differs from a fixed price. */
    private class Candidate(val chain: Chain, val runs: List<List<Long>>, val fixed: Boolean, val extraWeight: Int)

    fun detect(
        transactions: List<TransactionEntity>,
        overrides: List<RecurringOverrideEntity> = emptyList(),
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<RecurringSeries> {
        val overrideByKey = overrides.associateBy { it.seriesKey }
        // A cycle the user chose ("Track as subscription") replaces detection for that series.
        fun forcedCycle(key: String): RecurringCycle? = overrideByKey[key]
            ?.takeIf { it.state == RecurringOverrideState.CONFIRMED.name }
            ?.cycle
            ?.let { cycle -> RecurringCycle.entries.firstOrNull { it.name == cycle } }

        val series = transactions
            .groupBy { MerchantNames.key(it.merchant) to it.currency }
            .filterKeys { (merchantKey, _) -> merchantKey.isNotEmpty() && merchantKey != "unknown" }
            .flatMap { (group, rows) ->
                val baseKey = "${group.first}|${group.second}"
                val occurrences = rows.map { Occurrence(it.occurredAt.toLocalDate(zone), it) }
                    .sortedWith(compareBy({ it.date }, { it.transaction.occurredAt }))
                val whole = seriesFor(occurrences, baseKey, "", today, forcedCycle(baseKey))
                if (whole != null) return@flatMap listOf(whole)
                occurrences.groupBy { it.transaction.account }
                    .filterKeys { it.isNotBlank() }
                    .takeIf { it.size > 1 }
                    ?.mapNotNull { (account, subset) ->
                        val key = "$baseKey|$account"
                        seriesFor(subset, key, account, today, forcedCycle(key))
                    }
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
        today: LocalDate,
        forced: RecurringCycle? = null
    ): RecurringSeries? {
        if (occurrences.isEmpty()) return null
        val candidate = if (forced != null) {
            candidate(chain(occurrences, forced).let { chain ->
                // The user said it repeats, so even a lone payment starts the series.
                if (chain.kept.isNotEmpty()) chain else Chain(forced, listOf(occurrences.last()), emptyList(), 0, 0, 1)
            })
        } else {
            if (occurrences.size < RecurringCycle.YEARLY.minOccurrences) return null
            RecurringCycle.entries
                .map { candidate(chain(occurrences, it)) }
                .filter { valid(it) }
                .maxWithOrNull(compareBy<Candidate>({ it.chain.kept.size }, { -it.extraWeight }))
                ?: return null
        }
        val chain = candidate.chain
        val runs = candidate.runs
        val fixed = candidate.fixed
        val amounts = chain.kept.map { it.transaction.amountMinor }

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

    private fun candidate(chain: Chain): Candidate {
        val runs = priceRuns(chain.kept.map { it.transaction.amountMinor })
        val fixed = runs.isNotEmpty() && runs.size <= 3 && runs.dropLast(1).all { it.size >= 2 }
        // For a fixed price, a payment of another amount (a partial payment, a one-off purchase)
        // weighs half as much against the series as an off-cycle payment of the same amount.
        val prices = runs.map { median(it) }
        val weight = chain.extras.map { extra ->
            if (fixed && prices.none { near(extra.transaction.amountMinor, it) }) 1 else 2
        }.sum()
        return Candidate(chain, runs, fixed, weight)
    }

    private fun valid(candidate: Candidate): Boolean {
        val chain = candidate.chain
        val cycle = chain.cycle
        // Two charges a year apart, or a weekly shop visit, only count when the amount is fixed.
        if (!candidate.fixed && (chain.kept.size < 3 || cycle == RecurringCycle.WEEKLY)) return false
        return chain.kept.size >= cycle.minOccurrences &&
            chain.longestSegment >= cycle.minOccurrences &&
            candidate.extraWeight * 2 <= chain.kept.size &&
            chain.skipped <= chain.single / 2
    }

    /**
     * Splits the occurrences wherever no payment came for more than two cycles (a paused
     * subscription) and walks each part, so a series that resumed is still found and current.
     * Parts with a single payment are left out.
     */
    private fun chain(occurrences: List<Occurrence>, cycle: RecurringCycle): Chain {
        val segments = mutableListOf(mutableListOf(occurrences.first()))
        for (i in 1 until occurrences.size) {
            val previous = occurrences[i - 1].date
            if (occurrences[i].date.isAfter(cycle.advance(previous, MAX_SKIPPED_CYCLES).plusDays(cycle.toleranceDays))) {
                segments += mutableListOf(occurrences[i])
            } else {
                segments.last() += occurrences[i]
            }
        }
        val walked = segments.map { it to bestWalk(it, cycle) }.filter { it.second.kept.size >= 2 }
        val walks = walked.map { it.second }
        val kept = walks.flatMap { it.kept }
        val keptIds = kept.map { it.transaction.id }.toHashSet()
        return Chain(
            cycle = cycle,
            kept = kept,
            extras = walked.flatMap { it.first }.filter { it.transaction.id !in keptIds },
            single = walks.sumOf { it.single },
            skipped = walks.sumOf { it.skipped },
            longestSegment = walks.maxOfOrNull { it.kept.size } ?: 0
        )
    }

    /**
     * Walks a part from each of its first few occurrences as a start, keeping each one that lands
     * a cycle (or two, for a skipped payment) after the last kept one. Occurrences in between are
     * extras, such as a one-off purchase at a subscription merchant.
     */
    private fun bestWalk(occurrences: List<Occurrence>, cycle: RecurringCycle): Walk {
        var best: Walk? = null
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
            if (best == null || kept.size > best.kept.size) best = Walk(kept, single, skipped)
        }
        return best!!
    }

    private fun near(amount: Long, price: Long): Boolean = abs(amount - price) <= price * FIXED_TOLERANCE

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
