package com.vishwesh.slottracker.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object EffectiveDay {
    /**
     * The app's "today": the calendar date, except that before [rollover] it is still the
     * previous calendar date (spec §7).
     */
    fun of(now: LocalDateTime, rollover: LocalTime): LocalDate =
        if (now.toLocalTime() < rollover) now.toLocalDate().minusDays(1) else now.toLocalDate()
}
