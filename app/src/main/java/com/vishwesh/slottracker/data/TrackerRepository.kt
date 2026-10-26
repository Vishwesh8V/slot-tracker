package com.vishwesh.slottracker.data

import androidx.room.withTransaction
import com.vishwesh.slottracker.data.db.AppDatabase
import com.vishwesh.slottracker.data.db.HistoryEntryEntity
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.data.db.MetaEntity
import com.vishwesh.slottracker.data.db.TaskStateEntity
import com.vishwesh.slottracker.data.db.TemplateAssignmentEntity
import com.vishwesh.slottracker.data.db.TemplateSlotEntity
import com.vishwesh.slottracker.domain.DayBuilder
import com.vishwesh.slottracker.domain.DaySchedule
import com.vishwesh.slottracker.domain.EffectiveDay
import com.vishwesh.slottracker.domain.HistoryRecord
import com.vishwesh.slottracker.domain.ItemInfo
import com.vishwesh.slottracker.domain.LiveState
import com.vishwesh.slottracker.domain.RolloverPlanner
import com.vishwesh.slottracker.domain.SlotKeys
import com.vishwesh.slottracker.domain.SlotSource
import com.vishwesh.slottracker.domain.Task
import com.vishwesh.slottracker.domain.TaskKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The live day: the effective date currently shown and not yet logged to history. */
data class LiveDay(
    val date: LocalDate,
    val schedule: DaySchedule,
    val tasks: List<Task>,
)

class TrackerRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    // Read the clock on every call so time-zone changes apply immediately (spec §9).
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val instantNow: () -> Instant = Instant::now,
) {
    private val itemDao = db.itemDao()
    private val scheduleDao = db.scheduleDao()
    private val taskStateDao = db.taskStateDao()
    private val historyDao = db.historyDao()
    private val metaDao = db.metaDao()

    /** Seeds on first launch, then closes any days that ended while the app wasn't running. */
    suspend fun catchUp(): LocalDate {
        ensureSeeded()
        return rolloverIfNeeded()
    }

    suspend fun ensureSeeded() = db.withTransaction {
        if (metaDao.get(KEY_SEEDED) != null) return@withTransaction
        val itemIds = SeedData.items.mapIndexed { index, item ->
            item.name to itemDao.insert(
                ItemEntity(name = item.name, colorArgb = item.colorArgb, details = null, sortOrder = index),
            )
        }.toMap()
        for ((day, slots) in SeedData.week) {
            slots.forEachIndexed { slotIndex, slot ->
                val slotId = scheduleDao.insertTemplateSlot(
                    TemplateSlotEntity(
                        dayOfWeek = day.value,
                        label = slot.label,
                        startTime = slot.start,
                        endTime = slot.end,
                        sortOrder = slotIndex,
                    ),
                )
                slot.itemNames.forEachIndexed { itemIndex, name ->
                    scheduleDao.insertTemplateAssignment(
                        TemplateAssignmentEntity(templateSlotId = slotId, itemId = itemIds.getValue(name), sortOrder = itemIndex),
                    )
                }
            }
        }
        metaDao.put(MetaEntity(KEY_SEEDED, "1"))
    }

    /**
     * Rolls the live day forward to the current effective day, writing history for every day
     * closed on the way (spec §7). Safe to call any time and from several places at once.
     * Returns the live date afterwards.
     */
    suspend fun rolloverIfNeeded(): LocalDate {
        val rollover = settings.currentRolloverTime()
        val today = EffectiveDay.of(now(), rollover)
        return db.withTransaction {
            val liveDate = liveDate()
            val states = liveDate?.let { liveStates(it) }.orEmpty()
            val plan = RolloverPlanner.plan(liveDate, today, states) { scheduleFor(it, rollover) }
                ?: return@withTransaction liveDate!!
            historyDao.insertAll(plan.history.map { it.toEntity() })
            taskStateDao.clear()
            metaDao.put(MetaEntity(KEY_LIVE_DATE, plan.newLiveDate.toString()))
            plan.newLiveDate
        }
    }

    fun observeLiveDay(): Flow<LiveDay> =
        db.invalidationTracker.createFlow(*AppDatabase.LIVE_DAY_TABLES).map { loadLiveDay() }

    suspend fun loadLiveDay(): LiveDay {
        val rollover = settings.currentRolloverTime()
        val fallback = EffectiveDay.of(now(), rollover)
        return db.withTransaction {
            val date = liveDate() ?: fallback
            val schedule = scheduleFor(date, rollover)
            LiveDay(date, schedule, DayBuilder.tasks(schedule, liveStates(date)))
        }
    }

    /**
     * Ticks or unticks a task shown for [date]. Ignored (returns false) if [date] is no longer
     * the live day, so a tap that lands just after rollover can't tick the next day's task.
     */
    suspend fun setDone(date: LocalDate, key: TaskKey, done: Boolean): Boolean {
        if (rolloverIfNeeded() != date) return false
        db.withTransaction {
            val existing = taskStateDao.get(date, key.slotKey, key.itemId)
            val completedAt = when {
                !done -> null
                existing?.done == true -> existing.completedAt
                else -> instantNow()
            }
            taskStateDao.upsert(TaskStateEntity(date, key.slotKey, key.itemId, done, completedAt, existing?.note))
        }
        return true
    }

    /** Sets or clears (blank) the note on a live-day task. Same live-day rule as [setDone]. */
    suspend fun setNote(date: LocalDate, key: TaskKey, note: String?): Boolean {
        if (rolloverIfNeeded() != date) return false
        db.withTransaction {
            val existing = taskStateDao.get(date, key.slotKey, key.itemId)
            taskStateDao.upsert(
                TaskStateEntity(
                    date = date,
                    slotKey = key.slotKey,
                    itemId = key.itemId,
                    done = existing?.done ?: false,
                    completedAt = existing?.completedAt,
                    note = note?.trim()?.ifEmpty { null },
                ),
            )
        }
        return true
    }

    suspend fun scheduleFor(date: LocalDate): DaySchedule = scheduleFor(date, settings.currentRolloverTime())

    private suspend fun scheduleFor(date: LocalDate, rollover: LocalTime): DaySchedule {
        val items = itemDao.all().associate { it.id to ItemInfo(it.id, it.name, it.colorArgb, it.details) }
        val overrideSlots = if (scheduleDao.hasOverride(date)) {
            val assignments = scheduleDao.overrideAssignments(date).groupBy { it.overrideSlotId }
            scheduleDao.overrideSlots(date).map { slot ->
                SlotSource(
                    slotKey = SlotKeys.override(slot.id, slot.sourceTemplateSlotId),
                    label = slot.label,
                    start = slot.startTime,
                    end = slot.endTime,
                    sortOrder = slot.sortOrder,
                    itemIds = assignments[slot.id].orEmpty().map { it.itemId },
                )
            }
        } else {
            null
        }
        val templateSlots = if (overrideSlots == null) {
            val dayOfWeek = date.dayOfWeek.value
            val assignments = scheduleDao.templateAssignments(dayOfWeek).groupBy { it.templateSlotId }
            scheduleDao.templateSlots(dayOfWeek).map { slot ->
                SlotSource(
                    slotKey = SlotKeys.template(slot.id),
                    label = slot.label,
                    start = slot.startTime,
                    end = slot.endTime,
                    sortOrder = slot.sortOrder,
                    itemIds = assignments[slot.id].orEmpty().map { it.itemId },
                )
            }
        } else {
            emptyList()
        }
        return DayBuilder.schedule(date, overrideSlots, templateSlots, items, rollover)
    }

    private suspend fun liveDate(): LocalDate? = metaDao.get(KEY_LIVE_DATE)?.let(LocalDate::parse)

    private suspend fun liveStates(date: LocalDate): Map<TaskKey, LiveState> =
        taskStateDao.forDate(date).associate {
            TaskKey(it.slotKey, it.itemId) to LiveState(it.done, it.completedAt, it.note)
        }

    private fun HistoryRecord.toEntity() = HistoryEntryEntity(
        date = date,
        itemId = itemId,
        itemNameSnapshot = itemName,
        itemColorSnapshot = itemColorArgb,
        slotLabelSnapshot = slotLabel,
        slotStart = slotStart,
        slotEnd = slotEnd,
        status = status,
        completedAt = completedAt,
        note = note,
    )

    private companion object {
        const val KEY_SEEDED = "seeded"
        const val KEY_LIVE_DATE = "live_date"
    }
}
