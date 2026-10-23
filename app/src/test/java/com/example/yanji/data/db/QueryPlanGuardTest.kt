package com.example.yanji.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 查询计划守卫：把「热点查询必须走索引」钉成断言，索引被删/被改列序时 CI 立即变红。
 *
 * 为什么需要它：`focus_sessions` 的两条新索引（v25）不是可选优化，缺任一条都会让
 * 常驻查询退化成全表扫描 —— `observeTaskActualSeconds` 是 Today 页的常驻 Flow，
 * `focus_sessions` 每写一次它就跑一次；计划 LEFT JOIN 缺索引时会每次现场建
 * AUTOMATIC PARTIAL COVERING INDEX。这类退化在功能上完全看不出来，只能靠计划断言。
 *
 * 断言分两层：
 *  1. **计划层**：`EXPLAIN QUERY PLAN` 不出现 `SCAN focus_sessions`，且出现预期索引名；
 *     ANALYZE 前后各验一次 —— 生产库不会自动 ANALYZE，所以两种统计状态都必须成立。
 *  2. **结构层**（见 `YanjiMigrationTest`）：迁移后索引名与列序必须与 Room 生成的
 *     schema 完全一致，由 `runMigrationsAndValidate` 兜底。
 *
 * ⚠️ 下面的 SQL 逐字镜像 `Daos.kt` 里的 `@Query`。**改 DAO 查询时必须同步改这里**，
 * 否则守卫会静默失效（它只保证「这条 SQL 的计划是对的」，不保证「DAO 还是这条 SQL」）。
 */
class QueryPlanGuardTest {

