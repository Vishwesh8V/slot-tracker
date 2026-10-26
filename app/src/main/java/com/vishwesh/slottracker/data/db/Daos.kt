package com.vishwesh.slottracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY sortOrder, id")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT * FROM items ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM items")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE items SET archived = :archived, sortOrder = :sortOrder WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, sortOrder: Int)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)
}

/** A slot that uses an item, for the "where is it used" confirmation. */
data class SlotUsage(
    val slotId: Long,
    val dayOfWeek: Int?,
    val date: LocalDate?,
    val label: String,
    val startTime: java.time.LocalTime,
    val endTime: java.time.LocalTime,
    val itemCount: Int,
)

@Dao
interface ScheduleDao {
    // Template

    @Query("SELECT * FROM template_slots")
    suspend fun allTemplateSlots(): List<TemplateSlotEntity>

    @Query("SELECT * FROM template_slots WHERE dayOfWeek = :dayOfWeek")
    suspend fun templateSlots(dayOfWeek: Int): List<TemplateSlotEntity>

    @Query("SELECT * FROM template_assignments ORDER BY sortOrder, id")
    suspend fun allTemplateAssignments(): List<TemplateAssignmentEntity>

    @Query(
        """
        SELECT a.* FROM template_assignments a
        JOIN template_slots s ON a.templateSlotId = s.id
        WHERE s.dayOfWeek = :dayOfWeek
        ORDER BY a.sortOrder, a.id
        """,
    )
    suspend fun templateAssignments(dayOfWeek: Int): List<TemplateAssignmentEntity>

    @Insert
    suspend fun insertTemplateSlot(slot: TemplateSlotEntity): Long

    @Update
    suspend fun updateTemplateSlot(slot: TemplateSlotEntity)

    @Query("DELETE FROM template_slots WHERE id = :id")
    suspend fun deleteTemplateSlot(id: Long)

    @Query("DELETE FROM template_slots WHERE dayOfWeek = :dayOfWeek")
    suspend fun deleteTemplateDay(dayOfWeek: Int)

    @Insert
    suspend fun insertTemplateAssignment(assignment: TemplateAssignmentEntity): Long

    @Query("DELETE FROM template_assignments WHERE templateSlotId = :slotId")
    suspend fun clearTemplateAssignments(slotId: Long)

    @Query("DELETE FROM template_assignments WHERE itemId = :itemId")
    suspend fun removeItemFromTemplate(itemId: Long)

    @Query("DELETE FROM template_slots WHERE id NOT IN (SELECT templateSlotId FROM template_assignments)")
    suspend fun deleteEmptyTemplateSlots()

    @Query(
        """
        SELECT s.id AS slotId, s.dayOfWeek AS dayOfWeek, NULL AS date, s.label AS label,
            s.startTime AS startTime, s.endTime AS endTime,
            (SELECT COUNT(*) FROM template_assignments x WHERE x.templateSlotId = s.id) AS itemCount
        FROM template_slots s
        WHERE s.id IN (SELECT templateSlotId FROM template_assignments WHERE itemId = :itemId)
        ORDER BY s.dayOfWeek, s.startTime
        """,
    )
    suspend fun templateUsage(itemId: Long): List<SlotUsage>

    // Overrides

    @Query("SELECT EXISTS(SELECT 1 FROM override_days WHERE date = :date)")
    suspend fun hasOverride(date: LocalDate): Boolean

    @Query("SELECT date FROM override_days WHERE date >= :from ORDER BY date")
    fun observeOverrideDates(from: LocalDate): Flow<List<LocalDate>>

    @Insert
    suspend fun insertOverrideDay(day: OverrideDayEntity)

    @Query("DELETE FROM override_days WHERE date = :date")
    suspend fun deleteOverrideDay(date: LocalDate)

    @Query("SELECT * FROM override_slots WHERE date = :date")
    suspend fun overrideSlots(date: LocalDate): List<OverrideSlotEntity>

    @Query("SELECT * FROM override_slots WHERE id = :id")
    suspend fun overrideSlot(id: Long): OverrideSlotEntity?

    @Query(
        """
        SELECT a.* FROM override_assignments a
        JOIN override_slots s ON a.overrideSlotId = s.id
        WHERE s.date = :date
        ORDER BY a.sortOrder, a.id
        """,
    )
    suspend fun overrideAssignments(date: LocalDate): List<OverrideAssignmentEntity>

    @Insert
    suspend fun insertOverrideSlot(slot: OverrideSlotEntity): Long

    @Update
    suspend fun updateOverrideSlot(slot: OverrideSlotEntity)

    @Query("DELETE FROM override_slots WHERE id = :id")
    suspend fun deleteOverrideSlot(id: Long)

    @Insert
    suspend fun insertOverrideAssignment(assignment: OverrideAssignmentEntity): Long

    @Query("DELETE FROM override_assignments WHERE overrideSlotId = :slotId")
    suspend fun clearOverrideAssignments(slotId: Long)

