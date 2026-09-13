package com.example.yanji.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AchievementRepository private constructor(
    private val repo: YanjiRepository = YanjiRepository.getInstance()
) {
    companion object {
        @Volatile
        private var instance: AchievementRepository? = null

        fun getInstance(): AchievementRepository {
            return instance ?: synchronized(this) {
                instance ?: AchievementRepository().also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 15 Defined Achievements metadata
    data class AchievementDef(
        val id: String,
        val title: String,
        val description: String,
        val category: AchievementCategory,
        val iconKey: String,
        val target: Long,
        val unit: String,
        val rewardQuote: String,
        val calculateProgress: (
            focusSessions: List<FocusSession>,
            examSessions: List<ExamSession>,
            journalEntries: List<JournalEntry>,
            checkIns: List<CheckIn>
        ) -> Long
    )

    val definitions: List<AchievementDef> = listOf(
        // FOCUS CATEGORY
        AchievementDef(
            id = "focus_first",
            title = "初入研途",
            description = "完成第 1 次专注学习",
            category = AchievementCategory.FOCUS,
            iconKey = "book",
            target = 1L,
            unit = "次",
            rewardQuote = "千里之行，始于足下。你的研途第一步已经坚定踏出！",
            calculateProgress = { focus, exam, _, _ ->
                (focus.size + exam.size).toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "focus_10h",
            title = "渐入佳境",
            description = "累计专注学习达到 10 小时",
            category = AchievementCategory.FOCUS,
            iconKey = "hourglass",
            target = 10L,
            unit = "小时",
            rewardQuote = "十小时的专注沉淀，思维的齿轮已开始全速运转。",
            calculateProgress = { focus, exam, _, _ ->
                val totalSecs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (totalSecs / 3600L).coerceAtMost(10L)
            }
        ),
        AchievementDef(
            id = "focus_50h",
            title = "坐照入神",
            description = "累计专注学习达到 50 小时",
            category = AchievementCategory.FOCUS,
            iconKey = "compass",
            target = 50L,
            unit = "小时",
            rewardQuote = "半百学时，耐得住寂寞方能守得住繁华。",
            calculateProgress = { focus, exam, _, _ ->
                val totalSecs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (totalSecs / 3600L).coerceAtMost(50L)
            }
        ),
        AchievementDef(
            id = "focus_100h",
            title = "研途百炼",
            description = "累计专注学习达到 100 小时",
            category = AchievementCategory.FOCUS,
            iconKey = "crown",
            target = 100L,
            unit = "小时",
            rewardQuote = "百小时淬炼！每一分汗水都在为你筑起高分上岸的城墙！",
            calculateProgress = { focus, exam, _, _ ->
                val totalSecs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (totalSecs / 3600L).coerceAtMost(100L)
            }
        ),
        AchievementDef(
            id = "focus_deep_2h",
            title = "深沉入定",
            description = "单次连续专注超过 2 小时 (120分钟)",
            category = AchievementCategory.FOCUS,
            iconKey = "timer",
            target = 120L,
            unit = "分钟",
            rewardQuote = "心无旁骛，物我两忘。两个小时的极限入定，这就是强者的定力！",
            calculateProgress = { focus, exam, _, _ ->
                val maxFocusMins = focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0L
                val maxExamMins = exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0L
                maxOf(maxFocusMins, maxExamMins).coerceAtMost(120L)
            }
        ),
        AchievementDef(
            id = "focus_morning",
            title = "晨曦微露",
            description = "在清晨 7:30 之前开启专注",
            category = AchievementCategory.FOCUS,
            iconKey = "sunrise",
            target = 1L,
            unit = "次",
            rewardQuote = "清晨的微光与翻书声，是世界上最美妙的交响乐。",
            calculateProgress = { focus, exam, _, _ ->
                fun isMorning(time: Long): Boolean {
                    val localTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime()
                    return !localTime.isAfter(LocalTime.of(7, 30))
                }
                val hasMorning = focus.any { isMorning(it.startTime) } || exam.any { isMorning(it.startTime) }
                if (hasMorning) 1L else 0L
            }
        ),
        AchievementDef(
            id = "focus_night",
            title = "披星戴月",
            description = "在夜间 22:30 之后仍在专注学习",
            category = AchievementCategory.FOCUS,
            iconKey = "moon",
            target = 1L,
            unit = "次",
            rewardQuote = "夜深人静时，你的每一盏台灯都在照亮未来的录取通知书。",
            calculateProgress = { focus, exam, _, _ ->
                fun isNight(time: Long): Boolean {
                    val localTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime()
                    return !localTime.isBefore(LocalTime.of(22, 30)) || localTime.isBefore(LocalTime.of(4, 0))
                }
                val hasNight = focus.any { isNight(it.endTime) } || exam.any { isNight(it.endTime) }
                if (hasNight) 1L else 0L
            }
        ),

        // EXAM CATEGORY
        AchievementDef(
            id = "exam_first",
            title = "沙场初试",
            description = "完成第 1 场全真模拟考试",
            category = AchievementCategory.EXAM,
            iconKey = "award",
            target = 1L,
            unit = "场",
            rewardQuote = "敢于直面真实的考场节奏与时间压力，你已经战胜了怯懦！",
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "exam_full_180m",
            title = "身经百战",
            description = "完成 180 分钟标准全真大考 (或累计达标)",
            category = AchievementCategory.EXAM,
            iconKey = "shield",
            target = 180L,
            unit = "分钟",
            rewardQuote = "三个小时高强度的思维鏖战，你具备了真正考场战士的抗压体魄！",
            calculateProgress = { _, exam, _, _ ->
                val maxExamMins = exam.maxOfOrNull { it.plannedDurationSeconds / 60L } ?: 0L
                val maxActualMins = exam.maxOfOrNull { it.actualDurationSeconds / 60L } ?: 0L
                maxOf(maxExamMins, maxActualMins).coerceAtMost(180L)
            }
        ),
        AchievementDef(
            id = "exam_high_score",
            title = "金榜夺魁",
            description = "单场模考得分突破 120 分 (满分150分标准)",
            category = AchievementCategory.EXAM,
            iconKey = "medal",
            target = 120L,
            unit = "分",
            rewardQuote = "120+ 高分！这不仅是数字，更是你在专业与数学上的硬核竞争力！",
            calculateProgress = { _, exam, _, _ ->
                val maxScore = exam.mapNotNull { it.score }.maxOrNull() ?: 0.0
                maxScore.toLong().coerceAtMost(120L)
            }
        ),

        // JOURNAL CATEGORY
        AchievementDef(
            id = "journal_first",
            title = "落笔有痕",
            description = "记录第 1 篇研迹复盘日记",
            category = AchievementCategory.JOURNAL,
            iconKey = "pencil",
            target = 1L,
            unit = "篇",
            rewardQuote = "记录是最好的反思。把迷茫写下来，答案就已经浮现了一半。",
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "journal_7_entries",
            title = "字字珠玑",
            description = "累计记录 7 篇考研复盘日记",
            category = AchievementCategory.JOURNAL,
            iconKey = "feather",
            target = 7L,
            unit = "篇",
            rewardQuote = "七篇日记，见证了你一周的思考与蜕变，持续复盘让你不可战胜！",
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(7L)
            }
        ),

        // STREAK CATEGORY
        AchievementDef(
            id = "streak_first",
            title = "初露锋芒",
            description = "完成第 1 次每日打卡签到",
            category = AchievementCategory.STREAK,
            iconKey = "check_circle",
            target = 1L,
            unit = "天",
            rewardQuote = "今日打卡成功！坚持的种子已经悄然生根发芽。",
            calculateProgress = { _, _, _, checkIns ->
                checkIns.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "streak_7_days",
            title = "持之以恒",
            description = "连续打卡签到满 7 天",
            category = AchievementCategory.STREAK,
            iconKey = "fire",
            target = 7L,
            unit = "天",
            rewardQuote = "连续一周！习惯的齿轮已经转动，卓越已成为你的日常！",
            calculateProgress = { _, _, _, checkIns ->
                val currentStreak = repo.getCurrentStreak().toLong()
                val maxHistoryStreak = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(currentStreak, maxHistoryStreak).coerceAtMost(7L)
            }
        ),
        AchievementDef(
            id = "streak_30_days",
            title = "铁杵成针",
            description = "连续打卡签到满 30 天",
            category = AchievementCategory.STREAK,
            iconKey = "diamond",
            target = 30L,
            unit = "天",
            rewardQuote = "整整一个月！无论风雨从未间断，你拥有考研最可贵的毅力！",
            calculateProgress = { _, _, _, checkIns ->
                val currentStreak = repo.getCurrentStreak().toLong()
                val maxHistoryStreak = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(currentStreak, maxHistoryStreak).coerceAtMost(30L)
            }
        )
    )

    val achievements: StateFlow<List<Achievement>> = combine(
        repo.focusSessions,
        repo.examSessions,
        repo.journalEntries,
        repo.checkIns,
        repo.unlockedAchievements
    ) { focus, exam, journal, checkIns, unlockedMap ->
        definitions.map { def ->
            val progress = def.calculateProgress(focus, exam, journal, checkIns)
            val isUnlocked = unlockedMap.containsKey(def.id) || progress >= def.target
            val unlockedAt = unlockedMap[def.id] ?: if (progress >= def.target) {
                // Auto trigger unlock
                repo.unlockAchievement(def.id)
                System.currentTimeMillis()
            } else null

            Achievement(
                id = def.id,
                title = def.title,
                description = def.description,
                category = def.category,
                iconKey = def.iconKey,
                currentProgress = progress,
                targetProgress = def.target,
                unit = def.unit,
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt,
                rewardQuote = def.rewardQuote
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val unlockedCount: StateFlow<Int> = achievements.map { list ->
        list.count { it.isUnlocked }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    val totalCount: Int = definitions.size
}
