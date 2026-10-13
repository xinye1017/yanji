package com.example.yanji.bridge

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.data.DailySnapshotReader
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyTask
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.StudyTaskEntity
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.facebook.react.bridge.BridgeReactContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.Executor

/** Exercises production Room queries in an isolated database, never the user's database. */
@RunWith(AndroidJUnit4::class)
class DailySnapshotInstrumentedTest {
    private lateinit var db: YanjiDatabase
    private val queries = Collections.synchronizedList(mutableListOf<String>())
    private val date = "2026-10-10"

    @Before
    fun openIsolatedDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, YanjiDatabase::class.java
        ).setQueryCallback({ sql, _ -> queries.add(sql) }, Executor { it.run() }).build()
        runBlocking { DailySnapshotReader.read(db, date) } // Initialize before measuring.
        queries.clear()
    }

    @After
    fun closeIsolatedDatabase() { if (::db.isInitialized) db.close() }

    @Test
    fun emptySnapshotUsesFourBoundedBusinessReads() = runBlocking {
        val snapshot = DailySnapshotReader.read(db, date)
        assertEquals(date, snapshot.date)
        assertEquals(0L, snapshot.totalDurationSeconds)
        assertEquals(0, snapshot.focusCount)
        assertEquals(0, snapshot.examCount)
        assertTrue(snapshot.tasks.isEmpty())
        assertTrue(snapshot.sessions.isEmpty())
        assertTrue(snapshot.notes.isEmpty())
        assertBoundedReads()
    }

    @Test
    fun snapshotPreservesCrossDayTaskTimeAndFiltersDateBoundaries() = runBlocking {
        val range = YanjiTime.dayRange(LocalDate.parse(date))
        db.studyTaskDao().insert(StudyTaskEntity.fromDomainModel(StudyTask(
            id = "done", date = date, subjectId = "math", subjectName = "数学", title = "done",
            plannedMinutes = 30, isCompleted = true, createdAt = 1, updatedAt = 1
        )))
        db.studyTaskDao().insert(StudyTaskEntity.fromDomainModel(StudyTask(
            id = "pending", date = date, subjectId = "math", subjectName = "数学", title = "pending",
            plannedMinutes = 45, createdAt = 2, updatedAt = 2
        )))
        db.focusSessionDao().insert(FocusSessionEntity.fromDomainModel(FocusSession(
            id = "yesterday", subjectId = "math", subjectName = "数学", taskId = "done",
            startTime = range.startInclusive - 1_000, endTime = range.startInclusive + 58_000,
            durationSeconds = 59
        )))
        db.focusSessionDao().insert(FocusSessionEntity.fromDomainModel(FocusSession(
            id = "today", subjectId = "math", subjectName = "数学", taskId = "done",
            startTime = range.startInclusive, endTime = range.startInclusive + 61_000,
            durationSeconds = 61
        )))
        db.focusSessionDao().insert(FocusSessionEntity.fromDomainModel(FocusSession(
            id = "tomorrow", subjectId = "math", subjectName = "数学",
            startTime = range.endExclusive, endTime = range.endExclusive + 60_000,
            durationSeconds = 60
        )))
        db.examSessionDao().insert(ExamSessionEntity(
            id = "exam", subjectId = "math", subjectName = "数学", plannedDurationSeconds = 60,
            actualDurationSeconds = 59, startTime = range.startInclusive + 3_600_000,
            endTime = range.startInclusive + 3_659_000, score = null, maxScore = 150.0,
            note = "", status = "COMPLETED", createdAt = range.startInclusive
        ))
        db.noteEntryDao().insert(NoteEntryEntity.fromDomainModel(NoteEntry(
            id = "note", date = date, content = "local", createdAt = 3, updatedAt = 3
        )))
        db.noteEntryDao().insert(NoteEntryEntity.fromDomainModel(NoteEntry(
            id = "other", date = "2026-10-09", content = "outside", createdAt = 4, updatedAt = 4
        )))
        queries.clear()
        val snapshot = DailySnapshotReader.read(db, date)
        assertEquals(120L, snapshot.totalDurationSeconds)
        assertEquals(1, snapshot.focusCount)
        assertEquals(1, snapshot.examCount)
        assertEquals(listOf("exam", "today"), snapshot.sessions.map { it.id })
        assertEquals(listOf("pending", "done"), snapshot.tasks.map { it.id })
        assertEquals(120L, snapshot.taskActualSeconds["done"])
        assertEquals(2, BridgeMappers.actualMinutesOf(snapshot.taskActualSeconds.getValue("done")))
        assertEquals(listOf("note"), snapshot.notes.map { it.id })
        assertBoundedReads()
    }

    @Test
    fun invalidatingBridgeCollectorsLeavesTheNativeSessionUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = BridgeReactContext(instrumentation.targetContext)
        val sessionId = ActiveSessionCoordinator.active.value?.sessionId
        lateinit var data: YanjiDataModule
        lateinit var timer: YanjiTimerModule
        instrumentation.runOnMainSync {
            data = YanjiDataModule(context)
            timer = YanjiTimerModule(context)
        }
        val dataJob = moduleJob(data)
        val timerJob = moduleJob(timer)
        assertTrue(dataJob.isActive)
        assertTrue(timerJob.isActive)
        instrumentation.runOnMainSync {
            data.invalidate()
            timer.invalidate()
        }
        assertTrue(dataJob.isCancelled)
        assertTrue(timerJob.isCancelled)
        assertEquals(sessionId, ActiveSessionCoordinator.active.value?.sessionId)
        instrumentation.runOnMainSync {
            val rebuilt = YanjiDataModule(context)
            assertTrue("A rebuilt module owns a fresh scope", moduleJob(rebuilt).isActive)
            rebuilt.invalidate()
        }
    }

    private fun moduleJob(module: Any): Job {
        val field = module.javaClass.getDeclaredField("scope").apply { isAccessible = true }
        return (field.get(module) as CoroutineScope).coroutineContext[Job]!!
    }

    private fun assertBoundedReads() {
        val selects = synchronized(queries) { queries.toList() }.filter {
            it.trimStart().startsWith("SELECT", ignoreCase = true) &&
                listOf("focus_sessions", "exam_sessions", "study_tasks", "journal_entries")
                    .any { table -> it.contains(table) }
        }
        assertEquals("One read per daily projection, including task actual time", 4, selects.size)
        assertTrue("Every business projection must be bounded", selects.all { it.contains("WHERE", ignoreCase = true) })
        android.util.Log.i("YanjiSnapshotTest", "Daily snapshot executes ${selects.size} bounded business queries")
    }
}
