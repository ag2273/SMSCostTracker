package com.example.smscosttracker.analysis

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFiltersTest {
    private val now = YearMonth.of(2026, 10)

    @Test
    fun `month period covers the selected calendar month`() {
        val filter = HomeFilterState(
            unit = HomePeriodUnit.MONTH,
            anchor = YearMonth.of(2026, 9)
        )

        assertEquals(LocalDate.of(2026, 9, 1), filter.periodStart)
        assertEquals(LocalDate.of(2026, 10, 1), filter.periodEndExclusive)
        assertEquals("September 2026", filter.periodLabel())
    }

    @Test
    fun `quarter period covers three months from the quarter start`() {
        val filter = HomeFilterState(
            unit = HomePeriodUnit.QUARTER,
            anchor = YearMonth.of(2026, 11)
        )

        assertEquals(LocalDate.of(2026, 10, 1), filter.periodStart)
        assertEquals(LocalDate.of(2027, 1, 1), filter.periodEndExclusive)
        assertEquals("Q4 2026", filter.periodLabel())
    }

    @Test
    fun `shifting a quarter moves by three months`() {
        val filter = HomeFilterState(
            unit = HomePeriodUnit.QUARTER,
            anchor = YearMonth.of(2026, 11)
        ).shiftPeriod(-1)

        assertEquals(YearMonth.of(2026, 8), filter.anchor)
        assertEquals(LocalDate.of(2026, 7, 1), filter.periodStart)
    }

    @Test
    fun `cannot move forward beyond the current month or quarter`() {
        val currentMonth = HomeFilterState(unit = HomePeriodUnit.MONTH, anchor = now)
        val previousMonth = currentMonth.shiftPeriod(-1)
        val currentQuarter = HomeFilterState(unit = HomePeriodUnit.QUARTER, anchor = now)
        val previousQuarter = currentQuarter.shiftPeriod(-1)

        assertFalse(currentMonth.canMoveForward(now))
        assertTrue(previousMonth.canMoveForward(now))
        assertFalse(currentQuarter.canMoveForward(now))
        assertTrue(previousQuarter.canMoveForward(now))
    }

    @Test
    fun `spend label distinguishes current and past periods`() {
        val currentMonth = HomeFilterState(unit = HomePeriodUnit.MONTH, anchor = now)
        val previousMonth = currentMonth.shiftPeriod(-1)
        val currentQuarter = HomeFilterState(unit = HomePeriodUnit.QUARTER, anchor = now)
        val previousQuarter = currentQuarter.shiftPeriod(-1)

        assertEquals("SPENT THIS MONTH", currentMonth.spendLabel(now))
        assertEquals("SPENT IN SEP 2026", previousMonth.spendLabel(now))
        assertEquals("SPENT THIS QUARTER", currentQuarter.spendLabel(now))
        assertEquals("SPENT IN Q3 2026", previousQuarter.spendLabel(now))
    }
}
