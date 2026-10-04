package com.example.yanji.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.EpochRange
import com.example.yanji.data.MonthlyStudySummary
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.data.UserSettings
import com.example.yanji.data.WeeklyStudySummary
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.YearlyStudySummary
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 统计口径单位：周 / 月 / 年。
 *
 * 「上周」并入周、「上月」并入月 —— 图表粒度、目标口径、Hero 文案全都按单位走，
 * 只有取哪个区间才关心翻了几期。所以单位和翻期数是两个正交的维度。
 */
enum class StatsTimeUnit { WEEK, MONTH, YEAR }

/**
 * 统计页时间 Tab。声明顺序即分段控制器的展示顺序。
 *
 * [periodsBack] 是往回翻几个整期：周视角 0=本周 / 1=上周，月视角 0=本月 / 1=上月。
 * 年视角恒为 0 —— 不看上一年：跟一个早已结束的完整年度对比，
 * 学不到「现在要不要调整节奏」，「最近还在学吗」由 Hero 的「近 7 天」回答。
 */
enum class StatsTimeTab(val label: String, val unit: StatsTimeUnit, val periodsBack: Int) {
    THIS_WEEK("本周", StatsTimeUnit.WEEK, 0),
    PREVIOUS_WEEK("上周", StatsTimeUnit.WEEK, 1),
    THIS_MONTH("本月", StatsTimeUnit.MONTH, 0),
    PREVIOUS_MONTH("上月", StatsTimeUnit.MONTH, 1),
    THIS_YEAR("本年", StatsTimeUnit.YEAR, 0);

    /**
     * 周视角才谈「回翻几周」。非周视角下归零：
     * 否则停在「本月」时，周汇总那条 flow 会被错移到上周，白扫一段没人看的区间。
     */
    val weeksBack: Long get() = if (unit == StatsTimeUnit.WEEK) periodsBack.toLong() else 0L

    /** 月视角的 [weeksBack] 对偶项，见 [weeksBack]。 */
    val monthsBack: Long get() = if (unit == StatsTimeUnit.MONTH) periodsBack.toLong() else 0L

    /**
     * 该 Tab 对应的日历窗口。
     *
     * 总时长、科目分布、Hero 对比基准共用这一个区间 —— 分开算的话，
     * 三个数字各按各的算法取窗口，切换视角时对不上账。
     */
    fun epochRange(): EpochRange = when (unit) {
        StatsTimeUnit.WEEK -> YanjiTime.weekRange(periodsBack.toLong())
        StatsTimeUnit.MONTH -> YanjiTime.monthRange(periodsBack.toLong())
        StatsTimeUnit.YEAR -> YanjiTime.currentYearRange()
    }
}

/** Hero 卡「近 7 天」胶囊的窗口长度（含今天）。 */
internal const val RECENT_DAYS = 7L

data class StatsUiState(
    val selectedTimeTab: StatsTimeTab,
    val subjectStatsLevel: SubjectStatsLevel,
    val trendChartMode: TrendMode,
    /** 科目分布甜甜圈中心文案：直接取 Tab 标签，回看上周时它必须跟着变成「上周」。 */
    val timeRangeTitle: String,
    val weeklySummary: WeeklyStudySummary,
    val monthlySummary: MonthlyStudySummary,
    val subjectDistribution: List<SubjectDistributionItem>,
    /** 主指标卡的大数字：本周/上周=周汇总；本月/上月=月汇总；本年=自然年累计。 */
    val periodDurationSeconds: Long,
    /** 展示窗口的上一个自然周有效时长，用于「较上周」对比。 */
    val previousWeekSeconds: Long,
    /** 本年汇总（tab 为年视角才订阅，其余视角为空壳，避免进入页面就查全年）。 */
    val yearlySummary: YearlyStudySummary,
    /** 滚动近 7 天总时长：本年视角的 hero 用它替代「较上周」。 */
    val recentSevenDaysSeconds: Long,
    val latestReport: AiAnalysis?,
    val isAnalyzing: Boolean,
    val analysisError: String? = null
)

