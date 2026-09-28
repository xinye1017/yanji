package com.example.yanji.ui.stats

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DayBarData
import com.example.yanji.data.DailySessionItem
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.MonthBarData
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.AiConfigDialog
import com.example.yanji.ui.components.YanjiPageHeader
import com.example.yanji.ui.components.YanjiSegmentedControl
import com.example.yanji.ui.components.YanjiSegmentedControlVariant

/**
 * 把本年汇总的月粒度条装配成趋势图数据。
 *
 * 纯函数：月 → 柱、以及累计曲线的累加都在这里，UI 只负责画。
 * 抽出来是为了能被 JVM 单测钉住（累计值必须逐月单调递增）。
 */
internal fun yearTrendBars(months: List<MonthBarData>, mode: TrendMode): List<DayBarData> {
    if (months.isEmpty()) return emptyList()
    val bars = months.map { month ->
        DayBarData(
            date = "${month.year}-${month.month}",
            dayLabel = month.label,
            durationSeconds = month.durationSeconds,
            // 复用 isToday 作为「当月」高亮：图表只关心「哪一格是当前所在」。
            isToday = month.isCurrentMonth,
            subjectDistribution = month.subjectDistribution
        )
    }
    if (mode != TrendMode.LINE) return bars

    // 累计曲线：逐月累加。画的是成长斜率，不是单月波动 ——
    // 所以点上的数值语义也从「当月」变成「至今」，图表文案跟着换（见 trendModeLabel）。
    var accumulated = 0L
    return bars.map { bar ->
        accumulated += bar.durationSeconds
        bar.copy(durationSeconds = accumulated)
    }
}

/** 有效学习天数：三个视角各自的口径（本年 = 整个自然年）。 */
private fun StatsUiState.activeDaysForTab(): Int = when (selectedTimeTab) {
    STATS_TAB_WEEK -> weeklySummary.activeDays
    STATS_TAB_MONTH -> monthlySummary.activeDays
    else -> yearlySummary.activeDays
}

/** 日均投入：本年的分母是「有效学习天数」，不是 7 天也不是 365 天。 */
private fun StatsUiState.dailyAverageForTab(): Long = when (selectedTimeTab) {
    STATS_TAB_WEEK -> weeklySummary.dailyAverageSeconds
    STATS_TAB_MONTH -> monthlySummary.dailyAverageSeconds
    else -> yearlySummary.dailyAverageSeconds
}

private fun StatsUiState.streakDaysForTab(): Int = when (selectedTimeTab) {
    STATS_TAB_WEEK -> weeklySummary.streakDays
    STATS_TAB_MONTH -> monthlySummary.streakDays
    else -> yearlySummary.longestStreakDays
}

private fun StatsUiState.longestSessionForTab(): DailySessionItem? = when (selectedTimeTab) {
    STATS_TAB_WEEK -> weeklySummary.longestSession
    STATS_TAB_MONTH -> monthlySummary.longestSession
    else -> yearlySummary.longestSession
}

