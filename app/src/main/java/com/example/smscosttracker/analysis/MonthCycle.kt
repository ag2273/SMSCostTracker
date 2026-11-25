package com.example.smscosttracker.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The user's month: it starts on [startDay] (1–28) and ends the day before [startDay] of the next
 * month, for example 22 Oct – 21 Nov. A month is named after the calendar month most of its days
 * fall in, so with a start day after the 15th, 22 Oct – 21 Nov is "November".
 */
data class MonthCycle(val startDay: Int = 1) {
    init {
        require(startDay in 1..MAX_START_DAY) { "startDay must be 1..$MAX_START_DAY" }
    }

    private val offset: Long get() = if (startDay > 15) 1 else 0

    val isCalendar: Boolean get() = startDay == 1

    /** The month [date] belongs to. */
    fun monthOf(date: LocalDate): YearMonth {
        val starting = if (date.dayOfMonth >= startDay) YearMonth.from(date) else YearMonth.from(date).minusMonths(1)
        return starting.plusMonths(offset)
    }

    fun monthOf(epochMillis: Long, zone: ZoneId): YearMonth =
        monthOf(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    /** The first day of [month]. */
    fun start(month: YearMonth): LocalDate = month.minusMonths(offset).atDay(startDay)

    /** The day after the last day of [month]. */
    fun endExclusive(month: YearMonth): LocalDate = start(month.plusMonths(1))

    /** "22 Oct – 21 Nov", or null for calendar months. */
    fun rangeLabel(month: YearMonth): String? = rangeLabel(start(month), endExclusive(month))

    fun rangeLabel(start: LocalDate, endExclusive: LocalDate): String? {
        if (isCalendar) return null
        val last = endExclusive.minusDays(1)
        val format = if (start.year == last.year) SHORT else WITH_YEAR
        return "${start.format(format)} – ${last.format(format)}"
    }

    companion object {
        const val MAX_START_DAY = 28

        /** A stored day, kept inside 1–28 so every month has it. */
        fun of(startDay: Int): MonthCycle = MonthCycle(startDay.coerceIn(1, MAX_START_DAY))

        private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        private val WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}
