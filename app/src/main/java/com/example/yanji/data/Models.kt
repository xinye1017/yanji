package com.example.yanji.data

import androidx.compose.runtime.Immutable
import com.example.yanji.theme.*
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 计时模式单一数据源：模式名 ↔ 目标时长。
 * FocusScreen、专注准备页的时长滚轮与今日计划共用，避免多处硬编码漂移。
 */
object FocusModes {
    const val COUNT_UP = "正向计时"
    const val POMODORO_25 = "25分钟番茄"
    const val POMODORO_45 = "45分钟深度"
    const val DEEP_60 = "60分钟小测"
    const val BIG_90 = "90分钟专题"

    val ALL: List<String> = listOf(COUNT_UP, POMODORO_25, POMODORO_45, DEEP_60, BIG_90)

    /**
     * 时长 → 计时模式：正好落在四个标准档位上就复用该档位名，其余走「N分钟专注」。
     *
     * 此前这份映射在专注页的 `FocusViewModel.modeForTask` 与专注准备页的
     * `quietModeForMinutes` 里各写了一遍。两份实现今天恰好一致，但它们服务的是同一条
     * 「用户选了一个分钟数 → 该用哪个模式」的规则 —— 一旦某一侧单独调整档位
     * （新增 30 分钟、或把 45 分钟改名），两侧就会静默漂移。
     * 首页今日计划搬走后 `modeForTask` 失去唯一调用方已删除；本函数是该规则唯一实现。
     */
    fun forPlannedMinutes(minutes: Int): String = when (minutes) {
        25 -> POMODORO_25
        45 -> POMODORO_45
        60 -> DEEP_60
        90 -> BIG_90
        else -> "${minutes}分钟专注"
    }

    /**
     * 模式 → 目标秒数：一律从模式名里解析「N 分钟」；正向计时与解析不到的返回 0。
     *
     * 四个标准档位的模式名本身就带着自己的分钟数（`25分钟番茄` … `90分钟专题`），
     * 因此一条正则就覆盖全部档位，不必为每个档位单列分支。
     *
     * 曾经写成 `mode.contains("25") -> 1500` 这类子串判断，`125分钟专注` 会命中 "25"
     * 而被当成 25 分钟计时。只要时长档位不超过 100 这个 bug 就不可达；专注准备页与
     * 今日计划的时长上限都放到 180 之后，125/145/160 分钟都成了用户真能选到的值，
     * 子串匹配的错误计时就从「不可达」变成真会发生。
     */
    fun targetSeconds(mode: String): Long =
        Regex("""(\d+)\s*分钟""").find(mode)?.groupValues?.get(1)?.toLongOrNull()?.times(60L) ?: 0L
}

/**
 * 从外部页面（如今日计划卡片）请求在专注页预设/启动专注的传参模型。
 */
@Serializable
@Immutable
data class FocusPresetRequest(
    val subjectId: String,
    val subjectName: String,
    val plannedMinutes: Int = 0,
    val note: String = ""
)

/**
 * 首页快捷操作。type 区分内置模板与用户自定义组合：
 * - start_focus / exam / journal：出厂默认三项，行为与原先写死的三个按钮一致
 *   （`journal` 是写入 `quick_start_presets.type` 的持久化值，不随代码命名调整）
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
    companion object {
        const val TYPE_START_FOCUS = "start_focus"
        const val TYPE_EXAM = "exam"
        const val TYPE_JOURNAL = "journal"
        const val TYPE_CUSTOM = "custom"
    }
}

/**
 * 轻量学习任务：只服务「今天准备做什么 → 直接开始专注」这条执行链。
 *
 * 刻意不承担通用 Todo 职责：没有优先级、提醒、重复规则、截止时间或子任务。
 *
 * 对 Compose 而言是只读不可变的（字段全 val、无集合字段），故标注 [Immutable]：
 * 编译器可据此把它当稳定类型，避免以 List 传参时跳过失败。反之，含 List/Map 的
 * 领域模型（如 NoteEntry、DayBarData）**不要**标 @Immutable，那等于对编译器撒谎。
 */
