package com.example.yanji.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.data.db.CheckInEntity
import com.example.yanji.data.db.ChatMessageEntity
import com.example.yanji.data.db.ChatSessionEntity
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.QuickStartPresetEntity
import com.example.yanji.data.db.UnlockedAchievementEntity
import com.example.yanji.data.db.UserSettingsEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「不写假数据」这条不变量的真机回归测试。
 *
 * 对应审计报告里最危险的那个组合风险：
 * ```
 * 空数据库 → Repository 认为"需要初始化" → 写入样例数据
 *        → 用户真实数据被删空后，一重启假记录就"复活"并污染统计
 * ```
 *
 * 这里直接对着一次性数据库跑 [AppInitializer]，把「空表是合法状态」钉成断言。
 */
@RunWith(AndroidJUnit4::class)
class AppInitializerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: YanjiDatabase
    private lateinit var dbName: String

    private val epoch = 1_757_000_000_000L

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dbName = "instrumented-init-${System.nanoTime()}.db"
        db = Room.databaseBuilder(context, YanjiDatabase::class.java, dbName)
            .addMigrations(*YanjiDatabase.migrations(context))
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun init() = AppInitializer(db.userSettingsDao()).initialize()

    private suspend fun businessRowCount(): Int =
        db.focusSessionDao().count() +
            db.examSessionDao().count() +
            db.noteEntryDao().count() +
            db.chatMessageDao().count() +
            db.chatSessionDao().count() +
            db.checkInDao().count() +
            db.achievementDao().getAllFlow().first().size +
            db.quickStartPresetDao().count()

    @Test
    fun freshInstallWritesOnlyDefaultSettings() = runBlocking {
        init()

        assertEquals("应写入一条默认设置", 1, db.userSettingsDao().count())
        assertEquals("绝不允许写入任何业务记录", 0, businessRowCount())
    }

    @Test
    fun initializeIsIdempotent() = runBlocking {
        init()
        init()
        init()

        assertEquals("重复初始化不应产生多条设置", 1, db.userSettingsDao().count())
        assertEquals(0, businessRowCount())
    }

    @Test
    fun clearingAllLearningDataThenRestartingKeepsItEmpty() = runBlocking {
        // 1) 用户先在每一张业务表中都有记录。
        db.focusSessionDao().insert(
            FocusSessionEntity.fromDomainModel(
                FocusSession(
                    id = "fs-1",
                    subjectId = "math_advanced",
                    subjectName = "高等数学",
                    startTime = epoch,
                    endTime = epoch + 1000,
                    durationSeconds = 1000
                )
            )
        )
        db.noteEntryDao().insert(
            NoteEntryEntity.fromDomainModel(NoteEntry(id = "j-1", date = "2026-09-05"))
        )
        db.examSessionDao().insert(
            ExamSessionEntity.fromDomainModel(
                ExamSession(
                    id = "exam-1",
                    subjectId = "math_advanced",
                    subjectName = "高等数学",
                    startTime = epoch,
                    endTime = epoch + 1000
                )
            )
        )
        db.chatSessionDao().insert(
            ChatSessionEntity(id = "chat-1", title = "测试", createdAt = epoch, updatedAt = epoch, model = "test")
        )
        db.chatMessageDao().insert(
            ChatMessageEntity(id = "message-1", sessionId = "chat-1", sender = "USER", content = "测试", timestamp = epoch)
        )
        db.checkInDao().insert(CheckInEntity.fromDomainModel(CheckIn(date = "2026-09-11", checkInTime = epoch)))
        db.achievementDao().unlock(UnlockedAchievementEntity(id = "achievement-1", unlockedAt = epoch))
        db.quickStartPresetDao().insert(
            QuickStartPresetEntity(id = "quick-start-1", label = "兼容墓碑测试", createdAt = epoch)
        )
        init()

        assertEquals("测试前置必须覆盖全部业务表", 8, businessRowCount())

        // 2) 用户主动清空全部业务记录（设置保留）。
        db.focusSessionDao().deleteAll()
        db.examSessionDao().deleteAll()
        db.noteEntryDao().deleteAll()
        db.chatMessageDao().clearAll()
        db.chatSessionDao().clearAll()
        db.checkInDao().deleteAll()
        db.achievementDao().deleteAll()
        db.quickStartPresetDao().deleteAll()

        // 3) 重启（再跑一次初始化）
        init()

        // 4) 每一张业务表都必须保持为空 —— 这是本测试存在的唯一理由。
        assertEquals("清空后重启不得复活任何业务记录", 0, businessRowCount())
        assertEquals("默认设置仍应保留", 1, db.userSettingsDao().count())
    }

    @Test
    fun initializeDoesNotOverwriteExistingSettings() = runBlocking {
        db.userSettingsDao().saveSettings(
            UserSettingsEntity.fromDomainModel(
                UserSettings(targetSchool = "北京大学 信息科学技术学院", targetExamDate = "2027-01-10")
            )
        )

        init()

        val settings = db.userSettingsDao().getSettings().first()!!
        assertEquals("北京大学 信息科学技术学院", settings.targetSchool)
        assertEquals("2027-01-10", settings.targetExamDate)
        assertEquals(1, db.userSettingsDao().count())
    }
}
