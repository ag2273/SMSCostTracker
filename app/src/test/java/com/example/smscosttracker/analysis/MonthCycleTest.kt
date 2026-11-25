package com.example.smscosttracker.analysis

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthCycleTest {
    @Test
    fun `calendar months are unchanged`() {
        val cycle = MonthCycle()
        assertEquals(YearMonth.of(2026, 10), cycle.monthOf(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2026, 10, 1), cycle.start(YearMonth.of(2026, 10)))
        assertNull(cycle.rangeLabel(YearMonth.of(2026, 10)))
    }

    @Test
    fun `a late start day names the month after the one it starts in`() {
        val cycle = MonthCycle(22)
        assertEquals(YearMonth.of(2026, 11), cycle.monthOf(LocalDate.of(2026, 11, 21)))
        assertEquals(YearMonth.of(2026, 12), cycle.monthOf(LocalDate.of(2026, 11, 22)))
        assertEquals(LocalDate.of(2026, 10, 22), cycle.start(YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2026, 11, 22), cycle.endExclusive(YearMonth.of(2026, 11)))
        assertEquals("22 Dec 2026 – 21 Jan 2027", cycle.rangeLabel(YearMonth.of(2027, 1)))
    }

    @Test
    fun `an early start day names the month it starts in`() {
        val cycle = MonthCycle(5)
        assertEquals(YearMonth.of(2026, 10), cycle.monthOf(LocalDate.of(2026, 11, 4)))
        assertEquals("5 Oct – 4 Nov", cycle.rangeLabel(YearMonth.of(2026, 10)))
    }

    @Test
    fun `stored days outside 1 to 28 are kept inside`() {
        assertEquals(28, MonthCycle.of(31).startDay)
        assertEquals(1, MonthCycle.of(0).startDay)
    }
}