@Serializable
@Immutable
data class StudyTask(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val subjectId: String,
    val subjectName: String,
    val title: String,
    val plannedMinutes: Int,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Immutable
data class Subject(
    val id: String,
    val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val parentId: String? = null
) {
    val isCategory: Boolean get() = parentId == null
}

/**
 * 学科层级的**唯一事实来源**。
 *
 * 学科现在持久化在 Room 的 `subjects` 表（v14 起），这个 object 是该表的
 * **同步内存镜像**：由 [com.example.yanji.data.YanjiRepository] 在数据库发射时
 * 整体替换 [all]。之所以保留这套静态查询函数，是因为统计 / 成就 / 诊断等大量
 * 纯函数都在同步语境里按 id 反查学科，把它们全部改成挂起或 Flow 的收益极低。
 *
 * 约定：**只允许 Repository 调用 [replaceAll] 写入**，其余调用方一律只读。
 */
object SubjectCatalog {
    const val UNCLASSIFIED_SUFFIX = "__unclassified"

    /** 默认主学科主色（数学一等默认首选学科色）。 */
    const val DEFAULT_PRIMARY_COLOR = "#356AE6"

    /** 统计/反查未命中时的兜底中性灰色彩。 */
    const val DEFAULT_FALLBACK_COLOR = "#667085"

    /** 顶级学科创建时的候选调色板（按次序分配，避免相邻学科重色）。 */
    val CATEGORY_PALETTE: List<String> = listOf(
        "#356AE6", "#8B7CF6", "#2F9E6D", "#E67E22",
        "#B8426B", "#3B78B8", "#A95822", "#2F7F55"
    )

    /** 新装 / 迁移时写入的默认学科，同时也是「恢复默认」的数据源。 */
    val defaults: List<Subject> = listOf(
        Subject("math", "数学一", "#356AE6", 1),
        Subject("math_advanced", "高等数学", "#2453BF", 11, parentId = "math"),
        Subject("math_linear", "线性代数", "#4C7BE8", 12, parentId = "math"),
        Subject("math_probability", "概率论", "#678DEB", 13, parentId = "math"),
        Subject("major", "408专业课", "#8B7CF6", 2),
        Subject("major_organization", "计算机组成原理", "#725FD8", 21, parentId = "major"),
        Subject("major_data_structure", "数据结构", "#9A8CFA", 22, parentId = "major"),
        Subject("major_network", "计算机网络", "#AA9DFB", 23, parentId = "major"),
        Subject("major_os", "操作系统", "#B8ADFC", 24, parentId = "major"),
        Subject("english", "英语一", "#2F9E6D", 3),
        Subject("politics", "政治", "#E67E22", 4),
        Subject("other", "其他", "#667085", 5)
    )

    @Volatile
    private var snapshot: List<Subject> = defaults

    /** 当前学科列表的内存镜像。数据库为空时回落到 [defaults]，保证首帧不空白。 */
    val all: List<Subject> get() = snapshot

    /** 由 Repository 在数据库发射 / 备份恢复后调用。 */
    fun replaceAll(subjects: List<Subject>) {
        snapshot = if (subjects.isEmpty()) defaults else subjects.sortedWith(
            compareBy({ it.sortOrder }, { it.name })
        )
    }

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
@Immutable
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
@Immutable
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
data class NoteEntry(
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
    val updatedAt: Long = System.currentTimeMillis(),
    /** 收藏标记（v15 新增）。历史页向右滑即可切换。 */
    val isFavorite: Boolean = false,
    /** 草稿标记（v16 新增）。已保存随笔为 false，暂存草稿为 true。 */
    val isDraft: Boolean = false
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
    val aiModel: String = "",
    val aiProtocol: String = "OPENAI_CHAT",
    /**
     * 外观（主题）偏好，存 [com.example.yanji.theme.YanjiThemeMode] 的 `name`。
     * 用 String 而不是枚举，是为了让 Room 迁移、旧备份导入、以及未来新增模式都能安全降级
     * （解析走 `YanjiThemeMode.fromStorage`，未知值回落到 SYSTEM）。
     */
    val themeMode: String = com.example.yanji.theme.YanjiThemeMode.SYSTEM.name,
    /** 当前学习伙伴主题；未知值由主题层安全回落到 CLOUD（卷卷）。 */
    val mascotTheme: String = com.example.yanji.theme.MascotThemeId.CLOUD.name
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
@Immutable
data class CheckIn(
    val date: String, // "yyyy-MM-dd"
    val checkInTime: Long = System.currentTimeMillis(),
    val streak: Int = 1,
    val note: String = "",
    val mood: String = ""
)

enum class AchievementRarity(
    val title: String
) {
    COMMON("普通"),
    UNCOMMON("优秀"),
    RARE("稀有"),
    EPIC("史诗"),
    LEGENDARY("传说"),
    MYTHIC("神话")
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

