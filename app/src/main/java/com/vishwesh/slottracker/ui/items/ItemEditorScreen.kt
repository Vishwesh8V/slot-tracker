package com.vishwesh.slottracker.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.ItemUsage
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.data.db.SlotUsage
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.components.SectionLabel
import com.vishwesh.slottracker.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Item editor (spec §6.2): name, colour, details; archive, restore and delete. */
@Composable
fun ItemEditorScreen(container: AppContainer, itemId: Long?, navigator: Navigator) {
    val loaded by produceState<Pair<Boolean, ItemEntity?>>(false to null, itemId) {
        value = true to itemId?.let { container.schedule.item(it) }
    }
    val (ready, existing) = loaded
    if (ready) ItemForm(container, existing, navigator)
}

private enum class Pending { ARCHIVE, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemForm(container: AppContainer, existing: ItemEntity?, navigator: Navigator) {
    val schedule = container.schedule
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorArgb ?: PresetColors.first()) }
    var details by remember { mutableStateOf(existing?.details.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var usage by remember { mutableStateOf<ItemUsage?>(null) }

    fun ask(action: Pending) {
        val id = existing?.id ?: return
        scope.launch {
            usage = schedule.itemUsage(id)
            pending = action
        }
    }

    fun finish(block: suspend () -> Unit) {
        pending = null
        scope.launch {
            block()
            navigator.back()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New item" else "Edit item") },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.Outlined.Close, contentDescription = "Close") } },
                actions = {
                    TextButton(onClick = {
                        showErrors = true
                        if (name.isBlank()) return@TextButton
                        finish { schedule.saveItem(existing?.id, name, color, details) }
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
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Diffusion Models") },
                singleLine = true,
                isError = showErrors && name.isBlank(),
                supportingText = if (showErrors && name.isBlank()) ({ Text("Give the item a name") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel("Colour", Modifier.padding(top = 12.dp))
            ColorChooser(selected = color, onSelect = { color = it })
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Details (optional)") },
                placeholder = { Text("e.g. Italian — Duolingo unit 4") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (existing != null) {
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (existing.archived) {
                        OutlinedButton(onClick = { finish { schedule.restoreItem(existing.id) } }, modifier = Modifier.weight(1f)) {
                            Text("Restore")
                        }
                    } else {
                        OutlinedButton(onClick = { ask(Pending.ARCHIVE) }, modifier = Modifier.weight(1f)) { Text("Archive") }
                    }
                    OutlinedButton(onClick = { ask(Pending.DELETE) }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = LocalExtraColors.current.danger)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val item = existing ?: return
    val currentUsage = usage ?: return
    when (pending) {
        Pending.ARCHIVE -> UsageDialog(
            title = "Archive ${item.name}?",
            intro = "It's hidden from pickers; its history and stats are kept.",
            usage = currentUsage,
            onDismiss = { pending = null },
            confirm = "Archive" to { finish { schedule.archiveItem(item.id) } },
        )
        Pending.DELETE -> if (currentUsage.historyCount == 0) {
            UsageDialog(
                title = "Delete ${item.name}?",
                intro = "It has no history, so it's removed completely.",
                usage = currentUsage,
                onDismiss = { pending = null },
                confirm = "Delete" to { finish { schedule.deleteItemPermanently(item.id) } },
                destructive = true,
            )
        } else {
            // Deleting an item with history archives it instead; permanent delete is separate (spec §3).
            UsageDialog(
                title = "${item.name} has history",
                intro = "It has ${currentUsage.historyCount} history ${if (currentUsage.historyCount == 1) "entry" else "entries"}. " +
                    "Archive it to keep them, or delete it permanently including its history. This can't be undone.",
                usage = currentUsage,
                onDismiss = { pending = null },
                confirm = "Delete permanently" to { finish { schedule.deleteItemPermanently(item.id) } },
                destructive = true,
                alternative = if (item.archived) null else "Archive instead" to { finish { schedule.archiveItem(item.id) } },
            )
        }
        null -> Unit
    }
}

/** A confirmation that lists where the item is used and which slots would be left empty. */
@Composable
private fun UsageDialog(
    title: String,
    intro: String,
    usage: ItemUsage,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    destructive: Boolean = false,
    alternative: Pair<String, () -> Unit>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(intro)
                if (usage.places.isNotEmpty()) {
                    Text("It will be removed from:", Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
                    usage.places.forEach { Text("•  ${it.describe()}", style = MaterialTheme.typography.bodyMedium) }
                }
                if (usage.emptiedSlots.isNotEmpty()) {
                    Text(
                        "These slots would be left empty, so they'll be removed:",
                        Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    usage.emptiedSlots.forEach { Text("•  ${it.describe()}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm.second) {
                Text(confirm.first, color = if (destructive) LocalExtraColors.current.danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row {
                alternative?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun SlotUsage.describe(): String {
    val where = dayOfWeek?.let { "${DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault())}s" }
        ?: date?.let { Formats.shortDate(it) }
        ?: ""
    return "$where · $label ${Formats.timeRange(startTime, endTime)}"
}
