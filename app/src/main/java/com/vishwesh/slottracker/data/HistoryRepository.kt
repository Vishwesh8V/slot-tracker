package com.vishwesh.slottracker.data

import com.vishwesh.slottracker.data.db.AppDatabase
import com.vishwesh.slottracker.data.db.HistoryEntryEntity
import com.vishwesh.slottracker.data.db.ItemEntity
import com.vishwesh.slottracker.domain.Occurrence
import com.vishwesh.slottracker.domain.SlotTiming
import com.vishwesh.slottracker.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalTime

/** Everything the History screen needs, kept live. */
data class HistorySnapshot(
    val entries: List<HistoryEntryEntity>,
    val liveDay: LiveDay,
    val items: List<ItemEntity>,
) {
    val occurrences: List<Occurrence> by lazy {
        entries.map { Occurrence(it.date, it.itemId, occurrenceOrder(liveDay.rollover, it.slotStart), it.status == TaskStatus.DONE) }
    }

    val todayOccurrences: List<Occurrence> by lazy {
        liveDay.tasks.map { Occurrence(liveDay.date, it.item.id, occurrenceOrder(liveDay.rollover, it.slot.start), it.done) }
    }
}

/**
 * Orders occurrences within a day along the effective day (after-midnight slots last), for
 * streak sequencing. Returned as a time of day so it plugs into [Occurrence.slotStart].
 */
fun occurrenceOrder(rollover: LocalTime, start: LocalTime): LocalTime =
    LocalTime.MIN.plusMinutes(SlotTiming.offsetMinutes(rollover, start).toLong())

/** History (spec §6.3): read every outcome, and correct past entries. */
class HistoryRepository(db: AppDatabase, tracker: TrackerRepository) {
    private val historyDao = db.historyDao()
    private val snapshots: Flow<HistorySnapshot> =
        combine(historyDao.observeAll(), tracker.observeLiveDay(), db.itemDao().observeAll(), ::HistorySnapshot)

    fun observe(): Flow<HistorySnapshot> = snapshots

    /**
     * Flips Done/Missed and edits the note of a past entry. A task marked Done after the fact
     * has no known completion time, so completedAt stays empty.
     */
    suspend fun updateEntry(id: Long, status: TaskStatus, note: String?) {
        val entry = historyDao.get(id) ?: return
        historyDao.update(
            entry.copy(
                status = status,
                completedAt = if (status == TaskStatus.DONE) entry.completedAt else null,
                note = note?.trim()?.ifEmpty { null },
            ),
        )
    }
}
