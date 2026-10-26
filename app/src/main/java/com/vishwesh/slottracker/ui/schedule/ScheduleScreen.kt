package com.vishwesh.slottracker.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.DayRef
import com.vishwesh.slottracker.data.EditableDay
import com.vishwesh.slottracker.data.EditableSlot
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.ui.Destination
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.components.ConfirmDialog
import com.vishwesh.slottracker.ui.components.DatePickDialog
import com.vishwesh.slottracker.ui.components.ItemChips
import com.vishwesh.slottracker.ui.components.SectionLabel
import com.vishwesh.slottracker.ui.components.WarningRow
import com.vishwesh.slottracker.ui.items.ItemsTab
import com.vishwesh.slottracker.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/** Schedule (spec §6.2): the weekly template and the items. */
@Composable
fun ScheduleScreen(container: AppContainer, navigator: Navigator) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Week") }, unselectedContentColor = muted)
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Items") }, unselectedContentColor = muted)
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { navigator.open(Destination.ItemEditor(null)) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New item") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) WeekTab(container, navigator) else ItemsTab(container, navigator)
        }
    }
}

@Composable
private fun WeekTab(container: AppContainer, navigator: Navigator) {
    val schedule = container.schedule
    val week by remember { schedule.observeWeek() }.collectAsStateWithLifecycle(emptyList())
    val overrideDates by remember { schedule.observeOverrideDates() }.collectAsStateWithLifecycle(emptyList())
    val liveDate by produceState<LocalDate?>(null) { value = container.repository.currentLiveDate() }
    var expanded by remember { mutableStateOf(setOf(LocalDate.now().dayOfWeek)) }
    var pickingDate by remember { mutableStateOf(false) }
    var copySource by remember { mutableStateOf<DayOfWeek?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item(key = "edit-date") {
            FilledTonalButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit a specific date")
            }
        }
        if (overrideDates.isNotEmpty()) {
            item(key = "overrides-label") { SectionLabel("Edited dates", Modifier.padding(top = 8.dp)) }
            items(overrideDates, key = { "override-$it" }) { date ->
                OverrideDateRow(date, isToday = date == liveDate) { navigator.open(Destination.OverrideEditor(date)) }
            }
        }
        item(key = "template-label") { SectionLabel("Every week", Modifier.padding(top = 8.dp)) }
        items(week, key = { (it.ref as DayRef.Weekday).day }) { day ->
            val weekday = (day.ref as DayRef.Weekday).day
            DayCard(
                day = day,
                weekday = weekday,
                expanded = weekday in expanded,
                onToggle = { expanded = if (weekday in expanded) expanded - weekday else expanded + weekday },
                onEditSlot = { navigator.open(Destination.SlotEditor(day.ref, it)) },
                onDeleteSlot = { slot -> scope.launch { schedule.deleteSlot(day.ref, slot) } },
                onAddSlot = { navigator.open(Destination.SlotEditor(day.ref, null)) },
                onCopy = { copySource = weekday },
            )
        }
    }

    if (pickingDate) {
        liveDate?.let { minDate ->
            DatePickDialog(
                title = "Edit a specific date",
                minDate = minDate,
                marked = overrideDates.toSet(),
                onDismiss = { pickingDate = false },
                onPick = {
                    pickingDate = false
                    navigator.open(Destination.OverrideEditor(it))
                },
            )
        }
    }
    copySource?.let { source ->
        CopyDayDialog(
            source = source,
            onDismiss = { copySource = null },
            onConfirm = { targets ->
                copySource = null
                scope.launch { schedule.copyDay(source, targets) }
            },
        )
    }
}

@Composable
private fun OverrideDateRow(date: LocalDate, isToday: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(Formats.shortDate(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (isToday) {
                Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayCard(
    day: EditableDay,
    weekday: DayOfWeek,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditSlot: (EditableSlot) -> Unit,
    onDeleteSlot: (EditableSlot) -> Unit,
    onAddSlot: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        weekday.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        summary(day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    DaySlotList(day, scope = "${weekday.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])}s", onEditSlot, onDeleteSlot)
                    Row {
                        TextButton(onClick = onAddSlot) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add slot")
                        }
                        TextButton(onClick = onCopy, enabled = day.slots.isNotEmpty()) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy day to…")
                        }
                    }
                }
            }
        }
    }
}

private fun summary(day: EditableDay): String =
    if (day.slots.isEmpty()) "No slots" else day.slots.joinToString(" · ") { "${Formats.time(it.start)} ${it.label}" }

/**
 * The slots of one day (template weekday or override date), each tappable to edit, with an
 * overlap warning (spec §6.2: warn but allow). [scope] names where a deletion applies.
 */
@Composable
fun DaySlotList(
    day: EditableDay,
    scope: String,
    onEditSlot: (EditableSlot) -> Unit,
    onDeleteSlot: (EditableSlot) -> Unit,
) {
    var deleting by remember { mutableStateOf<EditableSlot?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        overlapWarning(day)?.let { WarningRow(it, Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
        if (day.slots.isEmpty()) {
            Text(
                "Nothing scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        day.slots.forEach { slot ->
            Surface(
                onClick = { onEditSlot(slot) },
                shape = MaterialTheme.shapes.medium,
                color = LocalExtraColors.current.slotTint,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slot.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Formats.timeRange(slot.start, slot.end),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { deleting = slot }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete slot", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ItemChips(slot.items, Modifier.padding(end = 10.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
    deleting?.let { slot ->
        ConfirmDialog(
            title = "Delete ${slot.label}?",
            text = "Removes the ${Formats.timeRange(slot.start, slot.end)} slot and its items from $scope.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                onDeleteSlot(slot)
            },
        )
    }
}

private fun overlapWarning(day: EditableDay): String? {
    val pairs = day.slots.flatMapIndexed { i, a ->
        day.slots.drop(i + 1)
            .filter { b -> SlotTiming.overlaps(a.start, a.end, b.start, b.end, day.rollover) }
            .map { b -> "${a.label} and ${b.label}" }
    }
    return if (pairs.isEmpty()) null else "${pairs.joinToString("; ")} overlap"
}

@Composable
private fun CopyDayDialog(source: DayOfWeek, onDismiss: () -> Unit, onConfirm: (Set<DayOfWeek>) -> Unit) {
    var targets by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    val sourceName = source.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy $sourceName to…") },
        text = {
            Column {
                Text(
                    "The selected days' slots are replaced with $sourceName's slots and items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                (DayOfWeek.entries - source).forEach { day ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { targets = if (day in targets) targets - day else targets + day },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = day in targets, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(targets) }, enabled = targets.isNotEmpty()) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
