package com.vishwesh.slottracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class DayBuilderTest {
    private val items = SeedFixture.items
    private val diffusion = SeedFixture.idOf("Diffusion Models")
    private val leetcode = SeedFixture.idOf("Leetcode / DSA")
    private val math = SeedFixture.idOf("Math Puzzle")

    @Test
    fun `seeded Monday is Diffusion Models in the morning and Leetcode in the evening`() {
        val schedule = SeedFixture.scheduleFor(MON)

        assertEquals(DayOfWeek.MONDAY, schedule.date.dayOfWeek)
        assertFalse(schedule.isOverride)
        assertEquals(listOf("Morning", "Evening"), schedule.slots.map { it.label })
        assertEquals(time(10), schedule.slots[0].start)
        assertEquals(time(13), schedule.slots[0].end)
        assertEquals(listOf("Diffusion Models"), schedule.slots[0].items.map { it.name })
        assertEquals(time(20), schedule.slots[1].start)
        assertEquals(time(22), schedule.slots[1].end)
        assertEquals(listOf("Leetcode / DSA"), schedule.slots[1].items.map { it.name })
    }

    @Test
    fun `seeded week matches the spec table`() {
        val expected = mapOf(
            DayOfWeek.MONDAY to listOf(listOf("Diffusion Models"), listOf("Leetcode / DSA")),
            DayOfWeek.TUESDAY to listOf(listOf("Game Development"), listOf("Roman Art", "Language Study")),
            DayOfWeek.WEDNESDAY to listOf(listOf("Diffusion Models"), listOf("Leetcode / DSA", "Math Puzzle")),
            DayOfWeek.THURSDAY to listOf(listOf("Game Development"), listOf("Roman Art", "Language Study")),
            DayOfWeek.FRIDAY to listOf(listOf("Diffusion Models"), listOf("Leetcode / DSA", "Math Puzzle")),
            DayOfWeek.SATURDAY to listOf(listOf("Game Development"), listOf("Leetcode / DSA")),
            DayOfWeek.SUNDAY to listOf(listOf("Diffusion Models"), listOf("Leetcode / DSA")),
        )
        for (offset in 0L..6L) {
            val date = MON.plusDays(offset)
            val actual = SeedFixture.scheduleFor(date).slots.map { slot -> slot.items.map { it.name } }
            assertEquals(date.dayOfWeek.toString(), expected.getValue(date.dayOfWeek), actual)
        }
    }

    @Test
    fun `seed colours match the spec`() {
        val colours = items.values.associate { it.name to it.colorArgb }
        assertEquals(0xFF5C6BC0.toInt(), colours["Diffusion Models"])
        assertEquals(0xFF43A047.toInt(), colours["Game Development"])
        assertEquals(0xFFFFB300.toInt(), colours["Leetcode / DSA"])
        assertEquals(0xFF00897B.toInt(), colours["Math Puzzle"])
        assertEquals(0xFFD84315.toInt(), colours["Roman Art"])
        assertEquals(0xFF8E24AA.toInt(), colours["Language Study"])
    }

    @Test
    fun `override replaces the template for that date`() {
        val override = listOf(SlotSource("o:1", "Afternoon", time(14), time(15), 0, listOf(math)))
        val schedule = DayBuilder.schedule(MON, override, SeedFixture.templateFor(DayOfWeek.MONDAY), items, ROLLOVER)

        assertTrue(schedule.isOverride)
        assertEquals(listOf("Afternoon"), schedule.slots.map { it.label })
        assertEquals(listOf("Math Puzzle"), schedule.slots.single().items.map { it.name })
    }

    @Test
    fun `empty override means nothing scheduled, not the template`() {
        val schedule = DayBuilder.schedule(MON, emptyList(), SeedFixture.templateFor(DayOfWeek.MONDAY), items, ROLLOVER)

        assertTrue(schedule.isOverride)
        assertTrue(schedule.slots.isEmpty())
        assertTrue(DayBuilder.tasks(schedule, emptyMap()).isEmpty())
    }

    @Test
    fun `slots are in time order regardless of stored order`() {
        val slots = listOf(
            SlotSource("t:1", "Late", time(23), time(1), 0, listOf(math)),
            SlotSource("t:2", "Early", time(7), time(8), 1, listOf(diffusion)),
            SlotSource("t:3", "Mid", time(12), time(13), 2, listOf(leetcode)),
        )
        val schedule = DayBuilder.schedule(MON, null, slots, items, ROLLOVER)

        assertEquals(listOf("Early", "Mid", "Late"), schedule.slots.map { it.label })
    }

    @Test
    fun `slots before the rollover time happen after midnight and sort last`() {
        val slots = listOf(
            SlotSource("t:1", "Night", time(2), time(4), 0, listOf(math)),
            SlotSource("t:2", "Evening", time(20), time(22), 1, listOf(leetcode)),
            SlotSource("t:3", "Dawn", time(6), time(7), 2, listOf(diffusion)),
            SlotSource("t:4", "Morning", time(10), time(13), 3, listOf(diffusion)),
        )

        val sixAm = DayBuilder.schedule(MON, null, slots, items, ROLLOVER)
        assertEquals(listOf("Dawn", "Morning", "Evening", "Night"), sixAm.slots.map { it.label })

        val midnight = DayBuilder.schedule(MON, null, slots, items, time(0))
        assertEquals(listOf("Night", "Dawn", "Morning", "Evening"), midnight.slots.map { it.label })
    }

    @Test
    fun `slots starting at the same time keep their stored order`() {
        val slots = listOf(
            SlotSource("t:1", "B", time(10), time(11), 1, listOf(math)),
            SlotSource("t:2", "A", time(10), time(12), 0, listOf(leetcode)),
        )

        assertEquals(listOf("A", "B"), DayBuilder.schedule(MON, null, slots, items, ROLLOVER).slots.map { it.label })
    }

    @Test
    fun `each slot-item pair is one task, in slot then item order`() {
        val wednesday = SeedFixture.scheduleFor(MON.plusDays(2))
        val tasks = DayBuilder.tasks(wednesday, emptyMap())

        assertEquals(listOf("Diffusion Models", "Leetcode / DSA", "Math Puzzle"), tasks.map { it.item.name })
        assertTrue(tasks.none { it.done })
    }

    @Test
    fun `live state is merged and ticks on removed tasks are dropped`() {
        val schedule = SeedFixture.scheduleFor(MON)
        val morningKey = TaskKey(schedule.slots[0].slotKey, diffusion)
        val completedAt = Instant.parse("2026-10-26T10:30:00Z")
        val states = mapOf(
            morningKey to LiveState(done = true, completedAt = completedAt, note = "DDPM §3"),
            TaskKey("t:999", diffusion) to LiveState(done = true, completedAt = completedAt, note = null),
        )
        val tasks = DayBuilder.tasks(schedule, states)

        assertEquals(2, tasks.size)
        assertTrue(tasks[0].done)
        assertEquals(completedAt, tasks[0].completedAt)
        assertEquals("DDPM §3", tasks[0].note)
        assertFalse(tasks[1].done)
        assertNull(tasks[1].completedAt)
    }

    @Test
    fun `same item in two slots gives two independent tasks`() {
        val slots = listOf(
            SlotSource("t:1", "Morning", time(10), time(13), 0, listOf(leetcode)),
            SlotSource("t:2", "Evening", time(20), time(22), 1, listOf(leetcode)),
        )
        val schedule = DayBuilder.schedule(MON, null, slots, items, ROLLOVER)
        val tasks = DayBuilder.tasks(schedule, mapOf(TaskKey("t:2", leetcode) to LiveState(true, null, null)))

        assertEquals(listOf(false, true), tasks.map { it.done })
    }

    @Test
    fun `assignments to unknown items are skipped`() {
        val slots = listOf(SlotSource("t:1", "Morning", time(10), time(13), 0, listOf(diffusion, 404L)))
        val schedule = DayBuilder.schedule(MON, null, slots, items, ROLLOVER)

        assertEquals(listOf("Diffusion Models"), schedule.slots.single().items.map { it.name })
    }

    @Test
    fun `override slot copied from a template slot keeps its key`() {
        assertEquals(SlotKeys.template(7), SlotKeys.override(overrideSlotId = 3, sourceTemplateSlotId = 7))
        assertEquals("o:3", SlotKeys.override(overrideSlotId = 3, sourceTemplateSlotId = null))
    }
}
