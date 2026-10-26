package com.vishwesh.slottracker.domain

import com.vishwesh.slottracker.data.SeedData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** The seed configuration turned into domain inputs, with item ids 1..n in seed order. */
object SeedFixture {
    val items: Map<Long, ItemInfo> = SeedData.items.mapIndexed { index, item ->
        val id = index + 1L
        id to ItemInfo(id, item.name, item.colorArgb, null)
    }.toMap()

    fun idOf(name: String): Long = items.values.first { it.name == name }.id

    fun templateFor(day: DayOfWeek): List<SlotSource> {
        val dayOffset = (day.value - 1) * 10L
        return SeedData.week.getValue(day).mapIndexed { index, slot ->
            SlotSource(
                slotKey = SlotKeys.template(dayOffset + index + 1),
                label = slot.label,
                start = slot.start,
                end = slot.end,
                sortOrder = index,
                itemIds = slot.itemNames.map(::idOf),
            )
        }
    }

    fun scheduleFor(date: LocalDate): DaySchedule =
        DayBuilder.schedule(date, null, templateFor(date.dayOfWeek), items, ROLLOVER)
}

fun time(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

/** The default day rollover time (spec §8). */
val ROLLOVER: LocalTime = time(6)

// Mon 26 Oct 2026 is the first Monday used throughout the tests.
val MON: LocalDate = LocalDate.of(2026, 10, 26)
