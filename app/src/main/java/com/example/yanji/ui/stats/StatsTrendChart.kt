package com.example.yanji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

@Composable
fun StatsTrendChart(
    selectedTimeTab: Int,
    days: List<DayBarData>,
    trendChartMode: TrendMode,
    onSelectTrendMode: (TrendMode) -> Unit,
    onSelectDay: (DayBarData) -> Unit
) {
    val isDark = yanjiIsDarkTheme()

    val availableModes = if (selectedTimeTab == 1) {
        listOf(TrendMode.HEATMAP, TrendMode.LINE)
    } else {
        listOf(TrendMode.BAR, TrendMode.LINE)
    }
    val currentModeIndex = availableModes.indexOf(trendChartMode).coerceAtLeast(0)

    val titleText = when {
        selectedTimeTab == 0 -> "本周学习时长趋势"
        selectedTimeTab == 1 && trendChartMode == TrendMode.HEATMAP -> "本月专注热力图"
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
                // Monthly Heatmap View
                selectedTimeTab == 1 && trendChartMode == TrendMode.HEATMAP -> {
                    MonthlyHeatmapView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }

                // Line Chart (Week, Month or All) - No hollow circular dots!
                trendChartMode == TrendMode.LINE -> {
                    LineChartView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }

                // Bar Chart View
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
    val isDark = yanjiIsDarkTheme()
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    // Determine leading empty slots based on the first day's day of week
    val firstDate = days.firstOrNull()?.let { YanjiTime.parseIsoDate(it.date) }
    val leadingOffset = if (firstDate != null) {
        firstDate.dayOfWeek.value - 1
    } else 0

    val totalSlots = leadingOffset + days.size
    val rowCount = (totalSlots + 6) / 7

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Weekday Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Calendar Grid Rows
        for (rowIndex in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (colIndex in 0 until 7) {
                    val slotIndex = rowIndex * 7 + colIndex
                    val dayIndex = slotIndex - leadingOffset

                    if (dayIndex in days.indices) {
                        val day = days[dayIndex]
                        val duration = day.durationSeconds
                        val isToday = day.isToday
                        val dayNumber = dayIndex + 1

                        val cellBgColor = heatmapCellColor(duration, isDark)
                        val cellTextColor = heatmapTextColor(duration, isDark)

                        val dayDurationText = DurationFormatter.formatHoursMinutes(duration)
                        val a11yText = "${day.date}，专注时长 $dayDurationText" + if (isToday) "，今日" else ""

                        val cellShape = RoundedCornerShape(8.dp)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .background(cellBgColor)
                                .then(
                                    if (isToday) {
                                        Modifier.border(
                                            width = 1.8.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = cellShape
                                        )
                                    } else Modifier
                                )
                                .clickable { onSelectDay(day) }
                                .semantics { contentDescription = a11yText },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday || duration >= 9000L) FontWeight.Bold else FontWeight.Medium,
                                    color = cellTextColor
                                )
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(cellTextColor)
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty slot
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Legend (图例)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeCount = days.count { it.durationSeconds >= 1800L }
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

                // 5 swatches for shades of blue: 0h, <1h, 1~2.5h, 2.5~4h, 4h+
                listOf(0L, 1800L, 5400L, 10800L, 18000L).forEach { duration ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatmapCellColor(duration, isDark))
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
 * 蓝色系热力图分级颜色：
 * - 0 秒：轻柔表面底色
 * - < 1 小时 (1 ~ 3599s)：清爽淡蓝 (0xFFD6E4FF / 0xFF1A365D)
 * - 1 ~ 2.5 小时 (3600 ~ 8999s)：明朗天蓝 (0xFF91B4F8 / 0xFF2B4C7E)
 * - 2.5 ~ 4 小时 (9000 ~ 14399s)：鲜明主蓝 (0xFF3B82F6 / 0xFF3B82F6)
 * - >= 4 小时 (>= 14400s)：深邃浓蓝 (0xFF1D4ED8 / 0xFF60A5FA)
 */
private fun heatmapCellColor(durationSeconds: Long, isDark: Boolean): Color {
    if (durationSeconds <= 0L) {
        return if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5FB)
    }
    return when {
        durationSeconds < 3600L -> if (isDark) Color(0xFF1A365D) else Color(0xFFD6E4FF)
        durationSeconds < 9000L -> if (isDark) Color(0xFF2B4C7E) else Color(0xFF91B4F8)
        durationSeconds < 14400L -> if (isDark) Color(0xFF3B82F6) else Color(0xFF3B82F6)
        else -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    }
}

private fun heatmapTextColor(durationSeconds: Long, isDark: Boolean): Color {
    if (durationSeconds <= 0L) {
        return if (isDark) Color(0xFF94A3B8) else Color(0xFF667085)
    }
    return when {
        durationSeconds < 3600L -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
        durationSeconds < 9000L -> if (isDark) Color.White else Color(0xFF0F2E7A)
        else -> Color.White
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
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .clickable { onSelectDay(day) }
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
                (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0.04f, 1f)
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
                    .clip(RoundedCornerShape(YanjiRadius.Small))
                    .clickable { onSelectDay(day) }
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
                                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
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
