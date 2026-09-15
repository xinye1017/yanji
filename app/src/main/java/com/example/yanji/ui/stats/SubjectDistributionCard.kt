package com.example.yanji.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.GlassSegmentedControl
import com.example.yanji.ui.components.YanjiCard as Card
import kotlin.math.min

@Composable
fun SubjectDistributionCard(
    subjectDistribution: List<SubjectDistributionItem>,
    subjectStatsLevel: SubjectStatsLevel,
    timeRangeTitle: String,
    onSelectSubjectLevel: (SubjectStatsLevel) -> Unit,
    onNavigateToSubjectDetail: (String) -> Unit
) {
    val subjectDist = subjectDistribution.associate { it.subjectName to it.durationSeconds }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.StandardCardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                GlassSegmentedControl(
                    items = listOf(SubjectStatsLevel.CATEGORY, SubjectStatsLevel.SUBCATEGORY),
                    selectedIndex = if (subjectStatsLevel == SubjectStatsLevel.CATEGORY) 0 else 1,
                    onItemSelected = {
                        onSelectSubjectLevel(if (it == 0) SubjectStatsLevel.CATEGORY else SubjectStatsLevel.SUBCATEGORY)
                    },
                    itemLabel = { it.title },
                    height = 32.dp,
                    modifier = Modifier.width(150.dp)
                )
            }
            Text(
                text = if (subjectStatsLevel == SubjectStatsLevel.SUBCATEGORY) {
                    "按具体学科统计 · 点击查看明细"
                } else {
                    "已汇总子类与大类直接记录 · 点击查看明细"
                },
                style = MaterialTheme.typography.labelMedium,
                color = YanjiColors.textTertiary
            )

            if (subjectDistribution.isEmpty()) {
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                Text(
                    text = "本周期暂无科目学时记录，完成一次专注后自动生成",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            } else if (subjectDistribution.size == 1) {
                // 单学科多态：显示 100% 紧凑信息条，不绘制巨大单色圆环
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                val singleSub = subjectDistribution.first()
                val color = subjectDisplayColor(singleSub)
                val durationText = DurationFormatter.formatHoursMinutes(singleSub.durationSeconds)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .clickable { onNavigateToSubjectDetail(singleSub.subjectId) }
                        .padding(vertical = 8.dp)
                        .semantics {
                            contentDescription = "${singleSub.subjectName}，学习时长 $durationText，占比 100%"
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = singleSub.subjectName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "$durationText (100%)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "全部专注时间集中在该学科 · 点击查看科目详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = YanjiColors.textTertiary
                    )
                }
            } else if (subjectDistribution.size in 2..5) {
                // 2~5 个科目：甜甜圈构成环 + 进度列表
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                SubjectDonutChart(
                    subjectDist = subjectDist,
                    totalLabel = timeRangeTitle
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                subjectDistribution.forEachIndexed { index, sub ->
                    val color = subjectDisplayColor(sub)
                    val subSecs = sub.durationSeconds
                    val totalSecs = maxOf(1L, subjectDist.values.sum())
                    val percent = (subSecs.toFloat() / totalSecs).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(YanjiRadius.Small))
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
            } else {
                // >5 个科目：横向堆叠百分比条 + 列表
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                SubjectDistributionBar(
                    segments = subjectDistribution.map { sub ->
                        subjectDisplayColor(sub) to sub.durationSeconds.toFloat()
                    }
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                subjectDistribution.forEachIndexed { index, sub ->
                    val color = subjectDisplayColor(sub)
                    val subSecs = sub.durationSeconds
                    val totalSecs = maxOf(1L, subjectDist.values.sum())
                    val percent = (subSecs.toFloat() / totalSecs).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(YanjiRadius.Small))
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
    }
}

@Composable
fun SubjectProgressBar(
    name: String,
    time: String,
    percent: Float,
    color: Color
) {
    val a11yDesc = "$name，学习时长 $time，占比 ${(percent * 100).toInt()}%"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yDesc }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "$time (${(percent * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun SubjectDistributionBar(segments: List<Pair<Color, Float>>) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat()
    if (segments.isEmpty() || total <= 0f) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
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

/**
 * 单条学科分布的显示色：优先用数据层给的 hex，解析失败再回落到学科序列色。
 */
@Composable
@ReadOnlyComposable
private fun subjectDisplayColor(sub: SubjectDistributionItem): Color {
    val fallback = subjectChartColor(sub.subjectName)
    return try {
        Color(sub.subjectColor.toColorInt())
    } catch (_: Exception) {
        fallback
    }
}

/**
 * 学科序列色。
 */
@Composable
@ReadOnlyComposable
fun subjectChartColor(name: String): Color = when {
    name.contains("数学") || name.contains("线性代数") || name.contains("概率论") ->
        yanjiSeriesToken(SubjectMath)
    name.contains("408") || name.contains("专业课") || name.contains("数据结构") ||
        name.contains("计算机组成") || name.contains("计算机网络") || name.contains("操作系统") ->
        yanjiSeriesToken(SubjectMajor)
    name.contains("英语") -> yanjiSeriesToken(SubjectEnglish)
    name.contains("政治") -> yanjiSeriesToken(SubjectPolitics)
    else -> yanjiSeriesToken(SubjectOther)
}

@Composable
private fun SubjectDonutChart(
    subjectDist: Map<String, Long>,
    modifier: Modifier = Modifier,
    totalLabel: String = "今日"
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
        val donutTrackColor = MaterialTheme.colorScheme.surfaceVariant
        val seriesColors = mutableMapOf<String, Color>()
        subjectDist.keys.forEach { name -> seriesColors[name] = subjectChartColor(name) }
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
                            color = seriesColors.getValue(name),
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
                drawCircle(
                    color = donutTrackColor,
                    radius = (min(size.width, size.height) - 24.dp.toPx()) / 2f,
                    style = Stroke(width = 24.dp.toPx())
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (total > 0) DurationFormatter.formatHoursMinutes(total) else "暂无数据",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (total > 0) MaterialTheme.colorScheme.onSurface else YanjiColors.textTertiary
            )
            if (total > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "${totalLabel}合计", style = MaterialTheme.typography.labelMedium, color = YanjiColors.textTertiary)
            }
        }
    }
}
