package com.vishwesh.slottracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.updateAll
import com.vishwesh.slottracker.appContainer
import com.vishwesh.slottracker.domain.TaskKey
import java.time.LocalDate

/** Ticks or unticks a task from the widget's checkbox. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val checked = parameters[ToggleableStateKey] ?: return
        val date = LocalDate.parse(parameters[DATE] ?: return)
        val key = TaskKey(parameters[SLOT_KEY] ?: return, parameters[ITEM_ID] ?: return)
        context.appContainer.repository.setDone(date, key, checked)
        // Re-render every instance; also corrects the box if the tap was rejected after rollover.
        SlotWidget().updateAll(context)
    }

    companion object {
        val DATE = ActionParameters.Key<String>("date")
        val SLOT_KEY = ActionParameters.Key<String>("slotKey")
        val ITEM_ID = ActionParameters.Key<Long>("itemId")
    }
}
