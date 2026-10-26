package com.vishwesh.slottracker.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.vishwesh.slottracker.MainActivity
import com.vishwesh.slottracker.R
import com.vishwesh.slottracker.SlotTrackerApp
import com.vishwesh.slottracker.appContainer
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.ui.Formats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Slot reminders (spec §9): one exact alarm at a time, for the next slot start. When it fires,
 * the slot's notification is posted and the following start is scheduled. Rescheduled on app
 * start, boot, clock/time-zone change, any schedule edit, and setting changes.
 */
object Reminders {
    private const val CHANNEL_ID = "slot_reminders"
    private const val EXTRA_DATE = "date"
    private const val EXTRA_START = "start"
    private const val LOOKAHEAD_DAYS = 8L

    /**
     * Whether notifications can be shown. POST_NOTIFICATIONS exists from Android 13; on 12 the
     * app may notify unless the user turned notifications off.
     */
    fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    suspend fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val container = context.appContainer
        alarms.cancel(alarmIntent(context, null))
        if (!container.settings.slotReminders.first()) return

        val now = LocalDateTime.now()
        val rollover = container.settings.currentRolloverTime()
        val liveDate = container.repository.currentLiveDate()
        for (offset in 0..LOOKAHEAD_DAYS) {
            val date = liveDate.plusDays(offset)
            val next = container.repository.scheduleFor(date).slots
                .map { SlotTiming.window(date, it.start, it.end, rollover).start }
                .filter { it > now }
                .minOrNull() ?: continue
            val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = alarmIntent(context, date to next)
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
            return
        }
    }

    /** Posts a notification for every slot of [date] that starts at [start]. */
    @SuppressLint("MissingPermission") // checked by canPostNotifications() below
    suspend fun notify(context: Context, date: LocalDate, start: LocalDateTime) {
        val container = context.appContainer
        if (!container.settings.slotReminders.first()) return
        if (!canPostNotifications(context)) return
        val rollover = container.settings.currentRolloverTime()
        ensureChannel(context)
        container.repository.scheduleFor(date).slots
            .filter { SlotTiming.window(date, it.start, it.end, rollover).start == start }
            .forEach { slot ->
                val open = PendingIntent.getActivity(
                    context,
                    0,
                    MainActivity.todayIntent(context).setData("slottracker://today".toUri()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("${slot.label} · ${Formats.timeRange(slot.start, slot.end)}")
                    .setContentText(slot.items.joinToString(", ") { it.name })
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .build()
                NotificationManagerCompat.from(context).notify("$date/${slot.slotKey}".hashCode(), notification)
            }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Slot reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder when each slot starts, listing its items"
            },
        )
    }

    private fun alarmIntent(context: Context, target: Pair<LocalDate, LocalDateTime>?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        target?.let { (date, start) ->
            intent.putExtra(EXTRA_DATE, date.toString()).putExtra(EXTRA_START, start.toString())
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse)
            val start = intent.getStringExtra(EXTRA_START)?.let(LocalDateTime::parse)
            val pending = goAsync()
            val app = context.applicationContext as SlotTrackerApp
            app.appScope.launch {
                try {
                    if (date != null && start != null) notify(app, date, start)
                    reschedule(app)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
