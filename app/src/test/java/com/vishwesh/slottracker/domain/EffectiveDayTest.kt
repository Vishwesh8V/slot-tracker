package com.vishwesh.slottracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EffectiveDayTest {
    private val six = LocalTime.of(6, 0)

    @Test
    fun `before rollover is still the previous day (spec example)`() {
        // Tuesday 27 Oct 2026, 01:30 -> Monday 26 Oct
        val now = LocalDateTime.of(2026, 10, 27, 1, 30)
        assertEquals(LocalDate.of(2026, 10, 26), EffectiveDay.of(now, six))
    }

    @Test
    fun `exactly at rollover switches to the calendar date`() {
        val now = LocalDateTime.of(2026, 10, 27, 6, 0)
        assertEquals(LocalDate.of(2026, 10, 27), EffectiveDay.of(now, six))
    }

    @Test
    fun `one minute before rollover is the previous day`() {
        val now = LocalDateTime.of(2026, 10, 27, 5, 59, 59)
        assertEquals(LocalDate.of(2026, 10, 26), EffectiveDay.of(now, six))
    }

    @Test
    fun `midnight rollover follows the calendar`() {
        val now = LocalDateTime.of(2026, 10, 27, 0, 0)
        assertEquals(LocalDate.of(2026, 10, 27), EffectiveDay.of(now, LocalTime.MIDNIGHT))
    }

    @Test
    fun `crosses month and year boundaries`() {
        val now = LocalDateTime.of(2027, 1, 1, 3, 0)
        assertEquals(LocalDate.of(2026, 12, 31), EffectiveDay.of(now, six))
    }

    @Test
    fun `custom rollover time is honoured`() {
        val rollover = LocalTime.of(4, 30)
        assertEquals(LocalDate.of(2026, 10, 26), EffectiveDay.of(LocalDateTime.of(2026, 10, 27, 4, 29), rollover))
        assertEquals(LocalDate.of(2026, 10, 27), EffectiveDay.of(LocalDateTime.of(2026, 10, 27, 4, 30), rollover))
    }
}
