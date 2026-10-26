package com.vishwesh.slottracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SlotTimingTest {
    private val monday = SeedFixture.scheduleFor(MON) // Morning 10-13, Evening 20-22
    private val morning = monday.slots[0].slotKey
    private val evening = monday.slots[1].slotKey

    private fun at(day: Long, hour: Int, minute: Int = 0) = MON.plusDays(day).atTime(hour, minute)

    @Test
    fun `before the first slot the next slot is highlighted`() {
        assertEquals(morning, SlotTiming.highlighted(monday, at(0, 7), ROLLOVER))
    }

    @Test
    fun `during a slot that slot is highlighted`() {
        assertEquals(morning, SlotTiming.highlighted(monday, at(0, 10), ROLLOVER))
        assertEquals(morning, SlotTiming.highlighted(monday, at(0, 12, 59), ROLLOVER))
    }

    @Test
    fun `between slots the next one is highlighted`() {
        assertEquals(evening, SlotTiming.highlighted(monday, at(0, 13), ROLLOVER))
    }

    @Test
    fun `after the last slot nothing is highlighted, even before rollover`() {
        assertNull(SlotTiming.highlighted(monday, at(0, 22), ROLLOVER))
        assertNull(SlotTiming.highlighted(monday, at(1, 3), ROLLOVER))
    }

    @Test
    fun `slot crossing midnight stays current after midnight`() {
        val late = DayBuilder.schedule(
            MON, null, listOf(SlotSource("t:1", "Late", time(23), time(1), 0, listOf(1L))), SeedFixture.items, ROLLOVER,
        )
        assertEquals("t:1", SlotTiming.highlighted(late, at(1, 0, 30), ROLLOVER))
        assertNull(SlotTiming.highlighted(late, at(1, 1), ROLLOVER))
        assertEquals(at(0, 23)..at(1, 1), SlotTiming.window(MON, time(23), time(1), ROLLOVER))
    }

    @Test
    fun `slot before the rollover time happens the next calendar day`() {
        assertEquals(at(1, 2)..at(1, 4), SlotTiming.window(MON, time(2), time(4), ROLLOVER))
    }

    @Test
    fun `next boundary is the next slot start or end`() {
        assertEquals(at(0, 10), SlotTiming.nextBoundary(monday, at(0, 6), ROLLOVER))
        assertEquals(at(0, 13), SlotTiming.nextBoundary(monday, at(0, 10), ROLLOVER))
        assertEquals(at(0, 22), SlotTiming.nextBoundary(monday, at(0, 21), ROLLOVER))
        assertNull(SlotTiming.nextBoundary(monday, at(0, 22), ROLLOVER))
    }

    @Test
    fun `overlap detection`() {
        assertTrue(SlotTiming.overlaps(time(10), time(13), time(12), time(14), ROLLOVER))
        assertFalse(SlotTiming.overlaps(time(10), time(13), time(13), time(14), ROLLOVER))
        assertTrue(SlotTiming.overlaps(time(23), time(1), time(0, 30), time(2), ROLLOVER))
        assertFalse(SlotTiming.overlaps(time(20), time(22), time(23), time(1), ROLLOVER))
    }

    @Test
    fun `next rollover`() {
        assertEquals(at(0, 6), EffectiveDay.nextRollover(at(0, 1), ROLLOVER))
        assertEquals(at(1, 6), EffectiveDay.nextRollover(at(0, 6), ROLLOVER))
        assertEquals(at(1, 6), EffectiveDay.nextRollover(LocalDateTime.of(MON, time(23)), ROLLOVER))
    }
}