    @Query(
        """
        DELETE FROM override_assignments WHERE itemId = :itemId
        AND overrideSlotId IN (SELECT id FROM override_slots WHERE date >= :from)
        """,
    )
    suspend fun removeItemFromOverrides(itemId: Long, from: LocalDate)

    @Query(
        """
        DELETE FROM override_slots WHERE date >= :from
        AND id NOT IN (SELECT overrideSlotId FROM override_assignments)
        """,
    )
    suspend fun deleteEmptyOverrideSlots(from: LocalDate)

    @Query(
        """
        SELECT s.id AS slotId, NULL AS dayOfWeek, s.date AS date, s.label AS label,
            s.startTime AS startTime, s.endTime AS endTime,
            (SELECT COUNT(*) FROM override_assignments x WHERE x.overrideSlotId = s.id) AS itemCount
        FROM override_slots s
        WHERE s.date >= :from
            AND s.id IN (SELECT overrideSlotId FROM override_assignments WHERE itemId = :itemId)
        ORDER BY s.date, s.startTime
        """,
    )
    suspend fun overrideUsage(itemId: Long, from: LocalDate): List<SlotUsage>
}

@Dao
interface TaskStateDao {
    @Query("SELECT * FROM task_states WHERE date = :date")
    suspend fun forDate(date: LocalDate): List<TaskStateEntity>

    @Query("SELECT * FROM task_states WHERE date = :date AND slotKey = :slotKey AND itemId = :itemId")
    suspend fun get(date: LocalDate, slotKey: String, itemId: Long): TaskStateEntity?

    @Upsert
    suspend fun upsert(state: TaskStateEntity)

    @Query("DELETE FROM task_states WHERE date = :date AND slotKey = :slotKey AND itemId = :itemId")
    suspend fun delete(date: LocalDate, slotKey: String, itemId: Long)

    @Query("DELETE FROM task_states")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertAll(entries: List<HistoryEntryEntity>)

    @Query("SELECT * FROM history ORDER BY date, slotStart, id")
    suspend fun all(): List<HistoryEntryEntity>

    @Query("SELECT * FROM history ORDER BY date, slotStart, id")
    fun observeAll(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntryEntity?

    @Update
    suspend fun update(entry: HistoryEntryEntity)

    @Query("SELECT COUNT(*) FROM history WHERE itemId = :itemId")
    suspend fun countForItem(itemId: Long): Int

    @Query("DELETE FROM history WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: MetaEntity)
}

/** Whole-database read and replace, for backup export/import (spec §9). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM items") suspend fun items(): List<ItemEntity>
    @Query("SELECT * FROM template_slots") suspend fun templateSlots(): List<TemplateSlotEntity>
    @Query("SELECT * FROM template_assignments") suspend fun templateAssignments(): List<TemplateAssignmentEntity>
    @Query("SELECT * FROM override_days") suspend fun overrideDays(): List<OverrideDayEntity>
    @Query("SELECT * FROM override_slots") suspend fun overrideSlots(): List<OverrideSlotEntity>
    @Query("SELECT * FROM override_assignments") suspend fun overrideAssignments(): List<OverrideAssignmentEntity>
    @Query("SELECT * FROM task_states") suspend fun taskStates(): List<TaskStateEntity>
    @Query("SELECT * FROM history") suspend fun history(): List<HistoryEntryEntity>
    @Query("SELECT * FROM app_meta") suspend fun meta(): List<MetaEntity>

    @Query("DELETE FROM task_states") suspend fun clearTaskStates()
    @Query("DELETE FROM template_assignments") suspend fun clearTemplateAssignments()
    @Query("DELETE FROM override_assignments") suspend fun clearOverrideAssignments()
    @Query("DELETE FROM template_slots") suspend fun clearTemplateSlots()
    @Query("DELETE FROM override_slots") suspend fun clearOverrideSlots()
    @Query("DELETE FROM override_days") suspend fun clearOverrideDays()
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM items") suspend fun clearItems()
    @Query("DELETE FROM app_meta") suspend fun clearMeta()

    @Insert suspend fun insertItems(rows: List<ItemEntity>)
    @Insert suspend fun insertTemplateSlots(rows: List<TemplateSlotEntity>)
    @Insert suspend fun insertTemplateAssignments(rows: List<TemplateAssignmentEntity>)
    @Insert suspend fun insertOverrideDays(rows: List<OverrideDayEntity>)
    @Insert suspend fun insertOverrideSlots(rows: List<OverrideSlotEntity>)
    @Insert suspend fun insertOverrideAssignments(rows: List<OverrideAssignmentEntity>)
    @Insert suspend fun insertTaskStates(rows: List<TaskStateEntity>)
    @Insert suspend fun insertHistory(rows: List<HistoryEntryEntity>)
    @Insert suspend fun insertMeta(rows: List<MetaEntity>)
}
