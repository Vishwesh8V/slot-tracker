package com.vishwesh.slottracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * The settings from spec §8 that are stored as preferences, plus two one-time UI flags
 * (not user-facing settings).
 */
class SettingsRepository(context: Context) {
    private val store = context.settingsStore

    val rolloverTime: Flow<LocalTime> = store.data.map { prefs ->
        val minutes = prefs[ROLLOVER_MINUTES] ?: DEFAULT_ROLLOVER_MINUTES
        LocalTime.of(minutes / 60, minutes % 60)
    }

    val slotReminders: Flow<Boolean> = store.data.map { it[SLOT_REMINDERS] ?: true }

    /** Whether the one-time Samsung battery tip was dismissed (spec §9). */
    val batteryTipDismissed: Flow<Boolean> = store.data.map { it[BATTERY_TIP_DISMISSED] ?: false }

    /** Whether notification permission was already requested on first launch. */
    val notificationPermissionAsked: Flow<Boolean> = store.data.map { it[NOTIFICATION_PERMISSION_ASKED] ?: false }

    suspend fun currentRolloverTime(): LocalTime = rolloverTime.first()

    suspend fun setRolloverTime(time: LocalTime) {
        store.edit { it[ROLLOVER_MINUTES] = time.hour * 60 + time.minute }
    }

    suspend fun setSlotReminders(enabled: Boolean) {
        store.edit { it[SLOT_REMINDERS] = enabled }
    }

    suspend fun dismissBatteryTip() {
        store.edit { it[BATTERY_TIP_DISMISSED] = true }
    }

    suspend fun markNotificationPermissionAsked() {
        store.edit { it[NOTIFICATION_PERMISSION_ASKED] = true }
    }

    private companion object {
        const val DEFAULT_ROLLOVER_MINUTES = 6 * 60
        val ROLLOVER_MINUTES = intPreferencesKey("rollover_minutes")
        val SLOT_REMINDERS = booleanPreferencesKey("slot_reminders")
        val BATTERY_TIP_DISMISSED = booleanPreferencesKey("battery_tip_dismissed")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
    }
}
