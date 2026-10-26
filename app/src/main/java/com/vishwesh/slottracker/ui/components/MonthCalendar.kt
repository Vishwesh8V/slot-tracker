package com.vishwesh.slottracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/** A calendar day's fill colour and the text colour on it. */
data class DayColors(val fill: Color, val content: Color)

/**
 * A month grid that always starts on Monday (spec §4). [marked] dates get a small dot;
 * dates outside [minDate]..[maxDate] can't be picked; [dayColors] fills days (History).
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    marked: Set<LocalDate> = emptySet(),
    today: LocalDate? = null,
    dayColors: (LocalDate) -> DayColors? = { null },
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0])),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            val canGoBack = minDate == null || month > YearMonth.from(minDate)
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }, enabled = canGoBack) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date == selected,
                                enabled = (minDate == null || date >= minDate) && (maxDate == null || date <= maxDate),
                                marked = date in marked,
                                isToday = date == today,
                                colors = dayColors(date),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    enabled: Boolean,
    marked: Boolean,
    isToday: Boolean,
    colors: DayColors?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Filled (History) days show selection as a ring so their status colour stays visible.
    val fill = when {
        colors != null -> colors.fill
        selected -> scheme.primary
        else -> Color.Transparent
    }
    Column(
        Modifier
            .size(40.dp)
            .then(if (selected && colors != null) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier)
            .padding(if (selected && colors != null) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                colors != null -> colors.content
                selected -> scheme.onPrimary
                !enabled -> scheme.onSurface.copy(alpha = 0.3f)
                isToday -> scheme.primary
                else -> scheme.onSurface
            },
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (marked) (if (selected) scheme.onPrimary else scheme.primary) else Color.Transparent),
        )
    }
}

/** A dialog for picking a date from today on; [marked] shows which dates have overrides. */
@Composable
fun DatePickDialog(
    title: String,
    minDate: LocalDate,
    marked: Set<LocalDate>,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(minDate)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MonthCalendar(
                    month = month,
                    selected = selected,
                    onSelect = { selected = it },
                    onMonthChange = { month = it },
                    minDate = minDate,
                    marked = marked,
                    today = minDate,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text(
                        "  Already edited",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onPick) }, enabled = selected != null) { Text("Edit date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