    private val dbDir: Path = Files.createTempDirectory("yanji-query-plan")
    private val dbFile: Path = dbDir.resolve("query-plan.db")
    private val driver = BundledSQLiteDriver()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        File("schemas").toPath(),
        dbFile,
        driver,
        YanjiDatabase::class,
        { YanjiDatabase_Impl() },
        emptyList()
    )

    private companion object {
        const val COVERING_INDEX =
            "index_focus_sessions_status_startTime_subjectId_subjectName_durationSeconds"
        const val TASK_INDEX = "index_focus_sessions_taskId_status_durationSeconds"
        const val START_TIME_INDEX = "index_focus_sessions_startTime"
    }

    /** 计划的文本形式；用于断言「用了哪条索引」与「有没有全表扫描」。 */
    private fun SQLiteConnection.plan(sql: String): String {
        val details = mutableListOf<String>()
        prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
            val detailIndex = statement.getColumnNames().indexOf("detail")
            while (statement.step()) details.add(statement.getText(detailIndex))
        }
        return details.joinToString(" ; ")
    }

    private fun SQLiteConnection.exec(sql: String) {
        prepare(sql).use { it.step() }
    }

    /**
     * 造一批有代表性的数据：跨越 120 天、其中三成挂计划（与真实使用一致——不是每次
     * 专注都绑计划）。数据量取 3,000 条，让优化器有理由在「索引 vs 全表」之间做真实取舍。
     */
    private fun SQLiteConnection.seed(): Pair<Long, Long> {
        val dayMs = 86_400_000L
        val anchor = java.time.LocalDate.of(2026, 10, 10)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        exec("BEGIN")
        val insertFocus = prepare(
            "INSERT INTO focus_sessions (id, subjectId, subjectName, startTime, endTime, " +
                "durationSeconds, pausedDurationSeconds, pauseCount, mode, note, taskId, status, createdAt) " +
                "VALUES (?,?,?,?,?,?,0,0,'正向计时','',?,?,?)"
        )
        for (i in 1..3_000) {
            val subject = if (i % 2 == 0) "math_linear" to "线性代数" else "english" to "英语一"
            val start = anchor - (i % 120) * dayMs - (i % 24) * 3_600_000L
            insertFocus.bindText(1, "seed-$i")
            insertFocus.bindText(2, subject.first)
            insertFocus.bindText(3, subject.second)
            insertFocus.bindLong(4, start)
            insertFocus.bindLong(5, start + 2_700_000)
            insertFocus.bindLong(6, 2_700)
            if (i % 10 < 3) insertFocus.bindText(7, "task-${i % 40}") else insertFocus.bindNull(7)
            insertFocus.bindText(8, "COMPLETED")
            insertFocus.bindLong(9, start)
            insertFocus.step()
            insertFocus.reset()
        }
        insertFocus.close()

        val insertTask = prepare(
            "INSERT INTO study_tasks (id, date, subjectId, subjectName, title, plannedMinutes, " +
                "isCompleted, carriedOverToId, createdAt, updatedAt) VALUES (?,?,?,?,?,60,0,NULL,?,?)"
        )
        for (i in 0 until 40) {
            insertTask.bindText(1, "task-$i")
            insertTask.bindText(2, "2026-10-10")
            insertTask.bindText(3, "math_linear")
            insertTask.bindText(4, "线性代数")
            insertTask.bindText(5, "计划 $i")
            insertTask.bindLong(6, anchor)
            insertTask.bindLong(7, anchor)
            insertTask.step()
            insertTask.reset()
        }
        insertTask.close()
        exec("COMMIT")

        return (anchor - 30 * dayMs) to anchor
    }

    /**
     * 逐条查询断言索引使用情况。
     *
     * 先跑 `PRAGMA optimize`（**逐字镜像生产的 `onOpen` 钩子**），再断言计划 —— 因为
     * 统计信息直接影响优化器的选择：实测 `observeTaskActualSeconds` 在无统计时会选错
     * 索引（扫描全部 COMPLETED 行 + 临时 B-tree），补齐统计后才走 taskId 覆盖索引。
     * 所以这个测试同时守着「索引存在」与「统计被补齐」两件事。
     *
     * 之后再跑一次完整 `ANALYZE` 复核：统计更充分时计划不得变坏。
     */
    private fun assertIndexUsed(
        connection: SQLiteConnection,
        label: String,
        sql: String,
        expectedIndex: String
    ) {
        listOf("PRAGMA optimize", "ANALYZE").forEach { statsStep ->
            if (statsStep == "ANALYZE") connection.exec(statsStep)
            val plan = connection.plan(sql)
            assertFalse(
                "$label 退化为全表扫描（统计步骤=$statsStep）：$plan",
                plan.contains("SCAN focus_sessions")
            )
            assertTrue(
                "$label 未使用预期索引 $expectedIndex（统计步骤=$statsStep）：$plan",
                plan.contains(expectedIndex)
            )
        }
    }

    @Test
    fun hotQueriesKeepUsingTheirIndices() {
        val db = helper.createDatabase(25)
        db.exec("PRAGMA optimize")
        val (monthStart, anchor) = db.seed()
        db.exec("PRAGMA optimize")
        val dayEnd = anchor + 86_400_000L

        // 1. Today 页常驻：计划累计用时（每次专注落库都会重跑）
        assertIndexUsed(
            db,
            "observeTaskActualSeconds",
            "SELECT taskId, COALESCE(SUM(durationSeconds), 0) AS seconds FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND taskId IS NOT NULL GROUP BY taskId",
            TASK_INDEX
        )

        // 2. 回顾页批量投影（合并区间扫描）
        assertIndexUsed(
            db,
            "getCompletedStatsRows",
            "SELECT startTime, subjectId, subjectName, durationSeconds FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= $monthStart AND startTime < $anchor",
            COVERING_INDEX
        )

        // 3. 单日/单周总时长（首页与回顾页都在用）
        assertIndexUsed(
            db,
            "observeTotalSeconds",
            "SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= $monthStart AND startTime < $anchor",
            COVERING_INDEX
        )

        // 4. 计划实际用时（LEFT JOIN）：不能依赖 SQLite 每次现场自建的 automatic index
        val joinPlan = db.plan(
            "SELECT t.id, COALESCE(SUM(f.durationSeconds), 0) AS actualSeconds " +
                "FROM study_tasks AS t LEFT JOIN focus_sessions AS f " +
                "ON f.taskId = t.id AND f.status = 'COMPLETED' " +
                "WHERE t.date = '2026-10-10' GROUP BY t.id"
        )
        assertFalse("计划用时 JOIN 出现了全表扫描：$joinPlan", joinPlan.contains("SCAN focus_sessions"))
        assertFalse("计划用时 JOIN 仍在依赖临时自建索引：$joinPlan", joinPlan.contains("AUTOMATIC"))
        assertTrue("计划用时 JOIN 未使用 $TASK_INDEX：$joinPlan", joinPlan.contains(TASK_INDEX))

        // 5. 每日快照的「跨午夜相交」查询：按区间取整行，允许用任一 focus_sessions 索引，
        //    但绝不允许全表扫描（这条每天都会跑，且数据只会越来越多）。
        val dailyPlan = db.plan(
            "SELECT * FROM focus_sessions WHERE status = 'COMPLETED' AND startTime < $dayEnd " +
                "AND (CASE WHEN endTime > startTime THEN endTime " +
                "ELSE startTime + durationSeconds * 1000 END) > $anchor ORDER BY startTime DESC"
        )
        assertFalse("每日相交查询退化为全表扫描：$dailyPlan", dailyPlan.contains("SCAN focus_sessions"))
        assertTrue(
            "每日相交查询未使用 startTime 前缀索引：$dailyPlan",
            dailyPlan.contains(COVERING_INDEX) || dailyPlan.contains(START_TIME_INDEX)
        )

        db.close()
    }

    /**
     * 源码级断言：生产的打开路径必须补齐统计信息。
     *
     * 没有这条，上面的计划断言会被「只在测试里跑 PRAGMA optimize」骗过：
     * 测试全绿，但真机上 `observeTaskActualSeconds` 依然选错索引。
     * 与 `BridgeWriteContractTest` 同一先例——断言生产代码里确实存在这段调用。
     */
    @Test
    fun databaseOpenPathKeepsAnalyzingStatistics() {
        val file = File("src/main/java/com/example/yanji/data/db/YanjiDatabase.kt")
        assertTrue("找不到 $file", file.exists())
        val code = file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertTrue(
            "YanjiDatabase 打开路径必须执行 PRAGMA optimize，否则统计缺失时 taskId 索引不会被选用",
            code.contains("PRAGMA optimize")
        )
        assertTrue(
            "PRAGMA optimize 必须以 onOpen 回调挂在数据库构建上",
            code.contains("addCallback(")
        )
    }
}
