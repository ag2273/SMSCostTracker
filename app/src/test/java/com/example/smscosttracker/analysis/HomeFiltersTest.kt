package com.example.smscosttracker.analysis

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFiltersTest {
    private val now = YearMonth.of(2026, 10)
    private val today = now.atDay(15)

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

        assertFalse(currentMonth.canMoveForward(today))
        assertTrue(previousMonth.canMoveForward(today))
        assertFalse(currentQuarter.canMoveForward(today))
        assertTrue(previousQuarter.canMoveForward(today))
    }

    @Test
    fun `spend label distinguishes current and past periods`() {
        val currentMonth = HomeFilterState(unit = HomePeriodUnit.MONTH, anchor = now)
        val previousMonth = currentMonth.shiftPeriod(-1)
        val currentQuarter = HomeFilterState(unit = HomePeriodUnit.QUARTER, anchor = now)
        val previousQuarter = currentQuarter.shiftPeriod(-1)

        assertEquals("SPENT THIS MONTH", currentMonth.spendLabel(today))
        assertEquals("SPENT IN SEP 2026", previousMonth.spendLabel(today))
        assertEquals("SPENT THIS QUARTER", currentQuarter.spendLabel(today))
        assertEquals("SPENT IN Q3 2026", previousQuarter.spendLabel(today))
    }

    @Test
    fun `a month starting on the 22nd runs to the 21st and is named after its later month`() {
        val filter = HomeFilterState(monthStartDay = 22).current(LocalDate.of(2026, 11, 25))

        assertEquals(YearMonth.of(2026, 12), filter.anchor)
        assertEquals(LocalDate.of(2026, 11, 22), filter.periodStart)
        assertEquals(LocalDate.of(2026, 12, 22), filter.periodEndExclusive)
        assertEquals("December 2026 · 22 Nov – 21 Dec", filter.periodLabel())
        assertEquals("SPENT THIS MONTH", filter.spendLabel(LocalDate.of(2026, 12, 21)))
        assertFalse(filter.canMoveForward(LocalDate.of(2026, 12, 21)))
        assertTrue(filter.canMoveForward(LocalDate.of(2026, 12, 22)))
    }

    @Test
    fun `a quarter with a start day covers three of the user's months`() {
        val filter = HomeFilterState(unit = HomePeriodUnit.QUARTER, anchor = YearMonth.of(2026, 11), monthStartDay = 23)

        assertEquals(LocalDate.of(2026, 9, 23), filter.periodStart)
        assertEquals(LocalDate.of(2026, 12, 23), filter.periodEndExclusive)
        assertEquals("Q4 2026 · 23 Sep – 22 Dec", filter.periodLabel())
    }
}
