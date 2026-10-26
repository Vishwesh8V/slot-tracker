package com.vishwesh.slottracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY sortOrder, id")
    suspend fun all(): List<ItemEntity>

    @Insert
    suspend fun insert(item: ItemEntity): Long
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM template_slots WHERE dayOfWeek = :dayOfWeek")
    suspend fun templateSlots(dayOfWeek: Int): List<TemplateSlotEntity>

    @Query(
        """
        SELECT a.* FROM template_assignments a
        JOIN template_slots s ON a.templateSlotId = s.id
        WHERE s.dayOfWeek = :dayOfWeek
        ORDER BY a.sortOrder, a.id
        """,
    )
    suspend fun templateAssignments(dayOfWeek: Int): List<TemplateAssignmentEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM override_days WHERE date = :date)")
    suspend fun hasOverride(date: LocalDate): Boolean

    @Query("SELECT * FROM override_slots WHERE date = :date")
    suspend fun overrideSlots(date: LocalDate): List<OverrideSlotEntity>

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
    suspend fun insertTemplateSlot(slot: TemplateSlotEntity): Long

    @Insert
    suspend fun insertTemplateAssignment(assignment: TemplateAssignmentEntity): Long
}

@Dao
interface TaskStateDao {
    @Query("SELECT * FROM task_states WHERE date = :date")
    suspend fun forDate(date: LocalDate): List<TaskStateEntity>

    @Query("SELECT * FROM task_states WHERE date = :date AND slotKey = :slotKey AND itemId = :itemId")
    suspend fun get(date: LocalDate, slotKey: String, itemId: Long): TaskStateEntity?

    @Upsert
    suspend fun upsert(state: TaskStateEntity)

    @Query("DELETE FROM task_states")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertAll(entries: List<HistoryEntryEntity>)

    @Query("SELECT * FROM history ORDER BY date, slotStart, id")
    suspend fun all(): List<HistoryEntryEntity>
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: MetaEntity)
}
