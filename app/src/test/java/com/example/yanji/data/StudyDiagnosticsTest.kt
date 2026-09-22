package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            noteEntries = listOf(
                NoteEntry("journal", YanjiTime.localDate(now).toString(), content = "复盘积分计算")
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

    /**
     * 图表取色索引（`colorIndexOf`）必须保证：
     * 1. 大类视图下 5 个大类落在互不相同的色阶；
     * 2. 子类视图下同一父大类的子类落在互不相同的色阶；
     * 3. 不同父大类的子类不共享色阶（否则两个不同科目的子类会显示成同色）。
     */
    @Test
    fun colorIndexAssignsDistinctPaletteSlotsPerView() {
        val paletteSize = 5

        // 1. 大类视图：5 个大类 → 5 个不同色阶
        val categorySteps = SubjectCatalog.categories.map { SubjectCatalog.colorIndexOf(it.id) % paletteSize }
        assertEquals("category view must use $paletteSize distinct steps", paletteSize, categorySteps.toSet().size)

        // 2. 子类视图：每个大类的所有子类 → 同父内互不相同
        for (category in SubjectCatalog.categories) {
            val children = SubjectCatalog.childrenOf(category.id)
            if (children.size < 2) continue
            val steps = children.map { SubjectCatalog.colorIndexOf(it.id) % paletteSize }
            assertEquals(
                "children of ${category.id} must not collide: ${children.map { it.name }}",
                children.size,
                steps.toSet().size
            )
        }

        // 3. 不同父大类的子类不得共享色阶
        val allChildSteps = SubjectCatalog.all
            .filter { it.parentId != null }
            .map { SubjectCatalog.colorIndexOf(it.id) }
        assertEquals(
            "subcategories across different categories must occupy distinct slots",
            allChildSteps.size,
            allChildSteps.toSet().size
        )
    }

    @Test
    fun colorIndexIsStableAndNeverNegative() {
        // 稳定：同一 id 多次调用结果一致（渲染层依赖它在重组间保持恒定）
        for (subject in SubjectCatalog.all) {
            val first = SubjectCatalog.colorIndexOf(subject.id)
            assertEquals(first, SubjectCatalog.colorIndexOf(subject.id))
            assertTrue("colorIndexOf must not be negative for ${subject.id}", first >= 0)
        }
        // 未知学科：安全回落到 0，不抛异常、不返回负数
        assertEquals(0, SubjectCatalog.colorIndexOf("完全不存在的自定义学科"))
        // 未细分桶（__unclassified）也应与父大类取同一槽位
        assertEquals(
            SubjectCatalog.colorIndexOf("math"),
            SubjectCatalog.colorIndexOf("math${SubjectCatalog.UNCLASSIFIED_SUFFIX}")
        )
    }
}
