package com.example.yanji.ui.exam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.ExamSession
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard as Card

@Composable
fun ExamScoreTrendSection(examSessions: List<ExamSession>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Trend Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YanjiRadius.StandardCardRadius),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "模考均分走势",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "近 4 次模拟全真成绩统计",
                    fontSize = 12.sp,
                    color = YanjiTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 真实数据源：examSessions（按开始时间倒序），取最近 4 次已录入成绩的模考
                val scoredSessions = examSessions.filter { it.score != null }
                val recentScored = scoredSessions.take(4).reversed()
                val numberingOffset = scoredSessions.size - recentScored.size

                if (recentScored.isEmpty()) {
                    // 空状态：暂无已录入成绩的模考
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            .background(YanjiSurfaceSoft)
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无模考成绩，完成模考并录入分数后展示走势",
                            fontSize = 12.sp,
                            color = YanjiTextTertiary
                        )
                    }
                } else {
                    // Score trend canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        // 按各自满分归一化到 150 分制，保证跨科目可比
                        val scores = recentScored.map { s ->
                            ((s.score ?: 0.0) / s.maxScore.coerceAtLeast(1.0) * 150.0).toFloat()
                        }
                        val points = scores.mapIndexed { index, sc ->
                            val x = if (scores.size == 1) width / 2f
                            else width * (index.toFloat() / (scores.size - 1))
                            val y = height - (sc / 150f * height * 0.8f + 10f)
                            Offset(x, y)
                        }

                        // Draw connecting lines
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = YanjiPrimary,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw points
                        points.forEach { pt ->
                            drawCircle(color = YanjiPrimary, radius = 5.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pt)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        recentScored.forEachIndexed { index, s ->
                            val isLatest = index == recentScored.lastIndex
                            val scoreValue = s.score ?: 0.0
                            val scoreText = if (scoreValue % 1.0 == 0.0) {
                                "${scoreValue.toInt()}"
                            } else {
                                "$scoreValue"
                            }
                            Text(
                                text = "第${numberingOffset + index + 1}次: ${scoreText}分",
                                fontSize = 12.sp,
                                fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLatest) YanjiPrimary else YanjiTextTertiary
                            )
                        }
                    }
                }
            }
        }

        // Exam History List
        Text(
            text = "模考历史归档",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = YanjiTextPrimary
        )

        examSessions.forEach { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.subjectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )

                        if (session.score != null) {
                            Text(
                                text = "${session.score.toInt()} 分",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiPrimary
                            )
                        } else {
                            Text("未录入成绩", fontSize = 12.sp, color = YanjiTextTertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "实际用时：${session.actualDurationSeconds / 60} 分钟 · 满分 ${session.maxScore.toInt()}",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )

                    if (session.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .background(YanjiSurfaceSoft)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = session.note,
                                fontSize = 12.sp,
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
