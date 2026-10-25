package com.example.yanji.data

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.db.*
import com.example.yanji.data.timer.TimerStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real generated Room DAOs, isolated files, and failures injected at the SQLite write boundary. */
class PersistedWritesTest {
    private class WriteGate(val fails: Boolean) {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
    }

    private class ControlledDriver : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        @Volatile var gate: WriteGate? = null
        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val normalized = sql.replace("`", "").uppercase()
                    val target = normalized.startsWith("UPDATE FOCUS_SESSIONS SET NOTE") ||
                        normalized.startsWith("DELETE FROM FOCUS_SESSIONS WHERE") ||
                        normalized.startsWith("DELETE FROM EXAM_SESSIONS WHERE") ||
                        normalized.startsWith("INSERT OR REPLACE INTO USER_SETTINGS")
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean {
                            val pending = if (target) gate else null
                            if (pending != null) {
                                gate = null
                                pending.entered.complete(Unit)
                                check(pending.release.await(10, TimeUnit.SECONDS)) { "Write gate timed out" }
                                check(!pending.fails) { "Injected SQLite write failure" }
                            }
                            return statement.step()
                        }
                    }
                }
            }
        }
    }

    private suspend fun fixture(block: suspend (YanjiDatabase, ControlledDriver, CoroutineScope, String) -> Unit) {
        val directory = Files.createTempDirectory("yanji-persisted-writes").toFile()
        val path = directory.resolve("isolated.db").absolutePath
        val driver = ControlledDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open(path, driver)
        try {
            db.focusSessionDao().insert(FocusSessionEntity("focus", "math", "数学", 1, 120001, 120, note = "原随笔"))
            db.examSessionDao().insert(ExamSessionEntity("exam", "math", "数学", 120, 120, 1, 120001, null, 100.0, "", "COMPLETED", 1))
            db.userSettingsDao().saveSettings(UserSettingsEntity.fromDomainModel(UserSettings()))
            block(db, driver, scope, path)
        } finally {
            driver.gate?.release?.countDown()
            scope.cancel()
            scope.coroutineContext[Job]?.join()
            db.close()
            directory.deleteRecursively()
        }
    }

    private fun open(path: String, driver: SQLiteDriver = BundledSQLiteDriver()): YanjiDatabase =
        Room.databaseBuilder<YanjiDatabase>(path, factory = { YanjiDatabase_Impl() })
            .setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()

    private fun repository(db: YanjiDatabase): YanjiRepository {
        val repository = YanjiRepository::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        YanjiRepository::class.java.getDeclaredField("database").apply { isAccessible = true }.set(repository, db)
        return repository
    }

    @Test fun sessionWritesWaitForSQLiteAndSurviveReopen() = runBlocking {
        for (operation in listOf("note", "focus", "exam")) fixture { db, driver, scope, path ->
            val store = TimerStore(scope, { db })
            val gate = WriteGate(false)
            driver.gate = gate
            val write = async(Dispatchers.Default) {
                when (operation) {
                    "note" -> store.updateFocusSessionNote("focus", "新随笔")
                    "focus" -> store.deleteFocusSession("focus")
                    else -> store.deleteExamSession("exam")
                }
            }
            try {
                withTimeout(5000) { gate.entered.await() }
                assertFalse("$operation returned before SQLite completed", write.isCompleted)
            } finally { gate.release.countDown() }
            write.await()
            db.close()
            val reopened = open(path)
            try {
                when (operation) {
                    "note" -> assertEquals("新随笔", reopened.focusSessionDao().getById("focus")?.note)
                    "focus" -> assertNull(reopened.focusSessionDao().getById("focus"))
                    else -> assertNull(reopened.examSessionDao().getById("exam"))
                }
            } finally { reopened.close() }
        }
    }

    @Test fun failedSessionWritesPropagateAndPreserveRecords() = runBlocking {
        for (operation in listOf("note", "focus", "exam")) fixture { db, driver, scope, _ ->
            val store = TimerStore(scope, { db })
            val gate = WriteGate(true).also { it.release.countDown() }
            driver.gate = gate
            val result = runCatching {
                when (operation) {
                    "note" -> store.updateFocusSessionNote("focus", "不应保存")
                    "focus" -> store.deleteFocusSession("focus")
                    else -> store.deleteExamSession("exam")
                }
            }
            assertTrue("$operation must propagate the actual write failure", result.isFailure)
            assertEquals("原随笔", db.focusSessionDao().getById("focus")?.note)
            assertNotNull(db.examSessionDao().getById("exam"))
        }
    }

    @Test fun settingsSuccessWaitsForCommitAndFailureKeepsLastSavedSettings() = runBlocking {
        fixture { db, driver, _, path ->
            val repository = repository(db)
            val previous = repository.settings.value
            val next = previous.copy(dailyGoalHours = 9f, themeMode = "DARK", decisionModel = "chosen-model")
            for (fails in listOf(true, false)) {
                val gate = WriteGate(fails)
                driver.gate = gate
                val write = async(Dispatchers.Default) { runCatching { repository.updateSettings { next } } }
                try {
                    withTimeout(5000) { gate.entered.await() }
                    assertFalse("Settings reported success before commit", write.isCompleted)
                    assertEquals(previous, repository.settings.value)
                } finally { gate.release.countDown() }
                val result = write.await()
                if (fails) {
                    assertTrue(result.isFailure)
                    assertEquals(previous, repository.settings.value)
                    assertEquals(previous.dailyGoalHours, db.userSettingsDao().getSettings().first()!!.dailyGoalHours)
                } else {
                    assertEquals(SettingsUpdateResult.Saved, result.getOrThrow())
                    assertEquals(next, repository.settings.value)
                    db.close()
                    val reopened = open(path)
                    try {
                        val saved = reopened.userSettingsDao().getSettings().first()!!.toDomainModel()
                        assertEquals(next.dailyGoalHours, saved.dailyGoalHours)
                        assertEquals(next.themeMode, saved.themeMode)
                        assertEquals(next.decisionModel, saved.decisionModel)
                    } finally { reopened.close() }
                }
            }
        }
    }

    @Test fun missingDatabaseCannotReportSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = TimerStore(scope, { null })
            assertTrue(runCatching { store.updateFocusSessionNote("missing", "note") }.isFailure)
            assertTrue(runCatching { store.deleteFocusSession("missing") }.isFailure)
            assertTrue(runCatching { store.deleteExamSession("missing") }.isFailure)
            val repository = YanjiRepository::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            assertTrue(runCatching { repository.updateSettings { UserSettings() } }.isFailure)
        } finally { scope.cancel() }
    }

    @Test fun missingOrAlreadyDeletedRecordsDoNotReportAnotherSuccessfulMutation() = runBlocking {
        fixture { db, _, scope, _ ->
            val store = TimerStore(scope, { db })
            assertTrue(store.deleteFocusSession("focus"))
            assertFalse(store.deleteFocusSession("focus"))
            assertFalse(store.updateFocusSessionNote("focus", "missing"))
            assertTrue(store.deleteExamSession("exam"))
            assertFalse(store.deleteExamSession("exam"))
        }
    }

    @Test fun concurrentSettingsPatchesKeepBothCommittedChanges() = runBlocking {
        fixture { db, driver, _, path ->
            val repository = repository(db)
            val gate = WriteGate(false)
            driver.gate = gate
            val first = async(Dispatchers.Default) { repository.updateSettings { it.copy(dailyGoalHours = 7f) } }
            try {
                withTimeout(5000) { gate.entered.await() }
                val second = async(Dispatchers.Default) { repository.updateSettings { it.copy(themeMode = "DARK") } }
                gate.release.countDown()
                assertEquals(SettingsUpdateResult.Saved, first.await())
                assertEquals(SettingsUpdateResult.Saved, second.await())
                db.close()
                val reopened = open(path)
                try {
                    val saved = reopened.userSettingsDao().getSettings().first()!!
                    assertEquals(7f, saved.dailyGoalHours)
                    assertEquals("DARK", saved.themeMode)
                } finally { reopened.close() }
            } finally { gate.release.countDown() }
        }
    }

    @Test fun waitingForPersistenceDoesNotBlockTheCallingUiDispatcher() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            fixture { db, driver, scope, _ ->
                val store = TimerStore(scope, { db })
                val gate = WriteGate(false)
                driver.gate = gate
                val write = async(ui) { store.updateFocusSessionNote("focus", "新随笔") }
                try {
                    withTimeout(5000) { gate.entered.await() }
                    assertTrue(withTimeout(5000) { withContext(ui) { true } })
                    assertFalse(write.isCompleted)
                } finally { gate.release.countDown() }
                assertTrue(write.await())
            }
        }
    }
}