private fun StatsUiState.examCountForTab(): Int = when (selectedTimeTab) {
    STATS_TAB_WEEK -> weeklySummary.examCount
    STATS_TAB_MONTH -> monthlySummary.examCount
    else -> yearlySummary.examCount
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    viewModel: StatsViewModel = yanjiViewModel { container ->
        StatsViewModel(container.repository, container.statisticsRepository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var selectedDayForSheet by remember { mutableStateOf<DayBarData?>(null) }
    // 月粒度抽屉：本年视图点柱子打开。按月柱的 "yyyy-M" 回查 MonthBarData，
    // 这样抽屉拿到的是完整月对象（有效天数、科目分布），而不是图表用的 DayBarData。
    var selectedMonthForSheet by remember { mutableStateOf<MonthBarData?>(null) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

        YanjiPageHeader(
            title = "学习统计"
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))

        // Time Range Filter
        YanjiSegmentedControl(
            items = listOf("本周", "本月", "本年"),
            selectedIndex = state.selectedTimeTab,
            onItemSelected = { viewModel.selectTimeTab(it) },
            variant = YanjiSegmentedControlVariant.OnPage,
            height = 48.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Hero Period Card
        StatsHeroCard(
            selectedTimeTab = state.selectedTimeTab,
            periodDurationSecs = state.periodDurationSeconds,
            activeDays = state.activeDaysForTab(),
            previousWeekSeconds = state.previousWeekSeconds,
            weeklyTotalSeconds = state.weeklySummary.totalDurationSeconds,
            dailyAverageSeconds = state.dailyAverageForTab(),
            recentSevenDaysSeconds = state.recentSevenDaysSeconds,
            settings = settings
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Trend Chart (Bar, Line & Heatmap)
        // 本年是月粒度：柱状一根一月，折线是累计值 —— 两者喂给同一张图的数据不同，
        // 所以先按视角把数据备好，图表只管画。
        val effectiveTrendMode = resolveEffectiveTrendMode(state.selectedTimeTab, state.trendChartMode)
        val trendDays = when (state.selectedTimeTab) {
            STATS_TAB_MONTH -> state.monthlySummary.days
            STATS_TAB_YEAR -> yearTrendBars(state.yearlySummary.months, effectiveTrendMode)
            else -> state.weeklySummary.days
        }

        StatsTrendChart(
            selectedTimeTab = state.selectedTimeTab,
            days = trendDays,
            trendChartMode = state.trendChartMode,
            onSelectTrendMode = { viewModel.selectTrendMode(it) },
            onSelectDay = { bar ->
                val month = state.yearlySummary.months
                    .firstOrNull { "${it.year}-${it.month}" == bar.date }
                if (month != null) selectedMonthForSheet = month else selectedDayForSheet = bar
            }
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Subject Distribution
        SubjectDistributionCard(
            subjectDistribution = state.subjectDistribution,
            subjectStatsLevel = state.subjectStatsLevel,
            timeRangeTitle = state.timeRange.title,
            onSelectSubjectLevel = { viewModel.selectSubjectLevel(it) },
            onNavigateToSubjectDetail = onNavigateToSubjectDetail
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Key Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricMiniCard(
                icon = RemixIcons.FireFill,
                iconTint = YanjiColors.warning,
                iconBg = YanjiColors.warningSoft,
                // 本年讲「最长连续」：当前连续在年初断一次就归零，作为年度指标没有信息量。
                title = if (state.selectedTimeTab == STATS_TAB_YEAR) "最长连续" else "连续研读",
                value = "${state.streakDaysForTab()}",
                unit = "天",
                modifier = Modifier.weight(1f)
            )

            val longest = state.longestSessionForTab()
            MetricMiniCard(
                icon = RemixIcons.TimeLine,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                title = "单次最长",
                value = DurationFormatter.formatHoursMinutes(longest?.durationSeconds ?: 0L),
                modifier = Modifier.weight(1f),
                onClick = longest?.let { l -> { onNavigateToFocusDetail(l.id) } }
            )

            val examCount = state.examCountForTab()
            MetricMiniCard(
                icon = RemixIcons.QuestionnaireLine,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                title = "全真模拟",
                value = "$examCount",
                unit = "场",
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToExamHistory() }
            )
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // AI Diagnosis Trigger & Report Card
        StatsAiReportCard(
            report = state.latestReport,
            isAnalyzing = state.isAnalyzing,
            errorMessage = state.analysisError,
            onGenerate = {
                if (settings.isAiConfigured) viewModel.generateAnalysis()
                else showAiConfigDialog = true
            }
        )

        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    if (selectedMonthForSheet != null) {
        StatsMonthDetailSheet(
            month = selectedMonthForSheet!!,
            sheetState = sheetState,
            onDismiss = { selectedMonthForSheet = null }
        )
    }
    if (selectedDayForSheet != null) {
        StatsDayDetailSheet(
            day = selectedDayForSheet!!,
            sheetState = sheetState,
            onDismiss = { selectedDayForSheet = null },
            onNavigateToDailyDetail = onNavigateToDailyDetail
        )
    }
    if (showAiConfigDialog) {
        AiConfigDialog(onDismissRequest = { showAiConfigDialog = false })
    }
}
