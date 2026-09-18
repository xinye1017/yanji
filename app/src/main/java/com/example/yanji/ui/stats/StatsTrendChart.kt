package com.example.yanji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.yanji.data.DayBarData
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.*
import com.example.yanji.ui.components.GlassSegmentedControl
import com.example.yanji.ui.components.YanjiCard as Card

enum class TrendMode(val label: String) {
    BAR("柱状"),
    LINE("折线"),
    HEATMAP("热力图")
}

fun resolveAvailableTrendModes(selectedTimeTab: Int): List<TrendMode> =
    if (selectedTimeTab == 1) {
        listOf(TrendMode.HEATMAP, TrendMode.LINE)
    } else {
        listOf(TrendMode.BAR, TrendMode.LINE)
    }

fun resolveEffectiveTrendMode(selectedTimeTab: Int, trendChartMode: TrendMode): TrendMode {
    val available = resolveAvailableTrendModes(selectedTimeTab)
    return if (trendChartMode in available) trendChartMode else available.first()
}

@Composable
fun StatsTrendChart(
    selectedTimeTab: Int,
    days: List<DayBarData>,
    trendChartMode: TrendMode,
    onSelectTrendMode: (TrendMode) -> Unit,
    onSelectDay: (DayBarData) -> Unit
) {
    val availableModes = resolveAvailableTrendModes(selectedTimeTab)
    val effectiveMode = resolveEffectiveTrendMode(selectedTimeTab, trendChartMode)
    val currentModeIndex = availableModes.indexOf(effectiveMode).coerceAtLeast(0)

    val titleText = when {
        selectedTimeTab == 0 -> "本周学习时长趋势"
        selectedTimeTab == 1 && effectiveMode == TrendMode.HEATMAP -> "本月专注热力图"
        selectedTimeTab == 1 -> "本月学习趋势"
        else -> "每日学时分布"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        colors = CardDefaults.cardColors(containerColor = YanjiColors.elevatedSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Title + Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                GlassSegmentedControl(
                    items = availableModes,
                    selectedIndex = currentModeIndex,
                    onItemSelected = { onSelectTrendMode(availableModes[it]) },
                    itemLabel = { it.label },
                    height = 32.dp,
                    modifier = Modifier.width(136.dp)
                )
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

            when {
                // Monthly Heatmap View (Month tab defaults to heatmap unless LINE is selected)
                selectedTimeTab == 1 && effectiveMode != TrendMode.LINE -> {
                    MonthlyHeatmapView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }

                // Line Chart (Week, Month or All) - No hollow circular dots!
                effectiveMode == TrendMode.LINE -> {
                    LineChartView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }

                // Bar Chart View (Week or All)
                else -> {
                    BarChartView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }
            }
        }
    }
}

/**
 * 蓝色系热力图：根据不同专注时长区分颜色深浅。
 */
