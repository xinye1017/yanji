package com.example.yanji.data.achievement

import com.example.yanji.data.*
import com.example.yanji.data.checkin.CheckInLogic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Frozen pre-PERF-02 formulas, only in tests: compare every progress value, not just unlocks. */
internal fun legacyAchievementProgress(
    def: AchievementDef,
    focus: List<FocusSession>,
    exam: List<ExamSession>,
    notes: List<NoteEntry>,
    checkIns: List<CheckIn>,
    now: Long
): Long {
    fun date(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    fun morning(time: Long): Boolean = time > 0 && !Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime().isAfter(LocalTime.of(8, 0))
    fun night(time: Long, minute: Int): Boolean {
        if (time <= 0) return false
        val local = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime()
        return !local.isBefore(LocalTime.of(23, minute)) || local.isBefore(LocalTime.of(4, 0))
    }
    val math = exam.filter { SubjectCatalog.categoryIdOf(it.subjectId) == "math" ||
        it.subjectId.contains("math", ignoreCase = true) || it.subjectName.contains("数") }
    val scoredMath = math.filter { it.score != null }.sortedBy { it.startTime }
    val progress = when (def.seriesId) {
        AchievementCatalog.SERIES_FOCUS_HOURS -> (focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }) / 3600L
        AchievementCatalog.SERIES_SINGLE_FOCUS -> maxOf(
            focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0,
            exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0
        )
        AchievementCatalog.SERIES_EXAM_COUNT -> exam.size.toLong()
        AchievementCatalog.SERIES_REVIEW_COUNT -> notes.size.toLong()
        AchievementCatalog.SERIES_MATH -> math.mapNotNull { it.score }.maxOrNull()?.toLong() ?: 0
        AchievementCatalog.SERIES_STREAK -> maxOf(
            CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), now).toLong(),
            checkIns.maxOfOrNull { it.streak.toLong() } ?: 0
        )
        else -> when (def.id) {
            "journey_focus_first" -> (focus.size + exam.size).toLong()
            "journey_checkin_first" -> checkIns.size.toLong()
            "journey_journal_first" -> notes.size.toLong()
            "journey_exam_first" -> exam.size.toLong()
            "streak_restart" -> {
                val sorted = checkIns.sortedBy { it.date }
                val broken = sorted.zipWithNext().any { (a, b) -> LocalDate.parse(b.date).toEpochDay() - LocalDate.parse(a.date).toEpochDay() >= 4 }
                if (checkIns.any { it.streak >= 7 } && (broken || sorted.any { it.streak == 1 })) 1L else 0L
            }
            "exam_endurance_120m" -> if (exam.any { it.plannedDurationSeconds >= 7200 && it.actualDurationSeconds >= (it.plannedDurationSeconds * 0.9).toLong() }) 1L else 0L
            "exam_full_180m" -> if (exam.any { it.actualDurationSeconds >= 10800 }) 1L else 0L
            "math_personal_best" -> if (scoredMath.size >= 2 && (scoredMath.last().score ?: 0.0) > (scoredMath.dropLast(1).mapNotNull { it.score }.maxOrNull() ?: 0.0)) 1L else 0L
            "math_jump_10" -> {
                var currentMax = 0
                var triggered = false
                for (item in scoredMath) {
                    val score = item.score?.toInt() ?: 0
                    if (currentMax > 0 && score - currentMax >= 10) { triggered = true; break }
                    if (score > currentMax) currentMax = score
                }
                if (triggered) 1L else 0L
            }
            "math_breakthrough" -> {
                var currentMax = 0
                var plateau = 0
                var broken = false
                for (item in scoredMath) {
                    val score = item.score?.toInt() ?: 0
                    if (score > currentMax) {
                        if (plateau >= 3) { broken = true; break }
                        currentMax = score
                        plateau = 0
                    } else plateau++
                }
                if (broken) 1L else 0L
            }
            "review_within_24h" -> if (exam.any { e -> notes.any { it.createdAt in e.endTime..(e.endTime + 86400000L) } }) 1L else 0L
            "review_5_same_day" -> {
                var count = 0L
                for (item in exam.sortedBy { it.endTime }) {
                    if (notes.any { date(it.createdAt) == date(item.endTime) }) count++ else count = 0
                    if (count >= 5) break
                }
                count
            }
            "hidden_morning_3d" -> {
                val days = (focus.filter { morning(it.startTime) }.map { date(it.startTime) } + exam.filter { morning(it.startTime) }.map { date(it.startTime) }).distinct().sorted()
                var streak = 0L
                var best = 0L
                for (index in days.indices) {
                    streak = if (index > 0 && days[index].toEpochDay() - days[index - 1].toEpochDay() == 1L) streak + 1 else 1
                    best = maxOf(best, streak)
                }
                best
            }
            "hidden_night_owl" -> if (focus.any { night(it.endTime, 30) } || exam.any { night(it.endTime, 30) }) 1L else 0L
            "hidden_day_and_night" -> {
                val morningDays = focus.filter { morning(it.startTime) || morning(it.endTime) }.map { date(it.startTime) }.toSet() +
                    exam.filter { morning(it.startTime) || morning(it.endTime) }.map { date(it.startTime) }.toSet()
                val nightDays = focus.filter { night(it.endTime, 0) }.map { date(it.startTime) }.toSet() +
                    exam.filter { night(it.endTime, 0) }.map { date(it.startTime) }.toSet()
                if (morningDays.intersect(nightDays).isNotEmpty()) 1L else 0L
            }
            "hidden_daily_10h", "hidden_extreme_12h" -> {
                val days = mutableMapOf<LocalDate, Long>()
                focus.forEach { days[date(it.startTime)] = (days[date(it.startTime)] ?: 0) + it.durationSeconds }
                exam.forEach { days[date(it.startTime)] = (days[date(it.startTime)] ?: 0) + it.actualDurationSeconds }
                (days.values.maxOrNull() ?: 0) / 3600L
            }
            else -> error("Uncovered legacy achievement: ${def.id}")
        }
    }
    return progress.coerceAtMost(def.target)
}
