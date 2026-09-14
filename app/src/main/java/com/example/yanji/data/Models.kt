package com.example.yanji.data

import androidx.compose.ui.graphics.Color
import com.example.yanji.theme.*
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 计时模式单一数据源：模式名 ↔ 目标时长。
 * FocusScreen 与首页快捷启动共用，避免两处硬编码漂移。
 */
object FocusModes {
    const val COUNT_UP = "正向计时"
    const val POMODORO_25 = "25分钟番茄"
    const val POMODORO_45 = "45分钟深度"
    const val DEEP_60 = "60分钟小测"
    const val BIG_90 = "90分钟专题"

    val ALL: List<String> = listOf(COUNT_UP, POMODORO_25, POMODORO_45, DEEP_60, BIG_90)

    /** 正向计时返回 0，表示不限时长；倒计时返回目标秒数。 */
    fun targetSeconds(mode: String): Long = when {
        mode.contains("25") -> 1500L
        mode.contains("45") -> 2700L
        mode.contains("60") -> 3600L
        mode.contains("90") -> 5400L
        mode.contains("分钟") -> {
            val minutes = Regex("""(\d+)\s*分钟""").find(mode)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            minutes * 60L
        }
        mode == "45分钟番茄" -> 2700L
        mode == "60分钟深度" -> 3600L
        mode == "90分钟大题" -> 5400L
        mode == COUNT_UP -> 0L
        else -> 0L
    }
}

/**
 * 首页快捷操作。type 区分内置模板与用户自定义组合：
 * - start_focus / exam / journal：出厂默认三项，行为与原先写死的三个按钮一致
 * - custom：在专注计时页保存的「科目 + 计时模式 + 备注」组合
 * 所有项统一支持长按删除。
 */
@Serializable
data class QuickStartPreset(
    val id: String = UUID.randomUUID().toString(),
    val type: String = TYPE_CUSTOM,
    val label: String = "",
    val subLabel: String = "",
    val subjectId: String = "math_advanced",
    val subjectName: String = "高等数学",
    val mode: String = FocusModes.COUNT_UP,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    val isBuiltin: Boolean get() = type != TYPE_CUSTOM
    val targetSeconds: Long get() = FocusModes.targetSeconds(mode)

    companion object {
        const val TYPE_START_FOCUS = "start_focus"
        const val TYPE_EXAM = "exam"
        const val TYPE_JOURNAL = "journal"
        const val TYPE_CUSTOM = "custom"
    }
}

@Serializable
data class Subject(
    val id: String,
    val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val parentId: String? = null
) {
    val isCategory: Boolean get() = parentId == null

    fun toComposeColor(): Color {
        return when (SubjectCatalog.categoryIdOf(id)) {
            "math" -> SubjectMath
            "major" -> SubjectMajor
            "english" -> SubjectEnglish
            "politics" -> SubjectPolitics
            else -> SubjectOther
        }
    }
}

/**
 * 学科层级的单一数据源。学习记录保存最具体的可选科目 ID，
 * 统计时再根据 parentId 汇总，不需要改动现有 Room 表结构。
 */
object SubjectCatalog {
    const val UNCLASSIFIED_SUFFIX = "__unclassified"

    val all: List<Subject> = listOf(
        Subject("math", "数学一", "#356AE6", 1),
        Subject("math_advanced", "高等数学", "#356AE6", 11, parentId = "math"),
        Subject("math_linear", "线性代数", "#4C7BE8", 12, parentId = "math"),
        Subject("math_probability", "概率论", "#678DEB", 13, parentId = "math"),
        Subject("major", "408专业课", "#8B7CF6", 2),
        Subject("major_organization", "计算机组成原理", "#8B7CF6", 21, parentId = "major"),
        Subject("major_data_structure", "数据结构", "#9A8CFA", 22, parentId = "major"),
        Subject("major_network", "计算机网络", "#AA9DFB", 23, parentId = "major"),
        Subject("major_os", "操作系统", "#B8ADFC", 24, parentId = "major"),
        Subject("english", "英语一", "#2F9E6D", 3),
        Subject("politics", "政治", "#E67E22", 4),
        Subject("other", "其他", "#667085", 5)
    )

    val categories: List<Subject> get() = all.filter { it.isCategory && it.enabled }
    val selectableSubjects: List<Subject> get() = all.filter { subject ->
        subject.enabled && (subject.parentId != null || childrenOf(subject.id).isEmpty())
    }

    fun find(subjectId: String): Subject? = all.find { it.id == subjectId }

