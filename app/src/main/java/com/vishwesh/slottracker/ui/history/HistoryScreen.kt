package com.vishwesh.slottracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.HistorySnapshot
import com.vishwesh.slottracker.data.db.HistoryEntryEntity
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.domain.DayStatus
import com.vishwesh.slottracker.domain.Occurrence
import com.vishwesh.slottracker.domain.Rate
import com.vishwesh.slottracker.domain.Stats
import com.vishwesh.slottracker.domain.TaskStatus
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.components.ColorBar
import com.vishwesh.slottracker.ui.components.ColorDot
import com.vishwesh.slottracker.ui.components.DayColors
import com.vishwesh.slottracker.ui.components.MonthCalendar
import com.vishwesh.slottracker.ui.components.SectionLabel
import com.vishwesh.slottracker.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val FlameColor = Color(0xFFFF7043)

/** History (spec §6.3): month calendar, day details with editing, item filter and stats. */
@Composable
fun HistoryScreen(container: AppContainer) {
    val snapshot by remember { container.history.observe() }.collectAsStateWithLifecycle(null)
    val data = snapshot ?: return
    val liveDate = data.liveDay.date
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(liveDate.minusDays(1))) }
    var selected by remember { mutableStateOf(liveDate.minusDays(1)) }
    var editing by remember { mutableStateOf<HistoryEntryEntity?>(null) }
    val scope = rememberCoroutineScope()

    // Items you can filter by: active ones, plus archived ones that still have history.
    val withHistory = data.entries.map { it.itemId }.toSet()
    val filterItems = data.items.filter { !it.archived || it.id in withHistory }
    val entries = data.entries.filter { filter == null || it.itemId == filter }
    val byDate = entries.groupBy { it.date }
    val statusColors = statusColors()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "title") {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 12.dp),
            )
        }
        item(key = "filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All items") }) }
                items(filterItems, key = { it.id }) { item ->
                    FilterChip(
                        selected = filter == item.id,
                        onClick = { filter = if (filter == item.id) null else item.id },
                        label = { Text(item.name) },
                        leadingIcon = { ColorDot(Color(item.colorArgb), size = 10.dp) },
                    )
                }
            }
        }
        item(key = "stats") {
            val item = filterItems.firstOrNull { it.id == filter }
            if (item == null) OverallCard(data, filterItems) { filter = it } else ItemCard(data, item)
        }
        item(key = "calendar") {
            Card {
                MonthCalendar(
                    month = month,
                    selected = selected,
                    onSelect = { selected = it },
                    onMonthChange = { month = it },
                    maxDate = liveDate,
                    today = liveDate,
                    dayColors = { date ->
                        if (date >= liveDate) null else statusColors[Stats.dayStatus(occurrencesOf(byDate[date]))]
                    },
                )
                Legend(statusColors)
            }
        }
        item(key = "day-title") {
            SectionLabel(Formats.shortDate(selected), Modifier.padding(top = 12.dp))
        }
        val dayEntries = byDate[selected].orEmpty()
        when {
            selected >= liveDate -> item(key = "in-progress") { Muted("Today is still in progress. Tick tasks on Today; they're logged here at rollover.") }
            dayEntries.isEmpty() -> item(key = "nothing") { Muted("Nothing scheduled") }
            else -> items(dayEntries, key = { it.id }) { entry -> EntryRow(entry) { editing = entry } }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { status, note ->
                editing = null
                scope.launch { container.history.updateEntry(entry.id, status, note) }
            },
        )
    }
}

private fun occurrencesOf(entries: List<HistoryEntryEntity>?) =
    entries.orEmpty().map { Occurrence(it.date, it.itemId, it.slotStart, it.status == TaskStatus.DONE) }

