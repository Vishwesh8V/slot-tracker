package com.vishwesh.slottracker.ui

import android.Manifest
import android.os.Build
import com.vishwesh.slottracker.reminders.Reminders
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.DayRef
import com.vishwesh.slottracker.data.EditableSlot
import com.vishwesh.slottracker.domain.TaskKey
import com.vishwesh.slottracker.ui.history.HistoryScreen
import com.vishwesh.slottracker.ui.items.ItemEditorScreen
import com.vishwesh.slottracker.ui.schedule.OverrideEditorScreen
import com.vishwesh.slottracker.ui.schedule.ScheduleScreen
import com.vishwesh.slottracker.ui.schedule.SlotEditorScreen
import com.vishwesh.slottracker.ui.settings.SettingsScreen
import com.vishwesh.slottracker.ui.today.TodayScreen
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY("Today", Icons.Outlined.Today, Icons.Filled.Today),
    SCHEDULE("Schedule", Icons.Outlined.CalendarViewWeek, Icons.Filled.CalendarViewWeek),
    HISTORY("History", Icons.Outlined.History, Icons.Filled.History),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Full-screen editors opened on top of the tabs. */
sealed interface Destination {
    data class OverrideEditor(val date: LocalDate) : Destination
    data class SlotEditor(val ref: DayRef, val slot: EditableSlot?) : Destination
    data class ItemEditor(val itemId: Long?) : Destination
}

/** Navigation for the screens in this file's tree. */
class Navigator(private val stack: MutableList<Destination>, private val selectTab: (Tab) -> Unit) {
    fun open(destination: Destination) { stack += destination }
    fun back() { stack.removeLastOrNull() }
    fun showTab(tab: Tab) { stack.clear(); selectTab(tab) }
}

/**
 * [focusTask] is a task to scroll to on Today (from a widget tap); [onFocusConsumed] clears it.
 */
@Composable
fun AppRoot(container: AppContainer, focusTask: TaskKey?, focusSerial: Int, onFocusConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val stack = remember { mutableStateListOf<Destination>() }
    val navigator = remember { Navigator(stack) { tab = it } }

    LaunchedEffect(focusSerial) {
        if (focusSerial > 0) navigator.showTab(Tab.TODAY)
    }
    BackHandler(enabled = stack.isNotEmpty()) { navigator.back() }
    FirstLaunchNotificationPermission(container)

    // Tabs stay composed under any open editor, so their state (scroll, expanded days) survives.
    Box(Modifier.fillMaxSize()) {
        Tabs(container, navigator, tab, onSelect = { tab = it }, focusTask, onFocusConsumed)
        val top = stack.lastOrNull()
        if (top != null) {
            // An opaque Surface also stops touches reaching the tabs underneath.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (top) {
                    is Destination.OverrideEditor -> OverrideEditorScreen(container, top.date, navigator)
                    is Destination.SlotEditor -> SlotEditorScreen(container, top.ref, top.slot, navigator)
                    is Destination.ItemEditor -> ItemEditorScreen(container, top.itemId, navigator)
                }
            }
        }
    }
}

@Composable
private fun Tabs(
    container: AppContainer,
    navigator: Navigator,
    tab: Tab,
    onSelect: (Tab) -> Unit,
    focusTask: TaskKey?,
    onFocusConsumed: () -> Unit,
) {
    // Each tab handles the status bar itself; the content padding here is just the nav bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { onSelect(it) },
                        icon = { Icon(if (tab == it) it.selectedIcon else it.icon, contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Content stops at the status bar, so scrolled lists never run under the clock.
        Box(Modifier.padding(padding).statusBarsPadding().fillMaxSize()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(container, navigator, focusTask, onFocusConsumed)
                Tab.SCHEDULE -> ScheduleScreen(container, navigator)
                Tab.HISTORY -> HistoryScreen(container)
                Tab.SETTINGS -> SettingsScreen(container)
            }
        }
    }
}

/**
 * Slot reminders are on by default, so the notification permission is requested on first
 * launch (spec §9: "request POST_NOTIFICATIONS on first enable").
 */
@Composable
private fun FirstLaunchNotificationPermission(container: AppContainer) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val settings = container.settings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect // no runtime permission before 13
        val granted = Reminders.canPostNotifications(context)
        if (!granted && settings.slotReminders.first() && !settings.notificationPermissionAsked.first()) {
            settings.markNotificationPermissionAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
