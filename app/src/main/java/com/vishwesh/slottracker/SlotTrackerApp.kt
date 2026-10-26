package com.vishwesh.slottracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SlotTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Catch-up on app start (spec §7): seed on first launch, close any missed days.
        appScope.launch { container.repository.catchUp() }
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as SlotTrackerApp).container
