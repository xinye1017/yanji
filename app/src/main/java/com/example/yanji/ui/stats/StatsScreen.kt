package com.example.yanji.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timelapse
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
import com.example.yanji.data.DurationFormatter
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.YanjiPageHeader
import com.example.yanji.ui.components.YanjiSegmentedControl

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
            items = listOf("本周", "本月", "全部累计"),
            selectedIndex = state.selectedTimeTab,
            onItemSelected = { viewModel.selectTimeTab(it) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Hero Period Card
        StatsHeroCard(
            selectedTimeTab = state.selectedTimeTab,
            periodDurationSecs = state.periodDurationSeconds,
            activeDays = if (state.selectedTimeTab == 1) state.monthlySummary.activeDays else state.weeklySummary.activeDays,
            previousWeekSeconds = state.previousWeekSeconds,
            weeklyTotalSeconds = state.weeklySummary.totalDurationSeconds,
            dailyAverageSeconds = if (state.selectedTimeTab == 1) state.monthlySummary.dailyAverageSeconds else state.weeklySummary.dailyAverageSeconds,
            settings = settings
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Trend Chart (Bar, Line & Heatmap)
        val trendDays = if (state.selectedTimeTab == 1) {
            state.monthlySummary.days
        } else {
            state.weeklySummary.days
        }

        StatsTrendChart(
            selectedTimeTab = state.selectedTimeTab,
            days = trendDays,
            trendChartMode = state.trendChartMode,
            onSelectTrendMode = { viewModel.selectTrendMode(it) },
            onSelectDay = { selectedDayForSheet = it }
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
                icon = Icons.Default.LocalFireDepartment,
                iconTint = YanjiColors.warning,
                iconBg = YanjiColors.warningSoft,
                title = "连续研读",
                value = "${if (state.selectedTimeTab == 1) state.monthlySummary.streakDays else state.weeklySummary.streakDays}",
                unit = "天",
                modifier = Modifier.weight(1f)
            )

            val longest = if (state.selectedTimeTab == 1) state.monthlySummary.longestSession else state.weeklySummary.longestSession
            MetricMiniCard(
                icon = Icons.Default.Timelapse,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                title = "单次最长",
                value = DurationFormatter.formatHoursMinutes(longest?.durationSeconds ?: 0L),
                modifier = Modifier.weight(1f),
                onClick = longest?.let { l -> { onNavigateToFocusDetail(l.id) } }
            )

            val examCount = if (state.selectedTimeTab == 1) state.monthlySummary.examCount else state.weeklySummary.examCount
            MetricMiniCard(
                icon = Icons.Default.Quiz,
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
            onGenerate = { viewModel.generateAnalysis() }
        )

        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    if (selectedDayForSheet != null) {
        StatsDayDetailSheet(
            day = selectedDayForSheet!!,
            sheetState = sheetState,
            onDismiss = { selectedDayForSheet = null },
            onNavigateToDailyDetail = onNavigateToDailyDetail
        )
    }
}
