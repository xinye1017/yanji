package com.example.yanji.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.example.yanji.data.db.StudySubjectAggregateRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StudyTimeRange(val title: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("本月"),
    ALL("全部"),
    /** 自然年：1 月 1 日 00:00 起。追加在末尾，既有 ordinal 与科目详情页选项不变。 */
    YEAR("本年")
}

enum class SubjectStatsLevel(val title: String) {
    SUBCATEGORY("子类"),
    CATEGORY("大类")
}

data class SubjectDistributionItem(
    val subjectId: String,
    val subjectName: String,
    val subjectColor: String,
    val durationSeconds: Long
)

/**
 * 领域模型对 Compose 是**只读不可变**的：字段全为 val、且不含任何集合字段。
 * 标注后编译器能把它们视为稳定类型，避免以 List/整体对象作参数时跳过失败。
 *
 * 只加在真正平坦的 val-only 类型上——含 List/Map 的类型（如 NoteEntry、DayBarData）
 * 标注 @Immutable 等于对编译器撒谎，不要加。
 */
@Immutable
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
    val mode: String = "正向计时"
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

data class SubjectStudySummary(
    val subjectId: String,
    val subjectName: String,
    val subjectColor: String,
    val timeRange: StudyTimeRange,
    val totalDurationSeconds: Long,
    val sessionCount: Int,
    val longestSessionSeconds: Long,
    val sessions: List<DailySessionItem>
)

data class DayBarData(
    val date: String, // "yyyy-MM-dd"
    val dayLabel: String, // "周日", "周一", etc.
    val durationSeconds: Long,
    val isToday: Boolean,
    val subjectDistribution: Map<String, Long>
)

data class WeeklyStudySummary(
    val totalDurationSeconds: Long,
    val dailyAverageSeconds: Long,
    val activeDays: Int,
    val longestSession: DailySessionItem?,
    val examCount: Int,
    val streakDays: Int,
    val days: List<DayBarData>
)

data class MonthlyStudySummary(
    val totalDurationSeconds: Long,
    val dailyAverageSeconds: Long,
    val activeDays: Int,
    val longestSession: DailySessionItem?,
    val examCount: Int,
    val streakDays: Int,
    val year: Int,
    val month: Int,
    val firstDayOfWeek: DayOfWeek,
    val days: List<DayBarData>
)

/**
 * 月度聚合条：「本年」视图趋势图的横轴一格。
 *
 * 用 [label]（"3月"）而不是 DayBarData 的 weekday 标签：
 * 本年视图的时间粒度是月，硬套「日」标签会让图表语义错位。
 */
data class MonthBarData(
    val year: Int,
    val month: Int, // 1..12
    val label: String,
    val durationSeconds: Long,
    /** 当月有效学习天数（>= 30min），口径与周/月汇总一致。 */
    val activeDays: Int,
    val isCurrentMonth: Boolean,
    /** 当月科目投入（展示名 -> 秒数），按月粒度倒序，供月度抽屉展示。 */
    val subjectDistribution: Map<String, Long> = emptyMap()
)

/**
 * 本年汇总：所有口径都是**自然年**（1 月 1 日 00:00 起，到今天为止）。
 *
 * [dailyAverageSeconds] 的分母是 [activeDays]（有效学习天数），不是自然日跨度 ——
 * 年初的空档会把日均稀释到失真，按有效天数算才反映「学的时候平均学多少」。
 */
data class YearlyStudySummary(
    val totalDurationSeconds: Long = 0L,
    val dailyAverageSeconds: Long = 0L,
    val activeDays: Int = 0,
    /** 本年最长连续学习天数（不是「当前连续」，不受日边界截断）。 */
    val longestStreakDays: Int = 0,
    val longestSession: DailySessionItem? = null,
    val examCount: Int = 0,
    /** 1 月..当月，未学习的月份也占位（时长为 0）。 */
    val months: List<MonthBarData> = emptyList()
)

object DurationFormatter {
    fun formatHoursMinutes(seconds: Long): String {
        if (seconds <= 0) return "0m"
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun formatDetailed(seconds: Long): String {
        if (seconds <= 0) return "0秒"
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 && secs > 0 -> "${mins}m ${secs}s"
            mins > 0 -> "${mins}m"
            else -> "${secs}s"
        }
    }