    fun childrenOf(categoryId: String): List<Subject> =
        all.filter { it.parentId == categoryId && it.enabled }.sortedBy { it.sortOrder }

    fun categoryIdOf(subjectId: String): String {
        val cleanId = subjectId.removeSuffix(UNCLASSIFIED_SUFFIX)
        return find(cleanId)?.parentId ?: cleanId
    }

    fun categoryOf(subjectId: String): Subject? = find(categoryIdOf(subjectId))

    fun directBucketId(categoryId: String): String = "$categoryId$UNCLASSIFIED_SUFFIX"

    fun isDirectBucket(subjectId: String): Boolean = subjectId.endsWith(UNCLASSIFIED_SUFFIX)

    fun displayName(subjectId: String): String? {
        if (isDirectBucket(subjectId)) {
            return categoryOf(subjectId)?.let { "${it.name}（综合/未细分）" }
        }
        return find(subjectId)?.name
    }

    fun idForDisplayName(name: String): String? {
        find(name)?.let { return it.id }
        all.find { it.name == name }?.let { return it.id }
        return categories.firstOrNull { "${it.name}（综合/未细分）" == name }
            ?.let { directBucketId(it.id) }
    }

    /** 兼容旧版 ID 和曾使用 custom ID 保存的模考记录。 */
    fun inferCategoryId(subjectId: String, subjectName: String): String {
        val knownCategory = categoryOf(subjectId)?.id
        if (knownCategory != null) return knownCategory
        return when {
            subjectName.contains("数学") -> "math"
            subjectName.contains("408") || subjectName.contains("专业课") -> "major"
            subjectName.contains("英语") -> "english"
            subjectName.contains("政治") -> "politics"
            else -> "other"
        }
    }

    fun subcategoryBucketId(subjectId: String, subjectName: String): String {
        val known = find(subjectId)
        if (known?.parentId != null) return known.id
        val categoryId = inferCategoryId(subjectId, subjectName)
        return if (childrenOf(categoryId).isEmpty()) categoryId else directBucketId(categoryId)
    }
}

enum class SessionStatus {
    RUNNING, PAUSED, COMPLETED, CANCELLED
}

