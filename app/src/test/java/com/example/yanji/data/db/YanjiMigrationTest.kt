package com.example.yanji.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 数据库迁移链测试（1 → 8 全链 + 7 → 8 单跳），纯 JVM 运行，不依赖设备。
 *
 * ## 为什么起点 schema 手写、终点 schema 用真实导出
 *
 * 仓库里只保留了 Room 实际导出的 `7.json` 与 `8.json`，没有 1~6 的历史 JSON
 * （Room 的 `exportSchema` 只写"当前版本"这一份，历史版本必须随代码一起提交才能留存）。
 * 因此：
 *  - **起点**（v1）用手写 DDL 构造，与 `MIGRATION_1_2` / `MIGRATION_3_4` 注释记录的
 *    历史结构一致（`chat_messages` 曾用 `text` + `isThinking` 列）；
 *  - **终点**用 Room 导出的真实 `8.json` 做结构校验 —— 这是最关键的一环：
 *    它能抓住"迁移写出的表结构 Room 打不开"这类只会在用户升级时爆炸的问题。
 *
 * ## 覆盖的回归点
 *  1. 全链迁移不丢用户数据；
 *  2. `MIGRATION_3_4` 修复历史 `text`/`isThinking` 结构后消息仍可读；
 *  3. v8 真的删掉了 `user_settings.aiApiKey` 与 `journal_entries.studyDurationSeconds`；
 *  4. 迁移过程会把 v7 的明文 API Key 抢救出来交给回调（由上层加密落盘）；
 *  5. **迁移不会凭空造数据**：v7 里本来是空的业务表，迁移后仍然为空。
 */
class YanjiMigrationTest {

    /** JUnit 4 每个测试方法都会新建一个实例，因此这里天然是"每个用例一个干净的目录"。 */
    private val dbDir: Path = Files.createTempDirectory("yanji-migration")

    private val dbFile: Path = dbDir.resolve("migration-test.db")

    private val driver = BundledSQLiteDriver()

    /** 与 `YanjiDatabase` 的 `@Database(version = ...)` 保持一致。 */
    private val CURRENT_VERSION = 12