@Composable
private fun MonthlyHeatmapView(
    days: List<DayBarData>,
    onSelectDay: (DayBarData) -> Unit
) {
    // Keep the month view as a compact horizontal calendar: seven day slots per row,
    // flowing left-to-right. The squares carry the visual rhythm; the selected day sheet
    // and accessibility label provide exact date details.

    // Determine leading empty slots based on the first day's day of week.
    val firstDate = days.firstOrNull()?.let { YanjiTime.parseIsoDate(it.date) }
    val leadingOffset = if (firstDate != null) {
        firstDate.dayOfWeek.value - 1
    } else 0

    val totalSlots = leadingOffset + days.size
    val rowCount = (totalSlots + 6) / 7
    val monthLabel = firstDate?.let { "${it.year}年${it.monthValue}月" } ?: "本月"
    val activeCount = days.count { it.durationSeconds >= 1800L }
    val cellTouchSize = 24.dp
    val cellVisualSize = 18.dp
    val cellGap = 3.dp
    val cellShape = RoundedCornerShape(YanjiRadius.ItemRadius / 2)

    Column(modifier = Modifier.fillMaxWidth()) {
        // The month label stays above the horizontal date grid.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Seven compact day slots run horizontally in every calendar row.
        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            for (rowIndex in 0 until rowCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellTouchSize),
                    horizontalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    for (columnIndex in 0..6) {
                        val slotIndex = rowIndex * 7 + columnIndex
                        val dayIndex = slotIndex - leadingOffset

                        if (dayIndex in days.indices) {
                            val day = days[dayIndex]
                            val dayDurationText = DurationFormatter.formatHoursMinutes(day.durationSeconds)
                            val a11yText = "${day.date}，专注时长 $dayDurationText" +
                                if (day.isToday) "，今日" else ""

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = a11yText
                                    }
                                    .clickable { onSelectDay(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(cellVisualSize)
                                        .clip(cellShape)
                                        .background(heatmapCellColor(day.durationSeconds))
                                        .then(
                                            if (day.isToday) {
                                                Modifier.border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = cellShape
                                                )
                                            } else Modifier
                                        )
                                )
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

        // The summary stays textual; the color scale remains a compact GitHub-style legend.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本月有效学习 ${activeCount} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Five contribution levels: empty, light, steady, focused, intensive.
                listOf(0L, 900L, 1800L, 5400L, 10800L).forEach { duration ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(cellShape)
                            .background(heatmapCellColor(duration))
                    )
                }

                Text(
                    text = "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * GitHub-style five-level blue scale. The base and alpha are theme-aware, so the
 * same quiet progression remains legible in both light and dark mode.
 */
@Composable
private fun heatmapCellColor(durationSeconds: Long): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when {
        durationSeconds <= 0L -> YanjiColors.fill
        durationSeconds < 1800L -> primary.copy(alpha = 0.22f)
        durationSeconds < 5400L -> primary.copy(alpha = 0.42f)
        durationSeconds < 10800L -> primary.copy(alpha = 0.68f)
        else -> primary
    }
}

/**
 * 优化后的折线图：
 * - 消除空心圆点，采用实心高质感圆点（solid circular dots）；
 * - 今日焦点采用柔光外晕 + 实心主点标识；
 * - 曲线下方支持柔和渐变填充；
 * - 针对月度多天场景自适应 X 轴刻度展示。
 */
@Composable
private fun LineChartView(
    days: List<DayBarData>,
    onSelectDay: (DayBarData) -> Unit
) {
    val actualMax = days.maxOfOrNull { it.durationSeconds } ?: 0L
    val maxBarDuration = if (actualMax > 0L) actualMax else 3600L
    val n = days.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val chartLineColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            if (n > 0) {
                val topPad = 16.dp.toPx()
                val bottomPad = 28.dp.toPx()
                val plotH = size.height - topPad - bottomPad

                val pts = days.mapIndexed { index, day ->
                    val ratio = (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0f, 1f)
                    Offset(
                        x = (index + 0.5f) * (size.width / n),
                        y = topPad + plotH * (1f - ratio)
                    )
                }

                if (pts.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            val prev = pts[i - 1]
                            val cur = pts[i]
                            val midX = (prev.x + cur.x) / 2f
                            cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                        }
                    }

                    // 曲线下方柔和区域渐变
                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(pts.last().x, topPad + plotH)
                        lineTo(pts.first().x, topPad + plotH)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                chartLineColor.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            startY = topPad,
                            endY = topPad + plotH
                        )
                    )

                    // 绘制平滑主折线
                    drawPath(
                        path = linePath,
                        color = chartLineColor,
                        style = Stroke(
                            width = 2.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 绘制实心点（杜绝空心圆点）：常规实心圆点，今日加柔和光晕
                val normalDotRadius = if (n > 15) 2.5f.dp.toPx() else 3.8f.dp.toPx()
                pts.forEachIndexed { index, pt ->
                    val isToday = days[index].isToday
                    val hasData = days[index].durationSeconds > 0L

                    if (isToday) {
                        // 今日光晕外环
                        drawCircle(
                            color = chartLineColor.copy(alpha = 0.22f),
                            radius = 8.dp.toPx(),
                            center = pt
                        )
                        // 今日实心主点
                        drawCircle(
                            color = chartLineColor,
                            radius = 4.5f.dp.toPx(),
                            center = pt
                        )
                    } else if (n <= 15 || hasData) {
                        // 实心圆点（无任何内掏空心）
                        drawCircle(
                            color = chartLineColor,
                            radius = normalDotRadius,
                            center = pt
                        )
                    }
                }
            }
        }

        // X-Axis Labels
        if (days.size <= 7) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Month line chart: milestone day labels precisely positioned under data points without truncation
            val rawMilestones = listOf(0, 4, 9, 14, 19, 24, days.lastIndex)
                .filter { it in days.indices }
                .distinct()
            val milestoneIndices = if (rawMilestones.size >= 2 && rawMilestones.last() - rawMilestones[rawMilestones.size - 2] < 3) {
                rawMilestones.filterIndexed { index, _ -> index != rawMilestones.size - 2 }
            } else {
                rawMilestones
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                milestoneIndices.forEach { idx ->
                    val day = days[idx]
                    Text(
                        text = "${idx + 1}日",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val targetCenterX = constraints.maxWidth * ((idx + 0.5f) / n)
                            val x = (targetCenterX - placeable.width / 2f)
                                .roundToInt()
                                .coerceIn(0, constraints.maxWidth - placeable.width)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(x, 0)
                            }
                        }
                    )
                }
            }
        }

        // Transparent clickable touch area for each day
        Row(modifier = Modifier.fillMaxSize()) {
            days.forEach { day ->
                val dayDurationText = DurationFormatter.formatHoursMinutes(day.durationSeconds)
                val a11yText = "${day.dayLabel}，学习时长 $dayDurationText" + if (day.isToday) "，今日" else ""
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectDay(day) }
                        .semantics { contentDescription = a11yText }
                )
            }
        }
    }
}

/**
 * 柱状图：用于周或紧凑视角。
 */
@Composable
private fun BarChartView(
    days: List<DayBarData>,
    onSelectDay: (DayBarData) -> Unit
) {
    val actualMax = days.maxOfOrNull { it.durationSeconds } ?: 0L
    val maxBarDuration = if (actualMax > 0L) actualMax else 3600L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val ratio = if (day.durationSeconds > 0L) {
                (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0.02f, 1f)
            } else {
                0f
            }
            val isToday = day.isToday
            val dayDurationText = DurationFormatter.formatHoursMinutes(day.durationSeconds)
            val a11yText = "${day.dayLabel}，学习时长 $dayDurationText" + if (isToday) "，今日" else ""

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelectDay(day) }
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = a11yText }
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 底轨槽位（全高弱背景槽）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    )

                    // 真实时长柱（0时长严格为0，不突起）
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(ratio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = day.dayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
