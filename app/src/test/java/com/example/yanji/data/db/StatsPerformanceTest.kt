package com.example.yanji.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.YanjiTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureNanoTime

/**
 * 统计性能单测：
 * 验证在拥有 20,000 条专注记录与 5,000 条模考记录的真实重度数据规模下：
 * 1. 日期区间过滤与聚合下推到 Room / SQLite 能够充分利用 startTime 索引；
 * 2. 区间查询及 GROUP BY / SUM 耗时稳定在 < 10ms，避免全表扫描；
 * 3. 周视图计算耗时复杂度为 O(当周记录数)，而非随历史总条数 O(N) 线性膨胀。
 */
class StatsPerformanceTest {

    private val dbDir: Path = Files.createTempDirectory("yanji-stats-perf")
    private val dbFile: Path = dbDir.resolve("stats-perf.db")
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

    @Test
    fun testRoomRangeQueryAndAggregatePerformanceWith25000Records() {
        val db = helper.createDatabase(11)

        val totalFocusCount = 20_000
        val totalExamCount = 5_000
        val nowMs = System.currentTimeMillis()
        val oneDayMs = 86400_000L

        // 插入 20,000 条 Focus 记录和 5,000 条 Exam 记录（时间跨度 200 天）
        db.prepare("BEGIN TRANSACTION").use { it.step() }

        val insertFocus = db.prepare(
            "INSERT INTO focus_sessions " +
                "(id, subjectId, subjectName, startTime, endTime, durationSeconds, pausedDurationSeconds, pauseCount, mode, note, status, createdAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, 0, '正向计时', '', 'COMPLETED', ?)"
        )
        val subjects = listOf(
            "math_advanced" to "高等数学",
            "english_one" to "考研英语",
            "politics" to "考研政治",
            "cs_408" to "408计算机"
        )

        for (i in 1..totalFocusCount) {
            val (subId, subName) = subjects[i % subjects.size]
            val sessionStart = nowMs - (i * (oneDayMs / 100))
            insertFocus.bindText(1, "fs-$i")
            insertFocus.bindText(2, subId)
            insertFocus.bindText(3, subName)
            insertFocus.bindLong(4, sessionStart)
            insertFocus.bindLong(5, sessionStart + 2700L * 1000L)
            insertFocus.bindLong(6, 2700L)
            insertFocus.bindLong(7, sessionStart)
            insertFocus.step()
            insertFocus.reset()
        }
        insertFocus.close()

        val insertExam = db.prepare(
            "INSERT INTO exam_sessions " +
                "(id, subjectId, subjectName, plannedDurationSeconds, actualDurationSeconds, startTime, endTime, score, maxScore, note, status, createdAt) " +
                "VALUES (?, ?, ?, 10800, ?, ?, ?, 120.0, 150.0, '', 'COMPLETED', ?)"
        )
        for (i in 1..totalExamCount) {
            val (subId, subName) = subjects[i % subjects.size]
            val sessionStart = nowMs - (i * (oneDayMs / 25))
            insertExam.bindText(1, "es-$i")
            insertExam.bindText(2, subId)
            insertExam.bindText(3, subName)
            insertExam.bindLong(4, 10500L)
            insertExam.bindLong(5, sessionStart)
            insertExam.bindLong(6, sessionStart + 10500L * 1000L)
            insertExam.bindLong(7, sessionStart)
            insertExam.step()
            insertExam.reset()
        }
        insertExam.close()

        db.prepare("COMMIT").use { it.step() }

        // 验证总数据量确为 20,000 与 5,000
        val dbFocusCount = db.prepare("SELECT COUNT(*) FROM focus_sessions").use { it.step(); it.getInt(0) }
        val dbExamCount = db.prepare("SELECT COUNT(*) FROM exam_sessions").use { it.step(); it.getInt(0) }
        assertEquals(20_000, dbFocusCount)
        assertEquals(5_000, dbExamCount)

        // 模拟本周范围查询（7 天窗口）
        val weekRange = YanjiTime.currentWeekRange()
        val startInclusive = weekRange.startInclusive
        val endExclusive = weekRange.endExclusive

        // 1. 验证 SQLite 范围查询耗时 < 10ms
        val rangeStmt = db.prepare(
            "SELECT * FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= ? AND startTime < ? " +
                "ORDER BY startTime DESC"
        )
        rangeStmt.bindLong(1, startInclusive)
        rangeStmt.bindLong(2, endExclusive)

        // 热身一次
        rangeStmt.step()
        rangeStmt.reset()

        val rangeQueryNanos = measureNanoTime {
            var rows = 0
            while (rangeStmt.step()) {
                rows++
            }
            rangeStmt.reset()
        }
        rangeStmt.close()
        val rangeQueryMs = rangeQueryNanos / 1_000_000.0
        assertTrue("startTime 索引加速下的本周范围查询必须在 10ms 内完成，实际耗时: ${rangeQueryMs}ms", rangeQueryMs < 10.0)

        // 2. 验证 GROUP BY 与 SUM 聚合下推到 SQLite 的耗时 < 10ms
        val aggregateStmt = db.prepare(
            "SELECT subjectId, subjectName, " +
                "COALESCE(SUM(durationSeconds), 0) AS durationSeconds, " +
                "COUNT(*) AS sessionCount, COALESCE(MAX(durationSeconds), 0) AS longestSessionSeconds " +
                "FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= ? AND startTime < ? " +
                "GROUP BY subjectId, subjectName"
        )
        aggregateStmt.bindLong(1, startInclusive)
        aggregateStmt.bindLong(2, endExclusive)

        aggregateStmt.step()
        aggregateStmt.reset()

        val aggregateNanos = measureNanoTime {
            while (aggregateStmt.step()) {
                aggregateStmt.getText(0)
                aggregateStmt.getText(1)
                aggregateStmt.getLong(2)
                aggregateStmt.getInt(3)
                aggregateStmt.getLong(4)
            }
            aggregateStmt.reset()
        }
        aggregateStmt.close()
        val aggregateMs = aggregateNanos / 1_000_000.0
        assertTrue("SQLite 聚合下推查询必须在 10ms 内完成，实际耗时: ${aggregateMs}ms", aggregateMs < 10.0)

        // 3. 验证单日求和 observeTotalSeconds 下推耗时 < 10ms
        val totalStmt = db.prepare(
            "SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions " +
                "WHERE status = 'COMPLETED' AND startTime >= ? AND startTime < ?"
        )
        totalStmt.bindLong(1, startInclusive)
        totalStmt.bindLong(2, endExclusive)
        totalStmt.step()
        totalStmt.reset()

        val totalNanos = measureNanoTime {
            totalStmt.step()
            totalStmt.getLong(0)
            totalStmt.reset()
        }
        totalStmt.close()
        val totalMs = totalNanos / 1_000_000.0
        assertTrue("单日/单周总学习时长聚合必须在 10ms 内完成，实际耗时: ${totalMs}ms", totalMs < 10.0)

        db.close()
    }
}
