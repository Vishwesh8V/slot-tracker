package com.vishwesh.slottracker.domain

import java.time.LocalDate

/** Done ÷ scheduled over some period; null [percent] when nothing was scheduled. */
data class Rate(val done: Int, val scheduled: Int) {
    val percent: Int? get() = if (scheduled == 0) null else Math.round(done * 100f / scheduled)
}

data class ItemStats(
    val streak: StreakResult,
    val totalDone: Int,
    val last7: Rate,
    val last30: Rate,
    val allTime: Rate,
)

data class OverallStats(
    val streak: StreakResult,
    val allTime: Rate,
)

/** How a calendar day looks in History (spec §6.3). */
enum class DayStatus { ALL_DONE, PARTIAL, NONE_DONE, NOTHING_SCHEDULED }

/**
 * Stats from spec §6.3. [history] is every rolled-over occurrence; [today] the live day's.
 * Streaks and total sessions include today's ticked tasks (like the streak on Today);
 * completion rates cover closed days only, the [window] days before [liveDate].
 */
object Stats {
    fun forItem(itemId: Long, history: List<Occurrence>, today: List<Occurrence>, liveDate: LocalDate): ItemStats {
        val mine = history.filter { it.itemId == itemId }
        return ItemStats(
            streak = Streaks.forItem(itemId, history, today),
            totalDone = mine.count { it.done } + today.count { it.itemId == itemId && it.done },
            last7 = rate(mine, liveDate, 7),
            last30 = rate(mine, liveDate, 30),
            allTime = rate(mine, liveDate, null),
        )
    }

    fun overall(history: List<Occurrence>, today: List<Occurrence>, liveDate: LocalDate): OverallStats =
        OverallStats(Streaks.overall(history, today), rate(history, liveDate, null))

    fun dayStatus(occurrences: List<Occurrence>): DayStatus = when {
        occurrences.isEmpty() -> DayStatus.NOTHING_SCHEDULED
        occurrences.all { it.done } -> DayStatus.ALL_DONE
        occurrences.any { it.done } -> DayStatus.PARTIAL
        else -> DayStatus.NONE_DONE
    }

    private fun rate(occurrences: List<Occurrence>, liveDate: LocalDate, window: Long?): Rate {
        val from = window?.let { liveDate.minusDays(it) }
        val inWindow = occurrences.filter { it.date < liveDate && (from == null || it.date >= from) }
        return Rate(inWindow.count { it.done }, inWindow.size)
    }
}
