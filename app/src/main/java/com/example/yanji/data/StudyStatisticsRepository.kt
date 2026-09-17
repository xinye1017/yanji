package com.example.yanji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.example.yanji.data.db.StudySubjectAggregateRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class StudyTimeRange(val title: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("本月"),
    ALL("全部")
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
    private val repo: YanjiRepository = YanjiRepository.getInstance()
) {
    companion object {
        @Volatile
        private var instance: StudyStatisticsRepository? = null

        fun getInstance(): StudyStatisticsRepository {
            return instance ?: synchronized(this) {
                instance ?: StudyStatisticsRepository().also { instance = it }
            }
        }
    }

    private fun getSubjectColor(subjectId: String, subjectName: String): String {
        val found = SubjectCatalog.find(subjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX))
            ?: repo.subjects.value.find { it.id == subjectId || it.name == subjectName }
        if (found != null) return found.colorHex
        return SubjectCatalog.find(SubjectCatalog.inferCategoryId(subjectId, subjectName))?.colorHex
            ?: "#667085"
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
        timeRange: StudyTimeRange,
        level: SubjectStatsLevel
    ): Flow<List<SubjectDistributionItem>> {
        val range = YanjiTime.rangeFor(timeRange)
        return combine(
            repo.observeFocusSubjectTotals(range.startInclusive, range.endExclusive),
            repo.observeExamSubjectTotals(range.startInclusive, range.endExclusive)
        ) { focusRows, examRows ->
            buildSubjectDistributionFromAggregates(level, focusRows + examRows)
        }
    }

    fun getStudyDurationFlow(timeRange: StudyTimeRange): Flow<Long> {
        val range = YanjiTime.rangeFor(timeRange)
        return repo.observeStudyDuration(range.startInclusive, range.endExclusive)
    }

    fun getPreviousCalendarWeekDurationFlow(): Flow<Long> {
        val range = YanjiTime.previousWeekRange()
        return repo.observeStudyDuration(range.startInclusive, range.endExclusive)
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
     */
    fun getWeeklyStudySummaryFlow(): Flow<WeeklyStudySummary> {
        val range = YanjiTime.currentWeekRange()
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive)
        ) { focusList, examList ->
            buildWeeklyStudySummary(focusList, examList)
        }
    }

    fun getWeeklyStudySummary(): WeeklyStudySummary {
        val range = YanjiTime.currentWeekRange()
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        return buildWeeklyStudySummary(focusList, examList)
    }

    private fun buildWeeklyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): WeeklyStudySummary {
        val today = YanjiTime.today()
        val monday = today.with(DayOfWeek.MONDAY)
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
        val activeDays = days.count { it.durationSeconds >= 1800L } // >= 30m
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
            if (days[i].durationSeconds >= 1800L) {
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
     */
    fun getMonthlyStudySummaryFlow(): Flow<MonthlyStudySummary> {
        val range = YanjiTime.currentMonthRange()
        return combine(
            repo.observeFocusSessionsInRange(range.startInclusive, range.endExclusive),
            repo.observeExamSessionsInRange(range.startInclusive, range.endExclusive)
        ) { focusList, examList ->
            buildMonthlyStudySummary(focusList, examList)
        }
    }

    fun getMonthlyStudySummary(): MonthlyStudySummary {
        val range = YanjiTime.currentMonthRange()
        val focusList = repo.focusSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        val examList = repo.examSessions.value.filter { it.startTime >= range.startInclusive && it.startTime < range.endExclusive }
        return buildMonthlyStudySummary(focusList, examList)
    }

    private fun buildMonthlyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): MonthlyStudySummary {
        val today = YanjiTime.today()
        val firstDay = today.withDayOfMonth(1)
        val daysInMonth = today.lengthOfMonth()
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
        val activeDays = days.count { it.durationSeconds >= 1800L } // >= 30m
        val dailyAvg = if (days.isNotEmpty()) totalDuration / days.size else 0L

        val recentFocus = focusList.filter { it.status == SessionStatus.COMPLETED }.map(::focusToSessionItem)
        val recentExams = examList.filter { it.status == SessionStatus.COMPLETED }.map(::examToSessionItem)
        val allRecent = recentFocus + recentExams
        val longest = allRecent.maxByOrNull { it.durationSeconds }
        val recentExamCount = recentExams.size

        var streak = 0
        for (i in days.indices.reversed()) {
            if (days[i].durationSeconds >= 1800L) {
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
            year = today.year,
            month = today.monthValue,
            firstDayOfWeek = firstDay.dayOfWeek,
            days = days
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
