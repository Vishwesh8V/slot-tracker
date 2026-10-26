package com.vishwesh.slottracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vishwesh.slottracker.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** Acceptance: export, then wipe, then import restores everything exactly (spec §9). */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val monday = LocalDate.of(2026, 10, 26)
    private var now = monday.atTime(9, 0)
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var tracker: TrackerRepository
    private lateinit var backup: BackupRepository

    @Before
    fun setUp(): Unit = runBlocking {
        settings = SettingsRepository(context)
        settings.setRolloverTime(LocalTime.of(6, 0))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tracker = TrackerRepository(db, settings, now = { now })
        backup = BackupRepository(db, settings)
        tracker.catchUp()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun everything(database: AppDatabase) = database.backupDao().let {
        listOf(
            it.items(), it.templateSlots(), it.templateAssignments(), it.overrideDays(), it.overrideSlots(),
            it.overrideAssignments(), it.taskStates(), it.history(), it.meta(),
        )
    }

    @Test
    fun exportWipeImportRestoresEverything(): Unit = runBlocking {
        // Some of every kind of data: history with a note, an override, live ticks and notes.
        val schedule = ScheduleRepository(db, tracker, settings)
        tracker.setNote(monday, tracker.loadLiveDay().tasks[1].key, "too tired")
        now = monday.plusDays(1).atTime(8, 0)
        tracker.rolloverIfNeeded()
        val tuesday = tracker.loadLiveDay()
        tracker.setDone(tuesday.date, tuesday.tasks[0].key, true)
        val math = db.itemDao().all().first { it.name == "Math Puzzle" }.id
        schedule.saveSlot(DayRef.Date(monday.plusDays(3)), SlotDraft(null, false, "Extra", LocalTime.of(15, 0), LocalTime.of(16, 0), listOf(math)))
        settings.setRolloverTime(LocalTime.of(5, 30))
        settings.setSlotReminders(false)
        val before = everything(db)

        val json = backup.export()
        val wiped = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        settings.setRolloverTime(LocalTime.of(6, 0))
        settings.setSlotReminders(true)
        val restoredInto = BackupRepository(wiped, settings)
        restoredInto.restore(restoredInto.parse(json))

        assertEquals(before, everything(wiped))
        assertEquals(LocalTime.of(5, 30), settings.currentRolloverTime())
        assertEquals(false, settings.slotReminders.first())
        wiped.close()
        settings.setRolloverTime(LocalTime.of(6, 0))
        settings.setSlotReminders(true)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        listOf("not json", """{"app":"other"}""", """{"app":"slot-tracker","schemaVersion":99}""").forEach { text ->
            val error = runCatching { backup.parse(text) }.exceptionOrNull()
            assertTrue("accepted: $text", error is InvalidBackupException)
        }
    }

    @Test
    fun rejectsInconsistentBackups(): Unit = runBlocking {
        val broken = backup.export().replace("\"templateSlotId\": 1,", "\"templateSlotId\": 9999,")
        assertTrue(runCatching { backup.parse(broken) }.exceptionOrNull() is InvalidBackupException)
    }
}

