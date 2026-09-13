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
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard as Card

@Composable
fun ExamPresetsList(
    onStartExam: (subjectId: String, name: String, durationSecs: Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExamCard(
            title = "数学一 全真模拟套卷",
            durationMins = 180,
            fullScore = 150,
            recommendedTime = "08:30 - 11:30",
            accentColor = SubjectMath,
            onStart = { onStartExam("math", "数学一 全真模拟", 10800L) }
        )

        ExamCard(
            title = "408 计算机学科专业基础",
            durationMins = 180,
            fullScore = 150,
            recommendedTime = "14:00 - 17:00",
            accentColor = SubjectMajor,
            onStart = { onStartExam("major", "408专业课 全真模拟", 10800L) }
        )

        ExamCard(
            title = "英语一 全真模考",
            durationMins = 180,
            fullScore = 100,
            recommendedTime = "14:00 - 17:00",
            accentColor = SubjectEnglish,
            onStart = { onStartExam("english", "英语一 模拟考试", 10800L) }
        )

        ExamCard(
            title = "思想政治理论 模考",
            durationMins = 180,
            fullScore = 100,
            recommendedTime = "08:30 - 11:30",
            accentColor = SubjectPolitics,
            onStart = { onStartExam("politics", "思想政治理论 模考", 10800L) }
        )

        ExamCard(
            title = "自定义专项模拟 (60分钟)",
            durationMins = 60,
            fullScore = 50,
            recommendedTime = "限时小题突破",
            accentColor = YanjiLavender,
            onStart = { onStartExam("other", "小题限时训练", 3600L) }
        )
    }
}

@Composable
fun ExamCard(
    title: String,
    durationMins: Int,
    fullScore: Int,
    recommendedTime: String,
    accentColor: Color,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$durationMins 分钟 · 满分 $fullScore",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = recommendedTime,
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                }
            }

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("开始考试", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
