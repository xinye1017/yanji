package com.example.yanji.data

import com.example.yanji.data.achievement.AchievementCatalog
import com.example.yanji.data.achievement.AchievementDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 卷卷成就系统 V2 —— 高强度考研成就核心仓储。
 *
 * 特性：
 *  - 59 项多维成就覆盖：研途启程、专注修炼、坚持之路、模考试炼、数学征途、复盘沉淀、隐藏成就；
 *  - 6 级稀有度体系：Common / Uncommon / Rare / Epic / Legendary / Mythic；
 *  - 成就系列链条：支持在详情页查看成长梯进度；
 *  - 修复真实计时判定：模考严格基于 actualDurationSeconds，严禁以计划时长冒充实际耗时。
 */
class AchievementRepository internal constructor(
    private val repo: YanjiRepository,
    private val scope: CoroutineScope
) {
    /**
     * @Deprecated 遗留的进程级单例入口，仅保留给尚未接入 [com.example.yanji.di.AppContainer]
     * 的少量测试调用点。生产代码必须由 AppContainer 显式构造注入（含受控 [CoroutineScope]）。
     * 不得新增调用方。
     */
    companion object {
        @Volatile
        private var instance: AchievementRepository? = null

        @Deprecated(
            message = "请改用 AppContainer 注入的 AchievementRepository；此入口仅兼容遗留测试",
            level = DeprecationLevel.WARNING
        )
        fun getInstance(): AchievementRepository {
            return instance ?: synchronized(this) {
                instance ?: AchievementRepository(
                    repo = YanjiRepository.getInstance(),
                    scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                ).also { instance = it }
            }
        }

        // Series IDs
        const val SERIES_MATH = AchievementCatalog.SERIES_MATH
        const val SERIES_FOCUS_HOURS = AchievementCatalog.SERIES_FOCUS_HOURS
        const val SERIES_SINGLE_FOCUS = AchievementCatalog.SERIES_SINGLE_FOCUS
        const val SERIES_STREAK = AchievementCatalog.SERIES_STREAK
        const val SERIES_EXAM_COUNT = AchievementCatalog.SERIES_EXAM_COUNT
        const val SERIES_REVIEW_COUNT = AchievementCatalog.SERIES_REVIEW_COUNT
    }

    val definitions: List<AchievementDef>
        get() = AchievementCatalog.definitions

    val achievements: StateFlow<List<Achievement>> = combine(
        repo.focusSessions,
        repo.examSessions,
        repo.noteEntries,
        repo.checkIns,
        repo.unlockedAchievements
    ) { focus, exam, notes, checkIns, unlockedMap ->
        definitions.map { def ->
            val progress = def.calculateProgress(focus, exam, notes, checkIns)
            val isUnlocked = unlockedMap.containsKey(def.id) || progress >= def.target
            val unlockedAt = unlockedMap[def.id] ?: if (isUnlocked) System.currentTimeMillis() else null

            Achievement(
                id = def.id,
                title = def.title,
                description = def.description,
                category = def.category,
                rarity = def.rarity,
                iconKey = def.iconKey,
                currentProgress = progress,
                targetProgress = def.target,
                unit = def.unit,
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt,
                rewardQuote = def.rewardQuote,
                isHidden = def.isHidden,
                seriesId = def.seriesId,
                seriesOrder = def.seriesOrder
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedCount: StateFlow<Int> = achievements.map { list ->
        list.count { it.isUnlocked }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: Int = definitions.size

    /**
     * 获取指定系列的所有成就定义，并按 order 升序排列。
     */
    fun getSeries(seriesId: String): List<AchievementDef> {
        return AchievementCatalog.getSeries(seriesId)
    }

    /**
     * 重置成就系统与学习测试记录，回归 0/15 初始锁定状态。
     */
    suspend fun resetAllAchievements() {
        repo.resetAchievementsAndStudyRecords()
    }
}
