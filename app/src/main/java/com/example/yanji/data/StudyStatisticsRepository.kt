package com.example.yanji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.yanji.data.db.StudySubjectAggregateRow
import java.time.DayOfWeek
import java.time.LocalDate
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
 * 统计结果模型**只读不可变**：字段全为 val，且不含可变集合字段。
 *
 * 这些对象由 Kotlin 统计层构建、经桥接层原样映射给 RN，中途没有任何一处需要回写；
 * 保持扁平 val-only 让「构建完就不再变」成为类型层面的保证，而不是靠调用方自觉。
 * 含 List/Map 的聚合模型（如 NoteEntry、DayBarData）不在此列。
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

    private fun getSubjectColor(subjectId: String, subjectName: String): String =
        resolveStudySubjectColor(subjectId, subjectName, repo.subjects.value)

    private fun focusToSessionItem(fs: FocusSession): DailySessionItem =
        toDailyFocusSessionItem(fs, ::getSubjectColor)

    private fun examToSessionItem(es: ExamSession): DailySessionItem =
        toDailyExamSessionItem(es, ::getSubjectColor)

    private fun buildDailyStudySummary(
        dateStr: String,
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): DailyStudySummary =
        buildDailyStudySummary(dateStr, focusList, examList, repo.subjects.value)

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
    ): Map<String, Long> = buildNamedSessionDistribution(items, level)

    private fun cutoffFor(timeRange: StudyTimeRange): Long {
        return YanjiTime.rangeFor(timeRange).startInclusive
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

    fun updateSessionNote(sessionId: String, isExam: Boolean, note: String) {
        if (!isExam) {
            repo.updateFocusSessionNote(sessionId, note)
        }
    }
}

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
