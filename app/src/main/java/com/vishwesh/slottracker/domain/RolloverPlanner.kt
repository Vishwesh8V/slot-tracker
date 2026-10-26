package com.vishwesh.slottracker.domain

import java.time.LocalDate

/** What a rollover should write: history for every closed day, and the new live day. */
data class RolloverPlan(
    val history: List<HistoryRecord>,
    val newLiveDate: LocalDate,
)

object RolloverPlanner {
    /**
     * Plans the rollover from [liveDate] (the day currently shown, null on first run) to
     * [today] (the current effective day).
     *
     * Returns null when there is nothing to do: same day, or the clock moved backwards
     * (the live day is kept until time catches up; see docs/DECISIONS.md).
     *
     * Every day from [liveDate] up to (not including) [today] is closed in order. The live day
     * uses [liveStates]; days in between were never opened, so all their tasks are MISSED.
     */
    suspend fun plan(
        liveDate: LocalDate?,
        today: LocalDate,
        liveStates: Map<TaskKey, LiveState>,
        scheduleFor: suspend (LocalDate) -> DaySchedule,
    ): RolloverPlan? {
        if (liveDate == null) return RolloverPlan(emptyList(), today)
        if (!today.isAfter(liveDate)) return null

        val history = mutableListOf<HistoryRecord>()
        var date: LocalDate = liveDate
        while (date.isBefore(today)) {
            val states = if (date == liveDate) liveStates else emptyMap()
            history += historyFor(scheduleFor(date), states)
            date = date.plusDays(1)
        }
        return RolloverPlan(history, today)
    }

    fun historyFor(schedule: DaySchedule, states: Map<TaskKey, LiveState>): List<HistoryRecord> =
        DayBuilder.tasks(schedule, states).map { task ->
            HistoryRecord(
                date = schedule.date,
                itemId = task.item.id,
                itemName = task.item.name,
                itemColorArgb = task.item.colorArgb,
                slotLabel = task.slot.label,
                slotStart = task.slot.start,
                slotEnd = task.slot.end,
                status = if (task.done) TaskStatus.DONE else TaskStatus.MISSED,
                completedAt = task.completedAt,
                note = task.note,
            )
        }
}
