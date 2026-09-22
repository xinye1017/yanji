package com.example.yanji.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.MonthlyStudySummary
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.StudyTimeRange
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.data.UserSettings
import com.example.yanji.data.WeeklyStudySummary
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 统计页不可变 UiState：周/月/全部的选择态、图表模式与全部统计数据都在这里。
 * 页面只渲染，不再直接读仓库或做派生计算。
 */
data class StatsUiState(
    /** 0: 本周, 1: 本月, 2: 全部累计 */
    val selectedTimeTab: Int,
    val subjectStatsLevel: SubjectStatsLevel,
    val trendChartMode: TrendMode,
    val timeRange: StudyTimeRange,
    val weeklySummary: WeeklyStudySummary,
    val monthlySummary: MonthlyStudySummary,
    val subjectDistribution: List<SubjectDistributionItem>,
    /** 主指标卡的大数字：本周=周汇总；本月=近 30 天；全部=累计总学时。 */
    val periodDurationSeconds: Long,
    /** 上一个 7 天窗口（第 8~14 天前）的有效时长，用于「较上周」对比。 */
    val previousWeekSeconds: Long,
    val latestReport: AiAnalysis?,
    val isAnalyzing: Boolean,
    val analysisError: String? = null
)

/**
 * 统计页 Feature ViewModel。
 *
 * 科目分布 Flow 依赖（时间范围 × 统计层级）两个选择态，用 flatMapLatest 跟随切换重订阅；
 * 其余选择态（tab、图表模式、AI 报告、分析中标记）合并进同一 UiState 流。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(
        StatsFilterState(
            timeTab = 0,
            subjectLevel = SubjectStatsLevel.SUBCATEGORY,
            trendChartMode = TrendMode.BAR
        )
    )
    private val isAnalyzing = MutableStateFlow(false)
    private val analysisError = MutableStateFlow<String?>(null)

    private val chartSelection = filterState.map { it.timeTab to it.subjectLevel }.distinctUntilChanged()

    private val subjectDistributionFlow = chartSelection.flatMapLatest { (tab, level) ->
        statsRepo.getSubjectDistributionFlow(tab.toTimeRange(), level)
            // 分布数据源会返回全量科目目录（含 0 时长的空科目）；
            // 展示口径：只有真实产生过计时的科目才进入 UiState。
            .map { list -> list.filter { it.durationSeconds > 0L } }
    }

    private val reportState = combine(isAnalyzing, repo.aiAnalyses, analysisError) { analyzing, all, error ->
        ReportStateTuple(analyzing, all, error)
    }

    private val periodDurationFlow = filterState.map { it.timeTab }.distinctUntilChanged().flatMapLatest { tab ->
        statsRepo.getStudyDurationFlow(tab.toTimeRange())
    }

    private val selectionMetrics = combine(
        filterState,
        periodDurationFlow,
        statsRepo.getPreviousCalendarWeekDurationFlow()
    ) { filter, periodSeconds, previousWeekSeconds ->
        SelectionMetrics(filter.timeTab, filter.subjectLevel, filter.trendChartMode, periodSeconds, previousWeekSeconds)
    }

    private val initial = StatsUiState(
        selectedTimeTab = 0,
        subjectStatsLevel = SubjectStatsLevel.SUBCATEGORY,
        trendChartMode = TrendMode.BAR,
        timeRange = StudyTimeRange.WEEK,
        weeklySummary = WeeklyStudySummary(0L, 0L, 0, null, 0, 0, emptyList()),
        monthlySummary = MonthlyStudySummary(0L, 0L, 0, null, 0, 0, 2026, 9, java.time.DayOfWeek.MONDAY, emptyList()),
        subjectDistribution = emptyList(),
        periodDurationSeconds = 0L,
        previousWeekSeconds = 0L,
        latestReport = repo.aiAnalyses.value.firstOrNull(),
        isAnalyzing = false,
        analysisError = null
    )

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepo.getWeeklyStudySummaryFlow(),
        statsRepo.getMonthlyStudySummaryFlow(),
        subjectDistributionFlow,
        selectionMetrics,
        reportState
    ) { weekly, monthly, distribution, metrics, reportTuple ->
        val tab = metrics.tab
        val level = metrics.level
        val (analyzing, allAnalyses, error) = reportTuple
        StatsUiState(
            selectedTimeTab = tab,
            subjectStatsLevel = level,
            trendChartMode = metrics.chartMode,
            timeRange = tab.toTimeRange(),
            weeklySummary = weekly,
            monthlySummary = monthly,
            subjectDistribution = distribution,
            periodDurationSeconds = metrics.periodSeconds,
            // Previous calendar week: Monday 00:00 through this Monday 00:00.
            previousWeekSeconds = metrics.previousWeekSeconds,
            latestReport = allAnalyses.firstOrNull(),
            isAnalyzing = analyzing,
            analysisError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initial
    )

    // ---- 动作 ----

    fun selectTimeTab(tab: Int) {
        filterState.update { current ->
            val newMode = when {
                tab == 1 && current.trendChartMode == TrendMode.BAR -> TrendMode.HEATMAP
                tab != 1 && current.trendChartMode == TrendMode.HEATMAP -> TrendMode.BAR
                else -> current.trendChartMode
            }
            current.copy(timeTab = tab, trendChartMode = newMode)
        }
    }

    /** 用户设置（同步读缓存）：统计页用于计算周目标进度。 */
    val settings: StateFlow<UserSettings> get() = repo.settings

    fun selectSubjectLevel(level: SubjectStatsLevel) {
        filterState.update { it.copy(subjectLevel = level) }
    }

    fun selectTrendMode(mode: TrendMode) {
        filterState.update { it.copy(trendChartMode = mode) }
    }

    fun clearAnalysisError() {
        analysisError.value = null
    }

    /** 生成近 7 天的 AI 学情诊断；进行中重复点击直接忽略。 */
    fun generateAnalysis() {
        if (isAnalyzing.value) return
        viewModelScope.launch {
            isAnalyzing.value = true
            analysisError.value = null
            try {
                repo.generateAiAnalysis(7)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                analysisError.value = e.message ?: "生成学情诊断失败"
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    private fun Int.toTimeRange(): StudyTimeRange = when (this) {
        0 -> StudyTimeRange.WEEK
        1 -> StudyTimeRange.MONTH
        else -> StudyTimeRange.ALL
    }

    private data class StatsFilterState(
        val timeTab: Int = 0,
        val subjectLevel: SubjectStatsLevel = SubjectStatsLevel.SUBCATEGORY,
        val trendChartMode: TrendMode = TrendMode.BAR
    )

    private data class SelectionMetrics(
        val tab: Int,
        val level: SubjectStatsLevel,
        val chartMode: TrendMode,
        val periodSeconds: Long,
        val previousWeekSeconds: Long
    )

    private data class ReportStateTuple(
        val isAnalyzing: Boolean,
        val allAnalyses: List<AiAnalysis>,
        val error: String?
    )
}
