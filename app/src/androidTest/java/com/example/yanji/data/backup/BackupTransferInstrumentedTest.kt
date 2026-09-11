package com.example.yanji.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.UserSettings
import com.example.yanji.data.db.CheckInEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.QuickStartPresetEntity
import com.example.yanji.data.db.UserSettingsEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 备份「采集 / 整表替换」的真机插桩测试。
 *
 * 关键设计：**每个用例都建一个一次性的 Room 数据库**，绝不碰用户真实的学习库。
 * 导入是破坏性操作（整表替换），如果直接在 Repository 上测，测试本身就会删掉用户数据。
 */
@RunWith(AndroidJUnit4::class)
class BackupTransferInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: YanjiDatabase
    private lateinit var dbName: String

    private val epoch = 1_757_000_000_000L

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dbName = "instrumented-backup-${System.nanoTime()}.db"
        db = Room.databaseBuilder(context, YanjiDatabase::class.java, dbName)
            .addMigrations(*YanjiDatabase.migrations(context))
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    // ---------------------------------------------------------------- 空库

    @Test
    fun emptyDatabaseCollectsEmptyBackupAndSurvivesRoundTrip() = runBlocking {
        val backup = BackupTransfer.collect(db, appVersionName = "test", now = epoch)

        assertTrue("全新数据库必须采集为空备份", backup.isEmpty)
        assertEquals(0, db.focusSessionDao().count())
        assertEquals(0, db.examSessionDao().count())
        assertEquals(0, db.journalEntryDao().count())

        // 空备份 → JSON → 解析，仍然为空；这保证「删空后导出」是合法且可还原的状态
        val json = BackupCodec.encode(backup)
        val decoded = BackupCodec.decode(json)
        assertTrue(decoded is BackupDecodeResult.Success)
        assertTrue((decoded as BackupDecodeResult.Success).backup.isEmpty)
    }

    // ---------------------------------------------------------------- 整表替换

    @Test
    fun applyReplacesExistingRowsInsteadOfMerging() = runBlocking {
        db.focusSessionDao().insertAll(
            listOf(
                FocusSessionEntity.fromDomainModel(focus("fs-old-1", "old-1")),
                FocusSessionEntity.fromDomainModel(focus("fs-old-2", "old-2"))
            )
        )
        assertEquals(2, db.focusSessionDao().count())

        val backup = YanjiBackup(exportedAt = epoch, focusSessions = listOf(focus("fs-new", "new")))
        BackupTransfer.applyInTransaction(db, backup)

        val rows = db.focusSessionDao().getAll().first()
        assertEquals("整表替换后只应剩下备份里的记录", 1, rows.size)
        assertEquals("fs-new", rows.first().id)
        assertEquals("new", rows.first().note)
    }

    @Test
    fun applyWithEmptyBackupClearsBusinessTables() = runBlocking {
        db.focusSessionDao().insert(FocusSessionEntity.fromDomainModel(focus("fs-1", "n")))
        db.checkInDao().insert(CheckInEntity.fromDomainModel(CheckIn(date = "2026-09-11", checkInTime = epoch)))
        db.quickStartPresetDao().insert(QuickStartPresetEntity.fromDomainModel(QuickStartPreset(id = "q-1")))
        db.userSettingsDao().saveSettings(UserSettingsEntity.fromDomainModel(UserSettings()))

        BackupTransfer.applyInTransaction(db, YanjiBackup(exportedAt = epoch))

        assertEquals(0, db.focusSessionDao().count())
        assertEquals(0, db.checkInDao().count())
        assertEquals(0, db.quickStartPresetDao().count())
        // settings 为空表示"备份里没有设置"，此时保留本机设置比清空更安全
        assertEquals(1, db.userSettingsDao().count())
    }

    // ---------------------------------------------------------------- 全量往返

    @Test
    fun fullRoundTripPreservesEveryTable() = runBlocking {
        val source = sampleBackup()
        BackupTransfer.applyInTransaction(db, source)

        // 采集 → JSON → 解析（模拟真实"导出再导入"链路）
        val collected = BackupTransfer.collect(db, appVersionName = "test", now = epoch)
        val json = BackupCodec.encode(collected)
        val restored = (BackupCodec.decode(json) as BackupDecodeResult.Success).backup

        // 写进第二个一次性数据库，验证还原结果
        val secondName = "instrumented-restore-${System.nanoTime()}.db"
        val secondDb = Room.databaseBuilder(context, YanjiDatabase::class.java, secondName)
            .addMigrations(*YanjiDatabase.migrations(context))
            .build()
        try {
            BackupTransfer.applyInTransaction(secondDb, restored)

            assertEquals(1, secondDb.focusSessionDao().count())
            assertEquals(1, secondDb.examSessionDao().count())
            assertEquals(1, secondDb.journalEntryDao().count())
            assertEquals(1, secondDb.chatSessionDao().count())
            assertEquals(2, secondDb.chatMessageDao().count())
            assertEquals(1, secondDb.checkInDao().count())
            assertEquals(2, secondDb.achievementDao().getAllFlow().first().size)
            assertEquals(1, secondDb.quickStartPresetDao().count())
            assertEquals(1, secondDb.userSettingsDao().count())

            val focus = secondDb.focusSessionDao().getById("fs-1")!!.toDomainModel()
            assertEquals("高等数学", focus.subjectName)
            assertEquals(3600L, focus.durationSeconds)
            assertEquals(SessionStatus.COMPLETED, focus.status)
            assertEquals("正向计时", focus.mode)

            val journal = secondDb.journalEntryDao().getByDate("2026-09-05")!!
            assertEquals(listOf("数学突破", "心态平和"), journal.toDomainModel().tags)

            val settings = secondDb.userSettingsDao().getSettings().first()!!.toDomainModel()
            assertEquals("浙江大学 计算机学院", settings.targetSchool)
            assertEquals("", settings.aiApiKey)
        } finally {
            secondDb.close()
            context.deleteDatabase(secondName)
        }
    }

    @Test
    fun exportedJsonFromRealSchemaNeverContainsApiKey() = runBlocking {
        db.userSettingsDao().saveSettings(
            UserSettingsEntity.fromDomainModel(UserSettings(aiApiKey = "sk-should-not-be-exported"))
        )

        val json = BackupCodec.encode(BackupTransfer.collect(db, appVersionName = "test", now = epoch))

        assertFalse(json.contains("aiApiKey"))
        assertFalse(json.contains("sk-should-not-be-exported"))
    }

    // ---------------------------------------------------------------- fixtures

    private fun focus(id: String, note: String) = FocusSession(
        id = id,
        subjectId = "math_advanced",
        subjectName = "高等数学",
        startTime = epoch,
        endTime = epoch + 3_600_000L,
        durationSeconds = 3600,
        pauseCount = 1,
        mode = "正向计时",
        note = note,
        status = SessionStatus.COMPLETED
    )

    private fun sampleBackup() = YanjiBackup(
        exportedAt = epoch,
        appVersionName = "test",
        settings = UserSettingsBackup.fromDomain(
            UserSettings(
                targetSchool = "浙江大学 计算机学院",
                aiApiKey = "sk-must-not-leak",
                aiModel = "deepseek-chat"
            )
        ),
        focusSessions = listOf(focus("fs-1", "二重积分")),
        examSessions = listOf(
            ExamSession(
                id = "es-1",
                subjectId = "math",
                subjectName = "数学一 全真模拟",
                plannedDurationSeconds = 10800,
                actualDurationSeconds = 10500,
                startTime = epoch,
                endTime = epoch + 10_500_000L,
                score = 126.0,
                maxScore = 150.0,
                note = "中值定理失分"
            )
        ),
        journalEntries = listOf(
            JournalEntry(
                id = "j-1",
                date = "2026-09-05",
                title = "渐入佳境",
                content = "今天状态很稳定",
                moodScore = 5,
                tags = listOf("数学突破", "心态平和")
            )
        ),
        chatSessions = listOf(ChatSession(id = "s-1", title = "数学草稿复盘")),
        chatMessages = listOf(
            ChatMessage(id = "m-1", sessionId = "s-1", sender = ChatSender.USER, content = "草稿写乱了"),
            ChatMessage(id = "m-2", sessionId = "s-1", sender = ChatSender.JUANJUAN, content = "试试十字四折法")
        ),
        checkIns = listOf(CheckIn(date = "2026-09-11", checkInTime = epoch, streak = 3, note = "打卡")),
        unlockedAchievements = mapOf("focus_first" to epoch, "streak_7" to epoch + 1),
        quickStartPresets = listOf(
            QuickStartPreset(id = "q-1", type = QuickStartPreset.TYPE_START_FOCUS, label = "开始专注", sortOrder = 0)
        )
    )
}
