package com.vishwesh.slottracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ItemEntity::class,
        TemplateSlotEntity::class,
        TemplateAssignmentEntity::class,
        OverrideDayEntity::class,
        OverrideSlotEntity::class,
        OverrideAssignmentEntity::class,
        TaskStateEntity::class,
        HistoryEntryEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun taskStateDao(): TaskStateDao
    abstract fun historyDao(): HistoryDao
    abstract fun metaDao(): MetaDao

    companion object {
        /** Tables whose changes can alter what the live day shows. */
        val LIVE_DAY_TABLES = arrayOf(
            "items",
            "template_slots",
            "template_assignments",
            "override_days",
            "override_slots",
            "override_assignments",
            "task_states",
            "app_meta",
        )

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "slot_tracker.db").build()
    }
}
