package com.example.yanji.ui.exam

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.AiAnalysis
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.YanjiCard as Card

/**
 * 模考页的「AI 深度诊断」。
 * 只渲染真实的 [AiAnalysis]，没有报告时给空状态。
 */
@Composable
fun ExamAiDiagnosisSection(
    analysis: AiAnalysis?,
    isAnalyzing: Boolean,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JuanjuanAvatar(size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI 模考深度诊断报告",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = if (analysis != null) {
                                "${analysis.provider} · ${analysis.model} · ${analysis.periodStart} ~ ${analysis.periodEnd}"
                            } else {
                                "基于你真实的专注、模考与日记记录生成"
                            },
                            fontSize = 12.sp,
                            color = YanjiTextSecondary
                        )
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
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("分析中...", fontSize = 12.sp)
                        } else {
                            Text(
                                if (analysis == null) "生成诊断" else "重新生成",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (analysis == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceSoft)
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAnalyzing) {
                                "正在生成诊断报告…"
                            } else {
                                "尚未生成诊断报告。\n点击右上角「生成诊断」，卷卷会只依据你已有的真实记录给出结论；记录不足时会直接说明数据缺口，不会编造分数或趋势。"
                            },
                            fontSize = 12.sp,
                            color = YanjiTextTertiary,
                            lineHeight = 18.sp
                        )
                    }
                    return@Column
                }

                if (analysis.overview.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceBlue)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "【综合概览】${analysis.overview}",
                            fontSize = 13.sp,
                            color = YanjiTextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (analysis.strengths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("已确认的优势", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.strengths.forEach { item ->
                        BulletLine(text = item, color = YanjiSuccess)
                    }
                }

                if (analysis.weaknesses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("待改进 / 数据缺口", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.weaknesses.forEach { item ->
                        BulletLine(text = item, color = YanjiWarning)
                    }
                }

                if (analysis.trendAnalysis.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("趋势判断", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(analysis.trendAnalysis, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
                }

                if (analysis.suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("未来 3 天计划", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.suggestions.forEachIndexed { index, item ->
                        DiagnosisItem(
                            tag = "第 ${index + 1} 天",
                            title = "行动建议",
                            desc = item,
                            color = YanjiPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
    }
}

@Composable
fun DiagnosisItem(
    tag: String,
    title: String,
    desc: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = YanjiTextPrimary)
            Text(text = desc, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
        }
    }
}
