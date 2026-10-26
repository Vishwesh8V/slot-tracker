package com.vishwesh.slottracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vishwesh.slottracker.domain.TaskKey
import com.vishwesh.slottracker.ui.AppRoot
import com.vishwesh.slottracker.ui.theme.SlotTrackerTheme

class MainActivity : ComponentActivity() {
    // A task to scroll to on Today, from a widget tap. The serial makes repeat taps count.
    private var focusTask by mutableStateOf<TaskKey?>(null)
    private var focusSerial by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        val container = appContainer
        setContent {
            SlotTrackerTheme {
                AppRoot(container, focusTask, focusSerial, onFocusConsumed = { focusTask = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.data == null) return // a normal launch, not from the widget
        focusTask = intent.getStringExtra(EXTRA_SLOT_KEY)?.let { TaskKey(it, intent.getLongExtra(EXTRA_ITEM_ID, -1)) }
        focusSerial++
    }

    companion object {
        private const val EXTRA_SLOT_KEY = "slotKey"
        private const val EXTRA_ITEM_ID = "itemId"

        fun todayIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        fun taskIntent(context: Context, key: TaskKey): Intent =
            todayIntent(context).putExtra(EXTRA_SLOT_KEY, key.slotKey).putExtra(EXTRA_ITEM_ID, key.itemId)
    }
}
