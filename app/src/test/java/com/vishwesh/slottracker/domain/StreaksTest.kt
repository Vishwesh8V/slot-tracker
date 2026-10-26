package com.vishwesh.slottracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val romanArt = 5L
    private val leetcode = 3L

    private fun occ(date: LocalDate, itemId: Long, done: Boolean, hour: Int = 20) =
        Occurrence(date, itemId, time(hour), done)

    @Test
    fun `Roman Art done every Tue and Thu for three weeks is a streak of 6 (spec example)`() {
        val history = (0L until 3L).flatMap { week ->
            val tue = MON.plusWeeks(week).plusDays(1)
            val thu = MON.plusWeeks(week).plusDays(3)
            listOf(
                occ(tue, romanArt, true),
                occ(thu, romanArt, true),
                // Other items missed on other days must not affect Roman Art.
                occ(MON.plusWeeks(week), leetcode, false),
            )
        }

        assertEquals(StreakResult(current = 6, best = 6), Streaks.forItem(romanArt, history, emptyList()))
    }

    @Test
    fun `a missed occurrence resets the current streak but best remembers the longest run`() {
        val history = listOf(true, true, true, false, true, true).mapIndexed { i, done ->
            occ(MON.plusDays(i.toLong()), leetcode, done)
        }

        assertEquals(StreakResult(current = 2, best = 3), Streaks.forItem(leetcode, history, emptyList()))
    }

    @Test
    fun `today's not-yet-done tasks do not break the streak`() {
        val history = listOf(occ(MON, leetcode, true), occ(MON.plusDays(1), leetcode, true))
        val today = listOf(occ(MON.plusDays(2), leetcode, false))

        assertEquals(2, Streaks.forItem(leetcode, history, today).current)
    }

    @Test
    fun `today's done tasks extend the streak`() {
        val history = listOf(occ(MON, leetcode, true))
        val today = listOf(occ(MON.plusDays(1), leetcode, true))

        assertEquals(StreakResult(current = 2, best = 2), Streaks.forItem(leetcode, history, today))
    }

    @Test
    fun `two occurrences on the same day count separately`() {
        val history = listOf(
            occ(MON, leetcode, true, hour = 10),
            occ(MON, leetcode, true, hour = 20),
            occ(MON.plusDays(1), leetcode, true, hour = 10),
            occ(MON.plusDays(1), leetcode, false, hour = 20),
            occ(MON.plusDays(2), leetcode, true, hour = 10),
        )

        assertEquals(StreakResult(current = 1, best = 3), Streaks.forItem(leetcode, history, emptyList()))
    }

    @Test
    fun `order within a day follows slot time, not input order`() {
        val history = listOf(
            occ(MON, leetcode, true, hour = 20),
            occ(MON, leetcode, false, hour = 10),
        )

        assertEquals(1, Streaks.forItem(leetcode, history, emptyList()).current)
    }

    @Test
    fun `editing a past entry changes the recomputed streak`() {
        val history = listOf(
            occ(MON, leetcode, true),
            occ(MON.plusDays(1), leetcode, false),
            occ(MON.plusDays(2), leetcode, true),
        )
        assertEquals(StreakResult(current = 1, best = 1), Streaks.forItem(leetcode, history, emptyList()))

        // User flips Tuesday from Missed to Done in History.
        val edited = history.map { if (it.date == MON.plusDays(1)) it.copy(done = true) else it }
        assertEquals(StreakResult(current = 3, best = 3), Streaks.forItem(leetcode, edited, emptyList()))
    }

    @Test
    fun `item with no history has no streak`() {
        assertEquals(StreakResult(0, 0), Streaks.forItem(99L, emptyList(), emptyList()))
    }

    @Test
    fun `overall streak counts days where every task was done and skips empty days`() {
        val history = listOf(
            occ(MON, leetcode, true, 10), occ(MON, romanArt, true, 20),
            // Tuesday: nothing scheduled (no entries) - skipped.
            occ(MON.plusDays(2), leetcode, true),
            occ(MON.plusDays(3), leetcode, true, 10), occ(MON.plusDays(3), romanArt, true, 20),
        )

        assertEquals(StreakResult(current = 3, best = 3), Streaks.overall(history, emptyList()))
    }

    @Test
    fun `overall streak breaks on a partial day`() {
        val history = listOf(
            occ(MON, leetcode, true),
            occ(MON.plusDays(1), leetcode, true, 10), occ(MON.plusDays(1), romanArt, false, 20),
            occ(MON.plusDays(2), leetcode, true),
        )

        assertEquals(StreakResult(current = 1, best = 1), Streaks.overall(history, emptyList()))
    }

    @Test
    fun `overall streak includes today only once everything today is done`() {
        val history = listOf(occ(MON, leetcode, true))
        val partToday = listOf(occ(MON.plusDays(1), leetcode, true, 10), occ(MON.plusDays(1), romanArt, false, 20))
        val allToday = partToday.map { it.copy(done = true) }

        assertEquals(StreakResult(current = 1, best = 1), Streaks.overall(history, partToday))
        assertEquals(StreakResult(current = 2, best = 2), Streaks.overall(history, allToday))
        assertEquals(StreakResult(current = 1, best = 1), Streaks.overall(history, emptyList()))
    }
}
