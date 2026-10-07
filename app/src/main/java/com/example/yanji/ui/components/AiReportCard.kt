package com.example.yanji.ui.components

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
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.currentMascotTheme
import com.example.yanji.ui.icons.RemixIcons

/**
 * AI 学情诊断报告卡——统计页与模考页共用同一套样式。
 *
 * 视觉结构（两页完全一致，只有 [title] / [emptyHint] 两个文案参数不同）：
 *
 *   - Header：吉祥物头像 + 标题 + 「已生成」徽章 + 生成按钮；
 *   - 概览：全卡唯一的底色 hero 块（综合学情洞察 + 数据依据）；
 *   - 其余 Section 全部扁平：图标底片 + 中性标题 + 计数胶囊，发丝线分组；
 *   - 颜色只活在底片 / 项目符号 / 胶囊上，标题不染色，避免整页彩虹块。
 *
 * 消除 card-in-card：旧版每个 Section 都是一个嵌套彩色 Surface，现只保留 hero 一块。
 */

// ============================================================================
// 报告数据清洗
//
// AI 返回的是自由文本，null / undefined / 空壳字段是常态。清洗规则统一收在这里，
// 渲染层只面对「可直接显示的字符串 / 列表」。
// ============================================================================

/** 单段文本清洗：不可渲染时返回 null（长度不足、无字母数字、null/undefined 字面量）。 */
internal fun String.cleanReportText(): String? {
    val t = trim()
    return t.takeIf {
        it.length >= 3 &&
            it.count { ch -> ch.isLetterOrDigit() } >= 2 &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("undefined", ignoreCase = true)
    }
}

/** 列表清洗：逐项过滤后保留原顺序。 */
internal fun List<String>.cleanReportItems(): List<String> =
    mapNotNull { it.cleanReportText() }

/** 报告年龄：写「N 小时前」而不是裸时间戳，让用户一眼判断数据新鲜度。 */
internal fun reportAgeLabel(createdAt: Long): String {
    val minutes = ((System.currentTimeMillis() - createdAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "刚刚生成"
        minutes < 60 -> "${minutes} 分钟前生成"
        minutes < 24 * 60 -> "${minutes / 60} 小时前生成"
        else -> "${minutes / (24 * 60)} 天前生成"
    }
}

/** ISO 日期 → "09.22" 紧凑形态，副标题一行放得下；格式异常时原样返回。 */
internal fun compactPeriodDate(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[1]}.${parts[2]}" else iso
}

// ============================================================================
// 报告内部零件
// ============================================================================

/** Section 图标底片：颜色只活在底片与项目符号上，标题保持中性色。 */
@Composable
internal fun ReportIconChip(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    chipSize: Int = 26,
    iconSize: Int = 15
) {
    Box(
        modifier = modifier
            .size(chipSize.dp)
            .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Section 头部：图标底片 + 标题 + 右侧计数胶囊。 */
@Composable
internal fun ReportSectionHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    count: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportIconChip(icon = icon, tint = tint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (count != null) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$count 项",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Section 之间的发丝分隔线：替代旧版「一个彩色盒子」的分组方式。 */
@Composable
internal fun ReportSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 13.dp),
        thickness = 0.5.dp,
        color = YanjiColors.rowDivider
    )
}

