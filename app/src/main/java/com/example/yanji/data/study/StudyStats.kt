package com.example.yanji.data.study

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.SubjectCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 学习时长的纯计算。
 *
 * 这里是「某天学了多久」的唯一口径：专注与模考都只统计 `COMPLETED` 状态。
 * 抽成纯函数后，口径本身可以被 JVM 单测钉住（比如 RUNNING / PAUSED 不计入），
 * UI 与成就系统都从这里取数，不再各算各的。
 */
internal object StudyStats {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun isCompletedToday(startTime: Long, dayEpochMs: Long): Boolean =
        dateFormat.format(Date(startTime)) == dateFormat.format(Date(dayEpochMs))

    /** 指定当天的有效学习秒数（专注 + 模考，仅 COMPLETED）。 */
    fun durationOnDay(
        focus: List<FocusSession>,
        exams: List<ExamSession>,
        dayEpochMs: Long
    ): Long =
        focus.filter { it.status == SessionStatus.COMPLETED && isCompletedToday(it.startTime, dayEpochMs) }
            .sumOf { it.durationSeconds } +
            exams.filter { it.status == SessionStatus.COMPLETED && isCompletedToday(it.startTime, dayEpochMs) }
                .sumOf { it.actualDurationSeconds }

    /** 指定时间段（从 cutoff 起）的有效学习秒数。 */
    fun durationSince(
        focus: List<FocusSession>,
        exams: List<ExamSession>,
        cutoffEpochMs: Long
    ): Long =
        focus.filter { it.status == SessionStatus.COMPLETED && it.startTime >= cutoffEpochMs }
            .sumOf { it.durationSeconds } +
            exams.filter { it.status == SessionStatus.COMPLETED && it.startTime >= cutoffEpochMs }
                .sumOf { it.actualDurationSeconds }

    /** 全部有效学习秒数。 */
    fun totalDuration(focus: List<FocusSession>, exams: List<ExamSession>): Long =
        focus.filter { it.status == SessionStatus.COMPLETED }.sumOf { it.durationSeconds } +
            exams.filter { it.status == SessionStatus.COMPLETED }.sumOf { it.actualDurationSeconds }

    /** 指定当天的科目分布（展示名 -> 秒数），用于首页的分布条。 */
    fun subjectDistributionOnDay(
        focus: List<FocusSession>,
        exams: List<ExamSession>,
        dayEpochMs: Long
    ): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        fun add(subjectId: String, subjectName: String, seconds: Long) {
            val bucketId = SubjectCatalog.subcategoryBucketId(subjectId, subjectName)
            val displayName = SubjectCatalog.displayName(bucketId) ?: subjectName
            map[displayName] = (map[displayName] ?: 0L) + seconds
        }
        focus.filter { it.status == SessionStatus.COMPLETED && isCompletedToday(it.startTime, dayEpochMs) }
            .forEach { add(it.subjectId, it.subjectName, it.durationSeconds) }
        exams.filter { it.status == SessionStatus.COMPLETED && isCompletedToday(it.startTime, dayEpochMs) }
            .forEach { add(it.subjectId, it.subjectName, it.actualDurationSeconds) }
        return map
    }
}
