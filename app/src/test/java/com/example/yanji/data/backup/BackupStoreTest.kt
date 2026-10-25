package com.example.yanji.data.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.data.UserSettings
import com.example.yanji.data.db.ChatMessageEntity
import com.example.yanji.data.db.ChatSessionEntity
import com.example.yanji.data.db.CheckInEntity
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.QuickStartPresetEntity
import com.example.yanji.data.db.StudyTaskEntity
import com.example.yanji.data.db.SubjectEntity
import com.example.yanji.data.db.UserSettingsEntity
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.db.YanjiDatabase_Impl
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.data.timer.ActiveSessionCoordinatorCore
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.ActiveSessionRecord
import com.example.yanji.data.timer.MonotonicClock
import com.example.yanji.data.timer.TimerPhase
import com.example.yanji.data.timer.TimerSessionPersistence
import com.example.yanji.data.timer.TimerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackupStoreTest {

    private val epoch = 1_757_000_000_000L

    private class ControlledDriver : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        val deleteReached = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseDelete = CountDownLatch(1)
        @Volatile var failAfterDelete = false
        @Volatile var armed = false

        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val normalized = sql.replace("`", "").uppercase()
                    if (armed && normalized.startsWith("DELETE FROM SUBJECTS")) {
                        return object : SQLiteStatement by statement {
                            override fun step(): Boolean {
                                val result = statement.step()
                                deleteReached.complete(Unit)
                                check(releaseDelete.await(10, TimeUnit.SECONDS)) { "Delete gate timed out" }
                                check(!failAfterDelete) { "Injected failure after deletes" }
                                return result
                            }
                        }
                    }
                    return statement
                }
            }
        }
    }

    private class FixedClock : MonotonicClock {
        override fun nowMs(): Long = 10_000L
        override fun currentBootId(): String = "boot-store-test"
    }

    private class PersistedState(var record: ActiveSessionRecord? = null) : TimerSessionPersistence {
        var failRead = false

        override suspend fun completeFocus(
            session: ActiveSession,
            actualSeconds: Long,
            pausedSeconds: Long,
            pauseCount: Int,
            endEpochMs: Long
        ) = Unit

        override suspend fun completeExam(session: ActiveSession, actualSeconds: Long, endEpochMs: Long) = Unit

        override suspend fun saveActiveSession(record: ActiveSessionRecord) {
            this.record = record
        }

        override suspend fun saveActiveSession(session: ActiveSession) {
            record = ActiveSessionRecord(
                activeSession = session,
                timerSnapshot = TimerSnapshot(
                    phase = if (session.paused) TimerPhase.PAUSED else TimerPhase.RUNNING,
                    startedAtEpochMs = session.startedAtEpochMs,
                    targetDurationSeconds = session.targetDurationSeconds,
                    accumulatedActiveMs = session.accumulatedActiveMs,
                    resumedAtMonotonicMs = if (session.paused) null else 10_000L,
                    pauseCount = session.pauseCount
                ),
                bootId = "boot-store-test"
            )
        }

        override suspend fun clearActiveSession() {
            record = null
        }

        override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? {
            if (failRead) error("injected persisted-session read failure")
            return record
        }

        override suspend fun loadActiveSession(): ActiveSession? {
            if (failRead) error("injected persisted-session read failure")
            return record?.activeSession
        }
    }

    private data class Fixture(
        val directory: java.io.File,
        val path: String,
        val driver: SQLiteDriver,
        val db: YanjiDatabase
    )

    private fun open(path: String, driver: SQLiteDriver = BundledSQLiteDriver()): YanjiDatabase =
        Room.databaseBuilder<YanjiDatabase>(path, factory = { YanjiDatabase_Impl() })
            .setDriver(driver)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private suspend fun fixture(
        driver: SQLiteDriver = BundledSQLiteDriver(),
        block: suspend (YanjiDatabase) -> Unit
    ) {
        val directory = Files.createTempDirectory("yanji-backup-store").toFile()
        val path = directory.resolve("isolated.db").absolutePath
        val db = open(path, driver)
        try {
            block(db)
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    private fun store(
        db: YanjiDatabase,
        coordinator: ActiveSessionCoordinatorCore = ActiveSessionCoordinatorCore(PersistedState(), FixedClock()),
        snapshotWriter: (suspend (YanjiBackup) -> String) = { "snapshot.json" },
        onMirror: () -> Unit = {},
        onSettingsMirror: (UserSettings) -> Unit = {}
    ) = BackupStore(
        dbProvider = { db },
        contextProvider = { null },
        onActiveFocusCleared = onMirror,
        onSubjectsReplaced = { _: List<Subject> -> },
        onSettingsReplaced = onSettingsMirror,
        snapshotWriter = snapshotWriter,
        coordinator = coordinator
    )

    private fun sampleBackup() = YanjiBackup(
        exportedAt = epoch,
        appVersionName = "test",
        settings = UserSettingsBackup.fromDomain(
            UserSettings(
                targetExamDate = "2026-12-20",
                targetSchool = "浙江大学 计算机学院",
                targetMajor = "计算机技术",
                dailyGoalHours = 8.5f,
                validStudyThresholdMinutes = 12,
                defaultSubjectId = "math_advanced",
                soundEnabled = false,
                vibrationEnabled = false,
                aiProvider = "DeepSeek",
                aiBaseUrl = "https://api.deepseek.com/v1",
                aiModel = "deepseek-chat",
                aiProtocol = "OPENAI_RESPONSES",
                themeMode = "DARK",
                mascotTheme = "STAR",
                decisionProvider = "openrouter",
                decisionModel = "respan/span-01-lite:free",
                decisionEndpoint = "https://openrouter.ai/api/alpha/decisions"
            )
        ),
        focusSessions = listOf(
            FocusSession(
                id = "fs-full",
                subjectId = "math_advanced",
                subjectName = "高等数学",
                startTime = epoch,
                endTime = epoch + 3_600_000L,
                durationSeconds = 3600,
                pausedDurationSeconds = 120,
                pauseCount = 2,
                mode = "倒计时 90分钟",
                note = "完成二重积分",
                taskId = "task-full",
                status = SessionStatus.COMPLETED,
                createdAt = epoch - 10
            )
        ),
        examSessions = listOf(
            ExamSession(
                id = "exam-full",
                subjectId = "math",
                subjectName = "数学一",
                plannedDurationSeconds = 10800,
                actualDurationSeconds = 10_500,
                startTime = epoch + 10_000_000L,
                endTime = epoch + 20_500_000L,
                score = 126.5,
                maxScore = 150.0,
                note = "全真模拟",
                status = SessionStatus.COMPLETED,
                createdAt = epoch + 9_000_000L
            )
        ),
        noteEntries = listOf(
            NoteEntry(
                id = "note-full",
                date = "2026-09-05",
                title = "渐入佳境",
                content = "稳定完成",
                moodScore = 5,
                energyScore = 3,
                studySatisfaction = 4,
                tomorrowPlan = "复盘",
                blockers = "导数",
                tags = listOf("数学", "复盘"),
                createdAt = epoch,
                updatedAt = epoch + 1,
                isFavorite = true,
                isDraft = false,
                sessionId = "fs-full",
                category = "DOUBT",
                isResolved = true,
                resolvedAt = epoch + 2
            )
        ),
        chatSessions = listOf(
            ChatSession(
                id = "chat-full",
                title = "数学复盘",
                createdAt = epoch,
                updatedAt = epoch + 10,
                model = "deepseek-chat"
            )
        ),
        chatMessages = listOf(
            ChatMessage(
                id = "msg-full",
                sessionId = "chat-full",
                sender = ChatSender.USER,
                content = "这题怎么想",
                timestamp = epoch + 5
            )
        ),
        checkIns = listOf(
            CheckIn(date = "2026-09-05", checkInTime = epoch, streak = 7, note = "连续", mood = "平稳")
        ),
        quickStartPresets = listOf(
            QuickStartPreset(
                id = "quick-full",
                type = QuickStartPreset.TYPE_CUSTOM,
                label = "高数二重积分",
                subLabel = "90 分钟",
                subjectId = "math_advanced",
                subjectName = "高等数学",
                mode = FocusModes.BIG_90,
                note = "练习",
                createdAt = epoch,
                sortOrder = 3
            )
        ),
        studyTasks = listOf(
            StudyTask(
                id = "task-full",
                date = "2026-09-05",
                subjectId = "math_advanced",
                subjectName = "高等数学",
                title = "二重积分",
                plannedMinutes = 90,
                isCompleted = true,
                createdAt = epoch - 100,
                updatedAt = epoch + 3_600_000L
            )
        ),
        subjects = listOf(
            Subject("math", "数学一", "#356AE6", 1, enabled = true),
            Subject("math_advanced", "高等数学", "#7C3AED", 11, parentId = "math")
        )
    )

    private suspend fun apply(db: YanjiDatabase, backup: YanjiBackup = sampleBackup()) {
        BackupTransfer.applyInTransaction(db, backup)
    }

    @Test
    fun `full file round trip restores every field and relation after reopen`() = runTest {
        fixture { sourceDb ->
            apply(sourceDb)
            val exported = store(sourceDb).exportJson()
            val targetDirectory = Files.createTempDirectory("yanji-backup-target").toFile()
            try {
                val backupFile = targetDirectory.resolve("yanji-backup.json")
                BackupDocumentIO.write(exported, { backupFile.outputStream() })
                val raw = BackupDocumentIO.read(backupFile.inputStream())

                val targetPath = targetDirectory.resolve("restored.db").absolutePath
                val target = open(targetPath)
                try {
                    val result = store(target).importJson(raw)
                    assertEquals("snapshot.json", (result as BackupImportResult.Success).snapshotPath)
                } finally {
                    target.close()
                }

                val reopened = open(targetPath)
                try {
                    val expected = sampleBackup()
                    assertEquals(expected.focusSessions.single(), reopened.focusSessionDao().getById("fs-full")!!.toDomainModel())
                    assertEquals(expected.examSessions.single(), reopened.examSessionDao().getById("exam-full")!!.toDomainModel())
                    assertEquals(expected.noteEntries.single(), reopened.noteEntryDao().getById("note-full")!!.toDomainModel())
                    assertEquals(expected.chatSessions.single(), reopened.chatSessionDao().getById("chat-full")!!.toDomainModel())
                    assertEquals(expected.chatMessages.single(), reopened.chatMessageDao().getAll().first().single().toDomainModel())
                    assertEquals(expected.checkIns.single(), reopened.checkInDao().getAllOnce().single().toDomainModel())
                    assertEquals(expected.quickStartPresets.single(), reopened.quickStartPresetDao().getAllFlow().first().single().toDomainModel())
                    assertEquals(expected.studyTasks.single(), reopened.studyTaskDao().getAllFlow().first().single().toDomainModel())
                    assertEquals(expected.subjects, reopened.subjectDao().getAll().map { it.toDomainModel() })
                    assertEquals(expected.settings!!.toDomain(), reopened.userSettingsDao().getSettings().first()!!.toDomainModel())
                    assertEquals("task-full", reopened.focusSessionDao().getById("fs-full")!!.taskId)
                    assertEquals("", reopened.userSettingsDao().getSettings().first()!!.toDomainModel().aiApiKey)
                    assertFalse(raw.contains("aiApiKey"))
                } finally {
                    reopened.close()
                }
            } finally {
                targetDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun `empty and omitted subjects preserve local subjects`() = runTest {
        fixture { db ->
            db.subjectDao().insert(SubjectEntity("local", "本机学科", "#000000", 1))
            val empty = sampleBackup().copy(subjects = emptyList())
            assertTrue(store(db).importJson(BackupCodec.encode(empty)) is BackupImportResult.Success)
            assertEquals("空学科列表必须保留本机学科", 1, db.subjectDao().count())

            db.subjectDao().insert(SubjectEntity("local", "本机学科", "#000000", 1))
            val legacy = BackupCodec.encode(
                sampleBackup().copy(
                    schemaVersion = 1,
                    studyTasks = emptyList(),
                    subjects = emptyList()
                )
            )
            val legacyRaw = kotlinx.serialization.json.Json.parseToJsonElement(legacy)
                .let { it as kotlinx.serialization.json.JsonObject }
                .let { obj ->
                    kotlinx.serialization.json.JsonObject(
                        obj.filterKeys { it !in setOf("studyTasks", "subjects") }
                    )
                }
            assertTrue(store(db).importJson(legacyRaw.toString()) is BackupImportResult.Success)
            assertEquals("local", db.subjectDao().getAll().single().id)
        }
    }

    @Test
    fun `injected failure after deletes rolls back every table`() = runTest {
        val driver = ControlledDriver()
        fixture(driver) { db ->
            apply(db, sampleBackup().copy(
                focusSessions = listOf(sampleBackup().focusSessions.single().copy(id = "old-focus"))
            ))
            val before = snapshotRows(db)
            driver.armed = true
            driver.failAfterDelete = true
            val import = async(Dispatchers.IO) {
                store(db).importJson(BackupCodec.encode(sampleBackup().copy(
                    focusSessions = listOf(sampleBackup().focusSessions.single().copy(id = "new-focus"))
                )))
            }
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { driver.deleteReached.await() }
            }
            driver.releaseDelete.countDown()
            assertTrue(import.await() is BackupImportResult.Failure)
            assertEquals(before, snapshotRows(db))
        }
    }

    @Test
    fun `snapshot creation failure refuses all database writes`() = runTest {
        fixture { db ->
            apply(db)
            val before = snapshotRows(db)
            val result = store(
                db,
                snapshotWriter = { error("injected snapshot failure") }
            ).importJson(BackupCodec.encode(sampleBackup().copy(subjects = emptyList())))
            assertTrue(result is BackupImportResult.Failure)
            assertEquals(before, snapshotRows(db))
        }
    }

    @Test
    fun `missing context cannot bypass mandatory snapshot creation`() = runTest {
        fixture { db ->
            apply(db)
            val before = snapshotRows(db)
            val store = BackupStore(
                dbProvider = { db },
                contextProvider = { null },
                onActiveFocusCleared = {},
                onSubjectsReplaced = {},
                coordinator = ActiveSessionCoordinatorCore(PersistedState(), FixedClock())
            )

            val result = store.importJson(BackupCodec.encode(sampleBackup()))

            assertTrue(result is BackupImportResult.Failure)
            assertEquals(before, snapshotRows(db))
        }
    }

    @Test
    fun `cancellation during snapshot propagates and leaves database unchanged`() = runTest {
        fixture { db ->
            apply(db)
            val before = snapshotRows(db)
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val coordinator = ActiveSessionCoordinatorCore(PersistedState(), FixedClock())
            val import = async(Dispatchers.IO) {
                store(
                    db,
                    coordinator = coordinator,
                    snapshotWriter = {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                ).importJson(BackupCodec.encode(sampleBackup()))
            }

            withContext(Dispatchers.IO) {
                withTimeout(5_000) { entered.await() }
            }
            import.cancel()
            assertTrue(runCatching { import.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertEquals(before, snapshotRows(db))
            assertFalse(coordinator.isBusy)
        }
    }

    @Test
    fun `snapshot writer runs while timer start is excluded`() = runTest {
        fixture { db ->
            val persistence = PersistedState()
            val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock())
            var excluded = false
            val result = store(
                db,
                coordinator = coordinator,
                snapshotWriter = {
                    excluded = coordinator.isBusy
                    assertFalse(
                        "Timer must not start while the import snapshot is being written",
                        coordinator.begin(
                            ActiveSession(
                                sessionId = "racing-snapshot",
                                kind = ActiveSessionKind.FOCUS,
                                subjectId = "math",
                                subjectName = "数学"
                            )
                        )
                    )
                    "snapshot.json"
                }
            ).importJson(BackupCodec.encode(sampleBackup()))

            assertTrue(result is BackupImportResult.Success)
            assertTrue("Restore must own the coordinator during snapshot creation", excluded)
            assertFalse(coordinator.isBusy)
        }
    }

    @Test
    fun `missing database export and import reject`() = runTest {
        val coordinator = ActiveSessionCoordinatorCore(PersistedState(), FixedClock())
        val missing = BackupStore(
            dbProvider = { null },
            contextProvider = { null },
            onActiveFocusCleared = {},
            onSubjectsReplaced = {},
            coordinator = coordinator
        )
        assertTrue(runCatching { missing.exportJson() }.isFailure)
        assertTrue(missing.importJson(BackupCodec.encode(sampleBackup())) is BackupImportResult.Failure)
    }

    @Test
    fun `malformed and newer backups leave the database unchanged`() = runTest {
        fixture { db ->
            apply(db)
            val before = snapshotRows(db)
            assertTrue(store(db).importJson("{}") is BackupImportResult.Failure)
            assertTrue(store(db).importJson("{ broken") is BackupImportResult.Failure)
            assertTrue(
                store(db).importJson(
                    BackupCodec.encode(sampleBackup().copy(schemaVersion = YanjiBackup.CURRENT_SCHEMA_VERSION + 1))
                ) is BackupImportResult.Failure
            )
            assertEquals(before, snapshotRows(db))
        }
    }

    @Test
    fun `postcommit mirror failure reports success with warning`() = runTest {
        fixture { db ->
            var calls = 0
            val result = store(
                db,
                onMirror = {
                    calls++
                    error("injected postcommit mirror failure")
                }
            ).importJson(BackupCodec.encode(sampleBackup()))

            assertTrue(result is BackupImportResult.Success)
            assertEquals(1, calls)
            assertTrue((result as BackupImportResult.Success).warnings.any { it.contains("重启") })
            assertEquals(1, db.focusSessionDao().count())
        }
    }

    @Test
    fun `restored settings are mirrored after commit`() = runTest {
        fixture { db ->
            var mirrored: UserSettings? = null
            val result = store(
                db,
                onSettingsMirror = { mirrored = it }
            ).importJson(BackupCodec.encode(sampleBackup()))

            assertTrue(result is BackupImportResult.Success)
            assertEquals(sampleBackup().settings?.toDomain(), mirrored)
            assertEquals(sampleBackup().settings?.toDomain(), db.userSettingsDao().getSettings().first()?.toDomainModel())
        }
    }

    @Test
    fun `settings mirror failure reports success with warning`() = runTest {
        fixture { db ->
            val result = store(
                db,
                onSettingsMirror = { error("injected settings mirror failure") }
            ).importJson(BackupCodec.encode(sampleBackup()))

            assertTrue(result is BackupImportResult.Success)
            assertTrue((result as BackupImportResult.Success).warnings.any { it.contains("设置") })
            assertEquals(sampleBackup().settings?.toDomain(), db.userSettingsDao().getSettings().first()?.toDomainModel())
        }
    }

    @Test
    fun `active persisted session and unreadable persisted state block writes`() = runTest {
        fixture { db ->
            apply(db)
            val before = snapshotRows(db)
            val active = sampleActiveRecord()
            val activeCoordinator = ActiveSessionCoordinatorCore(PersistedState(active), FixedClock())
            assertTrue(store(db, activeCoordinator).importJson(BackupCodec.encode(sampleBackup())) is BackupImportResult.Failure)
            assertEquals(before, snapshotRows(db))

            val unreadable = PersistedState(active).apply { failRead = true }
            val unreadableCoordinator = ActiveSessionCoordinatorCore(unreadable, FixedClock())
            assertTrue(store(db, unreadableCoordinator).importJson(BackupCodec.encode(sampleBackup())) is BackupImportResult.Failure)
            assertEquals(before, snapshotRows(db))
            assertEquals(active, unreadable.record)
        }
    }

    @Test
    fun `import probe does not move database work off the caller thread onto main`() = runTest {
        fixture { db ->
            val imported = async(Dispatchers.Default) {
                store(db).importJson(BackupCodec.encode(sampleBackup()))
            }
            assertTrue(imported.await() is BackupImportResult.Success)
        }
    }

    private fun sampleActiveRecord() = ActiveSessionRecord(
        activeSession = ActiveSession(
            sessionId = "active-store",
            kind = ActiveSessionKind.FOCUS,
            subjectId = "math",
            subjectName = "数学",
            startedAtEpochMs = epoch
        ),
        timerSnapshot = TimerSnapshot(
            phase = TimerPhase.RUNNING,
            startedAtEpochMs = epoch,
            resumedAtMonotonicMs = 10_000L
        ),
        bootId = "boot-store-test"
    )

    private suspend fun snapshotRows(db: YanjiDatabase): List<String> = listOf(
        db.focusSessionDao().getAllOnce().toString(),
        db.examSessionDao().getAllOnce().toString(),
        db.noteEntryDao().getAllOnce().toString(),
        db.chatSessionDao().getAll().first().toString(),
        db.chatMessageDao().getAll().first().toString(),
        db.checkInDao().getAllOnce().toString(),
        db.quickStartPresetDao().getAllFlow().first().toString(),
        db.studyTaskDao().getAllFlow().first().toString(),
        db.subjectDao().getAll().toString(),
        db.userSettingsDao().getSettings().first().toString()
    )
}
