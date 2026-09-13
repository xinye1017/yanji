package com.example.yanji.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.AiAnalysis
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.YanjiCard as Card

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
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = YanjiTextTertiary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary,
                    maxLines = 1
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatsAiReportCard(
    report: AiAnalysis?,
    isAnalyzing: Boolean,
    onGenerate: () -> Unit
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JuanjuanAvatar(size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "卷卷 · AI 阶段学情诊断",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = "基于真实学习与日记数据深度分析",
                            style = MaterialTheme.typography.labelMedium,
                            color = YanjiTextSecondary
                        )
                    }
                }

                Button(
                    onClick = onGenerate,
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = YanjiOnPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("分析中...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("生成诊断", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (report != null) {
                Spacer(modifier = Modifier.height(YanjiSpacing.InlineGap))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(YanjiSurfaceSoft)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "学情概览 (${report.periodStart} ~ ${report.periodEnd})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = YanjiTextPrimary,
                            lineHeight = 20.sp
                        )

                        if (report.strengths.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(YanjiSpacing.InnerGap))
                            Text(
                                text = "优势亮点",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = YanjiSuccess
                            )
                            report.strengths.forEach { s ->
                                Text(
                                    text = "• $s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = YanjiTextPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        if (report.weaknesses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "薄弱卡点",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = YanjiWarning
                            )
                            report.weaknesses.forEach { w ->
                                Text(
                                    text = "• $w",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = YanjiTextPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        if (report.suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "未来三天建议",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = YanjiPrimary
                            )
                            report.suggestions.forEach { sg ->
                                Text(
                                    text = "• $sg",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = YanjiTextPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
