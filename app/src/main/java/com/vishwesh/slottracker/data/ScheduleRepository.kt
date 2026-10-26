package com.vishwesh.slottracker.data

import androidx.room.withTransaction
import com.vishwesh.slottracker.data.db.AppDatabase
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.data.db.OverrideAssignmentEntity
import com.vishwesh.slottracker.data.db.OverrideDayEntity
import com.vishwesh.slottracker.data.db.OverrideSlotEntity
import com.vishwesh.slottracker.data.db.SlotUsage
import com.vishwesh.slottracker.data.db.TemplateAssignmentEntity
import com.vishwesh.slottracker.data.db.TemplateSlotEntity
import com.vishwesh.slottracker.domain.ItemInfo
import com.vishwesh.slottracker.domain.SlotTiming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Which day an editor is working on: a weekday of the template, or one date (an override). */
sealed interface DayRef {
    data class Weekday(val day: DayOfWeek) : DayRef
    data class Date(val date: LocalDate) : DayRef
}

/**
 * A slot as shown in an editor. [fromTemplate] is true when it is a template slot, including
 * when a date without an override is showing its template.
 */
data class EditableSlot(
    val id: Long,
    val fromTemplate: Boolean,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val items: List<ItemInfo>,
)

data class EditableDay(
    val ref: DayRef,
    val isOverride: Boolean,
    val rollover: LocalTime,
    val slots: List<EditableSlot>,
)

/** A new slot ([id] null) or edits to an existing one. */
data class SlotDraft(
    val id: Long?,
    val fromTemplate: Boolean,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val itemIds: List<Long>,
)

/** Where an item is used from today on, which slots would be left empty, and its history size. */
data class ItemUsage(
    val places: List<SlotUsage>,
    val emptiedSlots: List<SlotUsage>,
    val historyCount: Int,
)