/**
 * 统计页 Feature ViewModel。
 *
 * 科目分布 Flow 依赖（时间 Tab × 统计层级）两个选择态，用 flatMapLatest 跟随切换重订阅；
 * 周 / 月汇总同理跟着 Tab 回翻；其余选择态（图表模式、AI 报告、分析中标记）合并进同一 UiState 流。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(
        StatsFilterState(
            timeTab = StatsTimeTab.THIS_WEEK,
            subjectLevel = SubjectStatsLevel.SUBCATEGORY,
            trendChartMode = TrendMode.BAR
        )
    )
    private val isAnalyzing = MutableStateFlow(false)
    private val analysisError = MutableStateFlow<String?>(null)

    private val chartSelection = filterState.map { it.timeTab to it.subjectLevel }.distinctUntilChanged()

    private val subjectDistributionFlow = chartSelection.flatMapLatest { (tab, level) ->
        statsRepo.getSubjectDistributionFlow(tab.epochRange(), level)
            // 分布数据源会返回全量科目目录（含 0 时长的空科目）；
            // 展示口径：只有真实产生过计时的科目才进入 UiState。
            .map { list -> list.filter { it.durationSeconds > 0L } }
    }

    private val reportState = combine(isAnalyzing, repo.aiAnalyses, analysisError) { analyzing, all, error ->
        ReportStateTuple(analyzing, all, error)
    }

    private val periodDurationFlow = filterState.map { it.timeTab }.distinctUntilChanged()
        .flatMapLatest { tab -> statsRepo.getStudyDurationFlow(tab.epochRange()) }

    /**
     * 本年汇总按 Tab 懒加载：只有切到「本年」才订阅。
     *
     * 全年日粒度聚合（有效天数、最长连续段）要逐日扫一年，周/月视角下白扫；
     * 科目分布早就这么做了，这里保持一致，避免进入统计页就付全年查询的钱。
     */
    private val yearlySummaryFlow = filterState.map { it.timeTab }.distinctUntilChanged().flatMapLatest { tab ->
        if (tab.unit == StatsTimeUnit.YEAR) statsRepo.getYearlyStudySummaryFlow() else flowOf(YearlyStudySummary())
    }

    private val recentDaysFlow = statsRepo.getLastDaysDurationFlow(RECENT_DAYS)

    /**
     * 周汇总跟着 Tab 回翻：看「上周」时它就是上一个自然周。
     * 月视角下不展示周汇总，窗口归零停在自然周起点（见 [StatsTimeTab.weeksBack]）。
     */
    private val weeklySummaryFlow = filterState.map { it.timeTab }.distinctUntilChanged()
        .flatMapLatest { tab -> statsRepo.getWeeklyStudySummaryFlow(tab.weeksBack) }

    private val monthlySummaryFlow = filterState.map { it.timeTab }.distinctUntilChanged()
        .flatMapLatest { tab -> statsRepo.getMonthlyStudySummaryFlow(tab.monthsBack) }

    /**
     * 「较上周 / 较上上周」的对比基准：展示窗口的**上一个**自然周。
     *
     * 跟 Tab 联动，所以回看上周时它自动再往前挪一周，Hero 拿到的始终是
     * 「比展示窗口更早的那一周」，而不是写死的上一个自然周。
     *
     * 只有周视角订阅：Hero 的对比胶囊只在周视角渲染，月 / 年视角去查这个窗口是白扫。
     */
    private val previousWeekFlow = filterState.map { it.timeTab }.distinctUntilChanged()
        .flatMapLatest { tab ->
            if (tab.unit == StatsTimeUnit.WEEK) {
                statsRepo.getStudyDurationFlow(YanjiTime.weekRange(tab.weeksBack + 1))
            } else {
                flowOf(0L)
            }
        }

    private val periodSummaries = combine(
        weeklySummaryFlow,
        monthlySummaryFlow,
        previousWeekFlow
    ) { weekly, monthly, previousWeek ->
        PeriodSummaries(weekly, monthly, previousWeek)
    }

    private val selectionMetrics = combine(
        filterState,
        periodDurationFlow,
        periodSummaries,
        yearlySummaryFlow,
        recentDaysFlow
    ) { filter, periodSeconds, summaries, yearly, recentSeconds ->
        SelectionMetrics(
            filter.timeTab,
            filter.subjectLevel,
            filter.trendChartMode,
            periodSeconds,
            summaries.previousWeekSeconds,
            summaries.weekly,
            summaries.monthly,
            yearly,
            recentSeconds
        )
    }

    private val initial = StatsUiState(
        selectedTimeTab = StatsTimeTab.THIS_WEEK,
        subjectStatsLevel = SubjectStatsLevel.SUBCATEGORY,
        trendChartMode = TrendMode.BAR,
        timeRangeTitle = StatsTimeTab.THIS_WEEK.label,
        weeklySummary = WeeklyStudySummary(0L, 0L, 0, null, 0, 0, emptyList()),
        monthlySummary = MonthlyStudySummary(0L, 0L, 0, null, 0, 0, 2026, 9, java.time.DayOfWeek.MONDAY, emptyList()),
        subjectDistribution = emptyList(),
        periodDurationSeconds = 0L,
        previousWeekSeconds = 0L,
        yearlySummary = YearlyStudySummary(),
        recentSevenDaysSeconds = 0L,
        latestReport = repo.aiAnalyses.value.firstOrNull(),
        isAnalyzing = false,
        analysisError = null
    )

    val uiState: StateFlow<StatsUiState> = combine(
        subjectDistributionFlow,
        selectionMetrics,
        reportState
    ) { distribution, metrics, reportTuple ->
        val tab = metrics.tab
        val (analyzing, allAnalyses, error) = reportTuple
        StatsUiState(
            selectedTimeTab = tab,
            subjectStatsLevel = metrics.level,
            trendChartMode = metrics.chartMode,
            timeRangeTitle = tab.label,
            weeklySummary = metrics.weekly,
            monthlySummary = metrics.monthly,
            subjectDistribution = distribution,
            periodDurationSeconds = metrics.periodSeconds,
            // 上一个自然周：周一 00:00 至本周周一 00:00。
            previousWeekSeconds = metrics.previousWeekSeconds,
            yearlySummary = metrics.yearly,
            recentSevenDaysSeconds = metrics.recentSeconds,
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

    fun selectTimeTab(tab: StatsTimeTab) {
        filterState.update { current ->
            val newMode = when {
                // 本月/上月默认热力图；离开月视角时把 HEATMAP 收敛回柱状，别把热力图带到周/本年。
                tab.unit == StatsTimeUnit.MONTH && current.trendChartMode == TrendMode.BAR -> TrendMode.HEATMAP
                tab.unit != StatsTimeUnit.MONTH && current.trendChartMode == TrendMode.HEATMAP -> TrendMode.BAR
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

    private data class StatsFilterState(
        val timeTab: StatsTimeTab = StatsTimeTab.THIS_WEEK,
        val subjectLevel: SubjectStatsLevel = SubjectStatsLevel.SUBCATEGORY,
        val trendChartMode: TrendMode = TrendMode.BAR
    )

    private data class SelectionMetrics(
        val tab: StatsTimeTab,
        val level: SubjectStatsLevel,
        val chartMode: TrendMode,
        val periodSeconds: Long,
        val previousWeekSeconds: Long,
        val weekly: WeeklyStudySummary,
        val monthly: MonthlyStudySummary,
        val yearly: YearlyStudySummary,
        val recentSeconds: Long
    )

    private data class PeriodSummaries(
        val weekly: WeeklyStudySummary,
        val monthly: MonthlyStudySummary,
        val previousWeekSeconds: Long
    )

    private data class ReportStateTuple(
        val isAnalyzing: Boolean,
        val allAnalyses: List<AiAnalysis>,
        val error: String?
    )
}
