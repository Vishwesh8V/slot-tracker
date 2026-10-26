package com.vishwesh.slottracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vishwesh.slottracker.data.db.AppDatabase
import com.vishwesh.slottracker.domain.TaskKey
import com.vishwesh.slottracker.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Repository behaviour against a real (in-memory) Room database, with a controllable clock. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private var now: LocalDateTime = monday.atTime(9, 0)
    private lateinit var db: AppDatabase
    private lateinit var tracker: TrackerRepository
    private lateinit var schedule: ScheduleRepository

    @Before
    fun setUp(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context)
        settings.setRolloverTime(LocalTime.of(6, 0))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tracker = TrackerRepository(db, settings, now = { now })
        schedule = ScheduleRepository(db, tracker, settings)
        tracker.catchUp()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tasks() = tracker.loadLiveDay().tasks
    private suspend fun task(name: String) = tasks().first { it.item.name == name }
    private suspend fun itemId(name: String) = db.itemDao().all().first { it.name == name }.id

    @Test
    fun firstLaunchShowsSeededMonday() = runBlocking {
        val day = tracker.loadLiveDay()
        assertEquals(monday, day.date)
        assertEquals(listOf("Morning", "Evening"), day.schedule.slots.map { it.label })
        assertEquals(listOf("Diffusion Models", "Leetcode / DSA"), day.tasks.map { it.item.name })
    }

    @Test
    fun seedingHappensOnlyOnce() = runBlocking {
        tracker.catchUp()
        assertEquals(6, db.itemDao().all().size)
    }

    @Test
    fun tickPersistsAndUntickClears() = runBlocking {
        val key = task("Diffusion Models").key
        assertTrue(tracker.setDone(monday, key, true))
        assertTrue(task("Diffusion Models").done)
        // A fresh repository on the same database (like an app restart) sees the tick.
        val restarted = TrackerRepository(db, SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext), now = { now })
        assertTrue(restarted.loadLiveDay().tasks.first { it.key == key }.done)
        tracker.setDone(monday, key, false)
        assertFalse(task("Diffusion Models").done)
    }

    @Test
    fun rolloverWritesHistoryAndShowsTheNewDay() = runBlocking {
        tracker.setDone(monday, task("Diffusion Models").key, true)
        tracker.setNote(monday, task("Leetcode / DSA").key, "too tired")

        now = monday.plusDays(1).atTime(5, 59)
        assertEquals(monday, tracker.rolloverIfNeeded()) // still Monday before 06:00

        now = monday.plusDays(1).atTime(6, 0)
        assertEquals(monday.plusDays(1), tracker.rolloverIfNeeded())
        val history = db.historyDao().all()
        assertEquals(listOf(TaskStatus.DONE, TaskStatus.MISSED), history.map { it.status })
        assertEquals("too tired", history[1].note)
        assertEquals(listOf("Game Development", "Roman Art", "Language Study"), tasks().map { it.item.name })
        assertTrue(tasks().none { it.done })
    }

    @Test
    fun tickForAnEndedDayIsRejected() = runBlocking {
        val key = task("Diffusion Models").key
        now = monday.plusDays(1).atTime(7, 0)
        assertFalse(tracker.setDone(monday, key, true))
    }

    @Test
    fun overrideForTodayKeepsTicksAndOnlyChangesThatDate() = runBlocking {
        tracker.setDone(monday, task("Diffusion Models").key, true)
        val ref = DayRef.Date(monday)
        val evening = schedule.observeDayOnce(ref).slots.first { it.label == "Evening" }
        schedule.saveSlot(
            ref,
            SlotDraft(evening.id, evening.fromTemplate, "Evening", LocalTime.of(19, 0), LocalTime.of(21, 0), listOf(itemId("Math Puzzle"))),
        )

        val day = tracker.loadLiveDay()
        assertTrue(day.schedule.isOverride)
        assertTrue(task("Diffusion Models").done) // tick kept: the morning slot still exists
        assertEquals(listOf("Diffusion Models", "Math Puzzle"), day.tasks.map { it.item.name })
        // Next Monday still follows the template.
        val nextMonday = tracker.scheduleFor(monday.plusWeeks(1))
        assertFalse(nextMonday.isOverride)
        assertEquals(listOf("Leetcode / DSA"), nextMonday.slots[1].items.map { it.name })

        schedule.resetOverride(monday)
        assertFalse(tracker.loadLiveDay().schedule.isOverride)
        assertTrue(task("Diffusion Models").done) // template slot identity, so the tick is still there
    }

    @Test
    fun removingATaskDropsItsTickForGood() = runBlocking {
        tracker.setDone(monday, task("Leetcode / DSA").key, true)
        val ref = DayRef.Weekday(DayOfWeek.MONDAY)
        val evening = schedule.observeDayOnce(ref).slots.first { it.label == "Evening" }
        val leetcode = itemId("Leetcode / DSA")
        val math = itemId("Math Puzzle")

        schedule.saveSlot(ref, SlotDraft(evening.id, true, "Evening", evening.start, evening.end, listOf(math)))
        schedule.saveSlot(ref, SlotDraft(evening.id, true, "Evening", evening.start, evening.end, listOf(math, leetcode)))

        assertFalse(task("Leetcode / DSA").done)
    }

    @Test
    fun editingASlotsTimeKeepsItsTicks() = runBlocking {
        tracker.setDone(monday, task("Leetcode / DSA").key, true)
        val ref = DayRef.Weekday(DayOfWeek.MONDAY)
        val evening = schedule.observeDayOnce(ref).slots.first { it.label == "Evening" }
        schedule.saveSlot(ref, SlotDraft(evening.id, true, "Late", LocalTime.of(21, 0), LocalTime.of(23, 0), evening.items.map { it.id }))
        assertTrue(task("Leetcode / DSA").done)
    }

    @Test
    fun archivingRemovesItFromTheScheduleAndDropsEmptySlots() = runBlocking {
        val leetcode = itemId("Leetcode / DSA")
        val usage = schedule.itemUsage(leetcode)
        // Mon, Wed, Fri, Sat, Sun evenings; it's the only item on Mon, Sat and Sun.
        assertEquals(5, usage.places.size)
        assertEquals(3, usage.emptiedSlots.size)

        schedule.archiveItem(leetcode)

        assertTrue(db.itemDao().get(leetcode)!!.archived)
        assertEquals(listOf("Morning"), tracker.scheduleFor(monday).slots.map { it.label })
        assertEquals(listOf("Math Puzzle"), tracker.scheduleFor(monday.plusDays(2)).slots[1].items.map { it.name })
    }

    @Test
    fun permanentDeleteRemovesHistoryToo() = runBlocking {
        tracker.setDone(monday, task("Leetcode / DSA").key, true)
        now = monday.plusDays(1).atTime(7, 0)
        tracker.rolloverIfNeeded()
        val leetcode = itemId("Leetcode / DSA")
        assertEquals(1, schedule.itemUsage(leetcode).historyCount)

        schedule.deleteItemPermanently(leetcode)

        assertEquals(0, db.historyDao().countForItem(leetcode))
        assertEquals(1, db.historyDao().all().size) // Monday's Diffusion Models entry remains
    }

    @Test
    fun copyDayReplacesTheTargetDays() = runBlocking {
        schedule.copyDay(DayOfWeek.MONDAY, setOf(DayOfWeek.TUESDAY))
        val tuesday = tracker.scheduleFor(monday.plusDays(1))
        assertEquals(listOf(listOf("Diffusion Models"), listOf("Leetcode / DSA")), tuesday.slots.map { s -> s.items.map { it.name } })
    }

    @Test
    fun liveDayDoesNotMoveBackwardsWhenTheClockDoes() = runBlocking {
        now = monday.plusDays(1).atTime(7, 0)
        tracker.rolloverIfNeeded()
        now = monday.atTime(23, 0)
        assertEquals(monday.plusDays(1), tracker.rolloverIfNeeded())
        assertEquals(2, db.historyDao().all().size)
    }
}

private suspend fun ScheduleRepository.observeDayOnce(ref: DayRef): EditableDay = observeDay(ref).first()
