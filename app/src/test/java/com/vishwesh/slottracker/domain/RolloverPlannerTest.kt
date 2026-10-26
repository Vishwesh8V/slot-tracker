package com.vishwesh.slottracker.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RolloverPlannerTest {
    private val diffusion = SeedFixture.idOf("Diffusion Models")
    private val leetcode = SeedFixture.idOf("Leetcode / DSA")
    private val tue = MON.plusDays(1)
    private val wed = MON.plusDays(2)
    private val thu = MON.plusDays(3)

    private fun plan(
        liveDate: LocalDate?,
        today: LocalDate,
        states: Map<TaskKey, LiveState> = emptyMap(),
        scheduleFor: (LocalDate) -> DaySchedule = SeedFixture::scheduleFor,
    ) = runBlocking { RolloverPlanner.plan(liveDate, today, states) { scheduleFor(it) } }

    @Test
    fun `first run starts on today without writing history`() {
        val result = plan(liveDate = null, today = MON)!!

        assertEquals(MON, result.newLiveDate)
        assertTrue(result.history.isEmpty())
    }

    @Test
    fun `same day does nothing`() {
        assertNull(plan(liveDate = MON, today = MON))
    }

    @Test
    fun `clock moving backwards keeps the live day`() {
        assertNull(plan(liveDate = tue, today = MON))
    }

    @Test
    fun `next day logs the live day as done and missed with notes and snapshots`() {
        val monday = SeedFixture.scheduleFor(MON)
        val morning = monday.slots[0]
        val evening = monday.slots[1]
        val completedAt = Instant.parse("2026-10-26T11:00:00Z")
        val states = mapOf(
            TaskKey(morning.slotKey, diffusion) to LiveState(true, completedAt, "finished DDPM section 3"),
            TaskKey(evening.slotKey, leetcode) to LiveState(false, null, "too tired"),
        )

        val result = plan(liveDate = MON, today = tue, states = states)!!

        assertEquals(tue, result.newLiveDate)
        assertEquals(
            listOf(
                HistoryRecord(
                    MON, diffusion, "Diffusion Models", 0xFF5C6BC0.toInt(), "Morning", time(10), time(13),
                    TaskStatus.DONE, completedAt, "finished DDPM section 3",
                ),
                HistoryRecord(
                    MON, leetcode, "Leetcode / DSA", 0xFFFFB300.toInt(), "Evening", time(20), time(22),
                    TaskStatus.MISSED, null, "too tired",
                ),
            ),
            result.history,
        )
    }

    @Test
    fun `catch-up closes every missed day in order and marks unopened days missed`() {
        val monday = SeedFixture.scheduleFor(MON)
        val states = mapOf(TaskKey(monday.slots[0].slotKey, diffusion) to LiveState(true, null, null))

        // App last ran on Monday; next opened on Thursday.
        val result = plan(liveDate = MON, today = thu, states = states)!!

        assertEquals(thu, result.newLiveDate)
        assertEquals(listOf(MON, MON, tue, tue, tue, wed, wed, wed), result.history.map { it.date })
        assertEquals(TaskStatus.DONE, result.history[0].status)
        assertTrue(result.history.drop(1).all { it.status == TaskStatus.MISSED })
        assertTrue(result.history.none { it.date == thu })
    }

    @Test
    fun `live state never leaks into unopened days`() {
        // A key that also exists on Tuesday must not tick Tuesday's task.
        val sharedKey = TaskKey("t:shared", leetcode)
        val slot = SlotSource("t:shared", "Evening", time(20), time(22), 0, listOf(leetcode))
        val schedule = { date: LocalDate -> DayBuilder.schedule(date, null, listOf(slot), SeedFixture.items, ROLLOVER) }

        val result = plan(MON, wed, mapOf(sharedKey to LiveState(true, null, null)), schedule)!!

        assertEquals(listOf(TaskStatus.DONE, TaskStatus.MISSED), result.history.map { it.status })
    }

    @Test
    fun `override day in a gap uses the override schedule`() {
        val overrideTuesday = { date: LocalDate ->
            if (date == tue) {
                DayBuilder.schedule(date, emptyList(), emptyList(), SeedFixture.items, ROLLOVER)
            } else {
                SeedFixture.scheduleFor(date)
            }
        }

        val result = plan(MON, wed, scheduleFor = overrideTuesday)!!

        assertEquals(listOf(MON, MON), result.history.map { it.date })
    }

    @Test
    fun `history keeps snapshots of what the day was`() {
        // Snapshot fields come from the schedule at rollover time, not from item ids.
        val renamed = SeedFixture.items.mapValues { (_, item) -> item.copy(name = item.name.uppercase()) }
        val schedule = { date: LocalDate ->
            DayBuilder.schedule(date, null, SeedFixture.templateFor(date.dayOfWeek), renamed, ROLLOVER)
        }

        val result = plan(MON, tue, scheduleFor = schedule)!!

        assertEquals(listOf("DIFFUSION MODELS", "LEETCODE / DSA"), result.history.map { it.itemName })
    }
}
