package com.vishwesh.slottracker.ui.today

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vishwesh.slottracker.data.SettingsRepository
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.LiveDay
import com.vishwesh.slottracker.domain.DaySchedule
import com.vishwesh.slottracker.domain.ScheduledSlot
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.domain.Task
import com.vishwesh.slottracker.domain.TaskKey
import com.vishwesh.slottracker.ui.Destination
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.Tab
import com.vishwesh.slottracker.ui.components.ColorBar
import com.vishwesh.slottracker.ui.theme.LocalExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private const val PAGE_COUNT = 4001
private const val CENTER_PAGE = PAGE_COUNT / 2
private val FlameColor = Color(0xFFFF7043)

/**
 * Today (spec §6.1). Swipe or use the arrows to peek at other days: upcoming days are a
 * read-only preview, past days link to History.
 */
@Composable
fun TodayScreen(container: AppContainer, navigator: Navigator, focusTask: TaskKey?, onFocusConsumed: () -> Unit) {
    val repository = container.repository
    val liveDay by remember { repository.observeLiveDay() }.collectAsStateWithLifecycle(null)
    val day = liveDay ?: return
    val pager = rememberPagerState(initialPage = CENTER_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    LaunchedEffect(focusTask) {
        if (focusTask != null && pager.currentPage != CENTER_PAGE) pager.scrollToPage(CENTER_PAGE)
    }

    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        val offset = page - CENTER_PAGE
        val date = day.date.plusDays(offset.toLong())
        val dayNav = DayNav(
            onPrevious = { scope.launch { pager.animateScrollToPage(page - 1) } },
            onNext = { scope.launch { pager.animateScrollToPage(page + 1) } },
            onToday = if (offset != 0) ({ scope.launch { pager.animateScrollToPage(CENTER_PAGE) } }) else null,
        )
        when {
            offset == 0 -> LivePage(container, day, dayNav, navigator, focusTask, onFocusConsumed)
            offset > 0 -> PreviewPage(container, date, dayNav, navigator)
            else -> PastPage(date, dayNav, onOpenHistory = { navigator.showTab(Tab.HISTORY) })
        }
    }
}

private class DayNav(val onPrevious: () -> Unit, val onNext: () -> Unit, val onToday: (() -> Unit)?)

@Composable
private fun LivePage(
    container: AppContainer,
    day: LiveDay,
    dayNav: DayNav,
    navigator: Navigator,
    focusTask: TaskKey?,
    onFocusConsumed: () -> Unit,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val streaks by remember { repository.observeItemStreaks() }.collectAsStateWithLifecycle(emptyMap())
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val highlighted = SlotTiming.highlighted(day.schedule, now, day.rollover)
    val listState = rememberLazyListState()
    var noteTask by remember { mutableStateOf<Task?>(null) }
    var flashKey by remember { mutableStateOf<TaskKey?>(null) }
    var pendingFocus by remember { mutableStateOf<TaskKey?>(null) }

    // Take the request first, so clearing it upstream doesn't cancel the scroll below.
    LaunchedEffect(focusTask) {
        if (focusTask != null) {
            pendingFocus = focusTask
            onFocusConsumed()
        }
    }
    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        val slotIndex = day.schedule.slots.indexOfFirst { it.slotKey == target.slotKey }
        if (slotIndex >= 0) {
            listState.animateScrollToItem(slotIndex + 1) // item 0 is the header
            flashKey = target
            delay(1800)
            flashKey = null
        }
        pendingFocus = null
    }

    val tasksBySlot = day.tasks.groupBy { it.slot.slotKey }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "header") {
            DayHeader(day.date, "Today", dayNav)
            Progress(done = day.tasks.count { it.done }, total = day.tasks.size)
            OverrideChip(
                isOverride = day.schedule.isOverride,
                onClick = { navigator.open(Destination.OverrideEditor(day.date)) },
            )
            Spacer(Modifier.height(12.dp))
            BatteryTip(container.settings)
        }
        if (day.tasks.isEmpty()) {
            item(key = "empty") { EmptyDay("Nothing scheduled today") }
        }
        day.schedule.slots.forEach { slot ->
            item(key = slot.slotKey) {
                val window = SlotTiming.window(day.date, slot.start, slot.end, day.rollover)
                val badge = when {
                    slot.slotKey != highlighted -> null
                    now >= window.start -> "Now"
                    else -> "Next"
                }
                SlotCard(slot, badge) {
                    tasksBySlot[slot.slotKey].orEmpty().forEach { task ->
                        LiveTaskRow(
                            task = task,
                            streak = streaks[task.item.id] ?: 0,
                            flash = task.key == flashKey,
                            onCheckedChange = { checked -> scope.launch { repository.setDone(day.date, task.key, checked) } },
                            onNote = { noteTask = task },
                        )
                    }
                }
            }
        }
    }

    noteTask?.let { task ->
        NoteDialog(
            task = task,
            onDismiss = { noteTask = null },
            onSave = { note ->
                noteTask = null
                scope.launch { repository.setNote(day.date, task.key, note) }
            },
        )
    }
}