/** 圆点列表行：优势 / 薄弱环节共用。 */
@Composable
internal fun ReportBulletRow(dotColor: Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 编号建议行：1/2/3 阶梯，天然传达「按顺序执行」的语感。 */
@Composable
internal fun ReportSuggestionRow(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 空状态里的价值预览药丸。 */
@Composable
private fun DiagnosticFeaturePill(text: String) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
        color = MaterialTheme.colorScheme.surface
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

// ============================================================================
// 报告卡主体
// ============================================================================

/**
 * @param title 头部标题，调用方拼上当前吉祥物名，如「饼饼 · AI 阶段学情诊断」。
 * @param emptyHint 无报告时副标题的窗口口径说明（统计页讲近 7 天，模考页讲模考+随笔）。
 * @param periodScopeNote **有报告时**随日期行常驻的口径尾注。报告是缓存快照，
 *   窗口/数据源口径若只在空态出现过，用户对照顶部实时数字就会判定"统计自相矛盾"。
 *   统计页/模考页窗口不同，由调用方各自钉死，不猜。
 */
@Composable
fun AiReportCard(
    title: String,
    emptyHint: String,
    periodScopeNote: String,
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
                                text = title,
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
                                // 紧凑日期 + 新鲜度：报告是缓存数据，「多久前生成的」比裸周期更有信息量。
                                "${compactPeriodDate(report.periodStart)} ~ ${compactPeriodDate(report.periodEnd)} · ${reportAgeLabel(report.createdAt)} · $periodScopeNote"
                            } else {
                                emptyHint
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

            // 2. 错误警示
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
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

            // 3. 分析中骨架（无历史报告时）；有旧报告时只转按钮，内容照常展示
            if (isAnalyzing && report == null) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
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
                // 4. 空状态：视觉指引 + 价值预览
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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

            // 5. 报告主体
            if (report != null) {
                val cleanOverview = report.overview.cleanReportText()
                    ?: "已结合近阶段学情完成综合诊断，详见以下优势亮点与提分建议。"

                Column(modifier = Modifier.fillMaxWidth()) {
                    // (a) 综合学情概览——全卡唯一的底色 hero 块，视觉锚点
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportIconChip(
                                    icon = RemixIcons.CompassLine,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "综合学情洞察",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    color = YanjiColors.rowDivider,
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
                    val cleanStrengths = report.strengths.cleanReportItems()
                    if (cleanStrengths.isNotEmpty()) {
                        ReportSectionDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportSectionHeader(
                                icon = RemixIcons.CheckboxCircleFill,
                                tint = YanjiColors.success,
                                title = "阶段优势与亮点",
                                count = cleanStrengths.size
                            )
                            cleanStrengths.forEach { s ->
                                ReportBulletRow(dotColor = YanjiColors.success, text = s)
                            }
                        }
                    }

                    // (c) 待改进与突破点
                    val cleanWeaknesses = report.weaknesses.cleanReportItems()
                    if (cleanWeaknesses.isNotEmpty()) {
                        ReportSectionDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportSectionHeader(
                                icon = RemixIcons.AlarmWarningLine,
                                tint = YanjiColors.warning,
                                title = "薄弱环节与突破点",
                                count = cleanWeaknesses.size
                            )
                            cleanWeaknesses.forEach { w ->
                                ReportBulletRow(dotColor = YanjiColors.warning, text = w)
                            }
                        }
                    }

                    // (d) 态势与动量分析
                    val cleanTrend = report.trendAnalysis.cleanReportText()
                    if (cleanTrend != null) {
                        ReportSectionDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportSectionHeader(
                                icon = RemixIcons.LineChartLine,
                                tint = MaterialTheme.colorScheme.primary,
                                title = "学情走势与动量推演"
                            )
                            Text(
                                text = cleanTrend,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    // (e) 冲刺行动建议（编号阶梯）
                    val cleanSuggestions = report.suggestions.cleanReportItems()
                    if (cleanSuggestions.isNotEmpty()) {
                        ReportSectionDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReportSectionHeader(
                                icon = RemixIcons.RocketLine,
                                tint = MaterialTheme.colorScheme.primary,
                                title = "未来阶段提分建议"
                            )
                            cleanSuggestions.forEachIndexed { index, sg ->
                                ReportSuggestionRow(index = index, text = sg)
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

/** 统计页 / 模考页共用的标题拼接：当前吉祥物名 + 场景后缀。 */
@Composable
fun aiReportTitle(sceneSuffix: String): String =
    "${currentMascotTheme().name} · $sceneSuffix"