    fun formatTimeRange(startTime: Long, endTime: Long): String {
        val startStr = YanjiTime.formatTime(startTime)
        val endStr = if (endTime > startTime) YanjiTime.formatTime(endTime) else startStr
        return "$startStr — $endStr"
    }

    fun formatDateChinese(timestamp: Long): String = YanjiTime.formatChineseDate(timestamp)

    fun formatDateWithWeekday(dateStr: String): String {
        val date = YanjiTime.parseIsoDate(dateStr) ?: return dateStr
        return YanjiTime.formatShortDateWithWeekday(date)
    }

    fun formatFullDateWithWeekday(dateStr: String): String {
        val date = YanjiTime.parseIsoDate(dateStr) ?: return dateStr
        return YanjiTime.formatFullDateWithWeekday(date)
    }
}

class StudyStatisticsRepository(
    private val repo: YanjiRepository
) {
    /**
     * 「有效学习天数」阈值：当天累计 >= 用户配置分钟数才算一天。
     *
     * 口径**唯一来源**是 user_settings.validStudyThresholdMinutes（默认 30min）——
     * AI 学情诊断读的就是这个设置（见 [com.example.yanji.data.ai.StudyDataProvider]）。
     * 早先这里写死 1800L：用户在设置里改了阈值，统计页的"有效 N 天"纹丝不动，
     * 与 AI 诊断的同一名词当场对不上账。
     *
     * 周 / 月 / 年三个汇总 Flow 都从这个 Flow 派生同一个阈值 —— 阈值一旦分叉，
     * 「有效学习 N 天」在三个视角下就不是同一件事，对比就失去意义。
     */
    private val activeDayThresholdFlow: Flow<Long> =
        repo.settings.map { it.validStudyThresholdMinutes.coerceAtLeast(1).toLong() * 60L }

    /** 汇总构建期的兜底阈值：Flow 尚未吐出首个值时与用户默认值一致。 */
    private val activeDayThresholdSecondsFallback = 30L * 60L

    private fun getSubjectColor(subjectId: String, subjectName: String): String {
        val found = SubjectCatalog.find(subjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX))
            ?: repo.subjects.value.find { it.id == subjectId || it.name == subjectName }
        if (found != null) return found.colorHex
        return SubjectCatalog.find(SubjectCatalog.inferCategoryId(subjectId, subjectName))?.colorHex
            ?: SubjectCatalog.DEFAULT_FALLBACK_COLOR
    }

    private fun focusToSessionItem(fs: FocusSession): DailySessionItem {
        return DailySessionItem(
            id = fs.id,
            title = if (fs.note.isNotBlank()) fs.note else "${fs.subjectName} 专注",
            subjectId = fs.subjectId,
            subjectName = fs.subjectName,
            subjectColor = getSubjectColor(fs.subjectId, fs.subjectName),
            startTime = fs.startTime,
            endTime = fs.endTime,
            durationSeconds = fs.durationSeconds,
            isExam = false,
            note = fs.note,
            pauseCount = fs.pauseCount,
            mode = fs.mode
        )
    }

    private fun examToSessionItem(es: ExamSession): DailySessionItem {
        return DailySessionItem(
            id = es.id,
            title = es.subjectName,
            subjectId = es.subjectId,
            subjectName = es.subjectName,
            subjectColor = getSubjectColor(es.subjectId, es.subjectName),
            startTime = es.startTime,
            endTime = es.endTime,
            durationSeconds = es.actualDurationSeconds,
            isExam = true,
            score = es.score,
            maxScore = es.maxScore,
            note = es.note,
            pauseCount = 0,
            mode = "全真模拟"
        )
    }

    /**
     * Get summary for a specific date (defaults to today).
     * Single Source of Truth: Sum of valid focus + valid exam sessions on that day.
     */
    fun getDailyStudySummaryFlow(dateStr: String): Flow<DailyStudySummary> {
        val date = YanjiTime.parseIsoDate(dateStr) ?: return flowOf(
            buildDailyStudySummary(dateStr, emptyList(), emptyList())
        )
        val range = YanjiTime.dayRange(date)
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive)
        ) { focusList, examList ->
            buildDailyStudySummary(dateStr, focusList, examList)
        }
    }

    fun getDailyStudySummary(dateStr: String): DailyStudySummary {
        val date = YanjiTime.parseIsoDate(dateStr) ?: return buildDailyStudySummary(dateStr, emptyList(), emptyList())
        val range = YanjiTime.dayRange(date)
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        return buildDailyStudySummary(dateStr, focusList, examList)
    }

    private fun buildDailyStudySummary(
        dateStr: String,
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): DailyStudySummary {
        val requestedDate = YanjiTime.parseIsoDate(dateStr)
        val dayFocus = focusList.filter {
            it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == requestedDate
        }
        val dayExams = examList.filter {
            it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == requestedDate
        }

        val allItems = mutableListOf<DailySessionItem>()
        allItems.addAll(dayFocus.map { focusToSessionItem(it) })
        allItems.addAll(dayExams.map { examToSessionItem(it) })
        allItems.sortByDescending { it.startTime }

        val totalDuration = allItems.sumOf { it.durationSeconds }
        val subjectDist = buildNamedDistribution(allItems, SubjectStatsLevel.SUBCATEGORY)

        return DailyStudySummary(
            date = dateStr,
            formattedDate = DurationFormatter.formatFullDateWithWeekday(dateStr),
            totalDurationSeconds = totalDuration,
            focusCount = dayFocus.size,
            examCount = dayExams.size,
            subjectDistribution = subjectDist,
            sessions = allItems
        )
    }

    /**
     * Get subject study summary across a given time range.
     */
    fun getSubjectStudySummaryFlow(
        subjectId: String,
        timeRange: StudyTimeRange
    ): Flow<SubjectStudySummary> {
        val range = YanjiTime.rangeFor(timeRange)
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive),
            repo.subjects
        ) { focusList, examList, subjectsList ->
            buildSubjectStudySummary(subjectId, timeRange, focusList, examList, subjectsList)
        }
    }

    fun getSubjectDistributionFlow(
        range: EpochRange,
        level: SubjectStatsLevel
    ): Flow<List<SubjectDistributionItem>> {
        return combine(
            repo.observeFocusSubjectTotals(range.startInclusive, range.endExclusive),
            repo.observeExamSubjectTotals(range.startInclusive, range.endExclusive)
        ) { focusRows, examRows ->
            buildSubjectDistributionFromAggregates(level, focusRows + examRows)
        }
    }

    /**
     * 指定日历窗口的有效学习时长。
     *
     * 窗口由调用方用 [YanjiTime] 现取，而不是收一个「时间范围 + 翻几期」的组合：
     * 区间本身已经带上了「翻到第几期」的语义，仓库只负责按窗口汇总。
     */
    fun getStudyDurationFlow(range: EpochRange): Flow<Long> {
        return repo.observeStudyDuration(range.startInclusive, range.endExclusive)
    }

    /** 滚动 N 天总时长（含今天）。「本年」视角的 hero 用「近 7 天」替代无意义的「较上周」。 */
    fun getLastDaysDurationFlow(days: Long): Flow<Long> {
        val range = YanjiTime.lastDaysRange(days)
        return repo.observeStudyDuration(range.startInclusive, range.endExclusive)
    }

    /**
     * 本年汇总：自然年区间（1 月 1 日 00:00 起）的专注 + 模考。
     *
     * 区间有界（最多 366 天），走 startTime 索引；日粒度与月粒度聚合都在 Kotlin 侧完成，
     * 不引入按 UTC 月份分组的 SQL —— 那种写法在跨月/跨年边界会差 8 小时，
     * 而这里的数据量级（一年几百条）还远没到需要把分组下推到 SQL 的程度。
     */
    fun getYearlyStudySummaryFlow(): Flow<YearlyStudySummary> {
        val range = YanjiTime.currentYearRange()
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive),
            activeDayThresholdFlow
        ) { focusList, examList, threshold ->
            buildYearlyStudySummary(focusList, examList, activeDayThresholdSeconds = threshold)
        }
    }

    fun getSubjectDistribution(
        timeRange: StudyTimeRange,
        level: SubjectStatsLevel
    ): List<SubjectDistributionItem> {
        val range = YanjiTime.rangeFor(timeRange)
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        return buildSubjectDistribution(
            timeRange,
            level,
            focusList,
            examList
        )
    }

    fun getSubjectStudySummary(
        subjectId: String,
        timeRange: StudyTimeRange
    ): SubjectStudySummary {
        val range = YanjiTime.rangeFor(timeRange)
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        return buildSubjectStudySummary(
            subjectId,
            timeRange,
            focusList,
            examList,
            repo.subjects.value
        )
    }

    private fun buildSubjectStudySummary(
        subjectId: String,
        timeRange: StudyTimeRange,
        focusList: List<FocusSession>,
        examList: List<ExamSession>,
        subjectsList: List<Subject>
    ): SubjectStudySummary {
        val resolvedSubjectId = SubjectCatalog.idForDisplayName(subjectId) ?: subjectId
        val subject = subjectsList.find { it.id == resolvedSubjectId || it.name == subjectId }
            ?: SubjectCatalog.find(resolvedSubjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX))
        val subjectName = SubjectCatalog.displayName(resolvedSubjectId) ?: subject?.name ?: subjectId
        val subjectColor = subject?.colorHex ?: getSubjectColor(resolvedSubjectId, subjectName)
        val cutoff = cutoffFor(timeRange)

        fun matches(recordId: String, recordName: String): Boolean {
            if (SubjectCatalog.isDirectBucket(resolvedSubjectId)) {
                return SubjectCatalog.subcategoryBucketId(recordId, recordName) == resolvedSubjectId
            }
            val catalogSubject = SubjectCatalog.find(resolvedSubjectId)
            return if (catalogSubject?.isCategory == true) {
                SubjectCatalog.inferCategoryId(recordId, recordName) == resolvedSubjectId
            } else {
                recordId == resolvedSubjectId || recordName == subjectName
            }
        }

        val matchedFocus = focusList.filter {
            it.status == SessionStatus.COMPLETED && matches(it.subjectId, it.subjectName) && it.startTime >= cutoff
        }
        val matchedExams = examList.filter {
            it.status == SessionStatus.COMPLETED && matches(it.subjectId, it.subjectName) && it.startTime >= cutoff
        }

        val allItems = mutableListOf<DailySessionItem>()
        allItems.addAll(matchedFocus.map { focusToSessionItem(it) })
        allItems.addAll(matchedExams.map { examToSessionItem(it) })
        allItems.sortByDescending { it.startTime }

        val totalSecs = allItems.sumOf { it.durationSeconds }
        val longestSecs = allItems.maxOfOrNull { it.durationSeconds } ?: 0L

        return SubjectStudySummary(
            subjectId = resolvedSubjectId,
            subjectName = subjectName,
            subjectColor = subjectColor,
            timeRange = timeRange,
            totalDurationSeconds = totalSecs,
            sessionCount = allItems.size,
            longestSessionSeconds = longestSecs,
            sessions = allItems
        )
    }

    private fun buildSubjectDistribution(
        timeRange: StudyTimeRange,
        level: SubjectStatsLevel,
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): List<SubjectDistributionItem> {
        val cutoff = cutoffFor(timeRange)
        val totals = linkedMapOf<String, Long>()

        fun add(subjectId: String, subjectName: String, seconds: Long) {
            val bucketId = when (level) {
                SubjectStatsLevel.CATEGORY -> SubjectCatalog.inferCategoryId(subjectId, subjectName)
                SubjectStatsLevel.SUBCATEGORY -> SubjectCatalog.subcategoryBucketId(subjectId, subjectName)
            }
            totals[bucketId] = (totals[bucketId] ?: 0L) + seconds
        }

        focusList.asSequence()
            .filter { it.status == SessionStatus.COMPLETED && it.startTime >= cutoff }
            .forEach { add(it.subjectId, it.subjectName, it.durationSeconds) }
        examList.asSequence()
            .filter { it.status == SessionStatus.COMPLETED && it.startTime >= cutoff }
            .forEach { add(it.subjectId, it.subjectName, it.actualDurationSeconds) }

        val baseIds = when (level) {
            SubjectStatsLevel.CATEGORY -> SubjectCatalog.categories.map { it.id }
            SubjectStatsLevel.SUBCATEGORY -> SubjectCatalog.selectableSubjects.map { it.id }
        }
        val directIds = totals.keys.filter(SubjectCatalog::isDirectBucket)
        val orderedIds = (baseIds + directIds).distinct().sortedWith(
            compareBy<String> { id ->
                val category = SubjectCatalog.categoryOf(id)
                category?.sortOrder ?: Int.MAX_VALUE
            }.thenBy { id ->
                when {
                    SubjectCatalog.isDirectBucket(id) -> Int.MAX_VALUE
                    else -> SubjectCatalog.find(id)?.sortOrder ?: Int.MAX_VALUE
                }
            }
        )

        return orderedIds.map { id ->
            val name = SubjectCatalog.displayName(id) ?: id
            SubjectDistributionItem(
                subjectId = id,
                subjectName = name,
                subjectColor = getSubjectColor(id, name),
                durationSeconds = totals[id] ?: 0L
            )
        }
    }

    private fun buildSubjectDistributionFromAggregates(
        level: SubjectStatsLevel,
        rows: List<StudySubjectAggregateRow>
    ): List<SubjectDistributionItem> {
        val totals = linkedMapOf<String, Long>()
        rows.forEach { row ->
            val bucketId = when (level) {
                SubjectStatsLevel.CATEGORY -> SubjectCatalog.inferCategoryId(row.subjectId, row.subjectName)
                SubjectStatsLevel.SUBCATEGORY -> SubjectCatalog.subcategoryBucketId(row.subjectId, row.subjectName)
            }
            totals[bucketId] = (totals[bucketId] ?: 0L) + row.durationSeconds
        }

        val baseIds = when (level) {
            SubjectStatsLevel.CATEGORY -> SubjectCatalog.categories.map { it.id }
            SubjectStatsLevel.SUBCATEGORY -> SubjectCatalog.selectableSubjects.map { it.id }
        }
        val directIds = totals.keys.filter(SubjectCatalog::isDirectBucket)
        val orderedIds = (baseIds + directIds).distinct().sortedWith(
            compareBy<String> { id -> SubjectCatalog.categoryOf(id)?.sortOrder ?: Int.MAX_VALUE }
                .thenBy { id ->
                    if (SubjectCatalog.isDirectBucket(id)) Int.MAX_VALUE
                    else SubjectCatalog.find(id)?.sortOrder ?: Int.MAX_VALUE
                }
        )
        return orderedIds.map { id ->
            val name = SubjectCatalog.displayName(id) ?: id
            SubjectDistributionItem(id, name, getSubjectColor(id, name), totals[id] ?: 0L)
        }
    }

    private fun buildNamedDistribution(
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

    private fun cutoffFor(timeRange: StudyTimeRange): Long {
        return YanjiTime.rangeFor(timeRange).startInclusive
    }

    /**
     * Get Weekly summary for statistics and charts.
     *
     * [weeksBack] 支持回看自然周：0 = 本周，1 = 上周，以此类推。
     */
    fun getWeeklyStudySummaryFlow(weeksBack: Long = 0): Flow<WeeklyStudySummary> {
        val range = YanjiTime.weekRange(weeksBack)
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive),
            activeDayThresholdFlow
        ) { focusList, examList, threshold ->
            buildWeeklyStudySummary(focusList, examList, weeksBack, activeDayThresholdSeconds = threshold)
        }
    }

    fun getWeeklyStudySummary(weeksBack: Long = 0): WeeklyStudySummary {
        val range = YanjiTime.weekRange(weeksBack)
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val threshold = repo.settings.value.validStudyThresholdMinutes.coerceAtLeast(1).toLong() * 60L
        return buildWeeklyStudySummary(focusList, examList, weeksBack, activeDayThresholdSeconds = threshold)
    }

    /**
     * internal 而非 private：周一至周日 7 根柱子的边界要能被 JVM 单测钉住
     * （回看上一周时，第一根必须是那个周一而不是今天），见 StatsPreviousPeriodTest。
     *
     * @param today 可注入的「今天」，单测靠它把自然周边界钉死，避免跨周时结论漂移。
     * @param activeDayThresholdSeconds 有效天数阈值（秒）。默认取用户设置默认值 30min；
     *   Flow 调用方显式传入 [activeDayThresholdFlow] 当前值，与 AI 诊断共用同一口径。
     */
    internal fun buildWeeklyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>,
        weeksBack: Long = 0,
        today: LocalDate = YanjiTime.today(),
        activeDayThresholdSeconds: Long = 30L * 60L
    ): WeeklyStudySummary {
        val monday = today.minusWeeks(weeksBack)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = mutableListOf<DayBarData>()

        // "本周" is a calendar week (Monday through Sunday), not a rolling seven-day window.
        for (offset in 0L..6L) {
            val date = monday.plusDays(offset)
            val dateStr = date.format(YanjiTime.isoDateFormatter)
            val label = chineseWeekday(date)

            val dFocus = focusList.filter {
                it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == date
            }
            val dExams = examList.filter {
                it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == date
            }
            val total = dFocus.sumOf { it.durationSeconds } + dExams.sumOf { it.actualDurationSeconds }

            val subjectMap = buildNamedDistribution(
                dFocus.map(::focusToSessionItem) + dExams.map(::examToSessionItem),
                SubjectStatsLevel.SUBCATEGORY
            )

            days.add(
                DayBarData(
                    date = dateStr,
                    dayLabel = label,
                    durationSeconds = total,
                    isToday = date == today,
                    subjectDistribution = subjectMap
                )
            )
        }

        val totalDuration = days.sumOf { it.durationSeconds }
        val activeDays = days.count { it.durationSeconds >= activeDayThresholdSeconds } // >= 30m
        val dailyAvg = if (days.isNotEmpty()) totalDuration / days.size else 0L

        // Find longest session in the 7 days
        val recentFocus = focusList.filter { it.status == SessionStatus.COMPLETED }.map(::focusToSessionItem)
        val recentExams = examList.filter { it.status == SessionStatus.COMPLETED }.map(::examToSessionItem)
        val allRecent = recentFocus + recentExams
        val longest = allRecent.maxByOrNull { it.durationSeconds }
        val recentExamCount = recentExams.size

        // Calculate consecutive active streak days
        var streak = 0
        for (i in days.indices.reversed()) {
            if (days[i].durationSeconds >= activeDayThresholdSeconds) {
                streak++
            } else if (!days[i].isToday) {
                break
            }
        }

        return WeeklyStudySummary(
            totalDurationSeconds = totalDuration,
            dailyAverageSeconds = dailyAvg,
            activeDays = activeDays,
            longestSession = longest,
            examCount = recentExamCount,
            // 如实呈现：0 天就是 0 天，不用 maxOf(1, ...) 伪造（消费者已按 0 分支处理文案）
            streakDays = streak,
            days = days
        )
    }

    private fun chineseWeekday(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    /**
     * Get Monthly summary for statistics, heatmap, and monthly charts.
     *
     * [monthsBack] 支持回看自然月：0 = 本月，1 = 上月，以此类推。
     */
    fun getMonthlyStudySummaryFlow(monthsBack: Long = 0): Flow<MonthlyStudySummary> {
        val range = YanjiTime.monthRange(monthsBack)
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive),
            activeDayThresholdFlow
        ) { focusList, examList, threshold ->
            buildMonthlyStudySummary(focusList, examList, monthsBack, activeDayThresholdSeconds = threshold)
        }
    }

    fun getMonthlyStudySummary(monthsBack: Long = 0): MonthlyStudySummary {
        val range = YanjiTime.monthRange(monthsBack)
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val threshold = repo.settings.value.validStudyThresholdMinutes.coerceAtLeast(1).toLong() * 60L
        return buildMonthlyStudySummary(focusList, examList, monthsBack, activeDayThresholdSeconds = threshold)
    }

    /**
     * internal 而非 private：1 日至月末的柱数与首星期几要能被 JVM 单测钉住
     * （2 月是 28/29 天，回看上月时不能被「本月天数」带偏），见 StatsPreviousPeriodTest。
     *
     * @param today 可注入的「今天」，单测靠它把自然月边界钉死，避免跨月时结论漂移。
     * @param activeDayThresholdSeconds 有效天数阈值（秒），见 [buildWeeklyStudySummary]。
     */
    internal fun buildMonthlyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>,
        monthsBack: Long = 0,
        today: LocalDate = YanjiTime.today(),
        activeDayThresholdSeconds: Long = 30L * 60L
    ): MonthlyStudySummary {
        val anchor = today.minusMonths(monthsBack)
        val firstDay = anchor.withDayOfMonth(1)
        val daysInMonth = anchor.lengthOfMonth()
        val days = mutableListOf<DayBarData>()

        for (dayNum in 1..daysInMonth) {
            val date = firstDay.withDayOfMonth(dayNum)
            val dateStr = date.format(YanjiTime.isoDateFormatter)
            val label = "${dayNum}日"

            val dFocus = focusList.filter {
                it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == date
            }
            val dExams = examList.filter {
                it.status == SessionStatus.COMPLETED && YanjiTime.localDate(it.startTime) == date
            }
            val total = dFocus.sumOf { it.durationSeconds } + dExams.sumOf { it.actualDurationSeconds }

            val subjectMap = buildNamedDistribution(
                dFocus.map(::focusToSessionItem) + dExams.map(::examToSessionItem),
                SubjectStatsLevel.SUBCATEGORY
            )

            days.add(
                DayBarData(
                    date = dateStr,
                    dayLabel = label,
                    durationSeconds = total,
                    isToday = date == today,
                    subjectDistribution = subjectMap
                )
            )
        }

        val totalDuration = days.sumOf { it.durationSeconds }
        val activeDays = days.count { it.durationSeconds >= activeDayThresholdSeconds } // >= 30m
        val dailyAvg = if (days.isNotEmpty()) totalDuration / days.size else 0L

        val recentFocus = focusList.filter { it.status == SessionStatus.COMPLETED }.map(::focusToSessionItem)
        val recentExams = examList.filter { it.status == SessionStatus.COMPLETED }.map(::examToSessionItem)
        val allRecent = recentFocus + recentExams
        val longest = allRecent.maxByOrNull { it.durationSeconds }
        val recentExamCount = recentExams.size

        var streak = 0
        for (i in days.indices.reversed()) {
            if (days[i].durationSeconds >= activeDayThresholdSeconds) {
                streak++
            } else if (!days[i].isToday) {
                break
            }
        }

        return MonthlyStudySummary(
            totalDurationSeconds = totalDuration,
            dailyAverageSeconds = dailyAvg,
            activeDays = activeDays,
            longestSession = longest,
            examCount = recentExamCount,
            streakDays = streak,
            // 报的是**当前展示的那个自然月**，不是今天所在的月：
            // 回看上月时这两个数不同，抽屉与热力图靠它们认月份。
            year = anchor.year,
            month = anchor.monthValue,
            firstDayOfWeek = firstDay.dayOfWeek,
            days = days
        )
    }

    /**
     * 本年汇总构建：日粒度打底（有效天数、最长连续），再向上聚合成月粒度条。
     *
     * internal 而非 private：聚合口径（分母取有效天数、连续天数取本年最长一段、
     * 未学习的月份也要占位）需要被 JVM 单测钉住，见 StudyStatisticsRepositoryYearlyTest。
     *
     * @param today 可注入的“今天”，单测靠它把自然年边界钉死，避免跨年时结论漂移。
     * @param activeDayThresholdSeconds 有效天数阈值（秒），见 [buildWeeklyStudySummary]。
     */
    internal fun buildYearlyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>,
        today: LocalDate = YanjiTime.today(),
        activeDayThresholdSeconds: Long = 30L * 60L
    ): YearlyStudySummary {
        val yearStart = today.withDayOfYear(1)

        // 只认本自然年、且不晚于今天的 COMPLETED 记录：调用方（流程查询）已经按年区间过滤，
        // 这里再收口一次，是为了让这个函数对任意入参都能自洽（单测直接喂列表）。
        fun inThisYear(startTime: Long): Boolean {
            val date = YanjiTime.localDate(startTime)
            return !date.isBefore(yearStart) && !date.isAfter(today)
        }

        val completedFocus = focusList.filter {
            it.status == SessionStatus.COMPLETED && inThisYear(it.startTime)
        }
        val completedExams = examList.filter {
            it.status == SessionStatus.COMPLETED && inThisYear(it.startTime)
        }

        // 日粒度：日期 -> 当天秒数。只存 >0 的天，未学习天然缺席。
        val dayTotals = HashMap<LocalDate, Long>()
        completedFocus.forEach {
            dayTotals.merge(YanjiTime.localDate(it.startTime), it.durationSeconds, Long::plus)
        }
        completedExams.forEach {
            dayTotals.merge(YanjiTime.localDate(it.startTime), it.actualDurationSeconds, Long::plus)
        }

        val totalDuration = dayTotals.values.sum()
        val activeDays = dayTotals.count { it.value >= activeDayThresholdSeconds }
        // 分母是有效学习天数：按自然日跨度算会被年初空档稀释（见 YearlyStudySummary 注释）。
        val dailyAvg = if (activeDays > 0) totalDuration / activeDays else 0L

        // 本年最长连续段：从 1 月 1 日扫到今天，取最长的一段，
        // 而不是「当前连续」—— 后者在年初断一次就归零，作为年度指标信息量太低。
        var longestStreak = 0
        var runningStreak = 0
        var cursor = yearStart
        while (!cursor.isAfter(today)) {
            if ((dayTotals[cursor] ?: 0L) >= activeDayThresholdSeconds) {
                runningStreak++
                if (runningStreak > longestStreak) longestStreak = runningStreak
            } else {
                runningStreak = 0
            }
            cursor = cursor.plusDays(1)
        }

        // 月粒度：1 月..当月全部占位，没学过的月份也要有一根 0 柱，
        // 否则横轴会随数据跳动（2 月没学 → 柱子从 3 月开始，读者以为年初是 3 月）。
        val secondsByMonth = IntArray(13)
        val activeDaysByMonth = IntArray(13)
        dayTotals.forEach { (date, seconds) ->
            secondsByMonth[date.monthValue] += seconds.toInt()
            if (seconds >= activeDayThresholdSeconds) activeDaysByMonth[date.monthValue]++
        }

        // 当月科目分布：抽屉里点开某个月要能看到各科投了多少。
        // 桶口径与日视图一致（子类 + 目录展示名），只是按月份分开存。
        val distributionByMonth = Array(13) { HashMap<String, Long>() }
        fun addMonthSubject(date: LocalDate, subjectId: String, subjectName: String, seconds: Long) {
            val bucketId = SubjectCatalog.subcategoryBucketId(subjectId, subjectName)
            val name = SubjectCatalog.displayName(bucketId) ?: subjectName
            distributionByMonth[date.monthValue].merge(name, seconds, Long::plus)
        }
        completedFocus.forEach {
            addMonthSubject(YanjiTime.localDate(it.startTime), it.subjectId, it.subjectName, it.durationSeconds)
        }
        completedExams.forEach {
            addMonthSubject(YanjiTime.localDate(it.startTime), it.subjectId, it.subjectName, it.actualDurationSeconds)
        }

        val months = (1..today.monthValue).map { month ->
            MonthBarData(
                year = today.year,
                month = month,
                label = "${month}月",
                durationSeconds = secondsByMonth[month].toLong(),
                activeDays = activeDaysByMonth[month],
                isCurrentMonth = month == today.monthValue,
                subjectDistribution = distributionByMonth[month]
                    .toList()
                    .sortedByDescending { it.second }
                    .toMap()
            )
        }

        val allItems = completedFocus.map(::focusToSessionItem) + completedExams.map(::examToSessionItem)

        return YearlyStudySummary(
            totalDurationSeconds = totalDuration,
            dailyAverageSeconds = dailyAvg,
            activeDays = activeDays,
            longestStreakDays = longestStreak,
            longestSession = allItems.maxByOrNull { it.durationSeconds },
            examCount = completedExams.size,
            months = months
        )
    }

    /**
     * Get a specific session detail by ID.
     */
    suspend fun getFocusSessionDetail(sessionId: String): DailySessionItem? {
        val fs = repo.focusSessions.value.find { it.id == sessionId }
        if (fs != null) return focusToSessionItem(fs)
        val entity = repo.getFocusSessionByIdFromDb(sessionId)
        return entity?.toDomainModel()?.let { focusToSessionItem(it) }
    }

    suspend fun getExamSessionDetail(examId: String): DailySessionItem? {
        val es = repo.examSessions.value.find { it.id == examId }
        if (es != null) return examToSessionItem(es)
        val entity = repo.getExamSessionByIdFromDb(examId)
        return entity?.toDomainModel()?.let { examToSessionItem(it) }
    }

    fun deleteSession(item: DailySessionItem) {
        if (item.isExam) {
            repo.deleteExamSession(item.id)
        } else {
            repo.deleteFocusSession(item.id)
        }
    }

    fun updateSessionNote(sessionId: String, isExam: Boolean, note: String) {
        if (!isExam) {
            repo.updateFocusSessionNote(sessionId, note)
        }
    }
}
