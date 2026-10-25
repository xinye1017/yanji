package com.example.yanji.data.achievement

import com.example.yanji.data.AchievementCategory
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.checkin.CheckInLogic
import com.example.yanji.data.db.AchievementSessionTimeRow
import com.example.yanji.data.db.YanjiDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One evaluation's facts, never retained as a history cache or converted into domain records. */
data class AchievementFacts(
    val sessionCount: Long = 0,
    val studySeconds: Long = 0,
    val longestSessionSeconds: Long = 0,
    val examCount: Long = 0,
    val enduranceExam: Boolean = false,
    val fullExam: Boolean = false,
    val mathBestScore: Long = 0,
    val mathPersonalBest: Boolean = false,
    val mathJump: Boolean = false,
    val mathBreakthrough: Boolean = false,
    val noteCount: Long = 0,
    val reviewWithin24h: Boolean = false,
    val sameDayReviewStreak: Long = 0,
    val checkInCount: Long = 0,
    val checkInStreak: Long = 0,
    val checkInRestart: Boolean = false,
    val morningStreak: Long = 0,
    val nightOwl: Boolean = false,
    val dayAndNight: Boolean = false,
    val mostDaySeconds: Long = 0
)

/**
 * Read only facts needed by still-locked event candidates. Counts/durations stay in SQLite;
 * ordered scores and system-zone calendar rules use small projections for one evaluation.
 * Deliberately no status/draft predicate: the existing 59 definitions count all persisted rows.
 * The caller owns the Room transaction, including the unlocked-id read and unlock writes.
 */
internal object AchievementFactsReader {
    suspend fun read(
        db: YanjiDatabase,
        definitions: List<AchievementDef>,
        nowMs: Long = System.currentTimeMillis()
    ): AchievementFacts {
        val ids = definitions.map { it.id }.toSet()
        var facts = AchievementFacts()
        val study = definitions.any { it.seriesId == AchievementCatalog.SERIES_FOCUS_HOURS ||
            it.seriesId == AchievementCatalog.SERIES_SINGLE_FOCUS || it.id == "journey_focus_first" }
        val exam = definitions.any { it.seriesId == AchievementCatalog.SERIES_EXAM_COUNT ||
            it.id in setOf("journey_exam_first", "exam_endurance_120m", "exam_full_180m") }
        val focusTotals = if (study) db.focusSessionDao().getAchievementTotals() else null
        val examTotals = if (study || exam) db.examSessionDao().getAchievementTotals() else null
        facts = facts.copy(
            sessionCount = (focusTotals?.count ?: 0) + (examTotals?.count ?: 0),
            studySeconds = (focusTotals?.seconds ?: 0) + (examTotals?.seconds ?: 0),
            longestSessionSeconds = maxOf(focusTotals?.longestSeconds ?: 0, examTotals?.longestSeconds ?: 0),
            examCount = examTotals?.count ?: 0,
            enduranceExam = "exam_endurance_120m" in ids && db.examSessionDao().hasAchievementEnduranceExam(),
            fullExam = (examTotals?.longestSeconds ?: 0) >= 180 * 60L
        )
        if (definitions.any { it.category == AchievementCategory.MATH }) {
            val scores = db.examSessionDao().getAchievementScores()
                .filter { SubjectCatalog.categoryIdOf(it.subjectId) == "math" ||
                    it.subjectId.contains("math", ignoreCase = true) || it.subjectName.contains("数") }
                .sortedBy { it.startTime }
                .map { it.score }
            var currentMax = 0
            var plateau = 0
            var jump = false
            var breakthrough = false
            for (score in scores) {
                // The historical jump/plateau rules truncate each score before comparing.
                val value = score.toInt()
                if (currentMax > 0 && value - currentMax >= 10) jump = true
                if (value > currentMax) {
                    if (plateau >= 3) breakthrough = true
                    currentMax = value
                    plateau = 0
                } else {
                    plateau++
                }
            }
            facts = facts.copy(
                mathBestScore = scores.maxOrNull()?.toLong() ?: 0,
                mathPersonalBest = scores.size >= 2 && scores.last() > (scores.dropLast(1).maxOrNull() ?: 0.0),
                mathJump = jump,
                mathBreakthrough = breakthrough
            )
        }
        if ("journey_journal_first" in ids || definitions.any { it.seriesId == AchievementCatalog.SERIES_REVIEW_COUNT }) {
            facts = facts.copy(noteCount = db.noteEntryDao().count().toLong())
        }
        if ("review_within_24h" in ids) {
            facts = facts.copy(reviewWithin24h = db.examSessionDao().hasAchievementReviewWithin24h())
        }
        val zone = ZoneId.systemDefault()
        if ("review_5_same_day" in ids) {
            val noteDays = db.noteEntryDao().getAchievementCreatedTimes().map { date(it, zone) }.toSet()
            var count = 0L
            for (endTime in db.examSessionDao().getAchievementEndTimes()) {
                count = if (date(endTime, zone) in noteDays) count + 1 else 0
                if (count >= 5) break
            }
            facts = facts.copy(sameDayReviewStreak = count.coerceAtMost(5))
        }
        if (definitions.any { it.category == AchievementCategory.STREAK || it.id == "journey_checkin_first" }) {
            val checkIns = db.checkInDao().getAchievementRows()
            val dates = checkIns.map { it.date }.toSet()
            val sortedDates = dates.sorted().map(LocalDate::parse)
            val hadBreak = sortedDates.zipWithNext().any { (a, b) -> b.toEpochDay() - a.toEpochDay() >= 4 }
            facts = facts.copy(
                checkInCount = checkIns.size.toLong(),
                checkInStreak = maxOf(CheckInLogic.currentStreak(dates, nowMs).toLong(), checkIns.maxOfOrNull { it.streak.toLong() } ?: 0),
                checkInRestart = checkIns.any { it.streak >= 7 } && (hadBreak || checkIns.any { it.streak == 1 })
            )
        }
        if (definitions.any { it.isHidden }) {
            facts = temporalFacts(
                facts,
                db.focusSessionDao().getAchievementTimes().asSequence() + db.examSessionDao().getAchievementTimes().asSequence(),
                zone
            )
        }
        return facts
    }

