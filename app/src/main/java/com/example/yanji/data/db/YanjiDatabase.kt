package com.example.yanji.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.example.yanji.data.security.SecretStore
import java.io.File

@Database(
    entities = [
        FocusSessionEntity::class,
        ExamSessionEntity::class,
        JournalEntryEntity::class,
        UserSettingsEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        CheckInEntity::class,
        UnlockedAchievementEntity::class,
        QuickStartPresetEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class YanjiDatabase : RoomDatabase() {

    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun examSessionDao(): ExamSessionDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun checkInDao(): CheckInDao
    abstract fun achievementDao(): AchievementDao
    abstract fun quickStartPresetDao(): QuickStartPresetDao

    companion object {

        @Volatile
        private var instance: YanjiDatabase? = null

        // ------------------------------------------------------------------
        // 迁移统一使用 `migrate(connection: SQLiteConnection)` 而不是已被废弃的
        // `migrate(db: SupportSQLiteDatabase)`。
        //
        // 原因（Room 2.8 的实际实现）：
        //  - Android 变体的 `Migration` 同时声明了两个重载，其中 `migrate(SQLiteConnection)`
        //    只在 connection 是 `SupportSQLiteConnection` 时才桥接到 SupportSQLiteDatabase 版本，
        //    否则直接抛 NotImplementedError；
        //  - JVM 变体的 `Migration` **只有** `migrate(SQLiteConnection)`。
        //
        // 也就是说，只覆写 SupportSQLiteDatabase 版本的迁移，在带上 SQLiteDriver 的运行环境
        // （Room 的 KMP / JVM 迁移测试）里会被静默跳过——测试会通过但生产会出错，反之亦然。
        // 统一覆写 SQLiteConnection 版本可以在真机与 JVM 测试上走同一条代码路径。
        // ------------------------------------------------------------------

        private const val CHAT_MESSAGE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "sessionId TEXT NOT NULL, " +
                "sender TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL)"

        /** 执行一条不返回结果集的语句。 */
        private fun SQLiteConnection.exec(sql: String) {
            prepare(sql).use { it.step() }
        }

        /** 读取 `PRAGMA table_info(<table>)` 的列名集合。 */
        private fun SQLiteConnection.tableColumns(table: String): Set<String> {
            val names = mutableSetOf<String>()
            prepare("PRAGMA table_info(`$table`)").use { statement ->
                val nameIndex = statement.getColumnNames().indexOf("name")
                while (statement.step()) {
                    if (nameIndex >= 0) names.add(statement.getText(nameIndex))
                }
            }
            return names
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        model TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                // chat_messages 的列必须与 ChatMessageEntity 完全一致：
                //   - 列名是 `content`（不是 `text`）
                //   - 没有 `isThinking` 列
                // 历史版本这里错用了 `text` + `isThinking`，导致从 v1 升级上来的设备
                // 打开数据库时 Room schema 校验失败。MIGRATION_3_4 仍会兜底修复。
                connection.exec(CHAT_MESSAGE_TABLE_SQL)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec("ALTER TABLE focus_sessions ADD COLUMN pauseCount INTEGER NOT NULL DEFAULT 0")
                connection.exec("ALTER TABLE focus_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT '正向计时'")
            }
        }

        /**
         * 把 `chat_messages` 修复成与 [ChatMessageEntity] 一致的结构。
         *
         * 覆盖两条升级路径：
         *  - `v1 → MIGRATION_1_2 → 2 → 3 → 这里`（1_2 已建对表，这里通常是 no-op）
         *  - 「已经跑到 v3、但表还是 `text` + `isThinking` 的坏结构」→ 这里重建修复
         *
         * 用「建新表 → 拷贝 → 删旧表 → 改名」而不是 `ALTER TABLE ... DROP COLUMN`：
         * `DROP COLUMN` 需要 SQLite ≥ 3.35，而本项目 `minSdk 24` 的设备内置 SQLite 远低于此，
         * 在真机上会直接抛异常。重建方案在所有受支持版本上都可用。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                val columns = connection.tableColumns("chat_messages")
                val expected = setOf("id", "sessionId", "sender", "content", "timestamp")
                if (columns == expected) return

                val contentExpression = when {
                    "content" in columns -> "content"
                    "text" in columns -> "text"
                    else -> "''"
                }
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS chat_messages_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "sessionId TEXT NOT NULL, " +
                        "sender TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
                connection.exec(
                    "INSERT INTO chat_messages_new (id, sessionId, sender, content, timestamp) " +
                        "SELECT id, sessionId, sender, $contentExpression, timestamp FROM chat_messages"
                )
                connection.exec("DROP TABLE chat_messages")
                connection.exec("ALTER TABLE chat_messages_new RENAME TO chat_messages")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS check_ins (
                        date TEXT NOT NULL PRIMARY KEY,
                        checkInTime INTEGER NOT NULL,
                        streak INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        mood TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS unlocked_achievements (
                        id TEXT NOT NULL PRIMARY KEY,
                        unlockedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS quick_start_presets (
                        id TEXT NOT NULL PRIMARY KEY,
                        label TEXT NOT NULL,
                        subjectId TEXT NOT NULL,
                        subjectName TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // 快捷操作支持内置模板：为 v6 的 quick_start_presets 补 type / subLabel 两列。
        // MIGRATION_5_6 建表时不含这两列（保持当时的 schema），因此 5→6→7 链与 6→7 链都正确。
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec("ALTER TABLE quick_start_presets ADD COLUMN type TEXT NOT NULL DEFAULT 'custom'")
                connection.exec("ALTER TABLE quick_start_presets ADD COLUMN subLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v8：两处 P0 数据语义修正。
         *
         * 1. `user_settings.aiApiKey` 移除。凭据不应与可云备份的学习数据共享备份生命周期，
         *    改由 [com.example.yanji.data.security.SecretStore] 加密保存在 no-backup 目录。
         *    迁移时先把明文抢救出来交给 [persistLegacyApiKey]，由上层在首启时加密落盘后删除。
         * 2. `journal_entries.studyDurationSeconds` 移除。该字段是学习时长的第二事实来源，
         *    会让"真实统计为 0"这一合法状态回退到陈旧副本；学习时长统一改为实时聚合查询。
         *
         * 两张表都按「建新表 → 拷贝 → 删旧表 → 改名」重建，理由同 MIGRATION_3_4
         * （`DROP COLUMN` 在 minSdk 24 真机上不可用）。
         */
        fun migration7to8(persistLegacyApiKey: (String) -> Unit = {}): Migration = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                // ---- 1. 抢救明文 API Key（必须在重建 user_settings 之前读取）----
                val legacyKey = runCatching {
                    connection.prepare("SELECT aiApiKey FROM user_settings WHERE id = 1").use { statement ->
                        if (statement.step()) statement.getText(0).orEmpty() else ""
                    }
                }.getOrDefault("")

                // ---- 2. user_settings: 去掉 aiApiKey 列 ----
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS user_settings_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        targetExamDate TEXT NOT NULL,
                        targetSchool TEXT NOT NULL,
                        targetMajor TEXT NOT NULL,
                        dailyGoalHours REAL NOT NULL,
                        validStudyThresholdMinutes INTEGER NOT NULL,
                        defaultSubjectId TEXT NOT NULL,
                        soundEnabled INTEGER NOT NULL,
                        vibrationEnabled INTEGER NOT NULL,
                        aiProvider TEXT NOT NULL,
                        aiBaseUrl TEXT NOT NULL,
                        aiModel TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                connection.exec(
                    """
                    INSERT INTO user_settings_new (
                        id, targetExamDate, targetSchool, targetMajor, dailyGoalHours,
                        validStudyThresholdMinutes, defaultSubjectId, soundEnabled,
                        vibrationEnabled, aiProvider, aiBaseUrl, aiModel
                    )
                    SELECT
                        id, targetExamDate, targetSchool, targetMajor, dailyGoalHours,
                        validStudyThresholdMinutes, defaultSubjectId, soundEnabled,
                        vibrationEnabled, aiProvider, aiBaseUrl, aiModel
                    FROM user_settings
                    """.trimIndent()
                )
                connection.exec("DROP TABLE user_settings")
                connection.exec("ALTER TABLE user_settings_new RENAME TO user_settings")

                // ---- 3. journal_entries: 去掉 studyDurationSeconds 列 ----
                connection.exec(
                    """
                    CREATE TABLE IF NOT EXISTS journal_entries_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        moodScore INTEGER NOT NULL,
                        energyScore INTEGER NOT NULL,
                        studySatisfaction INTEGER NOT NULL,
                        tomorrowPlan TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.exec(
                    """
                    INSERT INTO journal_entries_new (
                        id, date, title, content, moodScore, energyScore,
                        studySatisfaction, tomorrowPlan, tags, createdAt, updatedAt
                    )
                    SELECT
                        id, date, title, content, moodScore, energyScore,
                        studySatisfaction, tomorrowPlan, tags, createdAt, updatedAt
                    FROM journal_entries
                    """.trimIndent()
                )
                connection.exec("DROP TABLE journal_entries")
                connection.exec("ALTER TABLE journal_entries_new RENAME TO journal_entries")

                // ---- 4. 明文凭据落盘（no-backup 目录，下一版由 SecretStore 加密后删除）----
                if (legacyKey.isNotBlank()) {
                    runCatching { persistLegacyApiKey(legacyKey) }
                }
            }
        }

        /**
         * v9：为高频查询路径补索引。
         *
         * 依据：统计 / 列表页反复按日期与科目扫描整表，聊天按 sessionId 过滤。
         * 数据量增长后这些查询会成为主要热点（明细见审计报告"性能"章节）。
         * 索引名与 Room 在 `9.json` 中导出的完全一致，否则迁移后 schema 校验会失败。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startTime` ON `focus_sessions` (`startTime`)")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_exam_sessions_startTime` ON `exam_sessions` (`startTime`)")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_quick_start_presets_sortOrder` ON `quick_start_presets` (`sortOrder`)")
            }
        }

        /**
         * v10：日记新增「遇到的困难 / 卡点」列。
         *
         * 对应日记编辑页重构（Stitch 设计稿）中的独立卡点输入区。
         * 纯 `ADD COLUMN ... NOT NULL DEFAULT ''`：SQLite 全版本可用，
         * 旧行自动回填空串，不需要重建表。
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    "ALTER TABLE journal_entries ADD COLUMN blockers TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v11: enforce the domain invariant "one journal per calendar date" in SQLite.
         *
         * Older builds only normalized this in repository code, so concurrent writes/imports
         * could leave duplicate dates. Before replacing the old non-unique index, keep exactly
         * one deterministic winner per date: updatedAt DESC, createdAt DESC, id DESC.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    """
                    DELETE FROM journal_entries
                    WHERE EXISTS (
                        SELECT 1
                        FROM journal_entries AS newer
                        WHERE newer.date = journal_entries.date
                          AND (
                              newer.updatedAt > journal_entries.updatedAt
                              OR (newer.updatedAt = journal_entries.updatedAt AND newer.createdAt > journal_entries.createdAt)
                              OR (
                                  newer.updatedAt = journal_entries.updatedAt
                                  AND newer.createdAt = journal_entries.createdAt
                                  AND newer.id > journal_entries.id
                              )
                          )
                    )
                    """.trimIndent()
                )
                connection.exec("DROP INDEX IF EXISTS `index_journal_entries_date`")
                connection.exec(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_date` " +
                        "ON `journal_entries` (`date`)"
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_timestamp` " +
                        "ON `chat_messages` (`sessionId`, `timestamp`)"
                )
            }
        }

        /**
         * 全部历史版本 → 当前版本的迁移集合。
         *
         * **刻意不提供 `fallbackToDestructiveMigration()`**：一旦某个版本的迁移路径缺失，
         * 正确行为是让 Room 直接抛异常暴露问题，而不是静默删除用户真实数据。
         * （Android 官方明确说明该配置会在找不到迁移路径时永久删除表数据。）
         */
        fun migrations(context: Context): Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            migration7to8 { value -> persistLegacyApiKey(context, value) },
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11
        )

        private fun persistLegacyApiKey(context: Context, value: String) {
            runCatching {
                File(context.applicationContext.noBackupFilesDir, SecretStore.LEGACY_FILE_NAME)
                    .writeText(value)
            }
        }

        fun getDatabase(context: Context): YanjiDatabase {
            return instance ?: synchronized(this) {
                val appContext = context.applicationContext
                instance ?: Room.databaseBuilder(
                    appContext,
                    YanjiDatabase::class.java,
                    "yanji_study.db"
                )
                .addMigrations(*migrations(appContext))
                .build()
                .also { instance = it }
            }
        }
    }
}
