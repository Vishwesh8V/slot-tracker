package com.vishwesh.slottracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vishwesh.slottracker.MainActivity
import com.vishwesh.slottracker.appContainer
import com.vishwesh.slottracker.data.LiveDay
import com.vishwesh.slottracker.domain.ScheduledSlot
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.domain.Task
import com.vishwesh.slottracker.ui.Formats
import com.vishwesh.slottracker.ui.theme.Palette
import java.time.LocalDateTime

/** The home-screen widget (spec §5). */
class SlotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.appContainer.repository
        // Catch-up on widget update (spec §7).
        repository.catchUp()
        val initial = repository.loadLiveDay()
        provideContent {
            val flow = remember { repository.observeLiveDay() }
            val day by flow.collectAsState(initial)
            WidgetContent(context, day, LocalDateTime.now())
        }
    }

    // Launchers report different heights per row (a 4×2 is ~215dp on Pixel, taller on One UI),
    // so "large" starts between typical 2-row and 3-row heights.
    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(230.dp, 110.dp)
        val LARGE = DpSize(230.dp, 280.dp)
    }
}

private enum class Layout { SMALL, MEDIUM, LARGE }

private fun tone(tone: Palette.Tone): ColorProvider = ColorProvider(day = tone.light, night = tone.dark)
private fun fixed(color: Color): ColorProvider = ColorProvider(day = color, night = color)

@Composable
private fun WidgetContent(context: Context, day: LiveDay, now: LocalDateTime) {
    val size = LocalSize.current
    val layout = when {
        size.width < SlotWidget.MEDIUM.width -> Layout.SMALL
        size.height < SlotWidget.LARGE.height -> Layout.MEDIUM
        else -> Layout.LARGE
    }
    val highlighted = SlotTiming.highlighted(day.schedule, now, day.rollover)
    val compact = layout == Layout.SMALL

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(tone(Palette.surface))
            .cornerRadius(24.dp)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 12.dp),
    ) {
        Header(context, day, compact)
        Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
        if (day.tasks.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing scheduled today", style = TextStyle(color = tone(Palette.secondaryText), fontSize = 13.sp))
            }
        } else {
            val tasksBySlot = day.tasks.groupBy { it.slot.slotKey }
            LazyColumn(GlanceModifier.fillMaxSize()) {
                day.schedule.slots.forEach { slot ->
                    item(itemId = slot.slotKey.hashCode().toLong()) {
                        val tasks = tasksBySlot[slot.slotKey].orEmpty()
                        val isHighlighted = slot.slotKey == highlighted
                        if (compact) {
                            CompactSlot(context, day, slot, tasks, isHighlighted)
                        } else {
                            SlotGroup(context, day, slot, tasks, isHighlighted, showDetails = layout == Layout.LARGE)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(context: Context, day: LiveDay, compact: Boolean) {
    val done = day.tasks.count { it.done }
    val allDone = day.tasks.isNotEmpty() && done == day.tasks.size
    Row(
        GlanceModifier.fillMaxWidth().clickable(openToday(context)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Formats.shortDate(day.date),
            style = TextStyle(color = tone(Palette.onSurface), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (day.tasks.isNotEmpty()) {
            Text(
                if (compact) "$done/${day.tasks.size}" else "$done/${day.tasks.size} done",
                style = TextStyle(
                    color = tone(if (allDone) Palette.accent else Palette.secondaryText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun SlotGroup(
    context: Context,
    day: LiveDay,
    slot: ScheduledSlot,
    tasks: List<Task>,
    highlighted: Boolean,
    showDetails: Boolean,
) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .background(tone(if (highlighted) Palette.highlightTint else Palette.slotTint))
                .cornerRadius(16.dp)
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            Text(
                "${slot.label} · ${Formats.timeRange(slot.start, slot.end)}",
                style = TextStyle(
                    color = tone(if (highlighted) Palette.accent else Palette.secondaryText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            tasks.forEach { TaskRow(context, day, it, showDetails, compact = false) }
        }
    }
}

@Composable
private fun CompactSlot(context: Context, day: LiveDay, slot: ScheduledSlot, tasks: List<Task>, highlighted: Boolean) {
    Column(GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
        // Slot header reduced to a thin coloured divider with the time (spec §5, small size).
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formats.time(slot.start),
                style = TextStyle(
                    color = tone(if (highlighted) Palette.accent else Palette.secondaryText),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier
                    .defaultWeight()
                    .height(if (highlighted) 2.dp else 1.dp)
                    .background(tone(if (highlighted) Palette.accent else Palette.divider)),
            ) {}
        }
        tasks.forEach { TaskRow(context, day, it, showDetails = false, compact = true) }
    }
}

@Composable
private fun TaskRow(context: Context, day: LiveDay, task: Task, showDetails: Boolean, compact: Boolean) {
    val details = task.item.details?.takeIf { showDetails }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier
                .width(if (compact) 3.dp else 4.dp)
                .height(if (details != null) 30.dp else if (compact) 14.dp else 18.dp)
                .background(fixed(Color(task.item.colorArgb)))
                .cornerRadius(2.dp),
        ) {}
        CheckBox(
            checked = task.done,
            onCheckedChange = actionRunCallback<ToggleTaskAction>(
                actionParametersOf(
                    ToggleTaskAction.DATE to day.date.toString(),
                    ToggleTaskAction.SLOT_KEY to task.key.slotKey,
                    ToggleTaskAction.ITEM_ID to task.key.itemId,
                ),
            ),
            colors = CheckboxDefaults.colors(checkedColor = tone(Palette.accent), uncheckedColor = tone(Palette.secondaryText)),
        )
        Column(GlanceModifier.defaultWeight().clickable(openTask(context, task))) {
            Text(
                task.item.name,
                style = TextStyle(
                    color = tone(if (task.done) Palette.secondaryText else Palette.onSurface),
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            if (details != null) {
                Text(details, style = TextStyle(color = tone(Palette.secondaryText), fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

private fun openToday(context: Context): Action =
    actionStartActivity(MainActivity.todayIntent(context).setData("slottracker://today".toUri()))

// Unique data URIs keep each row's PendingIntent distinct.
private fun openTask(context: Context, task: Task): Action =
    actionStartActivity(
        MainActivity.taskIntent(context, task.key)
            .setData("slottracker://task/${task.key.slotKey}/${task.key.itemId}".toUri()),
    )
