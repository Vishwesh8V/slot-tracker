package com.vishwesh.slottracker.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Where slots fall in real time. An effective day runs from its rollover time to the next one,
 * so a slot that starts before the rollover time happens after midnight, and a slot whose end
 * is not after its start crosses midnight (it belongs to the day it starts on, spec §9).
 */
object SlotTiming {
    private const val DAY_MINUTES = 24 * 60

    /** Minutes from the start of the effective day to [time]. */
    fun offsetMinutes(rollover: LocalTime, time: LocalTime): Int =
        Math.floorMod(time.toMinutes() - rollover.toMinutes(), DAY_MINUTES)

    /** Length of a slot in minutes; an end at or before the start means it crosses midnight. */
    fun durationMinutes(start: LocalTime, end: LocalTime): Int =
        Math.floorMod(end.toMinutes() - start.toMinutes(), DAY_MINUTES)

    /** The real start and end of a slot on effective [date]. */
    fun window(date: LocalDate, start: LocalTime, end: LocalTime, rollover: LocalTime): ClosedRange<LocalDateTime> {
        val begins = date.atTime(rollover).plusMinutes(offsetMinutes(rollover, start).toLong())
        return begins..begins.plusMinutes(durationMinutes(start, end).toLong())
    }

    /** The slot in progress at [now]; failing that, the next one to start; else null. */
    fun highlighted(schedule: DaySchedule, now: LocalDateTime, rollover: LocalTime): String? {
        val windows = schedule.slots.map { it.slotKey to window(schedule.date, it.start, it.end, rollover) }
        windows.firstOrNull { (_, w) -> now >= w.start && now < w.endInclusive }?.let { return it.first }
        return windows.filter { (_, w) -> w.start > now }.minByOrNull { (_, w) -> w.start }?.first
    }

    /** The next time after [now] that a slot starts or ends, when the highlight can change. */
    fun nextBoundary(schedule: DaySchedule, now: LocalDateTime, rollover: LocalTime): LocalDateTime? =
        schedule.slots
            .flatMap { window(schedule.date, it.start, it.end, rollover).let { w -> listOf(w.start, w.endInclusive) } }
            .filter { it > now }
            .minOrNull()

    /** Whether two slots on the same day overlap in time. */
    fun overlaps(
        aStart: LocalTime, aEnd: LocalTime,
        bStart: LocalTime, bEnd: LocalTime,
        rollover: LocalTime,
    ): Boolean {
        val a0 = offsetMinutes(rollover, aStart)
        val a1 = a0 + durationMinutes(aStart, aEnd)
        val b0 = offsetMinutes(rollover, bStart)
        val b1 = b0 + durationMinutes(bStart, bEnd)
        return a0 < b1 && b0 < a1
    }

    private fun LocalTime.toMinutes() = hour * 60 + minute
}
