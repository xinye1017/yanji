package com.example.yanji.data.ai

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.StudyDiagnosticSnapshot
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.SubjectStudyStat
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学习数据提供接口：负责从本地持久化真实数据中提取、统计与构建用于 AI 分析的结构化上下文与系统提示词。
 */
interface StudyDataProvider {

    fun buildSnapshot(
        periodDays: Int,
        settings: UserSettings,
        focusSessions: List<FocusSession>,
        examSessions: List<ExamSession>,
        noteEntries: List<NoteEntry>,
        companionName: String,
        now: Long = System.currentTimeMillis()
    ): StudyDiagnosticSnapshot

    fun buildSystemPrompt(companionName: String): String
}

/**
 * 默认学习数据提供实现。
 * 严格秉持零假数据原则：只聚合真实记录，不编造指标；在无对比数据时如实呈现。
 */
class DefaultStudyDataProvider : StudyDataProvider {

    override fun buildSnapshot(
        periodDays: Int,
        settings: UserSettings,
        focusSessions: List<FocusSession>,
        examSessions: List<ExamSession>,
        noteEntries: List<NoteEntry>,
        companionName: String,
        now: Long
    ): StudyDiagnosticSnapshot {
        val safePeriodDays = periodDays.coerceIn(1, 90)
        val endDay = YanjiTime.localDate(now)
        val startDay = endDay.minusDays((safePeriodDays - 1).toLong())
        val start = YanjiTime.dayRange(startDay).startInclusive
        val previousStartDay = startDay.minusDays(safePeriodDays.toLong())
        val previousStart = YanjiTime.dayRange(previousStartDay).startInclusive
        val endExclusive = YanjiTime.dayRange(endDay).endExclusive
        val startDate = startDay.format(YanjiTime.isoDateFormatter)
        val endDate = endDay.format(YanjiTime.isoDateFormatter)

        val sessions = focusSessions.filter {
            it.status == SessionStatus.COMPLETED && it.durationSeconds > 0L && it.startTime in start until endExclusive
        }
        val previousSessions = focusSessions.filter {
            it.status == SessionStatus.COMPLETED && it.durationSeconds > 0L && it.startTime in previousStart until start
        }

        val secondsByDay = mutableMapOf<LocalDate, Long>()
        sessions.forEach { session ->
            val day = YanjiTime.localDate(session.startTime)
            secondsByDay[day] = (secondsByDay[day] ?: 0L) + session.durationSeconds
        }
        val dailySeconds = (0 until safePeriodDays).map { dayOffset ->
            secondsByDay[startDay.plusDays(dayOffset.toLong())] ?: 0L
        }
        val totalSeconds = dailySeconds.sum()

        val subjectStats = sessions.groupBy {
            SubjectCatalog.subcategoryBucketId(it.subjectId, it.subjectName)
        }.map { (subjectId, subjectSessions) ->
            val seconds = subjectSessions.sumOf { it.durationSeconds }
            val name = SubjectCatalog.displayName(subjectId)
                ?: subjectSessions.firstOrNull()?.subjectName
                ?: subjectId
            SubjectStudyStat(name, seconds, if (totalSeconds == 0L) 0.0 else seconds.toDouble() / totalSeconds)
        }.sortedByDescending { it.seconds }

        val notes = noteEntries.filter { !it.isDraft && it.date in startDate..endDate }
        val exams = examSessions.filter {
            it.startTime in start until endExclusive && it.status == SessionStatus.COMPLETED
        }.sortedByDescending { it.startTime }
        val previousExams = examSessions.filter {
            it.startTime in previousStart until start && it.status == SessionStatus.COMPLETED
        }

        // 计算目标考试倒计时天数
        val daysUntilExam = runCatching {
            if (settings.targetExamDate.isNotBlank()) {
                val target = LocalDate.parse(settings.targetExamDate.trim())
                ChronoUnit.DAYS.between(endDay, target).takeIf { it >= 0 }
            } else null
        }.getOrNull()

        return StudyDiagnosticSnapshot(
            periodDays = safePeriodDays,
            periodStart = startDate,
            periodEnd = endDate,
            totalSeconds = totalSeconds,
            previousTotalSeconds = previousSessions.sumOf { it.durationSeconds },
            activeDays = dailySeconds.count { it >= settings.validStudyThresholdMinutes * 60L },
            goalDays = if (settings.dailyGoalHours > 0f) {
                dailySeconds.count { it >= (settings.dailyGoalHours * 3600).toLong() }
            } else {
                0
            },
            dailyGoalHours = settings.dailyGoalHours,
            dailyHours = dailySeconds.map { it / 3600.0 },
            subjectStats = subjectStats,
            sessionCount = sessions.size,
            previousSessionCount = previousSessions.size,
            averageSessionMinutes = if (sessions.isEmpty()) 0.0 else totalSeconds / 60.0 / sessions.size,
            longestSessionMinutes = (sessions.maxOfOrNull { it.durationSeconds } ?: 0L) / 60,
            completedExamCount = exams.size,
            previousExamCount = previousExams.size,
            scoredExamCount = exams.count { it.score != null && it.maxScore > 0.0 },
            recentExams = exams.take(5),
            noteCount = notes.size,
            averageMood = notes.map { it.moodScore }.takeIf { it.isNotEmpty() }?.average(),
            averageEnergy = notes.map { it.energyScore }.takeIf { it.isNotEmpty() }?.average(),
            noteSummaries = notes.sortedByDescending { it.updatedAt }.take(3).map { entry ->
                "${entry.date}：${clipText(entry.content.ifBlank { entry.title }, 200)}"
            },
            targetExamDate = settings.targetExamDate,
            targetSchool = settings.targetSchool,
            targetMajor = settings.targetMajor,
            daysUntilExam = daysUntilExam,
            companionName = companionName.ifBlank { "卷卷" }
        )
    }

    override fun buildSystemPrompt(companionName: String): String {
        val name = companionName.ifBlank { "卷卷" }
        return """
            你是研迹的学习伙伴「$name」，擅长依据用户的真实学习记录与状态，提供温和、专业且务实的学情诊断与复盘建议。

            【分析原则】
            1. 真实客观：严格依据提供的 <study_snapshot> 事实数据分析，严禁捏造未出现的时长、模考分数或虚假因果；若缺少历史可比数据，明确说明暂无法判断趋势。
            2. 务实落地：肯定积极投入，指出数据反映出的风险或薄弱点，给出未来 3 天切实的执行动作（每天仅一项清晰动作，时长不超合理目标）。
            3. 纯净输出：仅输出单个标准 JSON 对象，禁止 Markdown 代码块（如 ```json），前后不加任何额外闲聊或说明文字。

            【必须包含的 JSON 字段格式】
            {
              "overview": "必须是1-2句完整的自然语言概览（涵盖专注、模考或随笔关键进展；严禁仅输出括号或标点，严禁为 null）",
              "strengths": ["1-3条优势，指出具体数据依据"],
              "weaknesses": ["1-3条短板，指出风险或数据薄弱点"],
              "trendAnalysis": "1-2句周期对比趋势分析与限制说明",
              "threeDayPlan": [
                "第1天：[科目或任务] + 具体落地动作",
                "第2天：[科目或任务] + 具体落地动作",
                "第3天：[科目或任务] + 具体落地动作"
              ]
            }
        """.trimIndent()
    }

    private fun clipText(value: String, limit: Int): String =
        value.replace(Regex("[\\r\\n\\t]+"), " ")
            .replace('<', '［').replace('>', '］')
            .trim().take(limit)
}