@Serializable
data class FocusSession(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val pausedDurationSeconds: Long = 0,
    val pauseCount: Int = 0,
    val mode: String = "正向计时",
    val note: String = "",
    val status: SessionStatus = SessionStatus.COMPLETED,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExamSession(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val plannedDurationSeconds: Long = 10800, // 180 分钟
    val actualDurationSeconds: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val score: Double? = null,
    val maxScore: Double = 150.0,
    val note: String = "",
    val status: SessionStatus = SessionStatus.COMPLETED,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 日记条目。
 *
 * **不持有学习时长**：某一天的学习时长唯一事实来源是 FocusSession + ExamSession 的实时聚合
 * （见 [StudyStatisticsRepository.getDailyStudySummary]）。历史上这里曾冗余保存
 * `studyDurationSeconds`，导致"真实统计为 0"时回退到陈旧副本、同一指标出现两个事实来源。
 */
@Serializable
data class JournalEntry(
    val id: String,
    val date: String, // e.g. "2026-09-05"
    val title: String = "",
    val content: String = "",
    val moodScore: Int = 5, // 1 - 5
    val energyScore: Int = 4,
    val studySatisfaction: Int = 5,
    val tomorrowPlan: String = "",
    val blockers: String = "", // 遇到的困难 / 卡点（v10 新增，按行书写）
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AiAnalysis(
    val id: String,
    val periodStart: String,
    val periodEnd: String,
    val provider: String = "OpenAI",
    val model: String = "gpt-4o-mini",
    val requestSnapshot: String = "",
    val overview: String = "",
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val trendAnalysis: String = "",
    val suggestions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserSettings(
    val targetExamDate: String = "",
    val targetSchool: String = "",
    val targetMajor: String = "",
    val dailyGoalHours: Float = 0f,
    val validStudyThresholdMinutes: Int = 30,
    val defaultSubjectId: String = "math_advanced",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val aiProvider: String = "",
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = ""
) {
    val isAiConfigured: Boolean
        get() = aiApiKey.isNotBlank() || (aiBaseUrl.isNotBlank() && !aiBaseUrl.contains("api.deepseek.com"))
}

enum class ChatSender {
    USER, JUANJUAN
}

@Serializable
data class ChatSession(
    val id: String,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "deepseek-chat"
)

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String = "session-initial",
    val sender: ChatSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CheckIn(
    val date: String, // "yyyy-MM-dd"
    val checkInTime: Long = System.currentTimeMillis(),
    val streak: Int = 1,
    val note: String = "",
    val mood: String = ""
)

/**
 * 真实数据上下文来源。卷卷回复时引用了哪几类研迹数据，每一条都必须
 * 对应到具体的本地数据集合（focus_sessions / exam_sessions / journal_entries / 当前对话）。
 *
 * UI 展示为「已参考你的 N 项学习记录」可展开 chip。
 */
@Serializable
data class ChatContextSource(
    val type: ContextSourceType,
    val count: Int,
    val summary: String
)

enum class ContextSourceType {
    /** 数学模考记录（exam_sessions，限定 subject=math） */
    MATH_EXAM,
    /** 错题/学习记录（聚焦会话 + 模考 note + 日记中「错」字） */
    WRONG_NOTES,
    /** 专注记录（focus_sessions） */
    FOCUS,
    /** 当前对话上下文 */
    CURRENT_CONVERSATION
}

/**
 * 卷卷回复内嵌的「行动指令」。每个 action 都对应一个真实可执行的研迹内部
 * 操作；UI 不应只是把 label 当成另一条 prompt 发出去。
 */
@Serializable
data class JuanjuanAction(
    val id: String,
    val type: JuanjuanActionType,
    val label: String,
    val payload: String = ""
)

enum class JuanjuanActionType {
    /** 把动作写入当日 / 次日计划（journal tomorrowPlan） */
    CREATE_PLAN,
    /** 把当前回答摘要存入日记 */
    SAVE_TO_JOURNAL,
    /** 启动一个 focus session（payload: subjectId|mode|note） */
    START_FOCUS,
    /** 跳转打开日记编辑 */
    OPEN_JOURNAL,
    /** 跳转打开模考 */
    OPEN_EXAM,
    /** 设置一个提醒 */
    SET_REMINDER,
    /** 生成可下载 / 复制的草稿模板（payload: 模板 id） */
    GENERATE_TEMPLATE
}

/**
 * 一条回复的最小结构化单元。
 *   - DIAGNOSIS   「卷卷判断：过程性失分」单行
 *   - EVIDENCE    「原因：草稿混乱 → 定位困难 → 转抄错误」链式说明
 *   - MAIN        主体段落（含可能的 Markdown 文本）
 *   - STEPS       有序步骤（1/2/3）
 *   - ACTION      行动引导文本（与 [动作:...] 配对）
 *   - FOLLOWUP    追问 / 引导
 */
@Serializable
data class JuanjuanResponseBlock(
    val kind: JuanjuanBlockKind,
    val text: String
)

enum class JuanjuanBlockKind {
    DIAGNOSIS,
    EVIDENCE,
    MAIN,
    STEPS,
    ACTION,
    FOLLOWUP
}

/**
 * 完整的卷卷回复结构。优先尝试从内联 token 解析；解析失败则回退到
 * 旧的纯文本段落拆分，保留对历史消息的 100% 兼容。
 */
@Serializable
data class JuanjuanResponse(
    val diagnosis: String? = null,
    val evidence: String? = null,
    val blocks: List<JuanjuanResponseBlock> = emptyList(),
    val actions: List<JuanjuanAction> = emptyList(),
    val followups: List<String> = emptyList(),
    val contextSources: List<ChatContextSource> = emptyList()
)

enum class AchievementRarity(
    val title: String,
    val englishName: String,
    val order: Int
) {
    COMMON("普通", "Common", 1),
    UNCOMMON("优秀", "Uncommon", 2),
    RARE("稀有", "Rare", 3),
    EPIC("史诗", "Epic", 4),
    LEGENDARY("传说", "Legendary", 5),
    MYTHIC("神话", "Mythic", 6)
}

enum class AchievementCategory(val title: String) {
    ALL("全部"),
    JOURNEY("研途启程"),
    FOCUS("专注修炼"),
    STREAK("坚持之路"),
    EXAM("模考试炼"),
    MATH("数学征途"),
    REVIEW("复盘沉淀"),
    HIDDEN("隐藏成就")
}

@Serializable
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val rarity: AchievementRarity = AchievementRarity.COMMON,
    val iconKey: String,
    val currentProgress: Long,
    val targetProgress: Long,
    val unit: String = "",
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val rewardQuote: String = "",
    val isHidden: Boolean = false,
    val seriesId: String? = null,
    val seriesOrder: Int = 0
)

