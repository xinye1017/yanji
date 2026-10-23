package com.example.yanji.data.db

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
 * 查询计划守卫：把「热点查询走哪条索引」钉成断言。
 *
 * ## 为什么需要它
 * `focus_sessions` 的两条索引（v25）不是可选优化：
 *  - `observeTaskActualSeconds` 是 Today 页常驻 Flow，`focus_sessions` 每写一次就跑一次；
 *  - 计划 LEFT JOIN 缺索引时，SQLite 每次查询都要现场建 AUTOMATIC PARTIAL COVERING INDEX
 *    （真实分布放大到 10k 行：8.30ms → 0.28ms）。
 * 这类退化在功能上完全看不出来，只能靠计划断言拦住。
 *
 * ## 一条用实测换来的教训（别再引入统计信息）
 * 最初这里跑 `PRAGMA optimize` 后断言，结果在 3,000 行上恰好通过。把数据放大到 30k 行
 * 才发现：**`PRAGMA optimize` / `ANALYZE` 写入的部分统计会把 `observeTaskActualSeconds`
 * 的计划带偏**（0.7ms → 10.9ms，比全表扫描还慢），而完全无统计时优化器的启发式反而正确。
 * 所以现在生产既不 ANALYZE 也不依赖统计，而是用 `INDEXED BY` 显式钉住关键查询；
 * 本测试同时断言**三种统计状态下**（无统计 / optimize / ANALYZE）计划都不变——
 * 只在一个状态下测，正是当初漏掉问题的原因。
 *
 * ⚠️ 下面的 SQL 逐字镜像 `Daos.kt` 的 `@Query`。改 DAO 查询时必须同步改这里。
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

        /** 断言规模：跨过实测出现计划退化的阈值（约 10k 行）才有意义。 */
        const val SEEDED_ROWS = 20_000
    }

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

    /** 真实分布：跨 200 天、其中三成挂计划（不是每次专注都绑计划）。 */
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
        for (i in 1..SEEDED_ROWS) {
            val subject = if (i % 2 == 0) "math_linear" to "线性代数" else "english" to "英语一"
            val start = anchor - (i % 200) * dayMs - (i % 24) * 3_600_000L
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

    @Test
    fun hotQueriesKeepTheirIndicesUnderEveryStatisticsState() {
        val db = helper.createDatabase(25)
        val (monthStart, anchor) = db.seed()
        val dayEnd = anchor + 86_400_000L

        val taskAgg =
            "SELECT taskId, COALESCE(SUM(durationSeconds), 0) AS seconds FROM focus_sessions " +
                "INDEXED BY `$TASK_INDEX` " +
                "WHERE status = 'COMPLETED' AND taskId IS NOT NULL GROUP BY taskId"
        val reviewStats =
            "SELECT startTime, subjectId, subjectName, durationSeconds FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= $monthStart AND startTime < $anchor"
        val sumSeconds =
            "SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= $monthStart AND startTime < $anchor"
        val taskJoin =
            "SELECT t.id, COALESCE(SUM(f.durationSeconds), 0) AS actualSeconds " +
                "FROM study_tasks AS t LEFT JOIN focus_sessions AS f " +
                "ON f.taskId = t.id AND f.status = 'COMPLETED' " +
                "WHERE t.date = '2026-10-10' GROUP BY t.id"
        val dailyOverlap =
            "SELECT * FROM focus_sessions WHERE status = 'COMPLETED' AND startTime < $dayEnd " +
                "AND (CASE WHEN endTime > startTime THEN endTime " +
                "ELSE startTime + durationSeconds * 1000 END) > $anchor ORDER BY startTime DESC"

        // 三种统计状态都要成立：无统计（生产常态）/ optimize / 完整 ANALYZE。
        // 只在一种状态下断言，正是当初放过 30k 行计划退化的原因。
        listOf<String?>(null, "PRAGMA optimize", "ANALYZE").forEach { statsStep ->
            statsStep?.let { db.exec(it) }
            val state = statsStep ?: "无统计"

            val taskAggPlan = db.plan(taskAgg)
            assertTrue(
                "taskAgg 未走 $TASK_INDEX（$state）：$taskAggPlan",
                taskAggPlan.contains(TASK_INDEX)
            )
            assertFalse(
                "taskAgg 出现临时 B-tree 分组，说明选错了索引（$state）：$taskAggPlan",
                taskAggPlan.contains("TEMP B-TREE")
            )

            val reviewPlan = db.plan(reviewStats)
            assertFalse("reviewStats 退化为全表扫描（$state）：$reviewPlan", reviewPlan.contains("SCAN focus_sessions"))
            assertTrue("reviewStats 未使用覆盖索引（$state）：$reviewPlan", reviewPlan.contains(COVERING_INDEX))

            val sumPlan = db.plan(sumSeconds)
            assertTrue("sumSeconds 未使用覆盖索引（$state）：$sumPlan", sumPlan.contains(COVERING_INDEX))

            val joinPlan = db.plan(taskJoin)
            assertFalse("计划 JOIN 退化为全表扫描（$state）：$joinPlan", joinPlan.contains("SCAN focus_sessions"))
            assertFalse("计划 JOIN 仍在依赖临时自建索引（$state）：$joinPlan", joinPlan.contains("AUTOMATIC"))
            assertTrue("计划 JOIN 未使用 $TASK_INDEX（$state）：$joinPlan", joinPlan.contains(TASK_INDEX))

            val dailyPlan = db.plan(dailyOverlap)
            assertFalse("每日相交查询退化为全表扫描（$state）：$dailyPlan", dailyPlan.contains("SCAN focus_sessions"))
            assertTrue(
                "每日相交查询未使用 startTime 前缀索引（$state）：$dailyPlan",
                dailyPlan.contains(COVERING_INDEX) || dailyPlan.contains(START_TIME_INDEX)
            )
        }

        db.close()
    }

    /**
     * 锁死「不要在打开路径上写统计」这个决定。
     *
     * 背景见类注释：统计信息把 `observeTaskActualSeconds` 的计划带偏（30k 行 0.7ms → 10.9ms）。
     * 没有这条断言，后来者很容易为了「让优化器知道数据分布」再加一次 `PRAGMA optimize`，
     * 而上面的计划断言在 20k 行 + 无统计下依然全绿，根本发现不了。
     */
    @Test
    fun databaseOpenPathDoesNotWritePlannerStatistics() {
        val file = File("src/main/java/com/example/yanji/data/db/YanjiDatabase.kt")
        assertTrue("找不到 $file", file.exists())
        val code = file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertFalse(
            "打开路径不得执行 ANALYZE / PRAGMA optimize：部分统计会把 taskAgg 的计划带偏",
            code.contains("PRAGMA optimize") || Regex("""\bANALYZE\b""").containsMatchIn(code)
        )
        // 关键查询用 INDEXED BY 钉住意图；索引名写错会在 Room 编译期就报错。
        val dao = File("src/main/java/com/example/yanji/data/db/Daos.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")
        assertEquals(
            "observeTaskActualSeconds 必须用 INDEXED BY 钉在 taskId 覆盖索引上（去 DAO 看 KDoc 里的实测数据）",
            1,
            Regex("""INDEXED BY\s+`index_focus_sessions_taskId_status_durationSeconds`""")
                .findAll(dao).count()
        )
    }
}
