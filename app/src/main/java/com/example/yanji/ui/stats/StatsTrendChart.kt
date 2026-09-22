package com.example.yanji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
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

                // Month Tick Trend View (Bencho ProgressTicks design for dense monthly data)
                selectedTimeTab == 1 && effectiveMode == TrendMode.LINE -> {
                    MonthTickTrendView(
                        days = days,
                        onSelectDay = onSelectDay
                    )
                }

                // Line Chart (Week or All) - No hollow circular dots!
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
 * 月度高密度刻度波（基于 Bencho ProgressTicks 灵动设计）：
 * - 28~31 根圆角微胶囊刻度条，高度精准映射每日学习时长；
 * - 空白未研读天采用极简低对比度基线轨道胶囊；
 * - 触控横向滑动探针（Scrubbing）：手指滑过时动态放大刻度并带有灵动水滴波纹（Wave Decay）；
 * - 顶部伴随高质感悬浮定位指示球（Drag Ball Cursor）与柔光光晕；
 * - 触感微反馈：每次跨越刻度触发 LocalHapticFeedback TextHandleMove；
 * - 灵动统计头：静止状态显示月度总学时与日均，滑动探查时瞬时呈现当天具体学时、日期及与月均的差值对比（+X.Xh / -X.Xh）；
 * - 点击刻度直接调出日详情抽屉；
 * - 完美适配深色与浅色模式。
 */
