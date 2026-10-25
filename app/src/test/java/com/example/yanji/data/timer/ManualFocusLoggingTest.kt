package com.example.yanji.data.timer

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.db.YanjiDatabase_Impl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/**
 * 补记专注 / 模考的落库语义。
 *
 * 每条用例使用自己的一次性 Room 库，不碰 App 单例或真机数据。
 * 成就系统摘除后，原先「先落库再发成就事件」的顺序断言不再适用，
 * 因此这里改为直接断言写入后可从库中读回同一条记录。
 */
class ManualFocusLoggingTest {

    private class Fixture : AutoCloseable {
        private val directory = Files.createTempDirectory("yanji-manual-log").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db: YanjiDatabase = Room.databaseBuilder<YanjiDatabase>(
            directory.resolve("manual.db").absolutePath,
            factory = { YanjiDatabase_Impl() }
        ).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()

        override fun close() {
            scope.cancel()
            scope.coroutineContext[Job]?.let { runBlocking { it.join() } }
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test fun manualFocusIsPersistedAndReadableById() = runBlocking {
        Fixture().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val session = FocusSession("manual", "math", "高等数学", 1000, 1801000, 1800, mode = "补记专注")
            store.addFocusSession(session)
            assertEquals(session, fixture.db.focusSessionDao().getById(session.id)!!.toDomainModel())
            assertEquals(session, store.getFocusSessionByIdFromDb(session.id)!!.toDomainModel())
        }
    }

    @Test fun manualExamIsPersistedAndReadableById() = runBlocking {
        Fixture().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val session = ExamSession("manual-exam", "math", "数学", actualDurationSeconds = 10800)
            store.addExamSession(session)
            // addExamSession launches on the store's scope; wait for the row.
            var persisted: ExamSessionEntity? = null
            repeat(50) {
                persisted = fixture.db.examSessionDao().getById(session.id)
                if (persisted == null) Thread.sleep(20)
            }
            assertEquals(session, persisted!!.toDomainModel())
        }
    }

    @Test fun timerCompletionKeepsTheRecordingThreshold() = runBlocking {
        Fixture().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val active = ActiveSession("focus", ActiveSessionKind.FOCUS, "math", "数学", note = "真实随笔", taskId = "task", startedAtEpochMs = 1000)
            // Below the threshold nothing is recorded and no completion is announced.
            store.persistence.completeFocus(active, 59, 7, 2, 67000)
            assertEquals(0, fixture.db.focusSessionDao().count())
            assertNull(store.lastCompletedFocus.value)

            store.persistence.completeFocus(active, 60, 7, 2, 68000)
            val focus = fixture.db.focusSessionDao().getById("focus")!!.toDomainModel()
            assertEquals(60L, focus.durationSeconds)
            assertEquals(7L, focus.pausedDurationSeconds)
            assertEquals(2, focus.pauseCount)
            assertEquals("真实随笔", focus.note)
            assertEquals("task", focus.taskId)
            assertEquals(SessionStatus.COMPLETED, focus.status)
            assertEquals(focus, store.lastCompletedFocus.value)
            store.acknowledgeCompletedFocus()
            assertNull(store.lastCompletedFocus.value)

            val exam = active.copy(sessionId = "exam", kind = ActiveSessionKind.EXAM, plannedDurationSeconds = 10800)
            store.persistence.completeExam(exam, 10000, 10001000)
            assertEquals(fixture.db.examSessionDao().getById("exam")!!.toDomainModel(), store.lastCompletedExam.value)
            store.acknowledgeCompletedExam()
            assertNull(store.lastCompletedExam.value)
        }
    }
}
