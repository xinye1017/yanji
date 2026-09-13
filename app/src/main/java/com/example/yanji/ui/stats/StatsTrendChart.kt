package com.example.yanji.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yanji.data.DayBarData
import com.example.yanji.theme.*
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
                    text = if (selectedTimeTab == 0) "本周学习时长趋势" else "每日学时分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendMode.entries.forEach { mode ->
                        TrendModeChip(
                            label = mode.label,
                            selected = trendChartMode == mode,
                            onClick = { onSelectTrendMode(mode) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))

            val maxBarDuration = maxOf(36000L, days.maxOfOrNull { it.durationSeconds } ?: 36000L)

            if (trendChartMode == TrendMode.BAR) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { day ->
                        val ratio = (day.durationSeconds.toFloat() / maxBarDuration).coerceIn(0.06f, 1f)
                        val isToday = day.isToday

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectDay(day) }
                                .padding(horizontal = 4.dp)
                        ) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
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
                                color = YanjiPrimary,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            pts.forEachIndexed { index, pt ->
                                val isToday = days[index].isToday
                                if (isToday) {
                                    drawCircle(color = YanjiPrimarySoft, radius = 9.dp.toPx(), center = pt)
                                }
                                drawCircle(color = YanjiPrimary, radius = 4.5f.dp.toPx(), center = pt)
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
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
                                color = if (day.isToday) YanjiPrimary else YanjiTextSecondary,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        days.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectDay(day) }
                            )
                        }
                    }
                }
            }
        }
    }
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) YanjiPrimaryStrong else YanjiTextSecondary
        )
    }
}
