package com.example.yanji.data.achievement

import com.example.yanji.data.AchievementCategory
import com.example.yanji.data.AchievementRarity
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.checkin.CheckInLogic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 结构化成就定义数据模型。
 */
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val rarity: AchievementRarity,
    val iconKey: String,
    val target: Long,
    val unit: String,
    val rewardQuote: String,
    val isHidden: Boolean = false,
    val seriesId: String? = null,
    val seriesOrder: Int = 0,
    val calculateProgress: (
        focusSessions: List<FocusSession>,
        examSessions: List<ExamSession>,
        journalEntries: List<JournalEntry>,
        checkIns: List<CheckIn>
    ) -> Long
)

/**
 * 卷卷成就系统 V2 —— 59 项完整考研成就静态元数据清单。
 *
 * 严格保留全部 59 项成就，涵盖研途启程、专注修炼、坚持之路、模考试炼、数学征途、复盘沉淀、隐藏成就。
 */
object AchievementCatalog {

    const val SERIES_MATH = "series_math"
    const val SERIES_FOCUS_HOURS = "series_focus_hours"
    const val SERIES_SINGLE_FOCUS = "series_single_focus"
    const val SERIES_STREAK = "series_streak"
    const val SERIES_EXAM_COUNT = "series_exam_count"
    const val SERIES_REVIEW_COUNT = "series_review_count"

    private fun isMorning(timeMs: Long): Boolean {
        if (timeMs <= 0) return false
        val lt = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return !lt.isAfter(LocalTime.of(8, 0))
    }

    private fun isNight2330(timeMs: Long): Boolean {
        if (timeMs <= 0) return false
        val lt = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return !lt.isBefore(LocalTime.of(23, 30)) || lt.isBefore(LocalTime.of(4, 0))
    }

    private fun isNight2300(timeMs: Long): Boolean {
        if (timeMs <= 0) return false
        val lt = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return !lt.isBefore(LocalTime.of(23, 0)) || lt.isBefore(LocalTime.of(4, 0))
    }

