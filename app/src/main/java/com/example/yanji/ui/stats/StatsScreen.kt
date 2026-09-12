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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.*
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.*
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.JuanjuanAvatar
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel {
        StatsViewModel(YanjiRepository.getInstance(), StudyStatisticsRepository.getInstance())
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 用户设置：周/月目标进度 = 每日目标 × 天数（与设置页同源）
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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

        // Time Range Filter（设计稿式：浅灰胶囊容器 + 白色选中胶囊，整体全圆角）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(YanjiSurfaceSoft)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("本周", "本月", "全部累计").forEachIndexed { index, title ->
                val isSelected = state.selectedTimeTab == index
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) YanjiSurface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    onClick = { viewModel.selectTimeTab(index) }
                ) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) YanjiPrimary else YanjiTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 15.1: Hero Period Card（设计稿式：放大主数字 + 较上周对比 + 日均投入 + 目标进度）
        val periodDurationSecs = state.periodDurationSeconds

        // 目标进度：本周 = 日目标 × 7；本月 = 日目标 × 30；全部累计无目标语义
        val goalSeconds = when (state.selectedTimeTab) {
            0 -> (settings.dailyGoalHours * 7 * 3600f).toLong()
            1 -> (settings.dailyGoalHours * 30 * 3600f).toLong()
            else -> 0L
        }
        val goalLabel = if (state.selectedTimeTab == 1) "月目标进度" else "周目标进度"

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 行 1：标签 + 有效天数 + 较上周对比
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(YanjiPrimary, CircleShape)
                        )
                        Text(
                            text = when (state.selectedTimeTab) { 0 -> "本周学习时长"; 1 -> "本月学习时长"; else -> "累计总学时" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = YanjiTextSecondary
                        )
                        Text(
                            text = "有效学习 ${state.weeklySummary.activeDays} 天",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = YanjiSuccess
                        )
                    }

                    // 「较上周」仅在周视角显示（与近 7 天口径对齐）
                    if (state.selectedTimeTab == 0) {
                        val prev = state.previousWeekSeconds
                        val cur = state.weeklySummary.totalDurationSeconds
                        if (prev > 0L || cur > 0L) {
                            val delta = cur - prev
                            val isUp = delta > 0
                            val isFlat = delta == 0L
                            val pillBg = when {
                                isFlat -> YanjiSurfaceSoft
                                isUp -> YanjiSuccessSoft
                                else -> YanjiWarningSoft
                            }
                            val pillFg = when {
                                isFlat -> YanjiTextSecondary
                                isUp -> YanjiSuccess
                                else -> YanjiWarning
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = pillFg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isFlat) "较上周 持平" else "较上周 ${if (isUp) "+" else "-"}${formatDeltaCompact(abs(delta))}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillFg
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 行 2：放大主数字（h / m 单位分离）+ 右侧日均投入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val hoursValue = periodDurationSecs / 3600
                    val minutesValue = (periodDurationSecs % 3600) / 60
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$hoursValue",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "h",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextSecondary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                        )
                        if (minutesValue > 0) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$minutesValue",
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "m",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiTextSecondary,
                                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "日均投入",
                            fontSize = 11.sp,
                            color = YanjiTextTertiary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = DurationFormatter.formatHoursMinutes(state.weeklySummary.dailyAverageSeconds),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                    }
                }

                // 行 3：目标进度（设计稿：进度条 + 百分比）
                if (goalSeconds > 0L) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val goalProgress = (periodDurationSecs.toFloat() / goalSeconds).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$goalLabel (${formatGoalHours(periodDurationSecs)} / ${formatGoalHours(goalSeconds)})",
                            fontSize = 12.sp,
                            color = YanjiTextSecondary
                        )
                        Text(
                            text = "${(goalProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiPrimaryStrong
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(YanjiSurfaceSoft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(goalProgress.coerceAtLeast(0.002f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(YanjiPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Section 16: Interactive 7-Day Bar Chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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
                        text = if (state.selectedTimeTab == 0) "近 7 天学习时长趋势" else "每日学时分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )

                    // 图表视图切换：柱状 / 折线
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrendMode.entries.forEach { mode ->
                            TrendModeChip(
                                label = mode.label,
                                selected = state.trendChartMode == mode,
                                onClick = { viewModel.selectTrendMode(mode) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

                // Interactive Bar Chart
                val maxBarDuration = maxOf(36000L, state.weeklySummary.days.maxOfOrNull { it.durationSeconds } ?: 36000L)

                if (state.trendChartMode == TrendMode.BAR) {
                    // Interactive Bar Chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.weeklySummary.days.forEach { day ->
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
                            val n = state.weeklySummary.days.size
                            if (n > 0) {
                                val topPad = 16.dp.toPx()
                                val bottomPad = 30.dp.toPx() // 8dp 间隙 + 内嵌日期标签行
                                val plotH = size.height - topPad - bottomPad

                                val pts = state.weeklySummary.days.mapIndexed { index, day ->
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
                                    val isToday = state.weeklySummary.days[index].isToday
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
                            state.weeklySummary.days.forEach { day ->
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
                            state.weeklySummary.days.forEach { day ->
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
        // subjectDistribution 已在 ViewModel 过滤为「仅含真实计时科目」；
        // 这里只渲染有效科目，0 时长的空科目不会出现在比例条与列表里。
        val subjectDist = state.subjectDistribution.associate { it.subjectName to it.durationSeconds }

        // 科目展示色：优先使用科目自带色值，非法/缺失时按名称推断
        fun displayColor(sub: SubjectDistributionItem): Color = try {
            Color(android.graphics.Color.parseColor(sub.subjectColor))
        } catch (_: Exception) {
            subjectChartColor(sub.subjectName)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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
                    SubjectLevelSegmented(
                        selected = state.subjectStatsLevel,
                        onSelect = { viewModel.selectSubjectLevel(it) }
                    )
                }
                Text(
                    text = if (state.subjectStatsLevel == SubjectStatsLevel.SUBCATEGORY) {
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
                    totalLabel = state.timeRange.title
                )

                if (state.subjectDistribution.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                    // 分段比例条：一眼看清各科占比结构（参考设计稿的 Segmented Proportional Bar）
                    SubjectDistributionBar(
                        segments = state.subjectDistribution.map { sub ->
                            displayColor(sub) to sub.durationSeconds.toFloat()
                        }
                    )

                    Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                    state.subjectDistribution.forEachIndexed { index, sub ->
                        val color = displayColor(sub)
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

                        if (index < state.subjectDistribution.size - 1) {
                            Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                    Text(
                        text = "本周期暂无科目学时记录，完成一次专注后自动生成",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 关键指标（设计稿三小卡：连续研读 / 单次最长 / 全真模拟）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricMiniCard(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = YanjiWarning,
                iconBg = YanjiWarningSoft,
                title = "连续研读",
                value = "${state.weeklySummary.streakDays}",
                unit = "天",
                modifier = Modifier.weight(1f)
            )

            val longest = state.weeklySummary.longestSession
            MetricMiniCard(
                icon = Icons.Default.Timelapse,
                iconTint = YanjiPrimary,
                iconBg = YanjiPrimarySoft,
                title = "单次最长",
                value = DurationFormatter.formatHoursMinutes(longest?.durationSeconds ?: 0L),
                modifier = Modifier.weight(1f),
                onClick = longest?.let { l -> { onNavigateToFocusDetail(l.id) } }
            )

            MetricMiniCard(
                icon = Icons.Default.Quiz,
                iconTint = YanjiLavender,
                iconBg = YanjiLavenderSoft,
                title = "全真模拟",
                value = "${state.weeklySummary.examCount}",
                unit = "场",
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToExamHistory() }
            )
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // AI Diagnosis Trigger & Report Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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
                        onClick = { viewModel.generateAnalysis() },
                        enabled = !state.isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (state.isAnalyzing) {
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

                if (state.latestReport != null) {
                    Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                    val report = state.latestReport!!

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

/**
 * 关键指标小卡（设计稿三卡样式）：圆形上色图标 + 标签 + 加粗数值（含单位小字）。
 * 可点击时用于跳转对应明细页。
 */
@Composable
private fun MetricMiniCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text = title, fontSize = 12.sp, color = YanjiTextTertiary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary,
                    maxLines = 1
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        color = YanjiTextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
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

/**
 * 科目维度切换（大类 / 子类）· 对应设计稿的分段控件：
 * 浅灰底容器 + 白色选中胶囊。
 */
@Composable
private fun SubjectLevelSegmented(
    selected: SubjectStatsLevel,
    onSelect: (SubjectStatsLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(YanjiSurfaceSoft)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展示顺序与设计稿一致：大类在前、子类在后（默认选中态不受影响）
        listOf(SubjectStatsLevel.CATEGORY, SubjectStatsLevel.SUBCATEGORY).forEach { level ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) YanjiSurface else Color.Transparent)
                    .clickable { onSelect(level) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = level.title,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) YanjiPrimaryStrong else YanjiTextSecondary
                )
            }
        }
    }
}

/**
 * 分段比例条（对应设计稿的 Segmented Proportional Bar）：
 * 按各科目时长占比横向排布，段间留细缝，整条两端为胶囊圆角。
 * 仅在存在有效科目时渲染；单段时整条同色。
 */
@Composable
private fun SubjectDistributionBar(segments: List<Pair<Color, Float>>) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat()
    if (segments.isEmpty() || total <= 0f) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEach { (color, value) ->
            val fraction = (value / total).coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/** 「较上周」对比的紧凑时长："4.5h" / "45m"。 */
private fun formatDeltaCompact(seconds: Long): String {
    if (seconds < 3600L) return "${seconds / 60}m"
    return String.format(Locale.US, "%.1fh", seconds / 3600f)
}

/** 目标进度里的时长："48.5h"。 */
private fun formatGoalHours(seconds: Long): String =
    String.format(Locale.US, "%.1fh", seconds / 3600f)

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
