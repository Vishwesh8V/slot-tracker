package com.vishwesh.slottracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishwesh.slottracker.data.LiveDay

/**
 * Phase 1 placeholder: a read-only dump of the live day so the seed data can be checked on a
 * device. Replaced by the real Today/Schedule/History/Settings screens in phase 3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = appContainer.repository
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val flow = remember { repository.observeLiveDay() }
                    val liveDay by flow.collectAsStateWithLifecycle(initialValue = null)
                    liveDay?.let { LiveDayDump(it) }
                }
            }
        }
    }
}

@Composable
private fun LiveDayDump(day: LiveDay) {
    LazyColumn(Modifier.safeDrawingPadding().padding(16.dp)) {
        item { Text("${day.date} (${day.date.dayOfWeek})", style = MaterialTheme.typography.titleLarge) }
        items(day.schedule.slots) { slot ->
            Text(
                "${slot.label} · ${slot.start}–${slot.end}: ${slot.items.joinToString { it.name }}",
                Modifier.padding(top = 8.dp),
            )
        }
    }
}
