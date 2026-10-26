package com.vishwesh.slottracker

import android.content.Context
import com.vishwesh.slottracker.data.BackupRepository
import com.vishwesh.slottracker.data.HistoryRepository
import com.vishwesh.slottracker.data.ScheduleRepository
import com.vishwesh.slottracker.data.SettingsRepository
import com.vishwesh.slottracker.data.TrackerRepository
import com.vishwesh.slottracker.data.db.AppDatabase

/** Manual DI: one instance per process, owned by [SlotTrackerApp]. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val repository = TrackerRepository(database, settings)
    val schedule = ScheduleRepository(database, repository, settings)
    val history = HistoryRepository(database, repository)
    val backup = BackupRepository(database, settings)
}
