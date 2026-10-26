package com.vishwesh.slottracker.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.AppContainer
import com.vishwesh.slottracker.data.BackupData
import com.vishwesh.slottracker.data.InvalidBackupException
import com.vishwesh.slottracker.reminders.Reminders
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.components.ConfirmDialog
import com.vishwesh.slottracker.ui.components.SectionLabel
import com.vishwesh.slottracker.ui.components.TimePickerDialog
import com.vishwesh.slottracker.ui.components.WarningRow
import com.vishwesh.slottracker.work.DayTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Settings (spec §8): exactly rollover time, slot reminders, export and import. */
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rollover by settings.rolloverTime.collectAsStateWithLifecycle(LocalTime.of(6, 0))
    val reminders by settings.slotReminders.collectAsStateWithLifecycle(true)
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var editingRollover by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }

    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val json = container.backup.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }
            snackbar.showSnackbar(if (result.isSuccess) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                pendingImport = container.backup.parse(json)
            } catch (e: InvalidBackupException) {
                snackbar.showSnackbar(e.message ?: "Invalid backup")
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't read that file")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 12.dp),
            )

            SectionLabel("Day")
            Group {
                SettingRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Day rollover time",
                    subtitle = "When the widget and app switch to the next day and the previous day is logged to history",
                    onClick = { editingRollover = true },
                ) {
                    Text(Formats.time(rollover), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionLabel("Reminders", Modifier.padding(top = 12.dp))
            Group {
                SettingRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Slot reminders",
                    subtitle = "A notification at each slot's start time listing that slot's items",
                    onClick = {
                        val enable = !reminders
                        scope.launch { settings.setSlotReminders(enable) }
                        if (enable && !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                ) {
                    Switch(checked = reminders, onCheckedChange = null)
                }
                if (reminders && !notificationsAllowed) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        WarningRow("Notifications are blocked", Modifier.weight(1f))
                        TextButton(onClick = { context.startActivity(notificationSettings(context)) }) { Text("Allow") }
                    }
                }
            }

            SectionLabel("Backup", Modifier.padding(top = 12.dp))
            Group {
                SettingRow(
                    icon = Icons.Outlined.Upload,
                    title = "Export",
                    subtitle = "Save all data to a JSON file",
                    onClick = { exporter.launch("slot-tracker-backup-${LocalDate.now()}.json") },
                )
                SettingRow(
                    icon = Icons.Outlined.Download,
                    title = "Import",
                    subtitle = "Restore from a JSON export (replaces current data)",
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }

            Text(
                "Slot Tracker ${versionName(context)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editingRollover) {
        TimePickerDialog(
            title = "Day rollover time",
            initial = rollover,
            onDismiss = { editingRollover = false },
            onConfirm = { time ->
                editingRollover = false
                scope.launch {
                    settings.setRolloverTime(time)
                    DayTick.runNow(context)
                }
            },
        )
    }
    pendingImport?.let { data ->
        ConfirmDialog(
            title = "Restore this backup?",
            text = "It has ${data.items.size} items and ${data.history.size} history entries. " +
                "All current data and settings will be replaced.",
            confirmLabel = "Replace and restore",
            destructive = true,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    container.backup.restore(data)
                    DayTick.runNow(context) // refreshes widget, day and alarms
                    snackbar.showSnackbar("Backup restored")
                }
            },
        )
    }
}

private fun notificationsAllowed(context: Context): Boolean = Reminders.canPostNotifications(context)

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun versionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
