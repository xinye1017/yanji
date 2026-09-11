package com.example.yanji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

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
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = timeFormat.format(Date(startTime))
        val endStr = if (endTime > startTime) timeFormat.format(Date(endTime)) else startStr
        return "$startStr — $endStr"
    }

    fun formatDateChinese(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE)
        return format.format(Date(timestamp))
    }

    fun formatDateWithWeekday(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(dateStr) ?: return dateStr
            val output = SimpleDateFormat("M 月 d 日 · E", Locale.CHINESE)
            output.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatFullDateWithWeekday(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(dateStr) ?: return dateStr
            val output = SimpleDateFormat("yyyy 年 M 月 d 日 EEEE", Locale.CHINESE)
            output.format(date)
        } catch (e: Exception) {
            dateStr
        }
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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
            note = es.note ?: "",
            pauseCount = 0,
            mode = "全真模拟"
        )
    }

    /**
     * Get summary for a specific date (defaults to today).
     * Single Source of Truth: Sum of valid focus + valid exam sessions on that day.
     */
    fun getDailyStudySummaryFlow(dateStr: String): Flow<DailyStudySummary> {
        return combine(repo.focusSessions, repo.examSessions) { focusList, examList ->
            buildDailyStudySummary(dateStr, focusList, examList)
        }
    }

    fun getDailyStudySummary(dateStr: String): DailyStudySummary {
        return buildDailyStudySummary(dateStr, repo.focusSessions.value, repo.examSessions.value)
    }

    private fun buildDailyStudySummary(
        dateStr: String,
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): DailyStudySummary {
        val dayFocus = focusList.filter {
            it.status == SessionStatus.COMPLETED && dateFormat.format(Date(it.startTime)) == dateStr
        }
        val dayExams = examList.filter {
            it.status == SessionStatus.COMPLETED && dateFormat.format(Date(it.startTime)) == dateStr
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
        return combine(repo.focusSessions, repo.examSessions, repo.subjects) { focusList, examList, subjectsList ->
            buildSubjectStudySummary(subjectId, timeRange, focusList, examList, subjectsList)
        }
    }

    fun getSubjectDistributionFlow(
        timeRange: StudyTimeRange,
        level: SubjectStatsLevel
    ): Flow<List<SubjectDistributionItem>> {
        return combine(repo.focusSessions, repo.examSessions) { focusList, examList ->
            buildSubjectDistribution(timeRange, level, focusList, examList)
        }
    }

    fun getSubjectDistribution(
        timeRange: StudyTimeRange,
        level: SubjectStatsLevel
    ): List<SubjectDistributionItem> = buildSubjectDistribution(
        timeRange,
        level,
        repo.focusSessions.value,
        repo.examSessions.value
    )

    fun getSubjectStudySummary(
        subjectId: String,
        timeRange: StudyTimeRange
    ): SubjectStudySummary {
        return buildSubjectStudySummary(
            subjectId,
            timeRange,
            repo.focusSessions.value,
            repo.examSessions.value,
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
        if (timeRange == StudyTimeRange.ALL) return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (timeRange) {
            StudyTimeRange.TODAY -> Unit
            StudyTimeRange.WEEK -> calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            StudyTimeRange.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            StudyTimeRange.ALL -> Unit
        }
        return calendar.timeInMillis
    }

    /**
     * Get Weekly summary for statistics and charts.
     */
    fun getWeeklyStudySummaryFlow(): Flow<WeeklyStudySummary> {
        return combine(repo.focusSessions, repo.examSessions) { focusList, examList ->
            buildWeeklyStudySummary(focusList, examList)
        }
    }

    fun getWeeklyStudySummary(): WeeklyStudySummary {
        return buildWeeklyStudySummary(repo.focusSessions.value, repo.examSessions.value)
    }

    private fun buildWeeklyStudySummary(
        focusList: List<FocusSession>,
        examList: List<ExamSession>
    ): WeeklyStudySummary {
        val todayStr = dateFormat.format(Date())
        val dayLabels = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val days = mutableListOf<DayBarData>()
        val cal = Calendar.getInstance()

        // Generate the last 7 days ending with today
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(c.time)
            val dayOfWeek = c.get(Calendar.DAY_OF_WEEK) - 1
            val label = dayLabels.getOrElse(dayOfWeek) { "周" }

            val dFocus = focusList.filter {
                it.status == SessionStatus.COMPLETED && dateFormat.format(Date(it.startTime)) == dateStr
            }
            val dExams = examList.filter {
                it.status == SessionStatus.COMPLETED && dateFormat.format(Date(it.startTime)) == dateStr
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
                    isToday = dateStr == todayStr,
                    subjectDistribution = subjectMap
                )
            )
        }

        val totalDuration = days.sumOf { it.durationSeconds }
        val activeDays = days.count { it.durationSeconds >= 1800L } // >= 30m
        val dailyAvg = if (days.isNotEmpty()) totalDuration / days.size else 0L

        // Find longest session in the 7 days
        val cutoff = System.currentTimeMillis() - 7 * 86400000L
        val recentFocus = focusList.filter {
            it.status == SessionStatus.COMPLETED && it.startTime >= cutoff
        }.map { focusToSessionItem(it) }
        val recentExams = examList.filter {
            it.status == SessionStatus.COMPLETED && it.startTime >= cutoff
        }.map { examToSessionItem(it) }
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
            streakDays = maxOf(1, streak),
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
