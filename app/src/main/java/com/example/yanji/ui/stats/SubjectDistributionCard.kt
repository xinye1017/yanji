package com.example.yanji.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
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
                SubjectLevelSegmented(
                    selected = subjectStatsLevel,
                    onSelect = onSelectSubjectLevel
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

            Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

            // 学科构成环形图
            SubjectDonutChart(
                subjectDist = subjectDist,
                totalLabel = timeRangeTitle
            )

            if (subjectDistribution.isNotEmpty()) {
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
            } else {
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))
                Text(
                    text = "本周期暂无科目学时记录，完成一次专注后自动生成",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
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
            Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "$time (${(percent * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(12.dp)),  // token-exempt: 进度条轨道几何，不是产品组件圆角
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun SubjectLevelSegmented(
    selected: SubjectStatsLevel,
    onSelect: (SubjectStatsLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(SubjectStatsLevel.CATEGORY, SubjectStatsLevel.SUBCATEGORY).forEach { level ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(level) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = level.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

/**
 * 单条学科分布的显示色：优先用数据层给的 hex，解析失败再回落到学科序列色。
 *
 * 做成文件级 `@Composable` 组合子而不是 `SubjectDistributionCard` 里的局部函数：
 * 暗色下序列色要升调（design_dark.md §3.4），局部非 Composable 函数读不到主题。
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
 *
 * `@Composable` 是必需的：暗色下要按 design_dark.md §3.4 升调
 *（数学一 #4F7DF3 / 408 #818CF8 / 英语一 #A78BFA / 政治 #38BDF8），
 * 而亮色下返回的仍是原来的 `Subject*` token（逐值不变）。
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
        // Canvas 的绘制 lambda 不是 @Composable，读不到 MaterialTheme，
        // 因此序列色必须在 Canvas 之外先解析好。
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
