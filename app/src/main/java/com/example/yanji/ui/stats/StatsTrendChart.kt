package com.example.yanji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yanji.data.DayBarData
import com.example.yanji.data.DurationFormatter
import com.example.yanji.theme.*
import com.example.yanji.ui.components.GlassSegmentedControl
import com.example.yanji.ui.components.YanjiCard as Card

enum class TrendMode(val label: String) {
    BAR("柱状"),
    LINE("折线")
}

@Composable
fun StatsTrendChart(
    selectedTimeTab: Int,
    days: List<DayBarData>,
    trendChartMode: TrendMode,
    onSelectTrendMode: (TrendMode) -> Unit,
    onSelectDay: (DayBarData) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        colors = CardDefaults.cardColors(containerColor = YanjiColors.elevatedSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTimeTab == 0) "本周学习时长趋势" else "每日学时分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                GlassSegmentedControl(
                    items = TrendMode.entries,
                    selectedIndex = TrendMode.entries.indexOf(trendChartMode),
                    onItemSelected = { onSelectTrendMode(TrendMode.entries[it]) },
                    itemLabel = { it.label },
                    height = 32.dp,
                    modifier = Modifier.width(130.dp)
                )
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

            val actualMax = days.maxOfOrNull { it.durationSeconds } ?: 0L
            val maxBarDuration = if (actualMax > 0L) actualMax else 3600L

            if (trendChartMode == TrendMode.BAR) {
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val chartLineColor = MaterialTheme.colorScheme.primary
                    val chartTodayFill = MaterialTheme.colorScheme.primaryContainer
                    val dotCenterColor = MaterialTheme.colorScheme.surface

                    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        val n = days.size
                        if (n > 0) {
                            val topPad = 16.dp.toPx()
                            val bottomPad = 30.dp.toPx()
                            val plotH = size.height - topPad - bottomPad

                            val pts = days.mapIndexed { index, day ->
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
                                color = chartLineColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            pts.forEachIndexed { index, pt ->
                                val isToday = days[index].isToday
                                if (isToday) {
                                    drawCircle(color = chartTodayFill, radius = 9.dp.toPx(), center = pt)
                                }
                                drawCircle(color = chartLineColor, radius = 4.5f.dp.toPx(), center = pt)
                                drawCircle(color = dotCenterColor, radius = 2.dp.toPx(), center = pt)
                            }
                        }
                    }

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
        }
    }
}