    private fun date(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    private fun temporalFacts(facts: AchievementFacts, rows: Sequence<AchievementSessionTimeRow>, zone: ZoneId): AchievementFacts {
        val morningDays = mutableSetOf<LocalDate>()
        val morningDates = mutableSetOf<LocalDate>()
        val nightDates = mutableSetOf<LocalDate>()
        val daySeconds = mutableMapOf<LocalDate, Long>()
        var nightOwl = false
        fun morning(time: Long): Boolean = time > 0 && !Instant.ofEpochMilli(time).atZone(zone).toLocalTime().isAfter(LocalTime.of(8, 0))
        fun night(time: Long, minute: Int): Boolean {
            if (time <= 0) return false
            val local = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
            return !local.isBefore(LocalTime.of(23, minute)) || local.isBefore(LocalTime.of(4, 0))
        }
        for (row in rows) {
            val day = date(row.startTime, zone)
            val startsMorning = morning(row.startTime)
            if (startsMorning) morningDays.add(day)
            if (startsMorning || morning(row.endTime)) morningDates.add(day)
            if (night(row.endTime, 0)) nightDates.add(day)
            if (night(row.endTime, 30)) nightOwl = true
            daySeconds[day] = (daySeconds[day] ?: 0) + row.durationSeconds
        }
        var streak = 0L
        var best = 0L
        var previous: LocalDate? = null
        for (day in morningDays.sorted()) {
            streak = if (previous?.plusDays(1) == day) streak + 1 else 1
            best = maxOf(best, streak)
            previous = day
        }
        return facts.copy(
            morningStreak = best,
            nightOwl = nightOwl,
            dayAndNight = morningDates.any { it in nightDates },
            mostDaySeconds = daySeconds.values.maxOrNull() ?: 0
        )
    }
}
