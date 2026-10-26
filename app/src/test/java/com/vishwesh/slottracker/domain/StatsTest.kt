package com.vishwesh.slottracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val item = 1L
    private val other = 2L
    private val live: LocalDate = MON.plusDays(40)

    private fun occ(daysBeforeLive: Long, itemId: Long, done: Boolean) =
        Occurrence(live.minusDays(daysBeforeLive), itemId, time(10), done)

    @Test
    fun `completion rates use the 7 and 30 days before today, and all time`() {
        val history = listOf(
            occ(1, item, true), occ(3, item, false), occ(7, item, true), // last 7 days: 2/3
            occ(8, item, false), occ(30, item, true), // up to 30 days: 3/5
            occ(35, item, true), // all time: 4/6
            occ(2, other, false),
        )
        val stats = Stats.forItem(item, history, emptyList(), live)

        assertEquals(Rate(2, 3), stats.last7)
        assertEquals(Rate(3, 5), stats.last30)
        assertEquals(Rate(4, 6), stats.allTime)
        assertEquals(67, stats.last7.percent)
        assertEquals(4, stats.totalDone)
    }

    @Test
    fun `today counts toward sessions and streak but not rates`() {
        val history = listOf(occ(1, item, true))
        val today = listOf(Occurrence(live, item, time(10), true))
        val stats = Stats.forItem(item, history, today, live)

        assertEquals(2, stats.totalDone)
        assertEquals(2, stats.streak.current)
        assertEquals(Rate(1, 1), stats.allTime)
    }

    @Test
    fun `rate with nothing scheduled has no percent`() {
        assertNull(Stats.forItem(item, emptyList(), emptyList(), live).last7.percent)
    }

    @Test
    fun `overall stats`() {
        val history = listOf(occ(2, item, true), occ(2, other, true), occ(1, item, true), occ(1, other, false))
        val overall = Stats.overall(history, emptyList(), live)

        assertEquals(StreakResult(current = 0, best = 1), overall.streak)
        assertEquals(Rate(3, 4), overall.allTime)
    }

    @Test
    fun `day status`() {
        assertEquals(DayStatus.NOTHING_SCHEDULED, Stats.dayStatus(emptyList()))
        assertEquals(DayStatus.ALL_DONE, Stats.dayStatus(listOf(occ(1, item, true), occ(1, other, true))))
        assertEquals(DayStatus.PARTIAL, Stats.dayStatus(listOf(occ(1, item, true), occ(1, other, false))))
        assertEquals(DayStatus.NONE_DONE, Stats.dayStatus(listOf(occ(1, item, false))))
    }
}
