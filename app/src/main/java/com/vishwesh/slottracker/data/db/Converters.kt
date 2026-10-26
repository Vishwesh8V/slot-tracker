package com.vishwesh.slottracker.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Dates as ISO yyyy-MM-dd (sortable), times as minutes after midnight, instants as epoch millis. */
class Converters {
    @TypeConverter fun dateToString(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun timeToMinutes(time: LocalTime?): Int? = time?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minutesToTime(value: Int?): LocalTime? = value?.let { LocalTime.of(it / 60, it % 60) }

    @TypeConverter fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
