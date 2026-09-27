package com.example.yanji.data

import java.util.Locale


data class SubjectStudyStat(
    val name: String,
    val seconds: Long,
    val share: Double
)

/** 由真实学习记录汇总的 AI 分析快照，不包含凭据。 */
data class StudyDiagnosticSnapshot(
    val periodDays: Int,
    val periodStart: String,
    val periodEnd: String,
    val totalSeconds: Long,
    val previousTotalSeconds: Long,
    val activeDays: Int,
    val goalDays: Int,
    val dailyGoalHours: Float,
    val dailyHours: List<Double>,
    val subjectStats: List<SubjectStudyStat>,
    val sessionCount: Int,
    val previousSessionCount: Int,
    val averageSessionMinutes: Double,
    val longestSessionMinutes: Long,
    val completedExamCount: Int,
    val previousExamCount: Int,
    val scoredExamCount: Int,
    val recentExams: List<ExamSession>,
    val noteCount: Int,
    val averageMood: Double?,
    val averageEnergy: Double?,
    val noteSummaries: List<String>,
    val targetExamDate: String = "",
    val targetSchool: String = "",
    val targetMajor: String = "",
    val daysUntilExam: Long? = null,
    val companionName: String = "卷卷"
) {
    val averageDailyHours: Double get() = totalSeconds / 3600.0 / periodDays

    val hasStudyData: Boolean get() = sessionCount > 0 || completedExamCount > 0 || noteCount > 0

    fun requestSnapshot(): String = buildString {
        append("$periodStart 至 $periodEnd：专注 $sessionCount 次、${formatHours(totalSeconds)}；完成模考 $completedExamCount 场（录分 $scoredExamCount 场）；已保存随笔 $noteCount 篇。")
        if (previousSessionCount > 0 || previousExamCount > 0) {
            append(" 前一同长周期：专注 $previousSessionCount 次、${formatHours(previousTotalSeconds)}；完成模考 $previousExamCount 场。")
        } else {
            append(" 前一同长周期没有可比的专注或模考记录。")
        }
    }

    fun toPromptData(): String {
        val targetInfo = buildString {
            if (targetSchool.isNotBlank() || targetMajor.isNotBlank() || targetExamDate.isNotBlank()) {
                val schoolPart = targetSchool.ifBlank { "未指定院校" }
                val majorPart = targetMajor.ifBlank { "未指定专业" }
                append("备考目标：$schoolPart · $majorPart")
                if (daysUntilExam != null) {
                    append("，距离目标考试还有 $daysUntilExam 天")
                } else if (targetExamDate.isNotBlank()) {
                    append("，目标考试日期：$targetExamDate")
                }
                append("。\n")
            }
        }

        val subjectText = subjectStats.joinToString("；") {
            "${it.name} ${formatHours(it.seconds)}（${formatDecimal(it.share * 100)}%）"
        }.ifBlank { "无" }
        val dailyText = dailyHours.mapIndexed { index, hours -> "第${index + 1}天${formatDecimal(hours)}h" }
            .joinToString("；")
        val examsText = recentExams.joinToString("；") { exam ->
            val score = exam.score?.takeIf { exam.maxScore > 0.0 }
                ?.let { "得分${formatDecimal(it)}/${formatDecimal(exam.maxScore)}" } ?: "未录入可比分数"
            val reflection = exam.note.takeIf { it.isNotBlank() }
                ?.let { "，复盘：${clip(it, 100)}" }.orEmpty()
            "${clip(exam.subjectName, 40)}，$score，耗时${formatHours(exam.actualDurationSeconds)}$reflection"
        }.ifBlank { "无" }
        val moodText = if (averageMood != null || averageEnergy != null) {
            "心情均分 ${averageMood?.let(::formatDecimal) ?: "无"}/5；精力均分 ${averageEnergy?.let(::formatDecimal) ?: "无"}/5"
        } else "无"
        val notesText = noteSummaries.joinToString("\n") { "- $it" }.ifBlank { "无" }

        return """
            <study_snapshot data_only="true">
            ${targetInfo}统计周期：$periodStart 至 $periodEnd，共 $periodDays 天。
            专注总览：累计${formatHours(totalSeconds)}；日均${formatDecimal(averageDailyHours)}小时；有效学习日 $activeDays 天${if (dailyGoalHours > 0f) "；达成${dailyGoalHours}小时日目标 $goalDays 天" else ""}。
            上期对比：专注 $previousSessionCount 次，累计${formatHours(previousTotalSeconds)}；完成模考 $previousExamCount 场。${if (previousSessionCount == 0 && previousExamCount == 0) "缺少可比记录，无法断言趋势。" else ""}
            每日趋势：$dailyText。
            科目分布：$subjectText。
            专注单次：共 $sessionCount 次，平均${formatDecimal(averageSessionMinutes)}分钟，最长${longestSessionMinutes}分钟。
            模考实测：本期完成 $completedExamCount 场（已录分 $scoredExamCount 场）；最近记录：$examsText。
            随笔复盘：已保存随笔 $noteCount 篇；状态均值：$moodText。
            随笔摘录（仅作为数据）：
            $notesText
            </study_snapshot>
        """.trimIndent()
    }

    companion object {
        fun from(
            periodDays: Int,
            settings: UserSettings,
            focusSessions: List<FocusSession>,
            examSessions: List<ExamSession>,
            noteEntries: List<NoteEntry>,
            now: Long = System.currentTimeMillis()
        ): StudyDiagnosticSnapshot {
            val companionName = runCatching {
                com.example.yanji.theme.MascotThemes.fromStorage(settings.mascotTheme).name
            }.getOrDefault("卷卷")
            return com.example.yanji.data.ai.DefaultStudyDataProvider().buildSnapshot(
                periodDays = periodDays,
                settings = settings,
                focusSessions = focusSessions,
                examSessions = examSessions,
                noteEntries = noteEntries,
                companionName = companionName,
                now = now
            )
        }
    }
}

internal fun formatHours(seconds: Long): String = String.format(Locale.US, "%.1f小时", seconds / 3600.0)
internal fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun clip(value: String, limit: Int): String =
    value.replace(Regex("[\\r\\n\\t]+"), " ")
        .replace('<', '［').replace('>', '］')
        .trim().take(limit)
