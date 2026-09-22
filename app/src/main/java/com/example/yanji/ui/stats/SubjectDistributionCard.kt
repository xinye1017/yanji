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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.SubjectDistributionItem
import com.example.yanji.data.SubjectStatsLevel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.GlassSegmentedControl
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
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
    val isDark = yanjiIsDarkTheme()

    // 学科色直接来自持久化的 Subject.colorHex；换伙伴主题、增删或排序学科都不会改变颜色身份。
    val subjectColors = remember(subjectDistribution, isDark) {
        subjectDistribution.map { item -> resolveSubjectColor(item.subjectColor, isDark) }
    }

    /** 取第 index 个学科的稳定显示色。 */
    fun colorFor(index: Int): Color = subjectColors[index % subjectColors.size]

    YanjiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped
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
                    modifier = Modifier.width(130.dp)
                )
            }

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
                val color = colorFor(0)
                val durationText = DurationFormatter.formatHoursMinutes(singleSub.durationSeconds)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 不要在此处 clip(RoundedCornerShape(12.dp))：容器圆角(12dp=42px)远大于
                        // 底部内边距(8dp)，会把紧贴底部的进度条左下角切掉，表现为「左端像被截断」。
                        // 点击与涟漪均由 clickable 自身按节点范围生效，去掉 clip 不影响功能。
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
                    val singleTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        val radius = size.height / 2f
                        val corner = CornerRadius(radius, radius)
                        drawRoundRect(color = singleTrackColor, cornerRadius = corner)
                        drawRoundRect(color = color, cornerRadius = corner)
                    }
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
                    subjectColors = subjectDistribution.mapIndexed { index, sub ->
                        sub.subjectName to colorFor(index)
                    }.toMap(),
                    totalLabel = timeRangeTitle
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                subjectDistribution.forEachIndexed { index, sub ->
                    val color = colorFor(index)
                    val subSecs = sub.durationSeconds
                    val totalSecs = maxOf(1L, subjectDist.values.sum())
                    val percent = (subSecs.toFloat() / totalSecs).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 不要在此处 clip(RoundedCornerShape(12.dp))：容器圆角(12dp=42px)远大于
                            // 底部内边距(4dp)，会把紧贴底部的进度条左下角切掉，表现为「左端像被截断」。
                            // 点击与涟漪均由 clickable 自身按节点范围生效，去掉 clip 不影响功能。
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
                    segments = subjectDistribution.mapIndexed { index, sub ->
                        colorFor(index) to sub.durationSeconds.toFloat()
                    }
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                subjectDistribution.forEachIndexed { index, sub ->
                    val color = colorFor(index)
                    val subSecs = sub.durationSeconds
                    val totalSecs = maxOf(1L, subjectDist.values.sum())
                    val percent = (subSecs.toFloat() / totalSecs).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 不要在此处 clip(RoundedCornerShape(12.dp))：容器圆角(12dp=42px)远大于
                            // 底部内边距(4dp)，会把紧贴底部的进度条左下角切掉，表现为「左端像被截断」。
                            // 点击与涟漪均由 clickable 自身按节点范围生效，去掉 clip 不影响功能。
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
        // 用 Canvas 直接绘制：轨道与填充各自 drawRoundRect，半径取半高（胶囊形）。
        //
        // 注意：进度条左端「像被截断」的问题**不在本函数**，而是外层可点击 Column 的
        // clip(RoundedCornerShape(12.dp)) 在圆角处切到了进度条（详见该处注释）。
        // 这里保持最简单、最高效的两次 drawRoundRect，不要为了绕那个问题加 clipPath。
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val radius = size.height / 2f
            val corner = CornerRadius(radius, radius)

            drawRoundRect(color = trackColor, cornerRadius = corner)

            val filledWidth = size.width * percent.coerceIn(0f, 1f)
            if (filledWidth > 0f) {
                val filledRadius = min(radius, filledWidth / 2f)
                drawRoundRect(
                    color = color,
                    size = Size(filledWidth, size.height),
                    cornerRadius = CornerRadius(filledRadius, filledRadius)
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
 * 环形图单段的几何参数（弧度制），供 [annulusSegmentPath] 生成带平齐切口的路径。
 */
private data class AnnulusSegmentMeta(
    val startRad: Float,
    val endRad: Float,
    val centre: Offset,
    val innerRadius: Float,
    val outerRadius: Float
)

/**
 * 生成一段环形（annulus）扇形路径。若 [closed] 为 true，则画成一整圈不做切口。
 *
 * 关键点：两端切口都是**沿半径方向的直线**，并且在内弧与外弧上各缩进相同的**弧长**（gap/2），
 * 因此两个切面彼此平行、从内到外宽度一致；不会像用固定角度切割那样向外张开成楔形。
 */
private fun annulusSegmentPath(
    meta: AnnulusSegmentMeta,
    gapPx: Float,
    closed: Boolean
): Path? {
    fun pointAt(radius: Float, angle: Float) = Offset(
        x = meta.centre.x + radius * kotlin.math.cos(angle),
        y = meta.centre.y + radius * kotlin.math.sin(angle)
    )

    // 整段闭合：内外两个圆用 EvenOdd 填成圆环，不做任何切口。
    if (closed) {
        return Path().apply {
            addOval(Rect(center = meta.centre, radius = meta.outerRadius))
            addOval(Rect(center = meta.centre, radius = meta.innerRadius))
            fillType = PathFillType.EvenOdd
        }
    }

    // 把「线性间隙」换算成各自半径上的角度，两端各缩进 gap/2 对应的弧长。
    val innerHalf = (gapPx / 2f) / meta.innerRadius
    val outerHalf = (gapPx / 2f) / meta.outerRadius
    val startInner = meta.startRad + innerHalf
    val endInner = meta.endRad - innerHalf
    val startOuter = meta.startRad + outerHalf
    val endOuter = meta.endRad - outerHalf
    if (endInner <= startInner || endOuter <= startOuter) return null

    val outerRect = Rect(center = meta.centre, radius = meta.outerRadius)
    val innerRect = Rect(center = meta.centre, radius = meta.innerRadius)

    return Path().apply {
        val pStartOuter = pointAt(meta.outerRadius, startOuter)
        moveTo(pStartOuter.x, pStartOuter.y)
        arcTo(
            rect = outerRect,
            startAngleDegrees = Math.toDegrees(startOuter.toDouble()).toFloat(),
            sweepAngleDegrees = Math.toDegrees((endOuter - startOuter).toDouble()).toFloat(),
            forceMoveTo = false
        )
        val pEndInner = pointAt(meta.innerRadius, endInner)
        lineTo(pEndInner.x, pEndInner.y)
        arcTo(
            rect = innerRect,
            startAngleDegrees = Math.toDegrees(endInner.toDouble()).toFloat(),
            sweepAngleDegrees = Math.toDegrees((startInner - endInner).toDouble()).toFloat(),
            forceMoveTo = false
        )
        close()
    }
}

@Composable
private fun SubjectDonutChart(
    subjectDist: Map<String, Long>,
    subjectColors: Map<String, Color>,
    modifier: Modifier = Modifier,
    totalLabel: String = "今日"
) {
    val total = subjectDist.values.sum()
    val reveal by animateFloatAsState(
        targetValue = if (total > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "subjectDonutReveal"
    )
    // 调用方始终会为每个扇区传入颜色；此处仅作兜底，在 Composable 作用域内解析一次。
    val fallbackColor = yanjiSubjectColor("other")

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val donutTrackColor = MaterialTheme.colorScheme.surfaceVariant
        // 颜色由调用方按 Subject.colorHex 传入；缺失时回落到“其他”的稳定学科色。
        val seriesColors = mutableMapOf<String, Color>()
        subjectDist.keys.forEach { name -> seriesColors[name] = subjectColors[name] ?: fallbackColor }
        Canvas(modifier = Modifier.size(150.dp)) {
            if (total > 0) {
                val strokeW = 24.dp.toPx()
                val diameter = min(size.width, size.height) - strokeW
                val segmentCount = subjectDist.size
                val centre = Offset(size.width / 2f, size.height / 2f)
                val centreRadius = diameter / 2f
                val innerRadius = centreRadius - strokeW / 2f
                val outerRadius = centreRadius + strokeW / 2f
                // Gap width measured along the ring, identical at the inner and outer edges.
                val gapPx = if (segmentCount > 1) 3.dp.toPx() else 0f
                var startAngle = -90f

                subjectDist.entries.forEachIndexed { index, (name, secs) ->
                    val sweep = secs.toFloat() / total * 360f * reveal
                    if (sweep > 0.5f) {
                        val startRad = (startAngle * (Math.PI / 180.0)).toFloat()
                        val endRad = ((startAngle + sweep) * (Math.PI / 180.0)).toFloat()
                        val meta = AnnulusSegmentMeta(startRad, endRad, centre, innerRadius, outerRadius)
                        val segmentPath = annulusSegmentPath(meta, gapPx, closed = segmentCount == 1)
                        if (segmentPath != null) {
                            drawPath(path = segmentPath, color = seriesColors.getValue(name))
                        }
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
