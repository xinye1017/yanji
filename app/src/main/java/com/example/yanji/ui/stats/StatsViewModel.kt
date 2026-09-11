package com.example.yanji.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.StudyTimeRange
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.data.WeeklyStudySummary
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val subjectDistribution: List<SubjectDistributionItem>,
    /** 主指标卡的大数字：本周=周汇总；本月=近 30 天；全部=累计总学时。 */
    val periodDurationSeconds: Long,
    val latestReport: AiAnalysis?,
    val isAnalyzing: Boolean
)

/**
 * 统计页 Feature ViewModel。
 *
 * 科目分布 Flow 依赖（时间范围 × 统计层级）两个选择态，用 flatMapLatest 跟随切换重订阅；
 * 其余选择态（tab、图表模式、AI 报告、分析中标记）合并进同一 UiState 流。
 */
class StatsViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    private val selectedTimeTab = MutableStateFlow(0)
    private val subjectStatsLevel = MutableStateFlow(SubjectStatsLevel.SUBCATEGORY)
    private val trendChartMode = MutableStateFlow(TrendMode.BAR)
    private val latestReport = MutableStateFlow<AiAnalysis?>(null)
    private val isAnalyzing = MutableStateFlow(false)

    private val chartSelection = combine(selectedTimeTab, subjectStatsLevel) { tab, level ->
        tab to level
    }

    private val subjectDistributionFlow = chartSelection.flatMapLatest { (tab, level) ->
        statsRepo.getSubjectDistributionFlow(tab.toTimeRange(), level)
    }

    private val reportState = combine(latestReport, isAnalyzing, repo.aiAnalyses) { report, analyzing, all ->
        Triple(report, analyzing, all)
    }

    private val initial = StatsUiState(
        selectedTimeTab = 0,
        subjectStatsLevel = SubjectStatsLevel.SUBCATEGORY,
        trendChartMode = TrendMode.BAR,
        timeRange = StudyTimeRange.WEEK,
        weeklySummary = statsRepo.getWeeklyStudySummary(),
        subjectDistribution = statsRepo.getSubjectDistribution(
            StudyTimeRange.WEEK, SubjectStatsLevel.SUBCATEGORY
        ),
        periodDurationSeconds = statsRepo.getWeeklyStudySummary().totalDurationSeconds,
        latestReport = repo.aiAnalyses.value.firstOrNull(),
        isAnalyzing = false
    )

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepo.getWeeklyStudySummaryFlow(),
        subjectDistributionFlow,
        chartSelection,
        trendChartMode,
        reportState
    ) { weekly, distribution, selection, chartMode, reportTriple ->
        val (tab, level) = selection
        val (report, analyzing, allAnalyses) = reportTriple
        StatsUiState(
            selectedTimeTab = tab,
            subjectStatsLevel = level,
            trendChartMode = chartMode,
            timeRange = tab.toTimeRange(),
            weeklySummary = weekly,
            subjectDistribution = distribution,
            periodDurationSeconds = when (tab) {
                0 -> weekly.totalDurationSeconds
                1 -> repo.getStudyDurationForPeriod(30)
                else -> repo.getTotalStudyDurationSeconds()
            },
            latestReport = report ?: allAnalyses.firstOrNull(),
            isAnalyzing = analyzing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initial
    )

    // ---- 动作 ----

    fun selectTimeTab(tab: Int) {
        selectedTimeTab.value = tab
    }

    fun selectSubjectLevel(level: SubjectStatsLevel) {
        subjectStatsLevel.value = level
    }

    fun selectTrendMode(mode: TrendMode) {
        trendChartMode.value = mode
    }

    /** 生成近 7 天的 AI 学情诊断；进行中重复点击直接忽略。 */
    fun generateAnalysis() {
        if (isAnalyzing.value) return
        viewModelScope.launch {
            isAnalyzing.value = true
            try {
                latestReport.value = repo.generateAiAnalysis(7)
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
}