@Composable
private fun statusColors(): Map<DayStatus, DayColors> {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalExtraColors.current.danger
    return mapOf(
        DayStatus.ALL_DONE to DayColors(scheme.primary, scheme.onPrimary),
        DayStatus.PARTIAL to DayColors(scheme.primary.copy(alpha = 0.3f), scheme.onSurface),
        DayStatus.NONE_DONE to DayColors(danger.copy(alpha = 0.18f), scheme.onSurface),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun Legend(colors: Map<DayStatus, DayColors>) {
    Row(Modifier.padding(start = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(DayStatus.ALL_DONE to "All done", DayStatus.PARTIAL to "Partly", DayStatus.NONE_DONE to "None done").forEach { (status, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors.getValue(status).fill))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OverallCard(data: HistorySnapshot, items: List<ItemEntity>, onPickItem: (Long) -> Unit) {
    val overall = Stats.overall(data.occurrences, data.todayOccurrences, data.liveDay.date)
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${overall.streak.current}", "Day streak", Modifier.weight(1f), flame = true)
            StatTile("${overall.streak.best}", "Best streak", Modifier.weight(1f))
            StatTile(percent(overall.allTime), "Completion", Modifier.weight(1f))
        }
        if (items.isNotEmpty()) {
            Text(
                "Items",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
            )
            items.forEach { item ->
                val stats = Stats.forItem(item.id, data.occurrences, data.todayOccurrences, data.liveDay.date)
                Surface(onClick = { onPickItem(item.id) }, shape = MaterialTheme.shapes.medium, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Color(item.colorArgb), size = 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = FlameColor, modifier = Modifier.size(16.dp))
                        Text("${stats.streak.current}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                        Text(percent(stats.allTime), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(data: HistorySnapshot, item: ItemEntity) {
    val stats = Stats.forItem(item.id, data.occurrences, data.todayOccurrences, data.liveDay.date)
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${stats.streak.current}", "Streak", Modifier.weight(1f), flame = true)
            StatTile("${stats.streak.best}", "Best streak", Modifier.weight(1f))
            StatTile("${stats.totalDone}", "Sessions done", Modifier.weight(1f))
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(percent(stats.last7), "Last 7 days", Modifier.weight(1f))
            StatTile(percent(stats.last30), "Last 30 days", Modifier.weight(1f))
            StatTile(percent(stats.allTime), "All time", Modifier.weight(1f))
        }
    }
}

private fun percent(rate: Rate): String = rate.percent?.let { "$it%" } ?: "—"

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, flame: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.medium, color = LocalExtraColors.current.slotTint, contentColor = MaterialTheme.colorScheme.onSurface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (flame) Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = FlameColor, modifier = Modifier.size(20.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun EntryRow(entry: HistoryEntryEntity, onClick: () -> Unit) {
    val done = entry.status == TaskStatus.DONE
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorBar(Color(entry.itemColorSnapshot), 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.itemNameSnapshot, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.slotLabelSnapshot} · ${Formats.timeRange(entry.slotStart, entry.slotEnd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.note?.let { note ->
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                }
            }
            StatusPill(done, entry.completedAt?.let { Formats.time(it.atZone(ZoneId.systemDefault()).toLocalTime()) })
        }
    }
}

@Composable
private fun StatusPill(done: Boolean, time: String?) {
    val danger = LocalExtraColors.current.danger
    val (bg, fg) = if (done) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        danger.copy(alpha = 0.14f) to danger
    }
    Text(
        if (done) (time?.let { "Done · $it" } ?: "Done") else "Missed",
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(entry: HistoryEntryEntity, onDismiss: () -> Unit, onSave: (TaskStatus, String?) -> Unit) {
    var status by remember { mutableStateOf(entry.status) }
    var note by remember { mutableStateOf(entry.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(entry.itemNameSnapshot)
                Text(
                    "${Formats.shortDate(entry.date)} · ${entry.slotLabelSnapshot}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TaskStatus.DONE to "Done", TaskStatus.MISSED to "Missed").forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = status == value,
                            onClick = { status = value },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.size(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
