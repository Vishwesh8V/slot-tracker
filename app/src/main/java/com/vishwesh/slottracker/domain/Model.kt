package com.vishwesh.slottracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ItemInfo(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val details: String?,
)

/**
 * One slot as stored for a day (template or override), before item ids are resolved.
 * [slotKey] is the slot's stable identity; see docs/DECISIONS.md.
 */
data class SlotSource(
    val slotKey: String,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val sortOrder: Int,
    val itemIds: List<Long>,
)

data class ScheduledSlot(
    val slotKey: String,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val items: List<ItemInfo>,
)

data class DaySchedule(
    val date: LocalDate,
    val isOverride: Boolean,
    val slots: List<ScheduledSlot>,
)

/** Identifies a task (occurrence) within one day. */
data class TaskKey(val slotKey: String, val itemId: Long)

/** Live (not yet rolled over) state of one task. Absent state means not done, no note. */
data class LiveState(
    val done: Boolean,
    val completedAt: Instant?,
    val note: String?,
)

data class Task(
    val key: TaskKey,
    val slot: ScheduledSlot,
    val item: ItemInfo,
    val done: Boolean,
    val completedAt: Instant?,
    val note: String?,
)

enum class TaskStatus { DONE, MISSED }

/** A task outcome to be written to history, with snapshots of the item and slot. */
data class HistoryRecord(
    val date: LocalDate,
    val itemId: Long,
    val itemName: String,
    val itemColorArgb: Int,
    val slotLabel: String,
    val slotStart: LocalTime,
    val slotEnd: LocalTime,
    val status: TaskStatus,
    val completedAt: Instant?,
    val note: String?,
)
