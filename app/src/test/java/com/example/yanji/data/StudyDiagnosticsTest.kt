package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyDiagnosticsTest {
    @Test
    fun snapshotUsesCompletedRecordsAndBuildsDataOnlyPrompt() {
        val now = 1_700_000_000_000L
        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = 7,
            settings = UserSettings(dailyGoalHours = 2f, validStudyThresholdMinutes = 30),
            focusSessions = listOf(
                FocusSession("math", "math_advanced", "高等数学", now - 1_000, now, 7_200),
                FocusSession("english", "english", "英语一", now - 2_000, now, 1_800),
                FocusSession("cancelled", "major", "408", now - 3_000, now, 9_999, status = SessionStatus.CANCELLED)
            ),
            examSessions = listOf(ExamSession("exam", "math", "数学", score = 120.0, maxScore = 150.0)),
            journalEntries = listOf(
                JournalEntry("journal", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now)), content = "复盘积分计算")
            ),
            now = now
        )

        assertEquals(2, snapshot.sessionCount)
        assertEquals(9_000, snapshot.totalSeconds)
        assertEquals("高等数学", snapshot.subjectStats.first().name)
        assertTrue(snapshot.toPromptData().contains("复盘积分计算"))
        assertTrue(snapshot.toPromptData().contains("data_only"))
    }

    @Test
    fun subjectCatalogKeepsChildAndCategoryStatisticsCompatibleWithLegacyRecords() {
        assertEquals(
            listOf("高等数学", "线性代数", "概率论"),
            SubjectCatalog.childrenOf("math").map { it.name }
        )
        assertEquals(
            listOf("计算机组成原理", "数据结构", "计算机网络", "操作系统"),
            SubjectCatalog.childrenOf("major").map { it.name }
        )
        assertEquals("math", SubjectCatalog.categoryIdOf("math_linear"))
        assertEquals("math_linear", SubjectCatalog.subcategoryBucketId("math_linear", "线性代数"))
        assertEquals("math__unclassified", SubjectCatalog.subcategoryBucketId("math", "数学"))
        assertEquals("数学一（综合/未细分）", SubjectCatalog.displayName("math__unclassified"))
        assertEquals("major", SubjectCatalog.inferCategoryId("custom", "408 全真模拟"))
    }
}
