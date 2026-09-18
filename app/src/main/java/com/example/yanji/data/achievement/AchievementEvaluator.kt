package com.example.yanji.data.achievement

import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.db.UnlockedAchievementEntity
import com.example.yanji.data.db.YanjiDatabase

sealed interface AchievementEvent {
    data class FocusCompleted(val session: FocusSession) : AchievementEvent
    data class ExamCompleted(val session: ExamSession) : AchievementEvent
    data class JournalCreated(val entry: JournalEntry) : AchievementEvent
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

    fun candidatesFor(event: AchievementEvent): Set<String>? {
        return when (event) {
            is AchievementEvent.FocusCompleted -> FOCUS_CANDIDATES
            is AchievementEvent.ExamCompleted -> EXAM_CANDIDATES
            is AchievementEvent.JournalCreated -> JOURNAL_CANDIDATES
            is AchievementEvent.CheckInRecorded -> CHECKIN_CANDIDATES
            is AchievementEvent.ReconcileAll -> null // null means all 59
        }
    }
}

/**
 * 纯逻辑与事件驱动的成就评估器。
 *
 * 核心保障：
 * 1. 纯计算无副作用：[evaluate] 只根据输入数据返回应解锁的成就 ID 列表；
 * 2. 显式落库：[evaluateAndPersist] 只有在发现未解锁成就满足条件时才执行写入；
 * 3. 幂等：已解锁的成就直接跳过评估；数据库插入使用 INSERT OR IGNORE；
 * 4. 可恢复：[AchievementEvent.ReconcileAll] 安全网确保即使单次事件因进程崩溃丢失，亦可在启动时自动对齐。
 */
class AchievementEvaluator(
    private val definitions: List<AchievementDef> = AchievementCatalog.definitions
) {

    /**
     * 纯函数：根据当前数据和事件类型评估应解锁的成就 ID。
     *
     * @param event 触发事件（用于过滤候选集）
     * @param focusSessions 专注历史
     * @param examSessions 模考历史
     * @param journalEntries 日记历史
     * @param checkIns 打卡历史
     * @param unlockedIds 已经解锁的成就 ID 集合（避免重复计算）
     * @return 本次新满足解锁条件且尚未解锁的成就 ID 列表
     */
    fun evaluate(
        event: AchievementEvent,
        focusSessions: List<FocusSession>,
        examSessions: List<ExamSession>,
        journalEntries: List<JournalEntry>,
        checkIns: List<CheckIn>,
        unlockedIds: Set<String>
    ): List<String> {
        val candidates = AchievementEventFilter.candidatesFor(event)
        val newlyUnlocked = mutableListOf<String>()

        for (def in definitions) {
            // 1. 过滤已解锁
            if (unlockedIds.contains(def.id)) continue

            // 2. 过滤事件相关性
            if (candidates != null && !candidates.contains(def.id)) continue

            // 3. 计算进度
            val progress = def.calculateProgress(focusSessions, examSessions, journalEntries, checkIns)
            if (progress >= def.target) {
                newlyUnlocked.add(def.id)
            }
        }

        return newlyUnlocked
    }

    /**
     * 协调执行评估并执行显式幂等落库。
     */
    suspend fun evaluateAndPersist(
        event: AchievementEvent,
        db: YanjiDatabase,
        focusSessions: List<FocusSession>,
        examSessions: List<ExamSession>,
        journalEntries: List<JournalEntry>,
        checkIns: List<CheckIn>,
        unlockedIds: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> {
        val toUnlock = evaluate(
            event = event,
            focusSessions = focusSessions,
            examSessions = examSessions,
            journalEntries = journalEntries,
            checkIns = checkIns,
            unlockedIds = unlockedIds
        )

        if (toUnlock.isNotEmpty()) {
            val entities = toUnlock.map { UnlockedAchievementEntity(it, nowMs) }
            db.achievementDao().unlockAll(entities)
        }

        return toUnlock
    }
}