@Composable
private fun PreviewPage(container: AppContainer, date: LocalDate, dayNav: DayNav, navigator: Navigator) {
    val schedule by remember(date) { container.repository.observeSchedule(date) }.collectAsStateWithLifecycle(null)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "header") {
            DayHeader(date, "Upcoming", dayNav)
            Text(
                "Preview · tasks can be ticked on the day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            schedule?.takeIf(DaySchedule::isOverride)?.let {
                OverrideChip(isOverride = true, onClick = { navigator.open(Destination.OverrideEditor(date)) })
            }
            Spacer(Modifier.height(12.dp))
        }
        val slots = schedule?.slots ?: return@LazyColumn
        if (slots.isEmpty()) item(key = "empty") { EmptyDay("Nothing scheduled") }
        slots.forEach { slot ->
            item(key = slot.slotKey) {
                SlotCard(slot, badge = null) {
                    slot.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorBar(Color(item.colorArgb), 28.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                item.details?.let { Details(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastPage(date: LocalDate, dayNav: DayNav, onOpenHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        DayHeader(date, "Past", dayNav)
        Spacer(Modifier.height(48.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                "This day is in History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
            FilledTonalButton(onClick = onOpenHistory) { Text("Open History") }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, tag: String, dayNav: DayNav) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Formats.weekday(date), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Formats.dayMonth(date), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Pill(tag)
            }
        }
        dayNav.onToday?.let { TextButton(onClick = it) { Text("Today") } }
        IconButton(onClick = dayNav.onPrevious) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day") }
        IconButton(onClick = dayNav.onNext) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day") }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun Progress(done: Int, total: Int) {
    if (total == 0) return
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            if (done == total) "All $total done" else "$done of $total done",
            style = MaterialTheme.typography.labelLarge,
            color = if (done == total) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            trackColor = LocalExtraColors.current.slotTint,
        )
    }
}

@Composable
private fun OverrideChip(isOverride: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (isOverride) "Edited for this day" else "Edit this day only") },
        leadingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.padding(top = 8.dp),
        colors = if (isOverride) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        border = if (isOverride) null else AssistChipDefaults.assistChipBorder(enabled = true),
    )
}

/** One slot group: a rounded container with the slot's header, holding its task rows. */
@Composable
private fun SlotCard(slot: ScheduledSlot, badge: String?, content: @Composable ColumnScope.() -> Unit) {
    val highlighted = badge != null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) LocalExtraColors.current.highlightTint else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
            Row(Modifier.padding(end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(slot.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    Formats.timeRange(slot.start, slot.end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun LiveTaskRow(
    task: Task,
    streak: Int,
    flash: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNote: () -> Unit,
) {
    val flashColor by animateColorAsState(
        if (flash) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        label = "flash",
    )
    val faded = if (task.done) 0.5f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(flashColor)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorBar(Color(task.item.colorArgb).copy(alpha = if (task.done) 0.5f else 1f), 32.dp)
        Checkbox(checked = task.done, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                task.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded),
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            task.item.details?.let { Details(it) }
            task.note?.let { note ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.StickyNote2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }
        if (streak > 0) StreakBadge(streak)
        IconButton(onClick = onNote) {
            Icon(Icons.Outlined.EditNote, contentDescription = if (task.note == null) "Add note" else "Edit note")
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = FlameColor, modifier = Modifier.size(16.dp))
        Text("$streak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Details(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyDay(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteDialog(task: Task, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember { mutableStateOf(task.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.item.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. finished DDPM section 3") },
                label = { Text("Note") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (task.note != null) TextButton(onClick = { onSave(null) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * One-time, dismissible tip on Samsung phones (spec §9): set battery usage to Unrestricted so
 * alarms and rollover are reliable. Hidden once dismissed or once the app is unrestricted.
 */
@Composable
private fun BatteryTip(settings: SettingsRepository) {
    if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissed by settings.batteryTipDismissed.collectAsStateWithLifecycle(initialValue = true)
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = isUnrestricted(context)
        onPauseOrDispose {}
    }
    if (dismissed || unrestricted) return

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Keep reminders on time", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                "Samsung may pause the app in the background. Open App info → Battery and choose Unrestricted " +
                    "so reminders and the daily rollover run on time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { settings.dismissBatteryTip() } }) { Text("Dismiss") }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                }) { Text("Open App info") }
            }
        }
    }
}

private fun isUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
