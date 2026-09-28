package com.example.yanji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.AiAnalysis
import com.example.yanji.theme.*
import com.example.yanji.ui.components.AiAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.icons.RemixIcons

@Composable
fun MetricMiniCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: (() -> Unit)? = null
) {
    val cardContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }

    if (onClick != null) {
        YanjiCard(
            onClick = onClick,
            modifier = modifier,
            variant = YanjiCardVariant.Compact,
            content = cardContent
        )
    } else {
        YanjiCard(
            modifier = modifier,
            variant = YanjiCardVariant.Compact,
            content = cardContent
        )
    }
}

@Composable
fun StatsAiReportCard(
    report: AiAnalysis?,
    isAnalyzing: Boolean,
    errorMessage: String? = null,
    onGenerate: () -> Unit
) {
    YanjiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Standard
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部 Header 行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AiAvatar(size = 38.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${currentMascotTheme().name} · AI 阶段学情诊断",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (report != null) {
                                Surface(
                                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                                    color = YanjiColors.success.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "已生成",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = YanjiColors.success,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (report != null) {
                                "${report.periodStart} ~ ${report.periodEnd} 阶段评估"
                            } else {
                                // 说清窗口：这张卡始终按近 7 天生成，与上方时间 Tab 无关。
                                // 在「本年」视角下尤其要写出来，否则会被读成全年诊断。
                                "基于近 7 天专注记录、学科投入与模考深度建模"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onGenerate,
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (report == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (report == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = if (report == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("分析中…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = RemixIcons.SparklingLine,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (report == null) "生成诊断" else "重新诊断",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. 错误警示卡片
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = RemixIcons.ErrorWarningLine,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 3. 分析中状态骨架卡片（若无历史报告但处于生成中）
            if (isAnalyzing && report == null) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = RemixIcons.BrainLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "正在整合阶段备考数据并生成诊断…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI 正在梳理专注时长分布、学科复习连贯性及模考数据，为你推演针对性复盘建议",
                            style = MaterialTheme.typography.bodySmall,
                            color = YanjiColors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }
                }
            } else if (report == null && errorMessage == null) {
                // 4. 空状态卡片：具备视觉指引与价值预览
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 22.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = RemixIcons.SparklingLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "尚未生成阶段学情诊断",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "保存专注、模考或复盘记录后，AI 将结合你的真实学情，深度透视复习成效与提分盲区",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        // 3 大核心诊断价值药丸
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            DiagnosticFeaturePill("⏱️ 专注韧性")
                            DiagnosticFeaturePill("⚖️ 学科平衡")
                            DiagnosticFeaturePill("🚀 提分建议")
                        }
                    }
                }
            }

            // 5. 丰富详尽的诊断报告内容（当 report != null）
            if (report != null) {
                // (a) 综合学情概览卡片
                val cleanOverview = report.overview.trim().takeIf {
                    it.length >= 3 &&
                        it.count { ch -> ch.isLetterOrDigit() } >= 2 &&
                        !it.equals("null", ignoreCase = true) &&
                        !it.equals("undefined", ignoreCase = true)
                } ?: "已结合近阶段学情完成综合诊断，详见以下优势亮点与提分建议。"

                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = RemixIcons.CompassLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "综合学情洞察",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = cleanOverview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 21.sp
                        )

                        if (report.requestSnapshot.isNotBlank()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = RemixIcons.LineChartLine,
                                    contentDescription = null,
                                    tint = YanjiColors.textTertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "数据依据 · ${report.requestSnapshot}",
                                    fontSize = 11.sp,
                                    color = YanjiColors.textTertiary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // (b) 优势亮点
                val cleanStrengths = report.strengths.filter {
                    val t = it.trim()
                    t.length >= 2 && t.count { ch -> ch.isLetterOrDigit() } >= 2 &&
                        !t.equals("null", ignoreCase = true) && !t.equals("undefined", ignoreCase = true)
                }
                if (cleanStrengths.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = YanjiColors.success.copy(alpha = 0.05f),
                        border = BorderStroke(0.5.dp, YanjiColors.success.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = RemixIcons.CheckboxCircleFill,
                                        contentDescription = null,
                                        tint = YanjiColors.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "阶段优势与亮点",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = YanjiColors.success
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = YanjiColors.success.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${cleanStrengths.size} 项",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = YanjiColors.success,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            cleanStrengths.forEach { s ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(YanjiColors.success)
                                    )
                                    Text(
                                        text = s,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // (c) 待改进与突破点
                val cleanWeaknesses = report.weaknesses.filter {
                    val t = it.trim()
                    t.length >= 2 && t.count { ch -> ch.isLetterOrDigit() } >= 2 &&
                        !t.equals("null", ignoreCase = true) && !t.equals("undefined", ignoreCase = true)
                }
                if (cleanWeaknesses.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = YanjiColors.warning.copy(alpha = 0.05f),
                        border = BorderStroke(0.5.dp, YanjiColors.warning.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = RemixIcons.AlarmWarningLine,
                                        contentDescription = null,
                                        tint = YanjiColors.warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "薄弱环节与突破点",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = YanjiColors.warning
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = YanjiColors.warning.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${cleanWeaknesses.size} 项",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = YanjiColors.warning,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            cleanWeaknesses.forEach { w ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(YanjiColors.warning)
                                    )
                                    Text(
                                        text = w,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // (d) 态势与动量分析
                val cleanTrend = report.trendAnalysis.trim().takeIf {
                    it.length >= 3 &&
                        it.count { ch -> ch.isLetterOrDigit() } >= 2 &&
                        !it.equals("null", ignoreCase = true) &&
                        !it.equals("undefined", ignoreCase = true)
                }
                if (cleanTrend != null) {
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = RemixIcons.LineChartLine,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "学情走势与动量推演",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = cleanTrend,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                // (e) 冲刺行动建议（编号阶梯化卡片）
                val cleanSuggestions = report.suggestions.filter {
                    val t = it.trim()
                    t.length >= 2 && t.count { ch -> ch.isLetterOrDigit() } >= 2 &&
                        !t.equals("null", ignoreCase = true) && !t.equals("undefined", ignoreCase = true)
                }
                if (cleanSuggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = RemixIcons.RocketLine,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "未来阶段提分建议",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            cleanSuggestions.forEachIndexed { index, sg ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = sg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 19.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部轻量安全提示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RemixIcons.ShieldCheckLine,
                        contentDescription = null,
                        tint = YanjiColors.textTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "真实数据本地建模 · AI 建议仅供备考规划参考",
                        style = MaterialTheme.typography.labelSmall,
                        color = YanjiColors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticFeaturePill(text: String) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
