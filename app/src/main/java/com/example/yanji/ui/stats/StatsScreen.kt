package com.example.yanji.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.*
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.JuanjuanAvatar
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    repo: YanjiRepository = YanjiRepository.getInstance(),
    statsRepo: StudyStatisticsRepository = StudyStatisticsRepository.getInstance()
) {
    var selectedTimeTab by remember { mutableIntStateOf(0) } // 0: 本周, 1: 本月, 2: 全部累计
    var trendChartMode by remember { mutableStateOf(TrendMode.BAR) }
    var subjectStatsLevel by remember { mutableStateOf(SubjectStatsLevel.SUBCATEGORY) }
    val aiAnalyses by repo.aiAnalyses.collectAsStateWithLifecycle()
    var latestReport by remember { mutableStateOf(aiAnalyses.firstOrNull()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Single source of truth statistics
    val weeklySummary by statsRepo.getWeeklyStudySummaryFlow().collectAsStateWithLifecycle(
        initialValue = statsRepo.getWeeklyStudySummary()
    )
    val subjectTimeRange = when (selectedTimeTab) {
        0 -> StudyTimeRange.WEEK
        1 -> StudyTimeRange.MONTH
        else -> StudyTimeRange.ALL
    }
    val subjectDistributionFlow = remember(subjectTimeRange, subjectStatsLevel) {
        statsRepo.getSubjectDistributionFlow(subjectTimeRange, subjectStatsLevel)
    }
    val subjectDistribution by subjectDistributionFlow.collectAsStateWithLifecycle(
        initialValue = statsRepo.getSubjectDistribution(subjectTimeRange, subjectStatsLevel)
    )

    // Bottom sheet state for clicked chart bar
    var selectedDayForSheet by remember { mutableStateOf<DayBarData?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

        // Title
        Text(
            text = "学习统计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = YanjiTextPrimary
        )
        Text(
            text = "量化备考轨迹 · 多维图表与阶段AI诊断",
            style = MaterialTheme.typography.bodyMedium,
            color = YanjiTextSecondary
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))

        // Time Range Filter
        PrimaryTabRow(
            selectedTabIndex = selectedTimeTab,
            containerColor = YanjiSurfaceSoft,
            contentColor = YanjiPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            indicator = {}
        ) {
            listOf("本周", "本月", "全部累计").forEachIndexed { index, title ->
                val isSelected = selectedTimeTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTimeTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) YanjiPrimary else YanjiTextSecondary
                        )
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) YanjiSurface else Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 15.1: Emphasize Primary Metric (e.g. 本周学习 48h 32m)
        val periodDurationSecs = when (selectedTimeTab) {
            0 -> weeklySummary.totalDurationSeconds
            1 -> repo.getStudyDurationForPeriod(30)
            else -> repo.getTotalStudyDurationSeconds()
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = when (selectedTimeTab) { 0 -> "本周学习时长"; 1 -> "本月学习时长"; else -> "累计总学时" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = YanjiTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = DurationFormatter.formatHoursMinutes(periodDurationSecs),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "有效学习 ${weeklySummary.activeDays} 天",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = YanjiSuccess,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 15.2: Supporting Metrics (Row of 3 lightweight metric cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LightweightMetricCard(
                title = "日均专注",
                value = DurationFormatter.formatHoursMinutes(weeklySummary.dailyAverageSeconds),
                modifier = Modifier.weight(1f)
            )

            LightweightMetricCard(
                title = "连续有效",
                value = "${weeklySummary.streakDays} 天",
                modifier = Modifier.weight(1f)
            )

            LightweightMetricCard(
                title = "模拟考试",
                value = "${weeklySummary.examCount} 次",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToExamHistory() }
            )
        }

        // 15.3: Longest Single Session (Clickable to FocusSessionDetail)
        weeklySummary.longestSession?.let { longest ->
            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToFocusDetail(longest.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本周单次最长专注",
                            style = MaterialTheme.typography.labelMedium,
                            color = YanjiTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = longest.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DurationFormatter.formatHoursMinutes(longest.durationSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = YanjiPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = YanjiTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Section 16: Interactive 7-Day Bar Chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTimeTab == 0) "近 7 天学习时长趋势" else "每日学时分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )

                    // 图表视图切换：柱状 / 折线
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrendMode.entries.forEach { mode ->
                            TrendModeChip(
                                label = mode.label,
                                selected = trendChartMode == mode,
                                onClick = { trendChartMode = mode }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

                // Interactive Bar Chart
                val maxBarDuration = maxOf(36000L, weeklySummary.days.maxOfOrNull { it.durationSeconds } ?: 36000L)

                if (trendChartMode == TrendMode.BAR) {
                    // Interactive Bar Chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklySummary.days.forEach { day ->
                            val ratio = (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0.06f, 1f)
                            val isToday = day.isToday

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedDayForSheet = day }
                                    .padding(horizontal = 4.dp)
                            ) {
                                // Bar chart area: takes all vertical space above the label
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(ratio)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                            .background(if (isToday) YanjiPrimary else YanjiPrimary.copy(alpha = 0.40f))
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = day.dayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) YanjiPrimary else YanjiTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    // 折线视图：平滑曲线 + 圆点，标签行内嵌于同一 140dp 区域，
                    // 与柱状视图总高一致，切换时卡片尺寸不变化
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                            val n = weeklySummary.days.size
                            if (n > 0) {
                                val topPad = 16.dp.toPx()
                                val bottomPad = 30.dp.toPx() // 8dp 间隙 + 内嵌日期标签行
                                val plotH = size.height - topPad - bottomPad

                                val pts = weeklySummary.days.mapIndexed { index, day ->
                                    val ratio = (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0f, 1f)
                                    Offset(
                                        x = (index + 0.5f) * size.width / n,
                                        y = topPad + plotH * (1f - ratio)
                                    )
                                }

                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        val prev = pts[i - 1]
                                        val cur = pts[i]
                                        val midX = (prev.x + cur.x) / 2f
                                        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = YanjiPrimary,
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                pts.forEachIndexed { index, pt ->
                                    val isToday = weeklySummary.days[index].isToday
                                    if (isToday) {
                                        drawCircle(color = YanjiPrimarySoft, radius = 9.dp.toPx(), center = pt)
                                    }
                                    drawCircle(color = YanjiPrimary, radius = 4.5f.dp.toPx(), center = pt)
                                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                                }
                            }
                        }

                        // Date labels pinned to bottom (same 140dp footprint as bars)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weeklySummary.days.forEach { day ->
                                Text(
                                    text = day.dayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (day.isToday) YanjiPrimary else YanjiTextSecondary,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Transparent clickable columns aligned with the line points
                        Row(modifier = Modifier.fillMaxSize()) {
                            weeklySummary.days.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedDayForSheet = day }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Section 17: Subject Breakdown Cards (Dynamic & Clickable)
        val subjectDist = subjectDistribution.associate { it.subjectName to it.durationSeconds }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "科目时长与分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectStatsLevel.entries.forEach { level ->
                            TrendModeChip(
                                label = level.title,
                                selected = subjectStatsLevel == level,
                                onClick = { subjectStatsLevel = level }
                            )
                        }
                    }
                }
                Text(
                    text = if (subjectStatsLevel == SubjectStatsLevel.SUBCATEGORY) {
                        "按具体学科统计 · 点击查看明细"
                    } else {
                        "已汇总子类与大类直接记录 · 点击查看明细"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiTextTertiary
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                // 学科构成环形图
                SubjectDonutChart(
                    subjectDist = subjectDist,
                    totalLabel = subjectTimeRange.title
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                subjectDistribution.forEachIndexed { index, sub ->
                    val color = remember(sub.subjectColor) {
                        try {
                            Color(android.graphics.Color.parseColor(sub.subjectColor))
                        } catch (_: Exception) {
                            subjectChartColor(sub.subjectName)
                        }
                    }
                    val subSecs = sub.durationSeconds
                    val totalSecs = maxOf(1L, subjectDist.values.sum())
                    val percent = (subSecs.toFloat() / totalSecs).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigateToSubjectDetail(sub.subjectId) }
                            .padding(vertical = 4.dp)
                    ) {
                        SubjectProgressBar(
                            name = sub.subjectName,
                            time = DurationFormatter.formatHoursMinutes(subSecs),
                            percent = percent,
                            color = color
                        )
                    }

                    if (index < subjectDistribution.size - 1) {
                        Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // AI Diagnosis Trigger & Report Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JuanjuanAvatar(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "卷卷 · AI 阶段学情诊断",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary
                            )
                            Text(
                                text = "基于真实学习与日记数据深度分析",
                                style = MaterialTheme.typography.labelMedium,
                                color = YanjiTextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isAnalyzing = true
                                try {
                                    latestReport = repo.generateAiAnalysis(7)
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = YanjiOnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("分析中...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text("生成诊断", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (latestReport != null) {
                    Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                    val report = latestReport!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceSoft)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "学情概览 (${report.periodStart} ~ ${report.periodEnd})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = YanjiTextPrimary,
                                lineHeight = 20.sp
                            )

                            if (report.strengths.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))
                                Text(
                                    text = "优势亮点",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YanjiSuccess
                                )
                                report.strengths.forEach { s ->
                                    Text(
                                        text = "• $s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = YanjiTextPrimary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            if (report.weaknesses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "薄弱卡点",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YanjiWarning
                                )
                                report.weaknesses.forEach { w ->
                                    Text(
                                        text = "• $w",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = YanjiTextPrimary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            if (report.suggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "未来三天建议",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YanjiPrimary
                                )
                                report.suggestions.forEach { sg ->
                                    Text(
                                        text = "• $sg",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = YanjiTextPrimary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unified Bottom Inset Padding
        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    // ModalBottomSheet for clicked 7-day bar
    if (selectedDayForSheet != null) {
        val clickedDay = selectedDayForSheet!!
        ModalBottomSheet(
            onDismissRequest = { selectedDayForSheet = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = YanjiSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DurationFormatter.formatDateWithWeekday(clickedDay.date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                    Text(
                        text = DurationFormatter.formatHoursMinutes(clickedDay.durationSeconds),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "当日科目投入：",
                    fontSize = 13.sp,
                    color = YanjiTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (clickedDay.subjectDistribution.isEmpty()) {
                    Text(
                        text = "当天暂无分科记录",
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                } else {
                    clickedDay.subjectDistribution.forEach { (subName, secs) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = subName, fontSize = 13.sp, color = YanjiTextPrimary)
                            Text(
                                text = DurationFormatter.formatHoursMinutes(secs),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val d = clickedDay.date
                        selectedDayForSheet = null
                        onNavigateToDailyDetail(d)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("查看当天记录 →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LightweightMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 12.sp, color = YanjiTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )
        }
    }
}

@Composable
fun SubjectProgressBar(
    name: String,
    time: String,
    percent: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = YanjiTextPrimary)
            Text(text = "$time (${(percent * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = color,
            trackColor = YanjiSurfaceSoft
        )
    }
}

enum class TrendMode(val label: String) {
    BAR("柱状"),
    LINE("折线")
}

@Composable
private fun TrendModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) YanjiPrimarySoft else YanjiSurfaceSoft)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) YanjiPrimaryStrong else YanjiTextSecondary
        )
    }
}

private fun subjectChartColor(name: String): Color = when {
    name.contains("数学") || name.contains("线性代数") || name.contains("概率论") -> SubjectMath
    name.contains("408") || name.contains("专业课") || name.contains("数据结构") ||
        name.contains("计算机组成") || name.contains("计算机网络") || name.contains("操作系统") -> SubjectMajor
    name.contains("英语") -> SubjectEnglish
    name.contains("政治") -> SubjectPolitics
    else -> SubjectOther
}

@Composable
private fun SubjectDonutChart(
    subjectDist: Map<String, Long>,
    totalLabel: String = "今日",
    modifier: Modifier = Modifier
) {
    val total = subjectDist.values.sum()
    val reveal by animateFloatAsState(
        targetValue = if (total > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "subjectDonutReveal"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            if (total > 0) {
                val strokeW = 24.dp.toPx()
                val diameter = min(size.width, size.height) - strokeW
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                var startAngle = -90f

                subjectDist.forEach { (name, secs) ->
                    val sweep = secs.toFloat() / total * 360f * reveal
                    if (sweep > 0.5f) {
                        drawArc(
                            color = subjectChartColor(name),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweep
                }
            } else {
                // 空状态：安静的灰环
                drawCircle(
                    color = YanjiSurfaceSoft,
                    radius = (min(size.width, size.height) - 24.dp.toPx()) / 2f,
                    style = Stroke(width = 24.dp.toPx())
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (total > 0) DurationFormatter.formatHoursMinutes(total) else "暂无数据",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (total > 0) YanjiTextPrimary else YanjiTextTertiary
            )
            if (total > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "${totalLabel}合计", fontSize = 12.sp, color = YanjiTextTertiary)
            }
        }
    }
}
