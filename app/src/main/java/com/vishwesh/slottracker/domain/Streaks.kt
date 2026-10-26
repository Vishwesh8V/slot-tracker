package com.vishwesh.slottracker.domain

import java.time.LocalDate
import java.time.LocalTime

/** One scheduled occurrence of an item, from history or from today's live tasks. */
data class Occurrence(
    val date: LocalDate,
    val itemId: Long,
    val slotStart: LocalTime,
    val done: Boolean,
)

data class StreakResult(val current: Int, val best: Int)

/**
 * Streak rules from spec §7. [history] holds rolled-over occurrences; [today] holds the live
 * day's occurrences. Today's done tasks extend streaks; today's not-yet-done tasks are ignored
 * until rollover turns them into history.
 */
object Streaks {
    private val chronological = compareBy<Occurrence>({ it.date }, { it.slotStart })

    /** Consecutive Done scheduled occurrences of one item; unscheduled days don't break it. */
    fun forItem(itemId: Long, history: List<Occurrence>, today: List<Occurrence>): StreakResult {
        val sequence = history.filter { it.itemId == itemId }.sortedWith(chronological) +
            today.filter { it.itemId == itemId && it.done }.sortedWith(chronological)
        return runs(sequence.map { it.done })
    }

    /**
     * Consecutive effective days on which every scheduled task was Done. Days with nothing
     * scheduled have no occurrences, so they neither break nor extend the streak.
     */
    fun overall(history: List<Occurrence>, today: List<Occurrence>): StreakResult {
        val days = history.groupBy { it.date }.toSortedMap().values.map { day -> day.all { it.done } }
        val todayCounts = today.isNotEmpty() && today.all { it.done }
        return runs(if (todayCounts) days + true else days)
    }

    private fun runs(outcomes: List<Boolean>): StreakResult {
        var best = 0
        var run = 0
        for (done in outcomes) {
            run = if (done) run + 1 else 0
            if (run > best) best = run
        }
        return StreakResult(current = run, best = best)
    }
}
