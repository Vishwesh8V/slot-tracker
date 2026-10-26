package com.vishwesh.slottracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vishwesh.slottracker.domain.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val details: String?,
    val sortOrder: Int,
    val archived: Boolean = false,
)

/** [dayOfWeek] is 1=Mon..7=Sun. */
@Entity(tableName = "template_slots", indices = [Index("dayOfWeek")])
data class TemplateSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val sortOrder: Int,
)

@Entity(
    tableName = "template_assignments",
    foreignKeys = [
        ForeignKey(TemplateSlotEntity::class, ["id"], ["templateSlotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateSlotId"), Index("itemId")],
)
data class TemplateAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateSlotId: Long,
    val itemId: Long,
    val sortOrder: Int,
)

/** Marks that [date] has an override, so an override with zero slots is still an override. */
@Entity(tableName = "override_days")
data class OverrideDayEntity(
    @PrimaryKey val date: LocalDate,
)

/** [sourceTemplateSlotId] is the template slot this slot was copied from, if any. */
@Entity(
    tableName = "override_slots",
    foreignKeys = [
        ForeignKey(OverrideDayEntity::class, ["date"], ["date"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("date")],
)
data class OverrideSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val sortOrder: Int,
    val sourceTemplateSlotId: Long?,
)

@Entity(
    tableName = "override_assignments",
    foreignKeys = [
        ForeignKey(OverrideSlotEntity::class, ["id"], ["overrideSlotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("overrideSlotId"), Index("itemId")],
)
data class OverrideAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val overrideSlotId: Long,
    val itemId: Long,
    val sortOrder: Int,
)

/** Live state of one task on the live day. Keyed by the task itself so ticks can be upserted. */
@Entity(
    tableName = "task_states",
    primaryKeys = ["date", "slotKey", "itemId"],
    foreignKeys = [
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class TaskStateEntity(
    val date: LocalDate,
    val slotKey: String,
    val itemId: Long,
    val done: Boolean,
    val completedAt: Instant?,
    val note: String?,
)

/** No foreign key on [itemId]: history outlives archived items and keeps its snapshots. */
@Entity(tableName = "history", indices = [Index("date"), Index("itemId")])
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val itemId: Long,
    val itemNameSnapshot: String,
    val itemColorSnapshot: Int,
    val slotLabelSnapshot: String,
    val slotStart: LocalTime,
    val slotEnd: LocalTime,
    val status: TaskStatus,
    val completedAt: Instant?,
    val note: String?,
)

/** Small key/value table for app state that must change in the same transaction as data. */
@Entity(tableName = "app_meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
