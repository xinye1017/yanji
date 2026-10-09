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
 * 数据库迁移链测试（1 → 14 全链 + 各单跳），纯 JVM 运行，不依赖设备。
 *
 * ## 为什么起点 schema 手写、终点 schema 用真实导出
 *
 * 仓库里保留了 Room 实际导出的 `7.json` ～ `14.json`，但没有 1~6 的历史 JSON
 * （Room 的 `exportSchema` 只写"当前版本"这一份，历史版本必须随代码一起提交才能留存）。
 * 因此：
 *  - **起点**（v1）用手写 DDL 构造，与 `MIGRATION_1_2` / `MIGRATION_3_4` 注释记录的
 *    历史结构一致（`chat_messages` 曾用 `text` + `isThinking` 列）；
 *  - **终点**用 Room 导出的真实 `14.json` 做结构校验 —— 这是最关键的一环：
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
    private val CURRENT_VERSION = 22

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
     * 否则终点结构无法通过 14.json 校验。
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
        YanjiDatabase.MIGRATION_11_12,
        YanjiDatabase.MIGRATION_12_13,
        YanjiDatabase.MIGRATION_13_14,
        YanjiDatabase.MIGRATION_14_15,
        YanjiDatabase.MIGRATION_15_16,
        YanjiDatabase.MIGRATION_16_17,
        YanjiDatabase.MIGRATION_17_18,
        YanjiDatabase.MIGRATION_18_19,
        YanjiDatabase.MIGRATION_19_20,
        YanjiDatabase.MIGRATION_20_21,
        YanjiDatabase.MIGRATION_21_22
    )

    /**
     * 从 [fromVersion] 起补全到当前版本的迁移链。
     *
     * 单跳测试只关心自己那一跳的行为，但 `runMigrationsAndValidate` 要求给出**到达终态的
     * 完整路径**。按起始版本切片比按数量切片更不容易错：新增版本时无需改动任何调用点。
     *
     * [override] 用于替换链中带副作用的迁移（如 `migration7to8` 需要测试自己的 sink）。
     */
    private fun chainFrom(fromVersion: Int, override: Migration? = null): List<Migration> =
        allMigrations().filter { it.startVersion >= fromVersion }
            .map { if (override != null && it.startVersion == override.startVersion) override else it }

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
            chainFrom(7, YanjiDatabase.migration7to8 { legacyKeys += it })
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
            chainFrom(7, YanjiDatabase.migration7to8 { legacyKeys += it })
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
            chainFrom(9)
        )

        assertTrue("blockers 列应已存在", "blockers" in db.columnNames("journal_entries"))
        // 旧行回填空串，其余字段原样保留
        assertEquals("", db.textValue("SELECT blockers FROM journal_entries WHERE id='j1'"))
        assertEquals("正文", db.textValue("SELECT content FROM journal_entries WHERE id='j1'"))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        db.close()
    }

    @Test
    fun migrate9To10_emptyNoteTableStaysEmpty() {
        seedRawDatabase(version = 9, ddl = v9Ddl + v9IndexDdl)

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(9)
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

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(11)
        )

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

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(11))

        assertEquals("迁移不应凭空创建 settings 行", 0, db.intValue("SELECT COUNT(*) FROM user_settings"))
        db.close()
    }

    // ---------------------------------------------------------------- 12 -> 13 学习伙伴主题

    @Test
    fun migrate12To13_addsMascotThemeDefaultingToCloudAndPreservesSettings() {
        val db12 = helper.createDatabase(12)
        db12.prepare(
            """
            INSERT INTO user_settings (
                id, targetExamDate, targetSchool, targetMajor, dailyGoalHours,
                validStudyThresholdMinutes, defaultSubjectId, soundEnabled,
                vibrationEnabled, aiProvider, aiBaseUrl, aiModel, themeMode
            ) VALUES (
                1, '2026-12-19', '目标大学', '计算机', 8.0,
                30, 'math_advanced', 1,
                1, 'deepseek', 'https://api.deepseek.com', 'deepseek-chat', 'DARK'
            )
            """.trimIndent()
        ).use { it.step() }
        db12.close()

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(12)
        )

        assertTrue("mascotTheme 列应已存在", "mascotTheme" in db.columnNames("user_settings"))
        assertEquals("CLOUD", db.textValue("SELECT mascotTheme FROM user_settings WHERE id=1"))
        assertEquals("DARK", db.textValue("SELECT themeMode FROM user_settings WHERE id=1"))
        assertEquals("目标大学", db.textValue("SELECT targetSchool FROM user_settings WHERE id=1"))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM user_settings"))
        db.close()
    }

    // ---------------------------------------------------------------- 13 -> 14 学科持久化

    /**
     * 13→14 是全库第一次出现「迁移主动写入业务表内容」。
     *
     * 这是**刻意**的：学科是系统提供的默认配置（相当于内置词典），不是用户产生的记录。
     * 之所以不放在 App 启动时补种，是因为 `AppInitializer` 有一条不变量——「表为空」是合法
     * 业务状态，启动补种会让「用户删光学科」在重启后被悄悄复活。迁移只在升级路径上跑一次，
     * 之后的删除/改名会被如实保留。
     */
    @Test
    fun migrate13To14_createsSubjectsTableSeededWithDefaults() {
        val db13 = helper.createDatabase(13)
        db13.close()

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(13)
        )

        val columns = db.columnNames("subjects")
        assertTrue("subjects 应包含 id 列", "id" in columns)
        assertTrue("subjects 应包含 parentId 列", "parentId" in columns)
        assertTrue("subjects 应包含 sortOrder 列", "sortOrder" in columns)

        // 12 条默认学科（5 个类别 + 7 个子学科）
        assertEquals(12, db.intValue("SELECT COUNT(*) FROM subjects"))
        assertEquals(5, db.intValue("SELECT COUNT(*) FROM subjects WHERE parentId IS NULL"))

        // 默认内容与 SubjectCatalog.defaults 对齐
        assertEquals("数学一", db.textValue("SELECT name FROM subjects WHERE id='math'"))
        assertEquals("math", db.textValue("SELECT parentId FROM subjects WHERE id='math_advanced'"))
        assertEquals(1, db.intValue("SELECT enabled FROM subjects WHERE id='math'"))
        assertEquals(11, db.intValue("SELECT sortOrder FROM subjects WHERE id='math_advanced'"))
        assertEquals("#356AE6", db.textValue("SELECT colorHex FROM subjects WHERE id='math'"))

        // 索引必须建出来，否则 Room 的 TableInfo 校验会失败
        val indexes = db.indexNames("subjects")
        assertTrue("应存在 parentId 索引", "index_subjects_parentId" in indexes)
        assertTrue("应存在 sortOrder 索引", "index_subjects_sortOrder" in indexes)

        db.close()
    }

    @Test
    fun migrate13To14_preservesExistingStudyRecords() {
        val db13 = helper.createDatabase(13)
        db13.prepare(
            "INSERT INTO focus_sessions VALUES " +
                "('fs1','math_advanced','高等数学',1000,2000,3600,0,0,'正向计时','','COMPLETED',1)"
        ).use { it.step() }
        db13.close()

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(13)
        )

        assertEquals(1, db.intValue("SELECT COUNT(*) FROM focus_sessions"))
        assertEquals("高等数学", db.textValue("SELECT subjectName FROM focus_sessions WHERE id='fs1'"))
        db.close()
    }

    // ---------------------------------------------------------------- 14 -> 15 随笔多篇 + 收藏

    /**
     * 14→15 的两个关键不变量：
     *  1. `journal_entries.date` 的 UNIQUE 约束必须被放开——否则同一天的第二篇随笔会被
     *     `@Insert(REPLACE)` 顶掉（这正是「一天一篇」的来源）；
     *  2. 新增的 `isFavorite` 列默认 0，使存量随笔升级后全部为「未收藏」，视觉与升级前一致。
     */
    @Test
    fun migrate14To15_allowsMultipleEntriesPerDayAndAddsFavoriteColumn() {
        val db14 = helper.createDatabase(14)
        db14.close()

        val db = helper.runMigrationsAndValidate(
            CURRENT_VERSION,
            chainFrom(14)
        )

        assertTrue("isFavorite 列应已存在", "isFavorite" in db.columnNames("journal_entries"))

        // 迁移后同一天写入两篇：唯一约束已放开，两篇都必须留存。
        insertNoteRow(db, "j-1", "2026-09-21", "第一篇", 1000L)
        insertNoteRow(db, "j-2", "2026-09-21", "第二篇", 2000L)

        assertEquals(
            "同一天必须允许存在多篇随笔",
            2,
            db.intValue("SELECT COUNT(*) FROM journal_entries WHERE date='2026-09-21'")
        )

        // 存量行默认未收藏
        assertEquals(0, db.intValue("SELECT isFavorite FROM journal_entries WHERE id='j-1'"))

        // 收藏标记可持久化
        db.prepare("UPDATE journal_entries SET isFavorite=1 WHERE id='j-1'").use { it.step() }
        assertEquals(1, db.intValue("SELECT isFavorite FROM journal_entries WHERE id='j-1'"))
        assertEquals(0, db.intValue("SELECT isFavorite FROM journal_entries WHERE id='j-2'"))

        // date 索引仍然存在（降级为普通索引），供按日期分组/排序使用
        assertTrue(
            "date 普通索引必须保留",
            "index_journal_entries_date" in db.indexNames("journal_entries")
        )

        db.close()
    }

    @Test
    fun migrate14To15_preservesExistingNoteContent() {
        val db14 = helper.createDatabase(14)
        db14.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt) VALUES " +
                "('legacy-j','2026-09-20','旧随笔','原始正文',4,4,4,'','','复盘',111,222)"
        ).use { it.step() }
        db14.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(14))

        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        assertEquals("原始正文", db.textValue("SELECT content FROM journal_entries WHERE id='legacy-j'"))
        assertEquals("旧随笔", db.textValue("SELECT title FROM journal_entries WHERE id='legacy-j'"))
        assertEquals(111L, db.longValue("SELECT createdAt FROM journal_entries WHERE id='legacy-j'"))
        assertEquals(0, db.intValue("SELECT isFavorite FROM journal_entries WHERE id='legacy-j'"))
        assertEquals("存量随笔迁移到最新版默认非草稿", 0, db.intValue("SELECT isDraft FROM journal_entries WHERE id='legacy-j'"))
        db.close()
    }

    // ---------------------------------------------------------------- 15 -> 16 草稿标记

    @Test
    fun migrate15To16_addsIsDraftColumnWithDefaultZero() {
        val db15 = helper.createDatabase(15)
        db15.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite) VALUES " +
                "('legacy-15','2026-09-21','已保存随笔','正文内容',5,5,5,'','','标签',100,200,1)"
        ).use { it.step() }
        db15.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(15))

        assertEquals(1, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        assertEquals("已保存随笔", db.textValue("SELECT title FROM journal_entries WHERE id='legacy-15'"))
        assertEquals(1, db.intValue("SELECT isFavorite FROM journal_entries WHERE id='legacy-15'"))
        assertEquals(0, db.intValue("SELECT isDraft FROM journal_entries WHERE id='legacy-15'"))

        // 草稿记录可写入 1
        insertNoteRow(db, "draft-1", "2026-09-21", "草稿篇", 300L, isDraft = 1)
        assertEquals(1, db.intValue("SELECT isDraft FROM journal_entries WHERE id='draft-1'"))
        db.close()
    }

    // ---------------------------------------------------------------- 16 -> 17 AI 协议字段

    @Test
    fun migrate16To17_addsAiProtocolColumnWithDefaultOpenAiChat() {
        val db16 = helper.createDatabase(16)
        db16.prepare(
            "INSERT INTO user_settings " +
                "(id, targetExamDate, targetSchool, targetMajor, dailyGoalHours, " +
                "validStudyThresholdMinutes, defaultSubjectId, soundEnabled, " +
                "vibrationEnabled, aiProvider, aiBaseUrl, aiModel, themeMode, mascotTheme) VALUES " +
                "(1, '2026-12-26', '北大', '计算机', 4.0, 30, 'math', 1, 1, 'DeepSeek', 'https://api.deepseek.com/v1', 'deepseek-chat', 'SYSTEM', 'CLOUD')"
        ).use { it.step() }
        db16.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(16))
        assertEquals(1, db.intValue("SELECT COUNT(*) FROM user_settings"))
        assertEquals("OPENAI_CHAT", db.textValue("SELECT aiProtocol FROM user_settings WHERE id=1"))
        db.close()
    }

    // ---------------------------------------------------------------- 17 -> 18 AI 诊断报告落库

    @Test
    fun migrate17To18_createsEmptyAiAnalysesTable() {
        val db17 = helper.createDatabase(17)
        db17.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(17))

        val columns = db.columnNames("ai_analyses")
        assertTrue("ai_analyses 应包含 id 列", "id" in columns)
        assertTrue("ai_analyses 应包含 overview 列", "overview" in columns)
        assertTrue("ai_analyses 应包含 createdAt 列", "createdAt" in columns)
        // 诊断报告是用户行为派生的：迁移只建空表，绝不凭空造报告
        assertEquals(0, db.intValue("SELECT COUNT(*) FROM ai_analyses"))
        db.close()
    }

    // ---------------------------------------------------------------- 18 -> 19 今日学习计划

    @Test
    fun migrate18To19_createsEmptyStudyTasksTable() {
        val db18 = helper.createDatabase(18)
        db18.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(18))

        val columns = db.columnNames("study_tasks")
        assertTrue("study_tasks 应包含 date 列", "date" in columns)
        assertTrue("study_tasks 应包含 plannedMinutes 列", "plannedMinutes" in columns)
        assertTrue("study_tasks 应包含 isCompleted 列", "isCompleted" in columns)
        assertEquals(0, db.intValue("SELECT COUNT(*) FROM study_tasks"))
        db.close()
    }

    // ---------------------------------------------------------------- 19 -> 20 计划归因

    /** v19 的专注记录写入语句（那时还没有 taskId 列）。 */
    private fun insertFocusAt19(
        db: SQLiteConnection,
        id: String,
        durationSeconds: Long,
        status: String = "COMPLETED"
    ) {
        db.prepare(
            "INSERT INTO focus_sessions " +
                "(id, subjectId, subjectName, startTime, endTime, durationSeconds, " +
                "pausedDurationSeconds, pauseCount, mode, note, status, createdAt) VALUES " +
                "('$id','math_linear','线性代数',1000,2000,$durationSeconds,0,0,'25分钟专注','','$status',1)"
        ).use { it.step() }
    }

    /** v20 起带 taskId 的专注记录写入语句。 */
    private fun insertFocusAt20(
        db: SQLiteConnection,
        id: String,
        durationSeconds: Long,
        taskId: String?,
        status: String = "COMPLETED"
    ) {
        val taskExpression = if (taskId == null) "NULL" else "'$taskId'"
        db.prepare(
            "INSERT INTO focus_sessions " +
                "(id, subjectId, subjectName, startTime, endTime, durationSeconds, " +
                "pausedDurationSeconds, pauseCount, mode, note, taskId, status, createdAt) VALUES " +
                "('$id','math_linear','线性代数',1000,2000,$durationSeconds,0,0,'25分钟专注','',$taskExpression,'$status',1)"
        ).use { it.step() }
    }

    /**
     * v20 给 `focus_sessions` 加了 `taskId`，把一次专注挂到「今日计划」的具体某一条上。
     *
     * 三个不变量：
     *  1. 存量行一律为 NULL —— 升级前开始的计时不可能有归属计划，**迁移不编造归因**；
     *  2. 新行可写可读，聚合口径与 [com.example.yanji.data.db.FocusSessionDao.observeTaskActualSeconds]
     *     完全一致（`COMPLETED` 且 `taskId` 非空才计入）；
     *  3. 未完成的时段（RUNNING / PAUSED）与不挂计划的时段都不参与聚合。
     */
    @Test
    fun migrate19To20_addsNullableTaskIdColumnWithoutInventingAttribution() {
        val db19 = helper.createDatabase(19)
        insertFocusAt19(db19, "legacy-1", durationSeconds = 1800L)
        db19.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(19))

        // 1) 列存在，且存量行没有归属
        assertTrue("focus_sessions 应包含 taskId 列", "taskId" in db.columnNames("focus_sessions"))
        assertEquals(
            "迁移不得为存量时段编造归属计划",
            1L,
            db.longValue(
                "SELECT CASE WHEN taskId IS NULL THEN 1 ELSE 0 END " +
                    "FROM focus_sessions WHERE id='legacy-1'"
            )
        )
        assertEquals("存量的其他字段必须原样保留", 1800L, db.longValue("SELECT durationSeconds FROM focus_sessions WHERE id='legacy-1'"))

        // 2) 新增两段挂同一条计划的已完成专注
        insertFocusAt20(db, "linked-1", 1800L, taskId = "task-1")
        insertFocusAt20(db, "linked-2", 3600L, taskId = "task-1")

        // 3) 两类不该计入的时段：不挂计划的、还没结束的
        insertFocusAt20(db, "free-1", 900L, taskId = null)
        insertFocusAt20(db, "running-1", 300L, taskId = "task-1", status = "RUNNING")

        // 3) 聚合口径与 FocusSessionDao.observeTaskActualSeconds 逐字一致：
        //    只算已完成且挂着计划的时段 —— 未结束的、不挂计划的都被排除。
        assertEquals(
            1,
            db.intValue(
                "SELECT COUNT(*) FROM (" +
                    "SELECT taskId FROM focus_sessions " +
                    "WHERE status = 'COMPLETED' AND taskId IS NOT NULL GROUP BY taskId)"
            )
        )
        assertEquals(
            5400L,
            db.longValue(
                "SELECT seconds FROM (" +
                    "SELECT COALESCE(SUM(durationSeconds), 0) AS seconds FROM focus_sessions " +
                    "WHERE status = 'COMPLETED' AND taskId IS NOT NULL GROUP BY taskId)"
            )
        )
        // 全表确实还有 1800(存量) + 900(未挂计划) + 300(未完成) 没进这个数
        assertEquals(8400L, db.longValue("SELECT SUM(durationSeconds) FROM focus_sessions"))

        db.close()
    }

    /** 迁移只加列，绝不把任何现有时段重新贴到某条计划上。 */
    @Test
    fun migrate19To20_neverAttributesExistingSessions() {
        val db19 = helper.createDatabase(19)
        insertFocusAt19(db19, "legacy-1", durationSeconds = 1800L)
        insertFocusAt19(db19, "legacy-2", durationSeconds = 2700L)
        db19.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(19))

        assertEquals(2, db.intValue("SELECT COUNT(*) FROM focus_sessions"))
        assertEquals(
            "没有任何时段应当被自动贴到计划上",
            0,
            db.intValue("SELECT COUNT(*) FROM focus_sessions WHERE taskId IS NOT NULL")
        )
        db.close()
    }

    // ---------------------------------------------------------------- 20 -> 21 记录归属时段

    /**
     * v20 给 `journal_entries` 加了 `sessionId`，把「记录此刻」持久地挂到某次专注上。
     *
     * 修复的缺陷（challenger_m25_1 P0-4）：此前这个绑定只活在保存回调的响应体里，
     * 每一条读取路径都发出空串 —— 第一次读取之后归属关系就不可见了。
     *
     * 三个不变量：
     *  1. 存量行一律为 NULL —— 升级前的记录不可能有时段归属，**迁移不编造归属**；
     *  2. 新行可写可读：绑定与未绑定两种形态都能落库并被读回；
     *  3. 空串不是合法值 —— 契约要求 `string | null`。
     */
    @Test
    fun migrate20To21_addsNullableSessionIdColumnWithoutInventingBindings() {
        val db20 = helper.createDatabase(20)
        db20.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft) VALUES " +
                "('legacy-j','2026-09-21','旧记录','原始正文',5,5,5,'','','复盘',111,222,0,0)"
        ).use { it.step() }
        db20.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(20))

        // 1) 列存在，且存量行没有归属
        assertTrue("journal_entries 应包含 sessionId 列", "sessionId" in db.columnNames("journal_entries"))
        assertEquals(
            "迁移不得为存量记录编造时段归属",
            1L,
            db.longValue(
                "SELECT CASE WHEN sessionId IS NULL THEN 1 ELSE 0 END " +
                    "FROM journal_entries WHERE id='legacy-j'"
            )
        )
        assertEquals("存量的其他字段必须原样保留", "原始正文", db.textValue("SELECT content FROM journal_entries WHERE id='legacy-j'"))
        assertEquals(111L, db.longValue("SELECT createdAt FROM journal_entries WHERE id='legacy-j'"))

        // 2) 两种新形态：绑定时段 / 不绑定时段
        insertNoteRowAt21(db, "bound-j", "2026-10-08", "绑定时段", 1000L, sessionId = "focus-42")
        insertNoteRowAt21(db, "free-j", "2026-10-08", "未绑定时段", 2000L, sessionId = null)

        assertEquals("focus-42", db.textValue("SELECT sessionId FROM journal_entries WHERE id='bound-j'"))
        assertEquals(
            "未绑定的记录必须是 NULL，绝不是空串",
            1L,
            db.longValue(
                "SELECT CASE WHEN sessionId IS NULL THEN 1 ELSE 0 END " +
                    "FROM journal_entries WHERE id='free-j'"
            )
        )

        // 3) 空串不是合法值：可以直接按 id 反查绑定的记录
        assertEquals(
            1,
            db.intValue("SELECT COUNT(*) FROM journal_entries WHERE sessionId = 'focus-42'")
        )

        db.close()
    }

    /** 迁移只加列，绝不把任何现有时段重新贴到某条记录上。 */
    @Test
    fun migrate20To21_neverBindsExistingNotesToASession() {
        val db20 = helper.createDatabase(20)
        db20.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft) VALUES " +
                "('legacy-a','2026-09-20','A','正文A',5,5,5,'','','',111,222,0,0)"
        ).use { it.step() }
        db20.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft) VALUES " +
                "('legacy-b','2026-09-21','B','正文B',5,5,5,'','','',333,444,0,0)"
        ).use { it.step() }
        db20.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(20))

        assertEquals(2, db.intValue("SELECT COUNT(*) FROM journal_entries"))
        assertEquals(
            "没有任何现存的记录应当被自动绑到某个时段上",
            0,
            db.intValue("SELECT COUNT(*) FROM journal_entries WHERE sessionId IS NOT NULL")
        )
        db.close()
    }

    /** 19 → 20 → 21 连续两跳之后，任务归因与记录归属两个新列互不干扰。 */
    @Test
    fun migrate19To21_chainLeavesBothNewColumnsNullableAndDataIntact() {
        val db19 = helper.createDatabase(19)
        insertFocusAt19(db19, "legacy-1", durationSeconds = 1800L)
        db19.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft) VALUES " +
                "('legacy-j','2026-09-21','旧记录','原始正文',5,5,5,'','','复盘',111,222,0,0)"
        ).use { it.step() }
        db19.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(19))

        // 两列都存在且都为 NULL
        assertTrue("focus_sessions.taskId 应存在", "taskId" in db.columnNames("focus_sessions"))
        assertTrue("journal_entries.sessionId 应存在", "sessionId" in db.columnNames("journal_entries"))
        assertEquals(
            1L,
            db.longValue("SELECT CASE WHEN taskId IS NULL THEN 1 ELSE 0 END FROM focus_sessions WHERE id='legacy-1'")
        )
        assertEquals(
            1L,
            db.longValue("SELECT CASE WHEN sessionId IS NULL THEN 1 ELSE 0 END FROM journal_entries WHERE id='legacy-j'")
        )

        // 存量数据仍然可读
        assertEquals(1800L, db.longValue("SELECT durationSeconds FROM focus_sessions WHERE id='legacy-1'"))
        assertEquals("原始正文", db.textValue("SELECT content FROM journal_entries WHERE id='legacy-j'"))

        // 迁移之后两种新写入都能落地
        insertFocusAt20(db, "linked-1", 3600L, taskId = "task-1")
        insertNoteRowAt21(db, "bound-j", "2026-10-08", "绑定时段", 1000L, sessionId = "focus-7")
        assertEquals("task-1", db.textValue("SELECT taskId FROM focus_sessions WHERE id='linked-1'"))
        assertEquals("focus-7", db.textValue("SELECT sessionId FROM journal_entries WHERE id='bound-j'"))

        db.close()
    }

    // ---------------------------------------------------------------- 不造数据

    @Test
    fun migration_neverFabricatesBusinessRows() {
        // v7 库里业务表本来就是空的（用户主动删空 / 全新建库）
        seedRawDatabase(version = 7, ddl = v7Ddl)

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, allMigrations())

        // 注意：`subjects` **不在**此列表中。它是系统提供的默认配置（内置学科词典），
        // 迁移时主动写入是设计意图，见 `migrate13To14_createsSubjectsTableSeededWithDefaults`。
        listOf(
            "focus_sessions", "exam_sessions", "journal_entries",
            "chat_messages", "chat_sessions", "check_ins",
            "unlocked_achievements", "quick_start_presets", "ai_analyses", "study_tasks"
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

        // v10→v11 这一跳要求 date 唯一 —— 必须**只跑到 v11 为止**校验，
        // 因为 v14→v15 会刻意放开该唯一约束（一天允许多篇随笔）。
        val db = helper.runMigrationsAndValidate(
            11,
            chainFrom(10)
        )

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
        assertTrue("database must reject a second notes for the same date", duplicateInsert.isFailure)
        db.close()
    }

    // ---------------------------------------------------------------- SQL 小工具

    /** 插入一行随笔，其余列填中性默认值。用于验证「一天多篇」与 isFavorite 默认值。 */
    private fun insertNoteRow(
        db: SQLiteConnection,
        id: String,
        date: String,
        title: String,
        createdAt: Long,
        isDraft: Int = 0
    ) {
        db.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft) VALUES " +
                "(?, ?, ?, '', 3, 3, 3, '', '', '', ?, ?, 0, ?)"
        ).use { statement ->
            statement.bindText(1, id)
            statement.bindText(2, date)
            statement.bindText(3, title)
            statement.bindLong(4, createdAt)
            statement.bindLong(5, createdAt)
            statement.bindLong(6, isDraft.toLong())
            statement.step()
        }
    }

    /**
     * v21 起带 `sessionId` 的随笔写入语句。传 null 表示这条记录不绑定任何时段，
     * 与桥接层 `saveQuickNote` 在 JS 侧解析不到活动会话时的落库形态一致。
     */
    private fun insertNoteRowAt21(
        db: SQLiteConnection,
        id: String,
        date: String,
        title: String,
        createdAt: Long,
        sessionId: String?
    ) {
        db.prepare(
            "INSERT INTO journal_entries " +
                "(id, date, title, content, moodScore, energyScore, studySatisfaction, " +
                "tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft, sessionId) VALUES " +
                "(?, ?, ?, '', 3, 3, 3, '', '', '', ?, ?, 0, 0, ?)"
        ).use { statement ->
            statement.bindText(1, id)
            statement.bindText(2, date)
            statement.bindText(3, title)
            statement.bindLong(4, createdAt)
            statement.bindLong(5, createdAt)
            if (sessionId == null) statement.bindNull(6) else statement.bindText(6, sessionId)
            statement.step()
        }
    }

    // ---------------------------------------------------------------- 21 -> 22 学科换色

    /**
     * 21→22 只重写「仍是旧默认色」的默认学科行。
     *
     * 两条不能让步的边界：用户自己改过的颜色、以及用户自建学科的颜色，都是用户产生的
     * 数据，迁移一律不碰——所以守卫写在 SQL 里（`AND colorHex = 旧默认色`），而不是
     * 迁移后在 Kotlin 里比对。
     */
    @Test
    fun migrate21To22_recolorsDefaultSubjectsButNeverUserChoices() {
        val db21 = helper.createDatabase(21)
        db21.prepare(
            "INSERT INTO subjects (id, name, colorHex, sortOrder, enabled, parentId) VALUES " +
                "('math_linear','线性代数','#4C7BE8',12,1,'math')," +
                "('major_os','操作系统','#B8ADFC',24,1,'major')," +
                "('politics','政治','#E67E22',4,1,NULL)"
        ).use { it.step() }
        // 用户把英语改成了自己的颜色。
        db21.prepare(
            "INSERT INTO subjects (id, name, colorHex, sortOrder, enabled, parentId) VALUES " +
                "('english','英语一','#111111',3,1,NULL)"
        ).use { it.step() }
        // 用户自建的学科。
        db21.prepare(
            "INSERT INTO subjects (id, name, colorHex, sortOrder, enabled, parentId) VALUES " +
                "('custom-x','自学科','#222222',99,1,NULL)"
        ).use { it.step() }
        db21.close()

        val db = helper.runMigrationsAndValidate(CURRENT_VERSION, chainFrom(21))

        assertEquals("#0891B2", db.textValue("SELECT colorHex FROM subjects WHERE id='math_linear'"))
        assertEquals("#C026D3", db.textValue("SELECT colorHex FROM subjects WHERE id='major_os'"))
        assertEquals("#DC2626", db.textValue("SELECT colorHex FROM subjects WHERE id='politics'"))
        assertEquals(
            "用户自己改过的颜色必须原样保留",
            "#111111",
            db.textValue("SELECT colorHex FROM subjects WHERE id='english'")
        )
        assertEquals(
            "自定义学科一行都不碰",
            "#222222",
            db.textValue("SELECT colorHex FROM subjects WHERE id='custom-x'")
        )
        db.close()
    }

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
