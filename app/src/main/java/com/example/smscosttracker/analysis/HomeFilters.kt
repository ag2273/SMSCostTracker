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
    val currencyInitialized: Boolean = false
) {
    val periodStart: LocalDate
        get() {
            val startMonth = when (unit) {
                HomePeriodUnit.MONTH -> anchor.monthValue
                HomePeriodUnit.QUARTER -> ((anchor.monthValue - 1) / 3) * 3 + 1
            }
            return LocalDate.of(anchor.year, startMonth, 1)
        }

    val periodEndExclusive: LocalDate
        get() = periodStart.plusMonths(if (unit == HomePeriodUnit.MONTH) 1 else 3)

    fun periodLabel(): String = when (unit) {
        HomePeriodUnit.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        HomePeriodUnit.QUARTER -> "Q${(anchor.monthValue - 1) / 3 + 1} ${anchor.year}"
    }

    fun spendLabel(now: YearMonth = YearMonth.now()): String {
        val currentStart = HomeFilterState(unit = unit, anchor = now).periodStart
        return when {
            periodStart == currentStart && unit == HomePeriodUnit.MONTH -> "SPENT THIS MONTH"
            periodStart == currentStart && unit == HomePeriodUnit.QUARTER -> "SPENT THIS QUARTER"
            unit == HomePeriodUnit.MONTH -> {
                val month = anchor.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    .uppercase(Locale.ENGLISH)
                "SPENT IN $month ${anchor.year}"
            }
            else -> "SPENT IN ${periodLabel().uppercase(Locale.ENGLISH)}"
        }
    }

    fun shiftPeriod(steps: Long): HomeFilterState = copy(
        anchor = when (unit) {
            HomePeriodUnit.MONTH -> anchor.plusMonths(steps)
            HomePeriodUnit.QUARTER -> anchor.plusMonths(steps * 3)
        }
    )

    fun canMoveForward(now: YearMonth = YearMonth.now()): Boolean {
        val currentStart = HomeFilterState(unit = unit, anchor = now).periodStart
        return periodStart < currentStart
    }
}

data class ActivityFilterState(
    val filter: TransactionFilterState = TransactionFilterState(preset = DatePreset.THIS_MONTH),
    val currencyInitialized: Boolean = false
)