    private fun dateKey(timeMs: Long): String {
        return Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    private fun isMath(subjectId: String, subjectName: String): Boolean {
        return SubjectCatalog.categoryIdOf(subjectId) == "math" ||
            subjectId.contains("math", ignoreCase = true) ||
            subjectName.contains("数")
    }

    val definitions: List<AchievementDef> = listOf(
        // ==========================================
        // 1. 研途启程 (JOURNEY) - 前期高频正反馈
        // ==========================================
        AchievementDef(
            id = "journey_focus_first",
            title = "研途启程",
            description = "完成第 1 次专注学习",
            category = AchievementCategory.JOURNEY,
            rarity = AchievementRarity.COMMON,
            iconKey = "rocket",
            target = 1L,
            unit = "次",
            rewardQuote = "千里之行，始于足下。你的研途第一步已经坚定踏出！",
            calculateProgress = { focus, exam, _, _ ->
                (focus.size + exam.size).toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "journey_checkin_first",
            title = "今日有痕",
            description = "完成第 1 次每日打卡",
            category = AchievementCategory.JOURNEY,
            rarity = AchievementRarity.COMMON,
            iconKey = "check_circle",
            target = 1L,
            unit = "次",
            rewardQuote = "无论前路漫漫，今天你已按时赴约。",
            calculateProgress = { _, _, _, checkIns ->
                checkIns.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "journey_journal_first",
            title = "落笔有痕",
            description = "完成第 1 篇研迹/学习复盘",
            category = AchievementCategory.JOURNEY,
            rarity = AchievementRarity.COMMON,
            iconKey = "pencil",
            target = 1L,
            unit = "篇",
            rewardQuote = "笔尖划过纸页的沙沙声，是内心安定沉静的力量。",
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "journey_exam_first",
            title = "沙场初试",
            description = "完成第 1 场模拟考试",
            category = AchievementCategory.JOURNEY,
            rarity = AchievementRarity.COMMON,
            iconKey = "target",
            target = 1L,
            unit = "场",
            rewardQuote = "未曾登台，焉知锋芒？第一场实战模考顺利收官！",
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(1L)
            }
        ),

        // ==========================================
        // 2. 专注修炼 (FOCUS) - 长期成长与单次入定
        // ==========================================
        AchievementDef(
            id = "focus_1h",
            title = "星火初燃",
            description = "累计专注学习达到 1 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.COMMON,
            iconKey = "timer",
            target = 1L,
            unit = "小时",
            rewardQuote = "一小时的专注，星火微芒亦可燎原。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 1,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "focus_5h",
            title = "初窥门径",
            description = "累计专注学习达到 5 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.COMMON,
            iconKey = "clock",
            target = 5L,
            unit = "小时",
            rewardQuote = "五小时积累，专注渐成习惯。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 2,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(5L)
            }
        ),
        AchievementDef(
            id = "focus_10h",
            title = "渐入佳境",
            description = "累计专注学习达到 10 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "hourglass",
            target = 10L,
            unit = "小时",
            rewardQuote = "十小时的专注沉淀，思维的齿轮已开始全速运转。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 3,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(10L)
            }
        ),
        AchievementDef(
            id = "focus_25h",
            title = "稳步前行",
            description = "累计专注学习达到 25 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "path",
            target = 25L,
            unit = "小时",
            rewardQuote = "步履不停，日拱一卒，行稳方能致远。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 4,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(25L)
            }
        ),
        AchievementDef(
            id = "focus_50h",
            title = "坐照入神",
            description = "累计专注学习达到 50 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.RARE,
            iconKey = "compass",
            target = 50L,
            unit = "小时",
            rewardQuote = "半百学时，耐得住寂寞方能守得住繁华。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 5,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(50L)
            }
        ),
        AchievementDef(
            id = "focus_100h",
            title = "研途百炼",
            description = "累计专注学习达到 100 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.EPIC,
            iconKey = "crown",
            target = 100L,
            unit = "小时",
            rewardQuote = "百小时淬炼！每一分汗水都在为你筑起高分上岸的城墙！",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 6,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(100L)
            }
        ),
        AchievementDef(
            id = "focus_250h",
            title = "千锤百炼",
            description = "累计专注学习达到 250 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.EPIC,
            iconKey = "hammer",
            target = 250L,
            unit = "小时",
            rewardQuote = "二百五十小时。炉火纯青，渐入化境。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 7,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(250L)
            }
        ),
        AchievementDef(
            id = "focus_500h",
            title = "积土成山",
            description = "累计专注学习达到 500 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "mountains",
            target = 500L,
            unit = "小时",
            rewardQuote = "积土成山，风雨兴焉；五百小时已是峰峦耸峙。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 8,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(500L)
            }
        ),
        AchievementDef(
            id = "focus_1000h",
            title = "万卷归一",
            description = "累计专注学习达到 1000 小时",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "sparkle",
            target = 1000L,
            unit = "小时",
            rewardQuote = "一千小时。时间不会说谎。",
            seriesId = SERIES_FOCUS_HOURS,
            seriesOrder = 9,
            calculateProgress = { focus, exam, _, _ ->
                val secs = focus.sumOf { it.durationSeconds } + exam.sumOf { it.actualDurationSeconds }
                (secs / 3600L).coerceAtMost(1000L)
            }
        ),

        // 单次专注挑战
        AchievementDef(
            id = "focus_single_45m",
            title = "心流初现",
            description = "单次实际专注达到 45 分钟",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "lightning",
            target = 45L,
            unit = "分钟",
            rewardQuote = "沉入书海四十五分钟，世界静止，唯思绪奔涌。",
            seriesId = SERIES_SINGLE_FOCUS,
            seriesOrder = 1,
            calculateProgress = { focus, exam, _, _ ->
                val maxFocusMins = focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0L
                val maxExamMins = exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0L
                maxOf(maxFocusMins, maxExamMins).coerceAtMost(45L)
            }
        ),
        AchievementDef(
            id = "focus_single_90m",
            title = "深度潜航",
            description = "单次实际专注达到 90 分钟",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.RARE,
            iconKey = "waves",
            target = 90L,
            unit = "分钟",
            rewardQuote = "一个半小时的潜心深潜，知识的轮廓愈发清晰。",
            seriesId = SERIES_SINGLE_FOCUS,
            seriesOrder = 2,
            calculateProgress = { focus, exam, _, _ ->
                val maxFocusMins = focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0L
                val maxExamMins = exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0L
                maxOf(maxFocusMins, maxExamMins).coerceAtMost(90L)
            }
        ),
        AchievementDef(
            id = "focus_deep_2h",
            title = "深沉入定",
            description = "单次连续专注超过 2 小时 (120分钟)",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.EPIC,
            iconKey = "meditation",
            target = 120L,
            unit = "分钟",
            rewardQuote = "心无旁骛，物我两忘。两个小时的极限入定，这就是强者的定力！",
            seriesId = SERIES_SINGLE_FOCUS,
            seriesOrder = 3,
            calculateProgress = { focus, exam, _, _ ->
                val maxFocusMins = focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0L
                val maxExamMins = exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0L
                maxOf(maxFocusMins, maxExamMins).coerceAtMost(120L)
            }
        ),
        AchievementDef(
            id = "focus_single_180m",
            title = "物我两忘",
            description = "单次实际专注达到 180 分钟",
            category = AchievementCategory.FOCUS,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "flame",
            target = 180L,
            unit = "分钟",
            rewardQuote = "三小时不移方寸。书山有路，你已化身苦行之舟。",
            seriesId = SERIES_SINGLE_FOCUS,
            seriesOrder = 4,
            calculateProgress = { focus, exam, _, _ ->
                val maxFocusMins = focus.maxOfOrNull { it.durationSeconds }?.let { it / 60L } ?: 0L
                val maxExamMins = exam.maxOfOrNull { it.actualDurationSeconds }?.let { it / 60L } ?: 0L
                maxOf(maxFocusMins, maxExamMins).coerceAtMost(180L)
            }
        ),

        // ==========================================
        // 3. 坚持之路 (STREAK) - 长期打卡成长
        // ==========================================
        AchievementDef(
            id = "streak_3_days",
            title = "三日成势",
            description = "连续学习打卡达到 3 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.COMMON,
            iconKey = "calendar",
            target = 3L,
            unit = "天",
            rewardQuote = "事不过三，三日已成势头。",
            seriesId = SERIES_STREAK,
            seriesOrder = 1,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(3L)
            }
        ),
        AchievementDef(
            id = "streak_7_days",
            title = "七日成习",
            description = "连续学习打卡达到 7 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "fire",
            target = 7L,
            unit = "天",
            rewardQuote = "整整一周！习惯的齿轮已咬合，前进不再需要过多挣扎。",
            seriesId = SERIES_STREAK,
            seriesOrder = 2,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(7L)
            }
        ),
        AchievementDef(
            id = "streak_14_days",
            title = "双周不辍",
            description = "连续学习打卡达到 14 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.RARE,
            iconKey = "shield_check",
            target = 14L,
            unit = "天",
            rewardQuote = "半月之功，严谨自律，无惧风雨。",
            seriesId = SERIES_STREAK,
            seriesOrder = 3,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(14L)
            }
        ),
        AchievementDef(
            id = "streak_30_days",
            title = "月度守望",
            description = "连续学习打卡达到 30 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.EPIC,
            iconKey = "diamond",
            target = 30L,
            unit = "天",
            rewardQuote = "整整一个月！无论风雨从未间断，你拥有考研最可贵的毅力！",
            seriesId = SERIES_STREAK,
            seriesOrder = 4,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(30L)
            }
        ),
        AchievementDef(
            id = "streak_60_days",
            title = "两月不息",
            description = "连续学习打卡达到 60 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.EPIC,
            iconKey = "trophy",
            target = 60L,
            unit = "天",
            rewardQuote = "六十个昼夜的守望，自律早已刻入骨髓。",
            seriesId = SERIES_STREAK,
            seriesOrder = 5,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(60L)
            }
        ),
        AchievementDef(
            id = "streak_100_days",
            title = "百日筑基",
            description = "连续学习打卡达到 100 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "star",
            target = 100L,
            unit = "天",
            rewardQuote = "百日筑基！跨越漫长周期的坚守，已蜕变成沉稳的王者。",
            seriesId = SERIES_STREAK,
            seriesOrder = 6,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(100L)
            }
        ),
        AchievementDef(
            id = "streak_180_days",
            title = "半载求索",
            description = "连续学习打卡达到 180 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "award",
            target = 180L,
            unit = "天",
            rewardQuote = "半年苦修。青灯黄卷，行者无疆。",
            seriesId = SERIES_STREAK,
            seriesOrder = 7,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(180L)
            }
        ),
        AchievementDef(
            id = "streak_300_days",
            title = "道阻且长",
            description = "连续学习打卡达到 300 天",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "sun",
            target = 300L,
            unit = "天",
            rewardQuote = "三百日未辍。道阻且长，行则将至。",
            seriesId = SERIES_STREAK,
            seriesOrder = 8,
            calculateProgress = { _, _, _, checkIns ->
                val cur = CheckInLogic.currentStreak(checkIns.map { it.date }.toSet(), System.currentTimeMillis()).toLong()
                val maxH = checkIns.maxOfOrNull { it.streak.toLong() } ?: 0L
                maxOf(cur, maxH).coerceAtMost(300L)
            }
        ),
        AchievementDef(
            id = "streak_restart",
            title = "再次出发",
            description = "曾连续学习≥7天，中断后重整旗鼓再次打卡",
            category = AchievementCategory.STREAK,
            rarity = AchievementRarity.RARE,
            iconKey = "arrows_clockwise",
            target = 1L,
            unit = "次",
            rewardQuote = "跌倒并不致命，致命的是不再站起。重新出发，更加坚定！",
            calculateProgress = { _, _, _, checkIns ->
                val hasSeven = checkIns.any { it.streak >= 7 }
                val sorted = checkIns.sortedBy { it.date }
                var hadBreak = false
                for (i in 0 until sorted.size - 1) {
                    val d1 = LocalDate.parse(sorted[i].date)
                    val d2 = LocalDate.parse(sorted[i + 1].date)
                    if (d2.toEpochDay() - d1.toEpochDay() >= 4L) {
                        hadBreak = true
                        break
                    }
                }
                if (hasSeven && (hadBreak || sorted.any { it.streak == 1 })) 1L else 0L
            }
        ),

        // ==========================================
        // 4. 模考试炼 (EXAM) - 实战积累与耐力
        // ==========================================
        AchievementDef(
            id = "exam_3_count",
            title = "渐经试炼",
            description = "完成 3 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.COMMON,
            iconKey = "sword",
            target = 3L,
            unit = "场",
            rewardQuote = "三经沙场，试卷已不再令你心跳失序。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 1,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(3L)
            }
        ),
        AchievementDef(
            id = "exam_5_count",
            title = "磨砺成锋",
            description = "完成 5 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "knife",
            target = 5L,
            unit = "场",
            rewardQuote = "宝剑锋从磨砺出，考场节奏已然在心。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 2,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(5L)
            }
        ),
        AchievementDef(
            id = "exam_10_count",
            title = "十战磨剑",
            description = "完成 10 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.RARE,
            iconKey = "shield",
            target = 10L,
            unit = "场",
            rewardQuote = "十年磨一剑，十场真题实测，锋芒初露。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 3,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(10L)
            }
        ),
        AchievementDef(
            id = "exam_20_count",
            title = "身经百战",
            description = "完成 20 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.EPIC,
            iconKey = "medal",
            target = 20L,
            unit = "场",
            rewardQuote = "二十度交卷，大将之风，波澜不惊。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 4,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(20L)
            }
        ),
        AchievementDef(
            id = "exam_30_count",
            title = "久经沙场",
            description = "完成 30 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "flag",
            target = 30L,
            unit = "场",
            rewardQuote = "三十场硝烟淬炼，考场之上已无生疏之题。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 5,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(30L)
            }
        ),
        AchievementDef(
            id = "exam_50_count",
            title = "百炼成兵",
            description = "完成 50 场模拟考试",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "trophy",
            target = 50L,
            unit = "场",
            rewardQuote = "五十场全真模考。千锤百炼，势不可挡。",
            seriesId = SERIES_EXAM_COUNT,
            seriesOrder = 6,
            calculateProgress = { _, exam, _, _ ->
                exam.size.toLong().coerceAtMost(50L)
            }
        ),
        AchievementDef(
            id = "exam_endurance_120m",
            title = "全程作战",
            description = "完成计划≥120分钟且实际用时≥90%的实战模考",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.RARE,
            iconKey = "clock_countdown",
            target = 1L,
            unit = "次",
            rewardQuote = "两小时以上高强度推导，坚持到底就是胜利。",
            calculateProgress = { _, exam, _, _ ->
                val ok = exam.any {
                    it.plannedDurationSeconds >= 120 * 60L &&
                        it.actualDurationSeconds >= (it.plannedDurationSeconds * 0.9).toLong()
                }
                if (ok) 1L else 0L
            }
        ),
        AchievementDef(
            id = "exam_full_180m",
            title = "真刀真枪",
            description = "实际时长达到 180 分钟全真模考",
            category = AchievementCategory.EXAM,
            rarity = AchievementRarity.EPIC,
            iconKey = "seal_check",
            target = 1L,
            unit = "次",
            rewardQuote = "整整 180 分钟全真极限交卷。这一卷，直面终局！",
            calculateProgress = { _, exam, _, _ ->
                val ok = exam.any { it.actualDurationSeconds >= 180 * 60L }
                if (ok) 1L else 0L
            }
        ),

        // ==========================================
        // 5. 数学征途 (MATH) - 考研核心高分天梯
        // ==========================================
        AchievementDef(
            id = "math_score_90",
            title = "初见锋芒",
            description = "单场数学模考成绩达到 90 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "chart_line_up",
            target = 90L,
            unit = "分",
            rewardQuote = "九十分及格之槛已破，数学大厦根基稳固。",
            seriesId = SERIES_MATH,
            seriesOrder = 1,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(90L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_100",
            title = "百分之关",
            description = "单场数学模考成绩达到 100 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.RARE,
            iconKey = "crosshair",
            target = 100L,
            unit = "分",
            rewardQuote = "踏破三位数大关！数学不再是拦路虎，而是你的得分利器。",
            seriesId = SERIES_MATH,
            seriesOrder = 2,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(100L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_110",
            title = "登堂入室",
            description = "单场数学模考成绩达到 110 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.RARE,
            iconKey = "trend_up",
            target = 110L,
            unit = "分",
            rewardQuote = "一百一十分。基础扎实，难题亦有一战之力。",
            seriesId = SERIES_MATH,
            seriesOrder = 3,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(110L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_120",
            title = "金榜夺魁",
            description = "单场数学模考成绩达到 120 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.EPIC,
            iconKey = "graduation_cap",
            target = 120L,
            unit = "分",
            rewardQuote = "一百二十分！稳居名校竞争梯队，傲视群雄！",
            seriesId = SERIES_MATH,
            seriesOrder = 4,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(120L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_130",
            title = "一骑绝尘",
            description = "单场数学模考成绩达到 130 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.EPIC,
            iconKey = "lightning",
            target = 130L,
            unit = "分",
            rewardQuote = "130 分。真正的高分区，从这里开始。",
            seriesId = SERIES_MATH,
            seriesOrder = 5,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(130L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_140",
            title = "登峰",
            description = "单场数学模考成绩达到 140 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "mountain",
            target = 140L,
            unit = "分",
            rewardQuote = "山高万仞，你已经站到了极少数人能够抵达的位置。",
            seriesId = SERIES_MATH,
            seriesOrder = 6,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(140L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_145",
            title = "绝顶",
            description = "单场数学模考成绩达到 145 分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "crown",
            target = 145L,
            unit = "分",
            rewardQuote = "距离完美，只剩最后五分。",
            seriesId = SERIES_MATH,
            seriesOrder = 7,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(145L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_score_150",
            title = "天衣无缝",
            description = "数学取得满分 150 / 150",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "sparkle",
            target = 150L,
            unit = "分",
            rewardQuote = "150 / 150。此卷无憾。",
            seriesId = SERIES_MATH,
            seriesOrder = 8,
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) }
                mathExams.mapNotNull { it.score }.maxOrNull()?.toLong()?.coerceAtMost(150L) ?: 0L
            }
        ),
        AchievementDef(
            id = "math_personal_best",
            title = "更进一步",
            description = "单场数学模考刷新个人历史最高分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.RARE,
            iconKey = "arrow_fat_up",
            target = 1L,
            unit = "次",
            rewardQuote = "超越过去的自己，是最真实的成长！",
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) && it.score != null }
                    .sortedBy { it.startTime }
                if (mathExams.size < 2) 0L
                else {
                    val maxPrev = mathExams.dropLast(1).mapNotNull { it.score }.maxOrNull() ?: 0.0
                    val latest = mathExams.last().score ?: 0.0
                    if (latest > maxPrev) 1L else 0L
                }
            }
        ),
        AchievementDef(
            id = "math_jump_10",
            title = "一日千里",
            description = "相比此前个人最高成绩单次提升≥10分",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.EPIC,
            iconKey = "rocket_launch",
            target = 1L,
            unit = "次",
            rewardQuote = "十余分的大跨越！厚积薄发的顿悟时刻！",
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) && it.score != null }
                    .sortedBy { it.startTime }
                var triggered = false
                var currentMax = 0
                for (item in mathExams) {
                    val s = item.score?.toInt() ?: 0
                    if (currentMax > 0 && s - currentMax >= 10) {
                        triggered = true
                        break
                    }
                    if (s > currentMax) currentMax = s
                }
                if (triggered) 1L else 0L
            }
        ),
        AchievementDef(
            id = "math_breakthrough",
            title = "破壁",
            description = "连续至少 3 次模考未破纪录后成功打破僵局刷新最高",
            category = AchievementCategory.MATH,
            rarity = AchievementRarity.EPIC,
            iconKey = "wall",
            target = 1L,
            unit = "次",
            rewardQuote = "撞碎平台期的坚壁！耐住寂寞，终见光明！",
            calculateProgress = { _, exam, _, _ ->
                val mathExams = exam.filter { isMath(it.subjectId, it.subjectName) && it.score != null }
                    .sortedBy { it.startTime }
                var plateauCount = 0
                var currentMax = 0
                var broken = false
                for (item in mathExams) {
                    val s = item.score?.toInt() ?: 0
                    if (s > currentMax) {
                        if (plateauCount >= 3) {
                            broken = true
                            break
                        }
                        currentMax = s
                        plateauCount = 0
                    } else {
                        plateauCount++
                    }
                }
                if (broken) 1L else 0L
            }
        ),

        // ==========================================
        // 6. 复盘沉淀 (REVIEW) - 坚持反思成长
        // ==========================================
        AchievementDef(
            id = "review_1",
            title = "落笔有痕",
            description = "累计完成 1 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.COMMON,
            iconKey = "pencil",
            target = 1L,
            unit = "篇",
            rewardQuote = "第一篇复盘。敢于直面问题的人，已经赢了一半。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 1,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(1L)
            }
        ),
        AchievementDef(
            id = "review_3",
            title = "三省吾身",
            description = "累计完成 3 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.COMMON,
            iconKey = "book_open",
            target = 3L,
            unit = "篇",
            rewardQuote = "吾日三省吾身，知得失而明进退。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 2,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(3L)
            }
        ),
        AchievementDef(
            id = "review_7",
            title = "七日有思",
            description = "累计完成 7 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.UNCOMMON,
            iconKey = "notebook",
            target = 7L,
            unit = "篇",
            rewardQuote = "七篇反思总结，让模糊的灵感沉淀为清晰的方法论。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 3,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(7L)
            }
        ),
        AchievementDef(
            id = "review_15",
            title = "字句成章",
            description = "累计完成 15 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.RARE,
            iconKey = "books",
            target = 15L,
            unit = "篇",
            rewardQuote = "半月反思，字字句句皆是研路心血。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 4,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(15L)
            }
        ),
        AchievementDef(
            id = "review_30",
            title = "思绪成册",
            description = "累计完成 30 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.EPIC,
            iconKey = "folder_simple_star",
            target = 30L,
            unit = "篇",
            rewardQuote = "三十篇深度复盘，你已拥有属于自己的错题心法。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 5,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(30L)
            }
        ),
        AchievementDef(
            id = "review_50",
            title = "反躬自省",
            description = "累计完成 50 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "seal",
            target = 50L,
            unit = "篇",
            rewardQuote = "五十篇凝练自省。知错能改，善莫大焉。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 6,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(50L)
            }
        ),
        AchievementDef(
            id = "review_100",
            title = "著书自鉴",
            description = "累计完成 100 篇复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "book_bookmark",
            target = 100L,
            unit = "篇",
            rewardQuote = "百篇自省之书。以此为鉴，照亮远方。",
            seriesId = SERIES_REVIEW_COUNT,
            seriesOrder = 7,
            calculateProgress = { _, _, journal, _ ->
                journal.size.toLong().coerceAtMost(100L)
            }
        ),
        AchievementDef(
            id = "review_within_24h",
            title = "学而有思",
            description = "模考结束后 24 小时内完成复盘日记",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.RARE,
            iconKey = "hourglass_simple",
            target = 1L,
            unit = "次",
            rewardQuote = "趁热打铁。考后二十四小时的复盘黄金期，你抓住了！",
            calculateProgress = { _, exam, journal, _ ->
                val ok = exam.any { e ->
                    journal.any { j ->
                        j.createdAt in e.endTime..(e.endTime + 86400000L)
                    }
                }
                if (ok) 1L else 0L
            }
        ),
        AchievementDef(
            id = "review_5_same_day",
            title = "今日事今日毕",
            description = "连续 5 场模考全部在考试当天完成复盘",
            category = AchievementCategory.REVIEW,
            rarity = AchievementRarity.EPIC,
            iconKey = "check_square_offset",
            target = 5L,
            unit = "次",
            rewardQuote = "连续五场考后当天立克复盘！执行力就是你的超能力！",
            calculateProgress = { _, exam, journal, _ ->
                val sortedExams = exam.sortedBy { it.endTime }
                var count = 0
                for (e in sortedExams) {
                    val examDay = dateKey(e.endTime)
                    val hasSameDay = journal.any { j -> dateKey(j.createdAt) == examDay }
                    if (hasSameDay) count++ else count = 0
                    if (count >= 5) break
                }
                count.toLong().coerceAtMost(5L)
            }
        ),

        // ==========================================
        // 7. 隐藏成就 (HIDDEN) - 极度惊艳的特殊经历
        // ==========================================
        AchievementDef(
            id = "hidden_morning_3d",
            title = "晨光同行",
            description = "连续 3 个不同日期在清晨 8:00 前开始专注",
            category = AchievementCategory.HIDDEN,
            rarity = AchievementRarity.RARE,
            iconKey = "sun_horizon",
            target = 3L,
            unit = "天",
            rewardQuote = "晨光熹微，万籁俱寂，你与朝阳并肩而行。",
            isHidden = true,
            calculateProgress = { focus, exam, _, _ ->
                val morningDays = (focus.filter { isMorning(it.startTime) }.map { dateKey(it.startTime) } +
                    exam.filter { isMorning(it.startTime) }.map { dateKey(it.startTime) })
                    .distinct()
                    .sorted()
                var streak = 0
                var maxStreak = 0
                for (i in 0 until morningDays.size) {
                    if (i == 0) {
                        streak = 1
                    } else {
                        val d1 = LocalDate.parse(morningDays[i - 1])
                        val d2 = LocalDate.parse(morningDays[i])
                        if (d2.toEpochDay() - d1.toEpochDay() == 1L) streak++ else streak = 1
                    }
                    if (streak > maxStreak) maxStreak = streak
                }
                maxStreak.toLong().coerceAtMost(3L)
            }
        ),
        AchievementDef(
            id = "hidden_night_owl",
            title = "披星戴月",
            description = "在深夜 23:30 之后完成一次有效专注",
            category = AchievementCategory.HIDDEN,
            rarity = AchievementRarity.RARE,
            iconKey = "moon_stars",
            target = 1L,
            unit = "次",
            rewardQuote = "深夜二十三点半的台灯，照亮的是未来的录取通知书。",
            isHidden = true,
            calculateProgress = { focus, exam, _, _ ->
                val ok = focus.any { isNight2330(it.endTime) } || exam.any { isNight2330(it.endTime) }
                if (ok) 1L else 0L
            }
        ),
        AchievementDef(
            id = "hidden_day_and_night",
            title = "日月同辉",
            description = "同一天内 8:00 前与 23:00 后均完成过有效学习",
            category = AchievementCategory.HIDDEN,
            rarity = AchievementRarity.EPIC,
            iconKey = "yin_yang",
            target = 1L,
            unit = "次",
            rewardQuote = "朝披清晨露，晚顶漫天星。一日之内，尽览日月。",
            isHidden = true,
            calculateProgress = { focus, exam, _, _ ->
                val focusMorning = focus.filter { isMorning(it.startTime) || isMorning(it.endTime) }.map { dateKey(it.startTime) }.toSet()
                val examMorning = exam.filter { isMorning(it.startTime) || isMorning(it.endTime) }.map { dateKey(it.startTime) }.toSet()
                val morningDates = focusMorning + examMorning

                val focusNight = focus.filter { isNight2300(it.endTime) }.map { dateKey(it.startTime) }.toSet()
                val examNight = exam.filter { isNight2300(it.endTime) }.map { dateKey(it.startTime) }.toSet()
                val nightDates = focusNight + examNight

                val ok = morningDates.intersect(nightDates).isNotEmpty()
                if (ok) 1L else 0L
            }
        ),
        AchievementDef(
            id = "hidden_daily_10h",
            title = "学海无涯",
            description = "单日累计有效专注达到 10 小时",
            category = AchievementCategory.HIDDEN,
            rarity = AchievementRarity.LEGENDARY,
            iconKey = "infinity",
            target = 10L,
            unit = "小时",
            rewardQuote = "一天十小时深度沉浸！心流无止境，学海任遨游。",
            isHidden = true,
            calculateProgress = { focus, exam, _, _ ->
                val allDaySecs = mutableMapOf<String, Long>()
                focus.forEach {
                    val k = dateKey(it.startTime)
                    allDaySecs[k] = (allDaySecs[k] ?: 0L) + it.durationSeconds
                }
                exam.forEach {
                    val k = dateKey(it.startTime)
                    allDaySecs[k] = (allDaySecs[k] ?: 0L) + it.actualDurationSeconds
                }
                val maxHours = (allDaySecs.values.maxOrNull() ?: 0L) / 3600L
                maxHours.coerceAtMost(10L)
            }
        ),
        AchievementDef(
            id = "hidden_extreme_12h",
            title = "极限之日",
            description = "单日累计有效专注达到 12 小时",
            category = AchievementCategory.HIDDEN,
            rarity = AchievementRarity.MYTHIC,
            iconKey = "fire_extinguisher",
            target = 12L,
            unit = "小时",
            rewardQuote = "十二小时极限燃烧。这是属于拼命者的壮烈史诗。",
            isHidden = true,
            calculateProgress = { focus, exam, _, _ ->
                val allDaySecs = mutableMapOf<String, Long>()
                focus.forEach {
                    val k = dateKey(it.startTime)
                    allDaySecs[k] = (allDaySecs[k] ?: 0L) + it.durationSeconds
                }
                exam.forEach {
                    val k = dateKey(it.startTime)
                    allDaySecs[k] = (allDaySecs[k] ?: 0L) + it.actualDurationSeconds
                }
                val maxHours = (allDaySecs.values.maxOrNull() ?: 0L) / 3600L
                maxHours.coerceAtMost(12L)
            }
        )
    )

    fun getSeries(seriesId: String): List<AchievementDef> {
        return definitions.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
    }

    fun find(id: String): AchievementDef? {
        return definitions.firstOrNull { it.id == id }
    }
}
