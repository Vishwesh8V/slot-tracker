package com.vishwesh.slottracker.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.ui.Destination
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.components.ColorDot
import kotlinx.coroutines.launch

/** Items (spec §6.2): active items in order, archived items in a collapsed section. */
@Composable
fun ItemsTab(container: AppContainer, navigator: Navigator) {
    val schedule = container.schedule
    val items by remember { schedule.observeItems() }.collectAsStateWithLifecycle(emptyList())
    val active = items.filter { !it.archived }
    val archived = items.filter { it.archived }
    var showArchived by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun move(from: Int, to: Int) {
        val ids = active.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        scope.launch { schedule.reorderItems(ids) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
        if (active.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No items yet. Tap New item to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        itemsIndexed(active, key = { _, it -> it.id }) { index, item ->
            ItemRow(item, onClick = { navigator.open(Destination.ItemEditor(item.id)) }) {
                IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { move(index, index + 1) }, enabled = index < active.lastIndex) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
        }
        if (archived.isNotEmpty()) {
            item(key = "archived-header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showArchived = !showArchived }
                        .padding(start = 4.dp, top = 16.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Archived (${archived.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showArchived) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showArchived) {
                items(archived, key = { it.id }) { item ->
                    ItemRow(item, onClick = { navigator.open(Destination.ItemEditor(item.id)) }, faded = true) {
                        TextButton(onClick = { scope.launch { schedule.restoreItem(item.id) } }) { Text("Restore") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemEntity, onClick: () -> Unit, faded: Boolean = false, actions: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(Color(item.colorArgb).copy(alpha = if (faded) 0.5f else 1f), size = 24.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (faded) 0.6f else 1f),
                )
                item.details?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}
