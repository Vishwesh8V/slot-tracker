package com.vishwesh.slottracker.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.DayRef
import com.vishwesh.slottracker.data.EditableSlot
import com.vishwesh.slottracker.data.SlotDraft
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.components.ColorDot
import com.vishwesh.slottracker.ui.components.ConfirmDialog
import com.vishwesh.slottracker.ui.components.SectionLabel
import com.vishwesh.slottracker.ui.components.TimePickerDialog
import com.vishwesh.slottracker.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Where an edit applies, e.g. "Mondays" or "Tue, 3 Nov only". */
fun DayRef.scopeLabel(): String = when (this) {
    is DayRef.Weekday -> "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}s"
    is DayRef.Date -> "${Formats.shortDate(date)} only"
}

/** Slot editor (spec §6.2): label, start and end time, and the slot's items in order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditorScreen(container: AppContainer, ref: DayRef, slot: EditableSlot?, navigator: Navigator) {
    val schedule = container.schedule
    val day by remember(ref) { schedule.observeDay(ref) }.collectAsStateWithLifecycle(null)
    val allItems by remember { schedule.observeItems() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var label by remember { mutableStateOf(slot?.label.orEmpty()) }
    var start by remember { mutableStateOf(slot?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(slot?.end ?: LocalTime.of(10, 0)) }
    var itemIds by remember { mutableStateOf(slot?.items?.map { it.id }.orEmpty()) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var picking by remember { mutableStateOf(false) }
    var confirmOverlap by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val itemsById = allItems.associateBy { it.id }
    val draft = SlotDraft(slot?.id, slot?.fromTemplate ?: false, label.trim(), start, end, itemIds)
    val labelError = label.isBlank()
    val itemsError = itemIds.isEmpty()
    val timeError = start == end

    fun save() {
        scope.launch {
            schedule.saveSlot(ref, draft)
            navigator.back()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (slot == null) "New slot" else "Edit slot")
                        Text(ref.scopeLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.Outlined.Close, contentDescription = "Close") } },
                actions = {
                    TextButton(onClick = {
                        showErrors = true
                        if (labelError || itemsError || timeError) return@TextButton
                        val overlapping = day?.let { schedule.overlapsWith(it, draft) }.orEmpty()
                        if (overlapping.isEmpty()) {
                            save()
                        } else {
                            confirmOverlap = overlapping.joinToString { "${it.label} (${Formats.timeRange(it.start, it.end)})" }
                        }
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                placeholder = { Text("e.g. Morning") },
                singleLine = true,
                isError = showErrors && labelError,
                supportingText = if (showErrors && labelError) ({ Text("Give the slot a label") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Start", start, Modifier.weight(1f)) { editingTime = true }
                TimeField("End", end, Modifier.weight(1f)) { editingTime = false }
            }
            when {
                showErrors && timeError -> Hint("Start and end can't be the same time", LocalExtraColors.current.danger)
                end < start -> Hint("Ends the next day, after midnight")
            }

            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Items", Modifier.weight(1f))
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Choose items")
                }
            }
            if (itemIds.isEmpty()) {
                Hint(
                    "Add at least one item",
                    if (showErrors) LocalExtraColors.current.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemIds.forEachIndexed { index, id ->
                val item = itemsById[id] ?: return@forEachIndexed
                SelectedItemRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < itemIds.lastIndex,
                    onMoveUp = { itemIds = itemIds.swap(index, index - 1) },
                    onMoveDown = { itemIds = itemIds.swap(index, index + 1) },
                    onRemove = { itemIds = itemIds - id },
                )
            }

            if (slot != null) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete slot", color = LocalExtraColors.current.danger)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingTime?.let { isStart ->
        TimePickerDialog(
            title = if (isStart) "Start time" else "End time",
            initial = if (isStart) start else end,
            onDismiss = { editingTime = null },
            onConfirm = {
                if (isStart) start = it else end = it
                editingTime = null
            },
        )
    }
    if (picking) {
        ItemPickerDialog(
            items = allItems.filter { !it.archived },
            selected = itemIds,
            onDismiss = { picking = false },
            onDone = {
                itemIds = it
                picking = false
            },
        )
    }
    confirmOverlap?.let { others ->
        ConfirmDialog(
            title = "Slots overlap",
            text = "This slot overlaps $others. Save anyway?",
            confirmLabel = "Save anyway",
            onDismiss = { confirmOverlap = null },
            onConfirm = {
                confirmOverlap = null
                save()
            },
        )
    }
    if (confirmDelete && slot != null) {
        ConfirmDialog(
            title = "Delete ${slot.label}?",
            text = "Removes this slot and its items from ${ref.scopeLabel()}.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    schedule.deleteSlot(ref, slot)
                    navigator.back()
                }
            },
        )
    }
}

private fun List<Long>.swap(i: Int, j: Int): List<Long> = toMutableList().also {
    val tmp = it[i]
    it[i] = it[j]
    it[j] = tmp
}

@Composable
private fun TimeField(label: String, time: LocalTime, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = LocalExtraColors.current.slotTint,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formats.time(time), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun SelectedItemRow(
    item: ItemEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(Color(item.colorArgb), size = 12.dp)
            Spacer(Modifier.width(12.dp))
            Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = "Remove") }
        }
    }
}

/** Multi-select item picker. Keeps the current order; newly picked items go at the end. */
@Composable
private fun ItemPickerDialog(items: List<ItemEntity>, selected: List<Long>, onDismiss: () -> Unit, onDone: (List<Long>) -> Unit) {
    var picked by remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Items in this slot") },
        text = {
            if (items.isEmpty()) {
                Text("No items yet. Add some in Schedule → Items.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.id }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (item.id in picked) picked - item.id else picked + item.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = item.id in picked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            ColorDot(Color(item.colorArgb), size = 12.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kept = selected.filter { it in picked }
                onDone(kept + items.map { it.id }.filter { it in picked && it !in kept })
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