@Composable
private fun MonthTickTrendView(
    days: List<DayBarData>,
    onSelectDay: (DayBarData) -> Unit
) {
    if (days.isEmpty()) return

    val n = days.size
    val totalSeconds = remember(days) { days.sumOf { it.durationSeconds } }
    val activeDays = remember(days) { days.count { it.durationSeconds > 0L } }
    val dailyAverageSeconds = remember(days, totalSeconds) {
        if (days.isNotEmpty()) totalSeconds / days.size else 0L
    }
    val actualMax = remember(days) { days.maxOfOrNull { it.durationSeconds } ?: 0L }
    val maxBarDuration = if (actualMax > 0L) actualMax else 3600L

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val fillBg = YanjiColors.fill
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryLabelColor = YanjiColors.tertiaryLabel

    Column(modifier = Modifier.fillMaxWidth()) {
        // Dynamic Inspector Header (Bencho tik-fig + tik-delta)
        val activeDay = hoveredIndex?.let { days.getOrNull(it) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (activeDay != null) {
                    // Hovered State: Big figure for day duration
                    val hours = activeDay.durationSeconds / 3600
                    val mins = (activeDay.durationSeconds % 3600) / 60
                    val (figText, unitText) = when {
                        activeDay.durationSeconds == 0L -> "0" to "小时"
                        hours > 0 -> String.format(Locale.US, "%.1f", activeDay.durationSeconds / 3600f) to "小时"
                        else -> "$mins" to "分钟"
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = figText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unitText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subTextColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    val dateParts = activeDay.date.split("-")
                    val m = dateParts.getOrNull(1)?.toIntOrNull()
                    val d = dateParts.getOrNull(2)?.toIntOrNull()
                    val daySubtitle = if (m != null && d != null) {
                        "${m}月${d}日" + if (activeDay.isToday) " · 今日" else ""
                    } else {
                        activeDay.date + if (activeDay.isToday) " · 今日" else ""
                    }

                    Text(
                        text = daySubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor
                    )
                } else {
                    // Idle State: Big figure for monthly total
                    val totalHours = totalSeconds / 3600f
                    val figText = String.format(Locale.US, "%.1f", totalHours)

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = figText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "小时",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subTextColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    Text(
                        text = "本月累计专注 · 研读 ${activeDays} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor
                    )
                }
            }

            // Delta Badge / Average Pill
            if (activeDay != null) {
                val diffSeconds = activeDay.durationSeconds - dailyAverageSeconds
                val (badgeBg, badgeTextColor, badgeLabel) = when {
                    activeDay.durationSeconds == 0L -> {
                        Triple(fillBg, tertiaryLabelColor, "未学习")
                    }
                    diffSeconds >= 0L -> {
                        val diffH = diffSeconds / 3600f
                        val label = if (diffH >= 0.1f) {
                            "+${String.format(Locale.US, "%.1f", diffH)}h"
                        } else {
                            "+${(diffSeconds + 59) / 60}m"
                        }
                        Triple(
                            tertiaryContainer,
                            tertiaryColor,
                            label
                        )
                    }
                    else -> {
                        val diffH = (-diffSeconds) / 3600f
                        val label = if (diffH >= 0.1f) {
                            "-${String.format(Locale.US, "%.1f", diffH)}h"
                        } else {
                            "-${(-diffSeconds + 59) / 60}m"
                        }
                        Triple(
                            errorContainer,
                            errorColor,
                            label
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(YanjiRadius.Pill))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeTextColor
                    )
                }
            } else {
                val avgHours = dailyAverageSeconds / 3600f
                val avgLabel = if (avgHours >= 0.1f) {
                    "日均 ${String.format(Locale.US, "%.1f", avgHours)}h"
                } else {
                    "日均 ${dailyAverageSeconds / 60}m"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(YanjiRadius.Pill))
                        .background(fillBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = avgLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = subTextColor
                    )
                }
            }
        }

        // Ticks Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .pointerInput(days) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val count = days.size
                        if (count == 0) return@awaitEachGesture
                        var currentIdx = (down.position.x / size.width * count).toInt().coerceIn(0, count - 1)
                        hoveredIndex = currentIdx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        var isHorizontalScrub = false
                        var isVerticalScroll = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                if (!isHorizontalScrub && !isVerticalScroll) {
                                    onSelectDay(days[currentIdx])
                                }
                                hoveredIndex = null
                                break
                            }

                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)

                            if (!isHorizontalScrub && !isVerticalScroll) {
                                if (dy > 12.dp.toPx() && dy > dx * 1.5f) {
                                    isVerticalScroll = true
                                    hoveredIndex = null
                                    break
                                } else if (dx > 6.dp.toPx()) {
                                    isHorizontalScrub = true
                                }
                            }

                            if (isHorizontalScrub || (!isVerticalScroll && dx <= 6.dp.toPx())) {
                                val newIdx = (change.position.x / size.width * count).toInt().coerceIn(0, count - 1)
                                if (newIdx != currentIdx) {
                                    isHorizontalScrub = true
                                    currentIdx = newIdx
                                    hoveredIndex = currentIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (isHorizontalScrub) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val topPad = 18.dp.toPx()
                val bottomPad = 22.dp.toPx()
                val plotH = size.height - topPad - bottomPad
                val bottomY = topPad + plotH
                val minPillH = 6.dp.toPx()

                val slotW = size.width / n
                val barW = (slotW * 0.52f).coerceIn(3.dp.toPx(), 7.dp.toPx())

                // Draw each tick bar
                days.forEachIndexed { i, day ->
                    val centerX = (i + 0.5f) * slotW
                    val isHovered = (i == hoveredIndex)
                    val actualBarW = if (isHovered) {
                        (barW * 1.35f).coerceAtMost(slotW - 1.dp.toPx())
                    } else {
                        barW
                    }
                    val cornerRadius = CornerRadius(actualBarW / 2f, actualBarW / 2f)

                    if (day.durationSeconds == 0L) {
                        // Unstudied day: soft baseline capsule track
                        val alpha = if (isHovered) 0.55f else 0.35f
                        drawRoundRect(
                            color = trackColor.copy(alpha = alpha),
                            topLeft = Offset(centerX - actualBarW / 2f, bottomY - minPillH),
                            size = androidx.compose.ui.geometry.Size(actualBarW, minPillH),
                            cornerRadius = cornerRadius
                        )

                        // 减法优化 1：若当日未学习，滑动经过时在柱底部显示这个小点
                        if (isHovered) {
                            val dotCenter = Offset(centerX, bottomY - minPillH / 2f)
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.2f),
                                radius = 4.5.dp.toPx(),
                                center = dotCenter
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = 2.5.dp.toPx(),
                                center = dotCenter
                            )
                        }
                    } else {
                        // Studied day: 减法优化 2：有学习记录时不显示小点，仅放大并加高
                        val ratio = (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0f, 1f)
                        var h = minPillH + ratio * (plotH - minPillH)

                        // 动态加高与波纹扩散
                        if (hoveredIndex != null) {
                            val dist = abs(i - hoveredIndex!!)
                            if (dist == 0) {
                                h += 7.dp.toPx() // 焦点加高
                            } else if (dist == 1) {
                                h += 3.5.dp.toPx()
                            } else if (dist == 2) {
                                h += 1.5.dp.toPx()
                            }
                        }

                        val topY = bottomY - h
                        val startAlpha = if (isHovered) 0.95f else 0.75f
                        val endAlpha = if (isHovered) 1.0f else 0.95f
                        val brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = endAlpha),
                                primaryColor.copy(alpha = startAlpha)
                            ),
                            startY = topY,
                            endY = bottomY
                        )
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(centerX - actualBarW / 2f, topY),
                            size = androidx.compose.ui.geometry.Size(actualBarW, h),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }

            // Milestone Labels along X-Axis
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
                    val isSelected = hoveredIndex == idx
                    Text(
                        text = "${idx + 1}日",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            day.isToday -> MaterialTheme.colorScheme.primary
                            else -> subTextColor
                        },
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
