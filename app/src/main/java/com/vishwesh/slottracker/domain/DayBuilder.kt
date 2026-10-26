package com.vishwesh.slottracker.domain

import java.time.LocalDate

object DayBuilder {
    /**
     * Resolves the schedule for [date]: the override's slots if the date has an override
     * ([overrideSlots] non-null, possibly empty), otherwise the weekday template.
     * Slots are returned in time order.
     */
    fun schedule(
        date: LocalDate,
        overrideSlots: List<SlotSource>?,
        templateSlots: List<SlotSource>,
        items: Map<Long, ItemInfo>,
    ): DaySchedule {
        val sources = overrideSlots ?: templateSlots
        val slots = sources
            .sortedWith(compareBy<SlotSource>({ it.start }, { it.sortOrder }))
            .map { source ->
                ScheduledSlot(
                    slotKey = source.slotKey,
                    label = source.label,
                    start = source.start,
                    end = source.end,
                    items = source.itemIds.distinct().mapNotNull(items::get),
                )
            }
        return DaySchedule(date = date, isOverride = overrideSlots != null, slots = slots)
    }

    /**
     * One task per (slot, item) pair, merged with live state. State for pairs that no longer
     * exist in the schedule is ignored, which is how ticks on removed tasks get dropped.
     */
    fun tasks(schedule: DaySchedule, states: Map<TaskKey, LiveState>): List<Task> =
        schedule.slots.flatMap { slot ->
            slot.items.map { item ->
                val key = TaskKey(slot.slotKey, item.id)
                val state = states[key]
                Task(
                    key = key,
                    slot = slot,
                    item = item,
                    done = state?.done == true,
                    completedAt = state?.completedAt?.takeIf { state.done },
                    note = state?.note,
                )
            }
        }
}
