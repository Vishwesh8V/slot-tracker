package com.vishwesh.slottracker

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.vishwesh.slottracker.reminders.Reminders
import com.vishwesh.slottracker.widget.SlotWidget
import com.vishwesh.slottracker.work.DayTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class SlotTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.ensureChannel(this)
        appScope.launch {
            // Catch-up on app start (spec §7): seed on first launch, close any missed days.
            DayTick.runNow(this@SlotTrackerApp)
            // Any change to the live day (ticks, notes, schedule edits) refreshes every widget
            // and the next refresh time, so all widget instances and the app stay in sync.
            container.repository.observeLiveDay()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    SlotWidget().updateAll(this@SlotTrackerApp)
                    DayTick.scheduleNext(this@SlotTrackerApp, fromWorker = false)
                }
        }
        appScope.launch {
            // Reschedule reminders after any schedule/override edit or setting change (spec §9).
            combine(container.repository.observeScheduleChanges(), container.settings.slotReminders) { _, _ -> }
                .collect { Reminders.reschedule(this@SlotTrackerApp) }
        }
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as SlotTrackerApp).container
