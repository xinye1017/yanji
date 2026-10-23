package com.example.yanji.data

/**
 * 今日快照的领域模型与装配（原 `StudyStatisticsRepository` 中仍在生产路径上的部分）。
 *
 * 这里**只**保留每日快照真正需要的那条链：
 * `DailySnapshotReader` → [buildDailyStudySummary] → [DailySessionItem] / [DailyStudySummary]。
 *
 * 历史上本文件还带过一整套「周 / 月 / 年汇总」的内存聚合
 * （`buildWeeklyStudySummary` / `buildMonthlyStudySummary` / `buildYearlyStudySummary`
 * 与 `SubjectStudySummary`、`DayBarData`、`MonthBarData` 等模型）。它们已被删除，原因：
 *
 *  1. **没有任何生产调用方**——回顾页的全部窗口统计走
 *     `ReviewOverviewReader` + SQL 聚合下推（见 `bridge/ReviewOverviewReader.kt`），
 *     那套内存实现只剩自己的单测在引用；
 *  2. **它们是回归陷阱**——每个实现都是 `for (day) { list.filter { ... } }` 的双层循环，
 *     即 O(窗口天数 × 历史记录数)，且必须先整表读进内存。一旦被接回页面，
 *     历史一多就是可见卡顿，而功能测试完全发现不了。
 *
 * 保留 `buildNamedSessionDistribution` 与 `resolveStudySubjectColor`：它们是每日快照
 * 的科目分桶与配色口径，与 `BridgeMappers.subjectBucket` 同一套规则。
 */

enum class SubjectStatsLevel(val title: String) {
    SUBCATEGORY("子类"),
    CATEGORY("大类")
}

/**
 * 统计结果模型**只读不可变**：字段全为 val，且不含可变集合字段。
 *
 * 这些对象由 Kotlin 统计层构建、经桥接层原样映射给 RN，中途没有任何一处需要回写；
 * 保持扁平 val-only 让「构建完就不再变」成为类型层面的保证，而不是靠调用方自觉。
 */
data class DailySessionItem(
    val id: String,
    val title: String,
    val subjectId: String,
    val subjectName: String,
    val subjectColor: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val isExam: Boolean,
    val score: Double? = null,
    val maxScore: Double? = null,
    val note: String = "",
    val pauseCount: Int = 0,
    val mode: String = "正向计时",
    /** 关联的「今日计划」id；不属于任何计划时为 null。模考恒为 null（模考不挂计划）。 */
    val taskId: String? = null
)

data class DailyStudySummary(
    val date: String, // "yyyy-MM-dd"
    val formattedDate: String, // "2026年9月6日 星期日"
    val totalDurationSeconds: Long,
    val focusCount: Int,
    val examCount: Int,
    val subjectDistribution: Map<String, Long>, // subjectName -> seconds
    val sessions: List<DailySessionItem>
)

object DurationFormatter {
    fun formatFullDateWithWeekday(dateStr: String): String {
        val date = YanjiTime.parseIsoDate(dateStr) ?: return dateStr
        return YanjiTime.formatFullDateWithWeekday(date)
    }
}

/**
 * 组装某一天的摘要。输入是**已经限定在当天窗口内的**专注 / 模考行
 * （由 `DailySnapshotReader` 的区间查询提供），因此这里只做归属过滤与映射，不做全表扫描。
 */
internal fun buildDailyStudySummary(
    dateStr: String,
    focusList: List<FocusSession>,
    examList: List<ExamSession>,
    subjects: List<Subject>
): DailyStudySummary {
    val requestedDate = YanjiTime.parseIsoDate(dateStr)
    val dayFocus = focusList.filter {
        it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == requestedDate
    }
    val dayExams = examList.filter {
        it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == requestedDate
    }

    val colorFor: (String, String) -> String = { subjectId, subjectName ->
        resolveStudySubjectColor(subjectId, subjectName, subjects)
    }
    val allItems = (dayFocus.map { toDailyFocusSessionItem(it, colorFor) } +
        dayExams.map { toDailyExamSessionItem(it, colorFor) })
        .sortedByDescending { it.startTime }

    return DailyStudySummary(
        date = dateStr,
        formattedDate = DurationFormatter.formatFullDateWithWeekday(dateStr),
        totalDurationSeconds = allItems.sumOf { it.durationSeconds },
        focusCount = dayFocus.size,
        examCount = dayExams.size,
        subjectDistribution = buildNamedSessionDistribution(allItems, SubjectStatsLevel.SUBCATEGORY),
        sessions = allItems
    )
}

private fun toDailyFocusSessionItem(
    session: FocusSession,
    subjectColor: (String, String) -> String
): DailySessionItem = DailySessionItem(
    id = session.id,
    title = if (session.note.isNotBlank()) session.note else "${session.subjectName} 专注",
    subjectId = session.subjectId,
    subjectName = session.subjectName,
    subjectColor = subjectColor(session.subjectId, session.subjectName),
    startTime = session.startTime,
    endTime = session.endTime,
    durationSeconds = session.durationSeconds,
    isExam = false,
    note = session.note,
    pauseCount = session.pauseCount,
    mode = session.mode,
    taskId = session.taskId
)

private fun toDailyExamSessionItem(
    session: ExamSession,
    subjectColor: (String, String) -> String
): DailySessionItem = DailySessionItem(
    id = session.id,
    title = session.subjectName,
    subjectId = session.subjectId,
    subjectName = session.subjectName,
    subjectColor = subjectColor(session.subjectId, session.subjectName),
    startTime = session.startTime,
    endTime = session.endTime,
    durationSeconds = session.actualDurationSeconds,
    isExam = true,
    score = session.score,
    maxScore = session.maxScore,
    note = session.note,
    pauseCount = 0,
    mode = "全真模拟"
)

private fun resolveStudySubjectColor(
    subjectId: String,
    subjectName: String,
    subjects: List<Subject>
): String {
    val found = SubjectCatalog.find(subjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX))
        ?: subjects.find { it.id == subjectId || it.name == subjectName }
    if (found != null) return found.colorHex
    return SubjectCatalog.find(SubjectCatalog.inferCategoryId(subjectId, subjectName))?.colorHex
        ?: SubjectCatalog.DEFAULT_FALLBACK_COLOR
}

private fun buildNamedSessionDistribution(
    items: List<DailySessionItem>,
    level: SubjectStatsLevel
): Map<String, Long> {
    val result = linkedMapOf<String, Long>()
    items.forEach { item ->
        val id = when (level) {
            SubjectStatsLevel.CATEGORY -> SubjectCatalog.inferCategoryId(item.subjectId, item.subjectName)
            SubjectStatsLevel.SUBCATEGORY -> SubjectCatalog.subcategoryBucketId(item.subjectId, item.subjectName)
        }
        val name = SubjectCatalog.displayName(id) ?: item.subjectName
        result[name] = (result[name] ?: 0L) + item.durationSeconds
    }
    return result
}