/** Editing the weekly template, date overrides and items (spec §6.2). */
class ScheduleRepository(
    private val db: AppDatabase,
    private val tracker: TrackerRepository,
    private val settings: SettingsRepository,
) {
    private val itemDao = db.itemDao()
    private val scheduleDao = db.scheduleDao()
    private val historyDao = db.historyDao()

    fun observeItems(): Flow<List<ItemEntity>> = itemDao.observeAll()

    fun observeDay(ref: DayRef): Flow<EditableDay> = onScheduleChanges().map { loadDay(ref) }

    suspend fun item(id: Long): ItemEntity? = itemDao.get(id)

    fun observeWeek(): Flow<List<EditableDay>> =
        onScheduleChanges().map { DayOfWeek.entries.map { loadDay(DayRef.Weekday(it)) } }

    /** Dates from the live day on that have an override. */
    fun observeOverrideDates(): Flow<List<LocalDate>> = flow {
        emitAll(scheduleDao.observeOverrideDates(tracker.currentLiveDate()))
    }

    suspend fun saveSlot(ref: DayRef, draft: SlotDraft) = edit {
        when (ref) {
            is DayRef.Weekday -> {
                val slot = TemplateSlotEntity(
                    id = draft.id ?: 0,
                    dayOfWeek = ref.day.value,
                    label = draft.label,
                    startTime = draft.start,
                    endTime = draft.end,
                    sortOrder = 0,
                )
                val slotId = if (draft.id == null) {
                    scheduleDao.insertTemplateSlot(slot)
                } else {
                    scheduleDao.updateTemplateSlot(slot)
                    draft.id
                }
                scheduleDao.clearTemplateAssignments(slotId)
                draft.itemIds.forEachIndexed { index, itemId ->
                    scheduleDao.insertTemplateAssignment(TemplateAssignmentEntity(templateSlotId = slotId, itemId = itemId, sortOrder = index))
                }
            }
            is DayRef.Date -> {
                val copied = ensureOverride(ref.date)
                val existingId = draft.id?.let { if (draft.fromTemplate) copied.getValue(it) else it }
                val slotId = if (existingId == null) {
                    scheduleDao.insertOverrideSlot(
                        OverrideSlotEntity(
                            date = ref.date,
                            label = draft.label,
                            startTime = draft.start,
                            endTime = draft.end,
                            sortOrder = 0,
                            sourceTemplateSlotId = null,
                        ),
                    )
                } else {
                    val current = scheduleDao.overrideSlot(existingId)!!
                    scheduleDao.updateOverrideSlot(current.copy(label = draft.label, startTime = draft.start, endTime = draft.end))
                    existingId
                }
                scheduleDao.clearOverrideAssignments(slotId)
                draft.itemIds.forEachIndexed { index, itemId ->
                    scheduleDao.insertOverrideAssignment(OverrideAssignmentEntity(overrideSlotId = slotId, itemId = itemId, sortOrder = index))
                }
            }
        }
    }

    suspend fun deleteSlot(ref: DayRef, slot: EditableSlot) = edit {
        when (ref) {
            is DayRef.Weekday -> scheduleDao.deleteTemplateSlot(slot.id)
            is DayRef.Date -> {
                val copied = ensureOverride(ref.date)
                scheduleDao.deleteOverrideSlot(if (slot.fromTemplate) copied.getValue(slot.id) else slot.id)
            }
        }
    }

    /** Replaces each of [targets]' slots with copies of [source]'s slots and items. */
    suspend fun copyDay(source: DayOfWeek, targets: Set<DayOfWeek>) = edit {
        val slots = scheduleDao.templateSlots(source.value)
        val assignments = scheduleDao.templateAssignments(source.value).groupBy { it.templateSlotId }
        for (target in targets - source) {
            scheduleDao.deleteTemplateDay(target.value)
            for (slot in slots) {
                val newId = scheduleDao.insertTemplateSlot(slot.copy(id = 0, dayOfWeek = target.value))
                assignments[slot.id].orEmpty().forEach {
                    scheduleDao.insertTemplateAssignment(it.copy(id = 0, templateSlotId = newId))
                }
            }
        }
    }

    /** Removes [date]'s override so it follows the template again. */
    suspend fun resetOverride(date: LocalDate) = edit { scheduleDao.deleteOverrideDay(date) }

    /** Creates ([id] null) or updates an item. Returns its id. */
    suspend fun saveItem(id: Long?, name: String, colorArgb: Int, details: String?): Long = edit {
        val cleanDetails = details?.trim()?.ifEmpty { null }
        if (id == null) {
            itemDao.insert(ItemEntity(name = name.trim(), colorArgb = colorArgb, details = cleanDetails, sortOrder = itemDao.nextSortOrder()))
        } else {
            itemDao.update(itemDao.get(id)!!.copy(name = name.trim(), colorArgb = colorArgb, details = cleanDetails))
            id
        }
    }

    /** Sets the order of the active items to [ids]. */
    suspend fun reorderItems(ids: List<Long>) = edit {
        ids.forEachIndexed { index, id -> itemDao.setSortOrder(id, index) }
    }

    suspend fun itemUsage(itemId: Long): ItemUsage = db.withTransaction {
        val from = tracker.currentLiveDate()
        val places = scheduleDao.templateUsage(itemId) + scheduleDao.overrideUsage(itemId, from)
        ItemUsage(places, places.filter { it.itemCount == 1 }, historyDao.countForItem(itemId))
    }

    /**
     * Archives an item: hides it from pickers, keeps its history, and removes it from the
     * template and from overrides from today on, deleting any slot that leaves empty (spec §9).
     */
    suspend fun archiveItem(itemId: Long) = edit {
        val from = tracker.currentLiveDate()
        scheduleDao.removeItemFromTemplate(itemId)
        scheduleDao.removeItemFromOverrides(itemId, from)
        scheduleDao.deleteEmptyTemplateSlots()
        scheduleDao.deleteEmptyOverrideSlots(from)
        itemDao.setArchived(itemId, archived = true, sortOrder = itemDao.get(itemId)!!.sortOrder)
    }

    suspend fun restoreItem(itemId: Long) = edit {
        itemDao.setArchived(itemId, archived = false, sortOrder = itemDao.nextSortOrder())
    }

    /** Deletes an item and, permanently, its history. Slots it leaves empty are removed. */
    suspend fun deleteItemPermanently(itemId: Long) = edit {
        historyDao.deleteForItem(itemId)
        itemDao.delete(itemId)
        scheduleDao.deleteEmptyTemplateSlots()
        scheduleDao.deleteEmptyOverrideSlots(LocalDate.of(1970, 1, 1))
    }

    /** Whether [slot] overlaps any other slot of [day]. Returns the overlapping slots. */
    fun overlapsWith(day: EditableDay, draft: SlotDraft): List<EditableSlot> =
        day.slots
            .filter { !(it.id == draft.id && it.fromTemplate == draft.fromTemplate) }
            .filter { SlotTiming.overlaps(it.start, it.end, draft.start, draft.end, day.rollover) }

    private suspend fun loadDay(ref: DayRef): EditableDay {
        val rollover = settings.currentRolloverTime()
        return db.withTransaction {
            val items = itemDao.all().associate { it.id to ItemInfo(it.id, it.name, it.colorArgb, it.details) }
            val date = (ref as? DayRef.Date)?.date
            val isOverride = date != null && scheduleDao.hasOverride(date)
            val slots = if (isOverride) {
                val assignments = scheduleDao.overrideAssignments(date).groupBy { it.overrideSlotId }
                scheduleDao.overrideSlots(date).map { slot ->
                    EditableSlot(slot.id, false, slot.label, slot.startTime, slot.endTime,
                        assignments[slot.id].orEmpty().mapNotNull { items[it.itemId] })
                }
            } else {
                val dayOfWeek = date?.dayOfWeek?.value ?: (ref as DayRef.Weekday).day.value
                val assignments = scheduleDao.templateAssignments(dayOfWeek).groupBy { it.templateSlotId }
                scheduleDao.templateSlots(dayOfWeek).map { slot ->
                    EditableSlot(slot.id, true, slot.label, slot.startTime, slot.endTime,
                        assignments[slot.id].orEmpty().mapNotNull { items[it.itemId] })
                }
            }
            EditableDay(ref, isOverride, rollover, slots.sortedBy { SlotTiming.offsetMinutes(rollover, it.start) })
        }
    }

    /**
     * Makes sure [date] has an override, creating it as a copy of the template on first edit.
     * Returns template slot id -> override slot id for slots copied now (empty if it existed).
     */
    private suspend fun ensureOverride(date: LocalDate): Map<Long, Long> {
        if (scheduleDao.hasOverride(date)) return emptyMap()
        scheduleDao.insertOverrideDay(OverrideDayEntity(date))
        val assignments = scheduleDao.templateAssignments(date.dayOfWeek.value).groupBy { it.templateSlotId }
        return scheduleDao.templateSlots(date.dayOfWeek.value).associate { slot ->
            val newId = scheduleDao.insertOverrideSlot(
                OverrideSlotEntity(
                    date = date,
                    label = slot.label,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    sortOrder = slot.sortOrder,
                    sourceTemplateSlotId = slot.id,
                ),
            )
            assignments[slot.id].orEmpty().forEach {
                scheduleDao.insertOverrideAssignment(OverrideAssignmentEntity(overrideSlotId = newId, itemId = it.itemId, sortOrder = it.sortOrder))
            }
            slot.id to newId
        }
    }

    /** Runs a schedule edit in one transaction, then drops ticks for tasks that no longer exist. */
    private suspend fun <T> edit(block: suspend () -> T): T = db.withTransaction {
        block().also { tracker.pruneLiveStates() }
    }

    private fun onScheduleChanges(): Flow<Unit> =
        combine(db.invalidationTracker.createFlow(*AppDatabase.LIVE_DAY_TABLES), settings.rolloverTime) { _, _ -> }
}
