package com.example.smscosttracker.analysis

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class HomePeriodUnit { MONTH, QUARTER }

data class HomeFilterState(
    val unit: HomePeriodUnit = HomePeriodUnit.MONTH,
    val anchor: YearMonth = YearMonth.now(),
    val currency: String? = null,
    val account: String? = null,
    val currencyInitialized: Boolean = false,
    /** The day the user's month starts on; [anchor] is then a month as [MonthCycle] names it. */
    val monthStartDay: Int = 1
) {
    private val cycle: MonthCycle get() = MonthCycle.of(monthStartDay)

    /** The first month of the period. */
    private val firstMonth: YearMonth
        get() = when (unit) {
            HomePeriodUnit.MONTH -> anchor
            HomePeriodUnit.QUARTER -> YearMonth.of(anchor.year, ((anchor.monthValue - 1) / 3) * 3 + 1)
        }

    val periodStart: LocalDate
        get() = cycle.start(firstMonth)

    val periodEndExclusive: LocalDate
        get() = cycle.start(firstMonth.plusMonths(if (unit == HomePeriodUnit.MONTH) 1 else 3))

    private fun name(): String = when (unit) {
        HomePeriodUnit.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        HomePeriodUnit.QUARTER -> "Q${(anchor.monthValue - 1) / 3 + 1} ${anchor.year}"
    }

    /** "September 2026", with its dates when the month does not start on the 1st. */
    fun periodLabel(): String =
        cycle.rangeLabel(periodStart, periodEndExclusive)?.let { "${name()} · $it" } ?: name()

    /** The same filter on the period that contains [today], for when the start day changes. */
    fun current(today: LocalDate = LocalDate.now()): HomeFilterState = copy(anchor = cycle.monthOf(today))

    fun spendLabel(today: LocalDate = LocalDate.now()): String {
        val currentStart = current(today).periodStart
        return when {
            periodStart == currentStart && unit == HomePeriodUnit.MONTH -> "SPENT THIS MONTH"
            periodStart == currentStart && unit == HomePeriodUnit.QUARTER -> "SPENT THIS QUARTER"
            unit == HomePeriodUnit.MONTH -> {
                val month = anchor.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    .uppercase(Locale.ENGLISH)
                "SPENT IN $month ${anchor.year}"
            }
            else -> "SPENT IN ${name().uppercase(Locale.ENGLISH)}"
        }
    }

    fun shiftPeriod(steps: Long): HomeFilterState = copy(
        anchor = when (unit) {
            HomePeriodUnit.MONTH -> anchor.plusMonths(steps)
            HomePeriodUnit.QUARTER -> anchor.plusMonths(steps * 3)
        }
    )

    fun canMoveForward(today: LocalDate = LocalDate.now()): Boolean {
        return periodStart < current(today).periodStart
    }
}

data class ActivityFilterState(
    val filter: TransactionFilterState = TransactionFilterState(preset = DatePreset.THIS_MONTH),
    val currencyInitialized: Boolean = false
)
