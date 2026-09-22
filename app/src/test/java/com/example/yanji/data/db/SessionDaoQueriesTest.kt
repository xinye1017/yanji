package com.example.yanji.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SessionDaoQueriesTest {

    private val dbDir: Path = Files.createTempDirectory("yanji-dao-queries")
    private val dbFile: Path = dbDir.resolve("dao-test.db")
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
    fun testPreciseSqlQueries() {
        val db = helper.createDatabase(12)
        val now = System.currentTimeMillis()

        // 1. Focus sessions insertion
        db.prepare("INSERT INTO focus_sessions (id, subjectId, subjectName, startTime, endTime, durationSeconds, pausedDurationSeconds, pauseCount, mode, note, status, createdAt) VALUES (?, ?, ?, ?, ?, ?, 0, 0, '正向计时', '', ?, ?)").use { stmt ->
            // f1
            stmt.bindText(1, "f1")
            stmt.bindText(2, "math")
            stmt.bindText(3, "数学一")
            stmt.bindLong(4, now - 10_000)
            stmt.bindLong(5, now - 6_400)
            stmt.bindLong(6, 3600)
            stmt.bindText(7, "COMPLETED")
            stmt.bindLong(8, now - 10_000)
            stmt.step(); stmt.reset()

            // f2
            stmt.bindText(1, "f2")
            stmt.bindText(2, "408")
            stmt.bindText(3, "408专业课")
            stmt.bindLong(4, now - 5_000)
            stmt.bindLong(5, now - 3_200)
            stmt.bindLong(6, 1800)
            stmt.bindText(7, "COMPLETED")
            stmt.bindLong(8, now - 5_000)
            stmt.step(); stmt.reset()

            // f3 (cancelled)
            stmt.bindText(1, "f3")
            stmt.bindText(2, "english")
            stmt.bindText(3, "英语一")
            stmt.bindLong(4, now - 2_000)
            stmt.bindLong(5, now - 1_500)
            stmt.bindLong(6, 500)
            stmt.bindText(7, "CANCELLED")
            stmt.bindLong(8, now - 2_000)
            stmt.step()
        }

        // Test getAllOnce query: SELECT * FROM focus_sessions ORDER BY startTime DESC
        val allStmt = db.prepare("SELECT id FROM focus_sessions ORDER BY startTime DESC")
        val allIds = mutableListOf<String>()
        while (allStmt.step()) {
            allIds.add(allStmt.getText(0))
        }
        allStmt.close()
        assertEquals(listOf("f3", "f2", "f1"), allIds)

        // 最近 N 条：SELECT * FROM focus_sessions ORDER BY startTime DESC LIMIT 2
        val recentStmt = db.prepare("SELECT id FROM focus_sessions ORDER BY startTime DESC LIMIT 2")
        val recentIds = mutableListOf<String>()
        while (recentStmt.step()) {
            recentIds.add(recentStmt.getText(0))
        }
        recentStmt.close()
        assertEquals(listOf("f3", "f2"), recentIds)

        // Test getSessionsSince: SELECT * FROM focus_sessions WHERE startTime >= ? ORDER BY startTime DESC
        val sinceStmt = db.prepare("SELECT id FROM focus_sessions WHERE startTime >= ? ORDER BY startTime DESC")
        sinceStmt.bindLong(1, now - 6_000)
        val sinceIds = mutableListOf<String>()
        while (sinceStmt.step()) {
            sinceIds.add(sinceStmt.getText(0))
        }
        sinceStmt.close()
        assertEquals(listOf("f3", "f2"), sinceIds)

        // Test getTotalCompletedDuration: SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions WHERE status = 'COMPLETED'
        val sumStmt = db.prepare("SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions WHERE status = 'COMPLETED'")
        sumStmt.step()
        val totalSecs = sumStmt.getLong(0)
        sumStmt.close()
        assertEquals(3600L + 1800L, totalSecs)

        // 2. Exam sessions insertion
        db.prepare("INSERT INTO exam_sessions (id, subjectId, subjectName, plannedDurationSeconds, actualDurationSeconds, startTime, endTime, score, maxScore, note, status, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?)").use { stmt ->
            // e1
            stmt.bindText(1, "e1")
            stmt.bindText(2, "math")
            stmt.bindText(3, "数学一")
            stmt.bindLong(4, 10800)
            stmt.bindLong(5, 10500)
            stmt.bindLong(6, now - 20_000)
            stmt.bindLong(7, now - 9_500)
            stmt.bindDouble(8, 120.0)
            stmt.bindDouble(9, 150.0)
            stmt.bindText(10, "COMPLETED")
            stmt.bindLong(11, now - 20_000)
            stmt.step(); stmt.reset()

            // e2
            stmt.bindText(1, "e2")
            stmt.bindText(2, "408")
            stmt.bindText(3, "408专业课")
            stmt.bindLong(4, 10800)
            stmt.bindLong(5, 10800)
            stmt.bindLong(6, now - 10_000)
            stmt.bindLong(7, now + 800)
            stmt.bindDouble(8, 110.0)
            stmt.bindDouble(9, 150.0)
            stmt.bindText(10, "COMPLETED")
            stmt.bindLong(11, now - 10_000)
            stmt.step()
        }

        // Test Exam getTotalCompletedDuration
        val examSumStmt = db.prepare("SELECT COALESCE(SUM(actualDurationSeconds), 0) FROM exam_sessions WHERE status = 'COMPLETED'")
        examSumStmt.step()
        val examTotalSecs = examSumStmt.getLong(0)
        examSumStmt.close()
        assertEquals(10500L + 10800L, examTotalSecs)

        // 3. Achievement idempotent unlock & getUnlockedIds
        db.prepare("INSERT OR IGNORE INTO unlocked_achievements (id, unlockedAt) VALUES (?, ?)").use { stmt ->
            stmt.bindText(1, "ach_1")
            stmt.bindLong(2, 1000L)
            stmt.step(); stmt.reset()

            stmt.bindText(1, "ach_2")
            stmt.bindLong(2, 2000L)
            stmt.step(); stmt.reset()

            // Duplicate insert ignored
            stmt.bindText(1, "ach_1")
            stmt.bindLong(2, 3000L)
            stmt.step()
        }

        val achStmt = db.prepare("SELECT id FROM unlocked_achievements")
        val unlockedIds = mutableListOf<String>()
        while (achStmt.step()) {
            unlockedIds.add(achStmt.getText(0))
        }
        achStmt.close()
        assertEquals(2, unlockedIds.size)
        assertTrue(unlockedIds.contains("ach_1"))
        assertTrue(unlockedIds.contains("ach_2"))

        db.close()
    }
}
