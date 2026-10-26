package com.vishwesh.slottracker

import android.content.Context
import com.vishwesh.slottracker.data.SettingsRepository
import com.vishwesh.slottracker.data.TrackerRepository
import com.vishwesh.slottracker.data.db.AppDatabase

/** Manual DI: one instance per process, owned by [SlotTrackerApp]. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val repository = TrackerRepository(database, settings)
}
