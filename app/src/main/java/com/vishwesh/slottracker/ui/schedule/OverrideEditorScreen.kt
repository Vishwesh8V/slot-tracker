package com.vishwesh.slottracker.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.DayRef
import com.vishwesh.slottracker.ui.Destination
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.Navigator
import com.vishwesh.slottracker.ui.components.ConfirmDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Edits one date only (spec §6.2, overrides). Starts as that date's template; the first change
 * turns it into an override, which can be reset back to the template.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideEditorScreen(container: AppContainer, date: LocalDate, navigator: Navigator) {
    val schedule = container.schedule
    val ref = DayRef.Date(date)
    val day by remember(date) { schedule.observeDay(ref) }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Formats.shortDate(date))
                        Text("This date only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (day?.isOverride == true) TextButton(onClick = { confirmReset = true }) { Text("Reset") }
                },
            )
        },
    ) { padding ->
        val current = day ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (current.isOverride) {
                            "Edited for this date. Reset to follow the $weekday template again."
                        } else {
                            "Showing the $weekday template. Changes here apply to this date only."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            DaySlotList(
                day = current,
                scope = "${Formats.shortDate(date)} only",
                onEditSlot = { navigator.open(Destination.SlotEditor(ref, it)) },
                onDeleteSlot = { slot -> scope.launch { schedule.deleteSlot(ref, slot) } },
            )
            TextButton(onClick = { navigator.open(Destination.SlotEditor(ref, null)) }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add slot")
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset ${Formats.shortDate(date)}?",
            text = "Your changes for this date are removed and it follows the $weekday template again.",
            confirmLabel = "Reset",
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                scope.launch { schedule.resetOverride(date) }
            },
        )
    }
}
