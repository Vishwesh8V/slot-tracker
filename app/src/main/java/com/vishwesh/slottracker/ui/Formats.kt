package com.vishwesh.slottracker.ui

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date and time formats used by the app and widget (24-hour times, as in the spec). */
object Formats {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    // Built per call so a change of system language applies straight away.
    private fun pattern(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

    /** "Mon, 26 Oct" */
    fun shortDate(date: LocalDate): String = pattern("EEE, d MMM").format(date)

    /** "Monday" */
    fun weekday(date: LocalDate): String = pattern("EEEE").format(date)

    /** "26 October" */
    fun dayMonth(date: LocalDate): String = pattern("d MMMM").format(date)

    /** "Monday" for a weekday. */
    fun dayName(day: java.time.DayOfWeek, locale: Locale = Locale.getDefault()): String =
        day.getDisplayName(java.time.format.TextStyle.FULL, locale)

    /** "10:00" */
    fun time(time: LocalTime): String = clock.format(time)

    /** "10:00–13:00" */
    fun timeRange(start: LocalTime, end: LocalTime): String = "${time(start)}–${time(end)}"
}
