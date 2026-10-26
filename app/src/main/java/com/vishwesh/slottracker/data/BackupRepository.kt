package com.vishwesh.slottracker.data

import androidx.room.withTransaction
import com.vishwesh.slottracker.data.db.AppDatabase
import com.vishwesh.slottracker.data.db.HistoryEntryEntity
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.data.db.MetaEntity
import com.vishwesh.slottracker.data.db.OverrideAssignmentEntity
import com.vishwesh.slottracker.data.db.OverrideDayEntity
import com.vishwesh.slottracker.data.db.OverrideSlotEntity
import com.vishwesh.slottracker.data.db.TaskStateEntity
import com.vishwesh.slottracker.data.db.TemplateAssignmentEntity
import com.vishwesh.slottracker.data.db.TemplateSlotEntity
import com.vishwesh.slottracker.domain.TaskStatus
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Everything in a backup file: all tables, plus the settings (spec §9). */
data class BackupData(
    val items: List<ItemEntity>,
    val templateSlots: List<TemplateSlotEntity>,
    val templateAssignments: List<TemplateAssignmentEntity>,
    val overrideDays: List<OverrideDayEntity>,
    val overrideSlots: List<OverrideSlotEntity>,
    val overrideAssignments: List<OverrideAssignmentEntity>,
    val taskStates: List<TaskStateEntity>,
    val history: List<HistoryEntryEntity>,
    val meta: List<MetaEntity>,
    val rolloverTime: LocalTime,
    val slotReminders: Boolean,
)

class InvalidBackupException(message: String) : Exception(message)

/**
 * Export and import as one versioned JSON file. Ids are kept as they are, so an import
 * restores the database exactly.
 */
class BackupRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val dao = db.backupDao()

    suspend fun export(): String {
        val data = db.withTransaction {
            BackupData(
                dao.items(), dao.templateSlots(), dao.templateAssignments(), dao.overrideDays(), dao.overrideSlots(),
                dao.overrideAssignments(), dao.taskStates(), dao.history(), dao.meta(),
                settings.currentRolloverTime(), settings.slotReminders.first(),
            )
        }
        return encode(data).toString(2)
    }

    /** Parses and validates a backup without changing anything. */
    fun parse(json: String): BackupData {
        val data = try {
            decode(JSONObject(json))
        } catch (e: JSONException) {
            throw InvalidBackupException("This isn't a valid Slot Tracker backup (${e.message}).")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This backup contains invalid data (${e.message}).")
        } catch (e: java.time.DateTimeException) {
            throw InvalidBackupException("This backup contains an invalid date or time (${e.message}).")
        }
        validate(data)
        return data
    }

    /** Replaces all data and settings with [data], in one transaction. */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            dao.clearTaskStates()
            dao.clearTemplateAssignments()
            dao.clearOverrideAssignments()
            dao.clearTemplateSlots()
            dao.clearOverrideSlots()
            dao.clearOverrideDays()
            dao.clearHistory()
            dao.clearItems()
            dao.clearMeta()
            dao.insertItems(data.items)
            dao.insertTemplateSlots(data.templateSlots)
            dao.insertTemplateAssignments(data.templateAssignments)
            dao.insertOverrideDays(data.overrideDays)
            dao.insertOverrideSlots(data.overrideSlots)
            dao.insertOverrideAssignments(data.overrideAssignments)
            dao.insertTaskStates(data.taskStates)
            dao.insertHistory(data.history)
            dao.insertMeta(data.meta)
        }
        settings.setRolloverTime(data.rolloverTime)
        settings.setSlotReminders(data.slotReminders)
    }

    private fun validate(data: BackupData) {
        fun check(ok: Boolean, problem: String) {
            if (!ok) throw InvalidBackupException("This backup is inconsistent: $problem.")
        }
        val itemIds = data.items.map { it.id }.toSet()
        val templateSlotIds = data.templateSlots.map { it.id }.toSet()
        val overrideDates = data.overrideDays.map { it.date }.toSet()
        val overrideSlotIds = data.overrideSlots.map { it.id }.toSet()
        check(itemIds.size == data.items.size, "duplicate item ids")
        check(data.templateSlots.all { it.dayOfWeek in 1..7 }, "a template slot has an invalid weekday")
        check(data.templateAssignments.all { it.templateSlotId in templateSlotIds && it.itemId in itemIds }, "a template assignment points to a missing slot or item")
        check(data.overrideSlots.all { it.date in overrideDates }, "an override slot has no override day")
        check(data.overrideAssignments.all { it.overrideSlotId in overrideSlotIds && it.itemId in itemIds }, "an override assignment points to a missing slot or item")
        check(data.taskStates.all { it.itemId in itemIds }, "a task points to a missing item")
    }

    private companion object {
        const val APP = "slot-tracker"
        const val SCHEMA_VERSION = 1

        fun encode(d: BackupData) = JSONObject()
            .put("app", APP)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put(
                "settings",
                JSONObject().put("rolloverTime", d.rolloverTime.toString()).put("slotReminders", d.slotReminders),
            )
            .put("items", array(d.items) {
                JSONObject().put("id", it.id).put("name", it.name).put("colorArgb", it.colorArgb)
                    .put("details", it.details ?: JSONObject.NULL).put("sortOrder", it.sortOrder).put("archived", it.archived)
            })
            .put("templateSlots", array(d.templateSlots) {
                JSONObject().put("id", it.id).put("dayOfWeek", it.dayOfWeek).put("label", it.label)
                    .put("startTime", it.startTime.toString()).put("endTime", it.endTime.toString()).put("sortOrder", it.sortOrder)
            })
            .put("templateAssignments", array(d.templateAssignments) {
                JSONObject().put("id", it.id).put("templateSlotId", it.templateSlotId).put("itemId", it.itemId).put("sortOrder", it.sortOrder)
            })
            .put("overrideDays", array(d.overrideDays) { JSONObject().put("date", it.date.toString()) })
            .put("overrideSlots", array(d.overrideSlots) {
                JSONObject().put("id", it.id).put("date", it.date.toString()).put("label", it.label)
                    .put("startTime", it.startTime.toString()).put("endTime", it.endTime.toString()).put("sortOrder", it.sortOrder)
                    .put("sourceTemplateSlotId", it.sourceTemplateSlotId ?: JSONObject.NULL)
            })
            .put("overrideAssignments", array(d.overrideAssignments) {
                JSONObject().put("id", it.id).put("overrideSlotId", it.overrideSlotId).put("itemId", it.itemId).put("sortOrder", it.sortOrder)
            })
            .put("taskStates", array(d.taskStates) {
                JSONObject().put("date", it.date.toString()).put("slotKey", it.slotKey).put("itemId", it.itemId).put("done", it.done)
                    .put("completedAt", it.completedAt?.toString() ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL)
            })
            .put("history", array(d.history) {
                JSONObject().put("id", it.id).put("date", it.date.toString()).put("itemId", it.itemId)
                    .put("itemNameSnapshot", it.itemNameSnapshot).put("itemColorSnapshot", it.itemColorSnapshot)
                    .put("slotLabelSnapshot", it.slotLabelSnapshot).put("slotStart", it.slotStart.toString())
                    .put("slotEnd", it.slotEnd.toString()).put("status", it.status.name)
                    .put("completedAt", it.completedAt?.toString() ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL)
            })
            .put("meta", array(d.meta) { JSONObject().put("key", it.key).put("value", it.value) })

        fun decode(o: JSONObject): BackupData {
            if (o.optString("app") != APP) throw InvalidBackupException("This file isn't a Slot Tracker backup.")
            val version = o.getInt("schemaVersion")
            if (version > SCHEMA_VERSION) throw InvalidBackupException("This backup is from a newer version of the app.")
            val settings = o.getJSONObject("settings")
            return BackupData(
                items = list(o, "items") {
                    ItemEntity(it.getLong("id"), it.getString("name"), it.getInt("colorArgb"), it.nullableString("details"), it.getInt("sortOrder"), it.getBoolean("archived"))
                },
                templateSlots = list(o, "templateSlots") {
                    TemplateSlotEntity(it.getLong("id"), it.getInt("dayOfWeek"), it.getString("label"), it.time("startTime"), it.time("endTime"), it.getInt("sortOrder"))
                },
                templateAssignments = list(o, "templateAssignments") {
                    TemplateAssignmentEntity(it.getLong("id"), it.getLong("templateSlotId"), it.getLong("itemId"), it.getInt("sortOrder"))
                },
                overrideDays = list(o, "overrideDays") { OverrideDayEntity(it.date("date")) },
                overrideSlots = list(o, "overrideSlots") {
                    OverrideSlotEntity(
                        it.getLong("id"), it.date("date"), it.getString("label"), it.time("startTime"), it.time("endTime"),
                        it.getInt("sortOrder"), if (it.isNull("sourceTemplateSlotId")) null else it.getLong("sourceTemplateSlotId"),
                    )
                },
                overrideAssignments = list(o, "overrideAssignments") {
                    OverrideAssignmentEntity(it.getLong("id"), it.getLong("overrideSlotId"), it.getLong("itemId"), it.getInt("sortOrder"))
                },
                taskStates = list(o, "taskStates") {
                    TaskStateEntity(
                        it.date("date"), it.getString("slotKey"), it.getLong("itemId"), it.getBoolean("done"),
                        it.nullableString("completedAt")?.let(Instant::parse), it.nullableString("note"),
                    )
                },
                history = list(o, "history") {
                    HistoryEntryEntity(
                        it.getLong("id"), it.date("date"), it.getLong("itemId"), it.getString("itemNameSnapshot"),
                        it.getInt("itemColorSnapshot"), it.getString("slotLabelSnapshot"), it.time("slotStart"), it.time("slotEnd"),
                        TaskStatus.valueOf(it.getString("status")), it.nullableString("completedAt")?.let(Instant::parse),
                        it.nullableString("note"),
                    )
                },
                meta = list(o, "meta") { MetaEntity(it.getString("key"), it.getString("value")) },
                rolloverTime = LocalTime.parse(settings.getString("rolloverTime")),
                slotReminders = settings.getBoolean("slotReminders"),
            )
        }

        fun <T> array(rows: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(encode(it)) } }

        fun <T> list(o: JSONObject, key: String, decode: (JSONObject) -> T): List<T> {
            val array = o.getJSONArray(key)
            return List(array.length()) { decode(array.getJSONObject(it)) }
        }

        fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
        fun JSONObject.date(key: String): LocalDate = LocalDate.parse(getString(key))
        fun JSONObject.time(key: String): LocalTime = LocalTime.parse(getString(key))
    }
}
