package com.vishwesh.slottracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vishwesh.slottracker.SlotTrackerApp
import com.vishwesh.slottracker.appContainer
import com.vishwesh.slottracker.domain.EffectiveDay
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.reminders.Reminders
import com.vishwesh.slottracker.widget.SlotWidget
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps the day current: runs the rollover catch-up, refreshes every widget, and schedules the
 * next run (WorkManager, spec §2) at whichever comes first: the next rollover, or the next
 * slot start/end (when the widget's current/next highlight changes).
 */
object DayTick {
    private const val WORK_NAME = "day-tick"

    /** Full refresh: catch-up, widgets, next tick, and reminders. */
    suspend fun runNow(context: Context) {
        context.appContainer.repository.catchUp()
        SlotWidget().updateAll(context)
        scheduleNext(context, fromWorker = false)
        Reminders.reschedule(context)
    }

    /**
     * From inside the worker the next run is appended after the running one, because replacing
     * unique work that is running would cancel it.
     */
    suspend fun scheduleNext(context: Context, fromWorker: Boolean) {
        val day = context.appContainer.repository.loadLiveDay()
        val now = LocalDateTime.now()
        val next = listOfNotNull(
            EffectiveDay.nextRollover(now, day.rollover),
            SlotTiming.nextBoundary(day.schedule, now, day.rollover),
        ).min()
        val delay = Duration.between(ZonedDateTime.now(), next.atZone(ZoneId.systemDefault()))
            .coerceAtLeast(Duration.ZERO)
            .plusSeconds(5) // land just after the boundary, not just before it
        val request = OneTimeWorkRequestBuilder<DayTickWorker>().setInitialDelay(delay).build()
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

class DayTickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.repository.catchUp()
        SlotWidget().updateAll(applicationContext)
        DayTick.scheduleNext(applicationContext, fromWorker = true)
        return Result.success()
    }
}

/** Catch-up and alarm rescheduling after boot, app update, and clock or time-zone changes (spec §7, §9). */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext as SlotTrackerApp
        app.appScope.launch {
            try {
                DayTick.runNow(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