    /**
     * 注意 JVM 版 `MigrationTestHelper` 的构造参数顺序是
     * **(schemaDirectoryPath, databasePath, driver, databaseClass, databaseFactory, specs)**，
     * 且 `databaseFactory` 会在构造时被立即调用并 cast 到 `databaseClass`
     * ——所以它必须返回一个真实实例（生成的 `_Impl`），不能抛异常。
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        File("schemas").toPath(),
        dbFile,
        driver,
        YanjiDatabase::class,
        { YanjiDatabase_Impl() },
        emptyList()
    )

    // ---------------------------------------------------------------- v1 起点

    /**
     * v1 基线结构。与历史一致：
     *  - `focus_sessions` 尚无 `pauseCount` / `mode`（MIGRATION_2_3 添加）
     *  - `chat_messages` 使用 `text` + `isThinking`（MIGRATION_3_4 修复）
     *  - `journal_entries` 尚有 `studyDurationSeconds`（v8 移除）
     *  - `user_settings` 尚有 `aiApiKey`（v8 移除）
     */
    private val v1Ddl: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS focus_sessions (
            id TEXT NOT NULL PRIMARY KEY,
            subjectId TEXT NOT NULL,
            subjectName TEXT NOT NULL,
            startTime INTEGER NOT NULL,
            endTime INTEGER NOT NULL,
            durationSeconds INTEGER NOT NULL,
            pausedDurationSeconds INTEGER NOT NULL,
            note TEXT NOT NULL,
            status TEXT NOT NULL,
            createdAt INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS exam_sessions (
            id TEXT NOT NULL PRIMARY KEY,
            subjectId TEXT NOT NULL,
            subjectName TEXT NOT NULL,
            plannedDurationSeconds INTEGER NOT NULL,
            actualDurationSeconds INTEGER NOT NULL,
            startTime INTEGER NOT NULL,
            endTime INTEGER NOT NULL,
            score REAL,
            maxScore REAL NOT NULL,
            note TEXT NOT NULL,
            status TEXT NOT NULL,
            createdAt INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS journal_entries (
            id TEXT NOT NULL PRIMARY KEY,
            date TEXT NOT NULL,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            moodScore INTEGER NOT NULL,
            energyScore INTEGER NOT NULL,
            studySatisfaction INTEGER NOT NULL,
            tomorrowPlan TEXT NOT NULL,
            studyDurationSeconds INTEGER NOT NULL,
            tags TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS user_settings (
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
            aiApiKey TEXT NOT NULL,
            aiModel TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS chat_messages (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            sender TEXT NOT NULL,
            text TEXT NOT NULL,
            isThinking INTEGER NOT NULL DEFAULT 0,
            timestamp INTEGER NOT NULL
        )
        """.trimIndent()
    )

    // ---------------------------------------------------------------- v7 起点

    /** v7 起点结构：直接由真实 7.json 的 createSql 还原，含两张表在 v8 中会被删掉的列。 */
    private val v7Ddl: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `pausedDurationSeconds` INTEGER NOT NULL, `pauseCount` INTEGER NOT NULL, `mode` TEXT NOT NULL, `note` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `exam_sessions` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `plannedDurationSeconds` INTEGER NOT NULL, `actualDurationSeconds` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `score` REAL, `maxScore` REAL NOT NULL, `note` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `moodScore` INTEGER NOT NULL, `energyScore` INTEGER NOT NULL, `studySatisfaction` INTEGER NOT NULL, `tomorrowPlan` TEXT NOT NULL, `studyDurationSeconds` INTEGER NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `user_settings` (`id` INTEGER NOT NULL, `targetExamDate` TEXT NOT NULL, `targetSchool` TEXT NOT NULL, `targetMajor` TEXT NOT NULL, `dailyGoalHours` REAL NOT NULL, `validStudyThresholdMinutes` INTEGER NOT NULL, `defaultSubjectId` TEXT NOT NULL, `soundEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `aiProvider` TEXT NOT NULL, `aiBaseUrl` TEXT NOT NULL, `aiApiKey` TEXT NOT NULL, `aiModel` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `sender` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `model` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `check_ins` (`date` TEXT NOT NULL, `checkInTime` INTEGER NOT NULL, `streak` INTEGER NOT NULL, `note` TEXT NOT NULL, `mood` TEXT NOT NULL, PRIMARY KEY(`date`))",
        "CREATE TABLE IF NOT EXISTS `unlocked_achievements` (`id` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `quick_start_presets` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `label` TEXT NOT NULL, `subLabel` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `mode` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    )

    /**
     * v2 起才存在的表（由 MIGRATION_1_2 创建）。
     * 当测试直接从 v2 / v3 起跳时，MIGRATION_1_2 不会再执行，必须手工补上这张表，
     * 否则终点结构无法通过 8.json 校验。
     */
    private val chatSessionsDdl: String =
        "CREATE TABLE IF NOT EXISTS chat_sessions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, model TEXT NOT NULL)"

    private fun allMigrations(legacyKeySink: (String) -> Unit = {}): List<Migration> = listOf(
        YanjiDatabase.MIGRATION_1_2,
        YanjiDatabase.MIGRATION_2_3,
        YanjiDatabase.MIGRATION_3_4,
        YanjiDatabase.MIGRATION_4_5,
        YanjiDatabase.MIGRATION_5_6,
        YanjiDatabase.MIGRATION_6_7,
        YanjiDatabase.migration7to8(legacyKeySink),
        YanjiDatabase.MIGRATION_8_9,
        YanjiDatabase.MIGRATION_9_10,
        YanjiDatabase.MIGRATION_10_11,
        YanjiDatabase.MIGRATION_11_12
    )

    /** 用驱动直接把手工 DDL + 种子数据写进目标文件，并把 user_version 设成 [version]。 */
    private fun seedRawDatabase(version: Int, ddl: List<String>, statements: List<String> = emptyList()) {
        driver.open(dbFile.toString()).use { conn ->
            (ddl + statements).forEach { sql ->
                conn.prepare(sql).use { it.step() }
            }
            conn.prepare("PRAGMA user_version = $version").use { it.step() }
        }
    }

    // ---------------------------------------------------------------- 全链

    @Test
    fun migrate1ToLatest_fullChainPreservesUserDataAndValidatesFinalSchema() {
        seedRawDatabase(
            version = 1,
            ddl = v1Ddl,
            statements = listOf(
                "INSERT INTO focus_sessions VALUES ('fs1','math','高等数学',1000,2000,1234,0,'','COMPLETED',1)",
                "INSERT INTO focus_sessions VALUES ('fs2','english','考研英语',1000,2000,5678,0,'','COMPLETED',1)",
                "INSERT INTO exam_sessions VALUES ('es1','math','数学一',10800,10500,1000,2000,126.0,150.0,'中值定理失分','COMPLETED',1)",
                "INSERT INTO journal_entries VALUES ('j1','2026-09-05','标题','正文',5,4,5,'计划',27120,'标签',1,2)",
                "INSERT INTO user_settings VALUES (1,'2026-12-19','浙大','电子信息',10.0,30,'math_advanced',1,1,'DeepSeek','https://api.deepseek.com/v1','sk-legacy-v1','deepseek-chat')",
                "INSERT INTO chat_messages VALUES ('m1','s1','USER','1→最新 的遗留消息',0,99)"
            )
        )

        val legacyKeys = mutableListOf<String>()
        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, allMigrations { legacyKeys += it })

        // 1) 学习记录全保留
        assertEquals(2, db.intValue("SELECT COUNT(*) FROM focus_sessions"))
        assertEquals("高等数学", db.textValue("SELECT subjectName FROM focus_sessions WHERE id='fs1'"))
        assertEquals(1234L, db.longValue("SELECT durationSeconds FROM focus_sessions WHERE id='fs1'"))
        assertEquals(126.0, db.doubleValue("SELECT score FROM exam_sessions WHERE id='es1'"), 0.001)
        assertEquals("正文", db.textValue("SELECT content FROM journal_entries WHERE id='j1'"))
        // 日记的 studyDurationSeconds 在 v8 被移除，唯一需要保证的是记录本身与其余字段没丢
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries WHERE id='j1'"))
        assertEquals(5, db.intValue("SELECT moodScore FROM journal_entries WHERE id='j1'"))

        // 2) 历史 chat_messages 经 text→content 修复后仍可读
        assertEquals("1→最新 的遗留消息", db.textValue("SELECT content FROM chat_messages WHERE id='m1'"))

        // 3) v8 的两处 P0 修正真的生效
        assertFalse("user_settings 不应再有 aiApiKey 列", "aiApiKey" in db.columnNames("user_settings"))
        assertFalse(
            "journal_entries 不应再有 studyDurationSeconds 列",
            "studyDurationSeconds" in db.columnNames("journal_entries")
        )

        // 4) v7 的明文 API Key 被抢救出来交给回调（测试里不落盘）
        assertEquals(listOf("sk-legacy-v1"), legacyKeys)

        // 5) v9 的索引已建立（Room 的 schema 校验会覆盖索引，这里再做一次显式断言便于定位）
        assertTrue(
            "focus_sessions.startTime 索引缺失",
            "index_focus_sessions_startTime" in db.indexNames("focus_sessions")
        )
        assertTrue(
            "chat_messages.sessionId 索引缺失",
            "index_chat_messages_sessionId" in db.indexNames("chat_messages")
        )

        // 5.5) v10 的 blockers 列存在且旧行回填空串
        assertTrue("journal_entries blockers 列缺失", "blockers" in db.columnNames("journal_entries"))
        assertEquals("", db.textValue("SELECT blockers FROM journal_entries WHERE id='j1'"))

        // 6) 其余设置字段未被迁移破坏
        assertEquals("浙大", db.textValue("SELECT targetSchool FROM user_settings WHERE id=1"))
        assertEquals("deepseek-chat", db.textValue("SELECT aiModel FROM user_settings WHERE id=1"))

        db.close()
    }

    @Test
    fun migrate3To4_repairsLegacyChatColumnWithoutLosingMessages() {
        seedRawDatabase(
            version = 3,
            ddl = v1Ddl + listOf(
                chatSessionsDdl,
                "ALTER TABLE focus_sessions ADD COLUMN pauseCount INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE focus_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT '正向计时'"
            ),
            statements = listOf(
                "INSERT INTO chat_messages VALUES ('m1','s1','USER','遗留消息',0,7)"
            )
        )

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, allMigrations())

        assertEquals("遗留消息", db.textValue("SELECT content FROM chat_messages WHERE id='m1'"))
        assertEquals(7L, db.longValue("SELECT timestamp FROM chat_messages WHERE id='m1'"))
        val columns = db.columnNames("chat_messages")
        assertTrue("content" in columns)
        assertFalse("text" in columns)
        assertFalse("isThinking" in columns)
        db.close()
    }

    @Test
    fun migrate2To3_addsPauseColumnsWithDefaults() {
        seedRawDatabase(
            version = 2,
            ddl = v1Ddl + listOf(chatSessionsDdl),
            statements = listOf(
                "INSERT INTO focus_sessions VALUES ('fs1','math','高等数学',1000,2000,1000,0,'','COMPLETED',1)"
            )
        )

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, allMigrations())

        assertEquals(0, db.intValue("SELECT pauseCount FROM focus_sessions WHERE id='fs1'"))
        assertEquals("正向计时", db.textValue("SELECT mode FROM focus_sessions WHERE id='fs1'"))
        db.close()
    }

    // ---------------------------------------------------------------- 7 → 8 单跳

    @Test
    fun migrate7To8_rescuesCredentialAndDropsRedundantColumns() {
        seedRawDatabase(
            version = 7,
            ddl = v7Ddl,
            statements = listOf(
                "INSERT INTO user_settings VALUES (1,'2026-12-19','浙江大学 计算机学院','电子信息 (085400)',10.0,30,'math_advanced',1,1,'DeepSeek','https://api.deepseek.com/v1','sk-legacy-secret','deepseek-chat')",
                "INSERT INTO journal_entries VALUES ('j1','2026-09-05','标题','正文',5,4,5,'计划',27120,'标签',1,2)"
            )
        )

        val legacyKeys = mutableListOf<String>()
        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            listOf(
                YanjiDatabase.migration7to8 { legacyKeys += it },
                YanjiDatabase.MIGRATION_8_9,
                YanjiDatabase.MIGRATION_9_10,
                YanjiDatabase.MIGRATION_10_11,
                YanjiDatabase.MIGRATION_11_12
            )
        )

        // 凭据被读出，用于上层写入 Keystore；列本身必须消失
        assertEquals(listOf("sk-legacy-secret"), legacyKeys)
        assertFalse("aiApiKey" in db.columnNames("user_settings"))
        assertEquals("浙江大学 计算机学院", db.textValue("SELECT targetSchool FROM user_settings WHERE id=1"))

        assertFalse("studyDurationSeconds" in db.columnNames("journal_entries"))
        assertEquals("正文", db.textValue("SELECT content FROM journal_entries WHERE id='j1'"))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries"))

        db.close()
    }

    @Test
    fun migrate7To8_withEmptyLegacyCredentialDoesNotCallSink() {
        seedRawDatabase(
            version = 7,
            ddl = v7Ddl,
            statements = listOf(
                "INSERT INTO user_settings VALUES (1,'2026-12-19','浙大','电子信息',10.0,30,'math_advanced',1,1,'DeepSeek','https://api.deepseek.com/v1','','deepseek-chat')"
            )
        )

        val legacyKeys = mutableListOf<String>()
        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            listOf(
                YanjiDatabase.migration7to8 { legacyKeys += it },
                YanjiDatabase.MIGRATION_8_9,
                YanjiDatabase.MIGRATION_9_10,
                YanjiDatabase.MIGRATION_10_11,
                YanjiDatabase.MIGRATION_11_12
            )
        )

        assertTrue("空凭据不应触发落盘", legacyKeys.isEmpty())
        db.close()
    }

    // ---------------------------------------------------------------- 9 → 10 单跳

    /**
     * v9 起点结构：v7 的表结构经 MIGRATION_7_8 重建后的两张表
     * （journal_entries 移除 studyDurationSeconds、user_settings 移除 aiApiKey）
     * + MIGRATION_8_9 建立的全部索引。
     * 终点 10.json 会校验索引，因此 seed 必须把它们建齐。
     */
    private val v9Ddl: List<String> = v7Ddl.map { ddl ->
        when {
            ddl.startsWith("CREATE TABLE IF NOT EXISTS `journal_entries`") ->
                "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `moodScore` INTEGER NOT NULL, `energyScore` INTEGER NOT NULL, `studySatisfaction` INTEGER NOT NULL, `tomorrowPlan` TEXT NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            ddl.startsWith("CREATE TABLE IF NOT EXISTS `user_settings`") ->
                "CREATE TABLE IF NOT EXISTS `user_settings` (`id` INTEGER NOT NULL, `targetExamDate` TEXT NOT NULL, `targetSchool` TEXT NOT NULL, `targetMajor` TEXT NOT NULL, `dailyGoalHours` REAL NOT NULL, `validStudyThresholdMinutes` INTEGER NOT NULL, `defaultSubjectId` TEXT NOT NULL, `soundEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `aiProvider` TEXT NOT NULL, `aiBaseUrl` TEXT NOT NULL, `aiModel` TEXT NOT NULL, PRIMARY KEY(`id`))"
            else -> ddl
        }
    }

    private val v9IndexDdl: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_focus_sessions_startTime` ON `focus_sessions` (`startTime`)",
        "CREATE INDEX IF NOT EXISTS `index_exam_sessions_startTime` ON `exam_sessions` (`startTime`)",
        "CREATE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_quick_start_presets_sortOrder` ON `quick_start_presets` (`sortOrder`)"
    )

    @Test
    fun migrate9To10_addsBlockersColumnWithBackfilledEmptyString() {
        seedRawDatabase(
            version = 9,
            ddl = v9Ddl + v9IndexDdl,
            statements = listOf(
                "INSERT INTO journal_entries VALUES ('j1','2026-09-05','标题','正文',5,4,5,'计划','标签',1,2)"
            )
        )

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            listOf(YanjiDatabase.MIGRATION_9_10, YanjiDatabase.MIGRATION_10_11, YanjiDatabase.MIGRATION_11_12)
        )

        assertTrue("blockers 列应已存在", "blockers" in db.columnNames("journal_entries"))
        // 旧行回填空串，其余字段原样保留
        assertEquals("", db.textValue("SELECT blockers FROM journal_entries WHERE id='j1'"))
        assertEquals("正文", db.textValue("SELECT content FROM journal_entries WHERE id='j1'"))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        db.close()
    }

    @Test
    fun migrate9To10_emptyJournalTableStaysEmpty() {
        seedRawDatabase(version = 9, ddl = v9Ddl + v9IndexDdl)

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            listOf(YanjiDatabase.MIGRATION_9_10, YanjiDatabase.MIGRATION_10_11, YanjiDatabase.MIGRATION_11_12)
        )

        assertEquals("迁移不应向 journal_entries 写入任何记录", 0, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        db.close()
    }

    // ---------------------------------------------------------------- 11 -> 12 外观设置

    /**
     * v11 起点结构：列结构与 v10 相同（MIGRATION_10_11 只做去重与索引，不动列），
     * 但索引必须是 v11 的最终形态（journal_entries.date 唯一索引 + chat 复合索引）。
     * 这些索引会与 12.json 逐项校验，因此 seed 必须建齐。
     */
    private val v11IndexDdl: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_focus_sessions_startTime` ON `focus_sessions` (`startTime`)",
        "CREATE INDEX IF NOT EXISTS `index_exam_sessions_startTime` ON `exam_sessions` (`startTime`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_timestamp` ON `chat_messages` (`sessionId`, `timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_quick_start_presets_sortOrder` ON `quick_start_presets` (`sortOrder`)"
    )

    @Test
    fun migrate11To12_addsThemeModeColumnDefaultingToSystemAndPreservesSettings() {
        seedRawDatabase(
            version = 11,
            ddl = v10Ddl + v11IndexDdl,
            statements = listOf(
                "INSERT INTO user_settings VALUES (1,'2026-12-19','目标大学','计算机',8.0,30,'math_advanced',1,1,'deepseek','https://api.deepseek.com','deepseek-chat')"
            )
        )

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, listOf(YanjiDatabase.MIGRATION_11_12))

        assertTrue("themeMode 列应已存在", "themeMode" in db.columnNames("user_settings"))
        assertEquals(
            "存量用户升级后必须一律回落到「跟随系统」，保证视觉与升级前一致",
            "SYSTEM",
            db.textValue("SELECT themeMode FROM user_settings WHERE id=1")
        )
        assertEquals("目标大学", db.textValue("SELECT targetSchool FROM user_settings WHERE id=1"))
        assertEquals("2026-12-19", db.textValue("SELECT targetExamDate FROM user_settings WHERE id=1"))
        assertEquals("deepseek-chat", db.textValue("SELECT aiModel FROM user_settings WHERE id=1"))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM user_settings"))
        db.close()
    }

    @Test
    fun migrate11To12_doesNotFabricateASettingsRow() {
        seedRawDatabase(version = 11, ddl = v10Ddl + v11IndexDdl)

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, listOf(YanjiDatabase.MIGRATION_11_12))

        assertEquals("迁移不应凭空创建 settings 行", 0, db.intValue("SELECT COUNT(*) FROM user_settings"))
        db.close()
    }

    // ---------------------------------------------------------------- 不造数据

    @Test
    fun migration_neverFabricatesBusinessRows() {
        // v7 库里业务表本来就是空的（用户主动删空 / 全新建库）
        seedRawDatabase(version = 7, ddl = v7Ddl)

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, allMigrations())

        listOf(
            "focus_sessions", "exam_sessions", "journal_entries",
            "chat_messages", "chat_sessions", "check_ins",
            "unlocked_achievements", "quick_start_presets"
        ).forEach { table ->
            assertEquals("迁移不应向 $table 写入任何记录", 0, db.intValue("SELECT COUNT(*) FROM $table"))
        }
        db.close()
    }

    // ---------------------------------------------------------------- 10 -> 11 duplicate cleanup

    private val v10Ddl: List<String> = v9Ddl.map { ddl ->
        if (ddl.startsWith("CREATE TABLE IF NOT EXISTS `journal_entries`")) {
            "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `moodScore` INTEGER NOT NULL, `energyScore` INTEGER NOT NULL, `studySatisfaction` INTEGER NOT NULL, `tomorrowPlan` TEXT NOT NULL, `blockers` TEXT NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        } else {
            ddl
        }
    }

    @Test
    fun migrate10To11_deduplicatesByUpdatedCreatedAndIdThenEnforcesUniqueDate() {
        seedRawDatabase(
            version = 10,
            ddl = v10Ddl + v9IndexDdl,
            statements = listOf(
                // updatedAt wins first.
                "INSERT INTO journal_entries VALUES ('old','2026-09-01','old','','3','3','3','','','','10','100')",
                "INSERT INTO journal_entries VALUES ('updated','2026-09-01','updated','','3','3','3','','','','1','101')",
                // createdAt breaks an updatedAt tie.
                "INSERT INTO journal_entries VALUES ('created-old','2026-09-02','created-old','','3','3','3','','','','10','200')",
                "INSERT INTO journal_entries VALUES ('created-new','2026-09-02','created-new','','3','3','3','','','','11','200')",
                // id DESC breaks the final tie.
                "INSERT INTO journal_entries VALUES ('a','2026-09-03','a','','3','3','3','','','','20','300')",
                "INSERT INTO journal_entries VALUES ('z','2026-09-03','z','','3','3','3','','','','20','300')",
                "INSERT INTO journal_entries VALUES ('single','2026-09-04','single','','3','3','3','','','','30','400')"
            )
        )

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, listOf(YanjiDatabase.MIGRATION_10_11, YanjiDatabase.MIGRATION_11_12))

        assertEquals(4, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        assertEquals("updated", db.textValue("SELECT id FROM journal_entries WHERE date='2026-09-01'"))
        assertEquals("created-new", db.textValue("SELECT id FROM journal_entries WHERE date='2026-09-02'"))
        assertEquals("z", db.textValue("SELECT id FROM journal_entries WHERE date='2026-09-03'"))
        assertEquals("single", db.textValue("SELECT id FROM journal_entries WHERE date='2026-09-04'"))
        assertEquals(
            1,
            db.intValue(
                "SELECT `unique` FROM pragma_index_list('journal_entries') " +
                    "WHERE name='index_journal_entries_date'"
            )
        )

        val duplicateInsert = runCatching {
            db.prepare(
                "INSERT INTO journal_entries VALUES ('duplicate','2026-09-04','','','3','3','3','','','','31','401')"
            ).use { it.step() }
        }
        assertTrue("database must reject a second journal for the same date", duplicateInsert.isFailure)
        db.close()
    }

    // ---------------------------------------------------------------- SQL 小工具

    private fun SQLiteConnection.intValue(sql: String): Int =
        prepare(sql).use { it.step(); it.getInt(0) }

    private fun SQLiteConnection.longValue(sql: String): Long =
        prepare(sql).use { it.step(); it.getLong(0) }

    private fun SQLiteConnection.doubleValue(sql: String): Double =
        prepare(sql).use { it.step(); it.getDouble(0) }

    private fun SQLiteConnection.textValue(sql: String): String? =
        prepare(sql).use { if (it.step()) it.getText(0) else null }

    private fun SQLiteConnection.indexNames(table: String): Set<String> =
        prepare("PRAGMA index_list(`$table`)").use { statement ->
            val names = mutableSetOf<String>()
            val nameIndex = statement.getColumnNames().indexOf("name")
            while (statement.step()) {
                if (nameIndex >= 0) names.add(statement.getText(nameIndex))
            }
            names
        }

    private fun SQLiteConnection.columnNames(table: String): Set<String> =
        prepare("PRAGMA table_info(`$table`)").use { statement ->
            val names = mutableSetOf<String>()
            while (statement.step()) {
                names.add(statement.getText(1))
            }
            names
        }
}
