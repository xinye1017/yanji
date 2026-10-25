package com.example.yanji.data.achievement

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.db.UnlockedAchievementEntity
import com.example.yanji.data.db.YanjiDatabase

sealed interface AchievementEvent {
    data class FocusCompleted(val session: FocusSession) : AchievementEvent
    data class ExamCompleted(val session: ExamSession) : AchievementEvent
    data class NoteCreated(val entry: NoteEntry) : AchievementEvent
    data class CheckInRecorded(val checkIn: CheckIn) : AchievementEvent
    object ReconcileAll : AchievementEvent
}

object AchievementEventFilter {
    val FOCUS_CANDIDATES = setOf(
        "journey_focus_first",
        "focus_1h", "focus_5h", "focus_10h", "focus_25h", "focus_50h",
        "focus_100h", "focus_250h", "focus_500h", "focus_1000h",
        "focus_single_45m", "focus_single_90m", "focus_deep_2h", "focus_single_180m",
        "hidden_morning_3d", "hidden_night_owl", "hidden_day_and_night",
        "hidden_daily_10h", "hidden_extreme_12h"
    )

    val EXAM_CANDIDATES = setOf(
        "journey_focus_first", "journey_exam_first",
        "focus_1h", "focus_5h", "focus_10h", "focus_25h", "focus_50h",
        "focus_100h", "focus_250h", "focus_500h", "focus_1000h",
        "focus_single_45m", "focus_single_90m", "focus_deep_2h", "focus_single_180m",
        "exam_3_count", "exam_5_count", "exam_10_count", "exam_20_count", "exam_30_count", "exam_50_count",
        "exam_endurance_120m", "exam_full_180m",
        "math_score_90", "math_score_100", "math_score_110", "math_score_120", "math_score_130",
        "math_score_140", "math_score_145", "math_score_150",
        "math_personal_best", "math_jump_10", "math_breakthrough",
        "review_within_24h", "review_5_same_day",
        "hidden_morning_3d", "hidden_night_owl", "hidden_day_and_night",
        "hidden_daily_10h", "hidden_extreme_12h"
    )

    val JOURNAL_CANDIDATES = setOf(
        "journey_journal_first",
        "review_1", "review_3", "review_7", "review_15", "review_30", "review_50", "review_100",
        "review_within_24h", "review_5_same_day"
    )

    val CHECKIN_CANDIDATES = setOf(
        "journey_checkin_first",
        "streak_3_days", "streak_7_days", "streak_14_days", "streak_30_days",
        "streak_60_days", "streak_100_days", "streak_180_days", "streak_300_days",
        "streak_restart"
    )

    fun candidatesFor(event: AchievementEvent): Set<String>? = when (event) {
        is AchievementEvent.FocusCompleted -> FOCUS_CANDIDATES
        is AchievementEvent.ExamCompleted -> EXAM_CANDIDATES
        is AchievementEvent.NoteCreated -> JOURNAL_CANDIDATES
        is AchievementEvent.CheckInRecorded -> CHECKIN_CANDIDATES
        is AchievementEvent.ReconcileAll -> null
    }
}

/** Pure progress calculation plus an explicit, idempotent Room evaluation boundary. */
class AchievementEvaluator(
    private val definitions: List<AchievementDef> = AchievementCatalog.definitions
) {
    internal fun pendingDefinitions(event: AchievementEvent, unlockedIds: Set<String>): List<AchievementDef> {
        val candidates = AchievementEventFilter.candidatesFor(event)
        return definitions.filter { it.id !in unlockedIds && (candidates == null || it.id in candidates) }
    }

    fun evaluate(event: AchievementEvent, facts: AchievementFacts, unlockedIds: Set<String>): List<String> =
        pendingDefinitions(event, unlockedIds).filter { it.calculateProgress(facts) >= it.target }.map { it.id }

    /**
     * Every event arrives after its business write. Read the persisted source, not the event's
     * domain object or a possibly stale Flow mirror. Filter already-unlocked candidates before
     * reading any facts; serializing the read/unlock transaction also prevents duplicate work.
     */
    suspend fun evaluateAndPersist(
        event: AchievementEvent,
        db: YanjiDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> {
        // The writer-connection pair instead of `withTransaction`: the latter is
        // only in the Android room-ktx artifact, while the JVM tests resolve
        // room-runtime-jvm and would crash with NoSuchMethodError. Both paths are
        // the same serialized write transaction, so production and tests agree.
        var unlocked: List<String> = emptyList()
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val unlockedIds = db.achievementDao().getUnlockedIds().toSet()
                val pending = pendingDefinitions(event, unlockedIds)
                if (pending.isEmpty()) return@immediateTransaction
                val facts = AchievementFactsReader.read(db, pending, nowMs)
                val toUnlock = pending.filter { it.calculateProgress(facts) >= it.target }.map { it.id }
                if (toUnlock.isNotEmpty()) {
                    db.achievementDao().unlockAll(toUnlock.map { UnlockedAchievementEntity(it, nowMs) })
                }
                unlocked = toUnlock
            }
        }
        return unlocked
    }
}
