package com.example.yanji.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard as Card
import java.util.Locale

@Composable
fun ImmersiveExamTimer(
    examName: String,
    remainingSeconds: Long,
    totalSeconds: Long,
    startTime: Long,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onEarlyFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val hours = remainingSeconds / 3600
    val mins = (remainingSeconds % 3600) / 60
    val secs = remainingSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

    val startStr = remember(startTime) { YanjiTime.formatTime(startTime) }
    val endStr = remember(startTime, totalSeconds) {
        YanjiTime.formatTime(startTime + totalSeconds * 1000L)
    }

    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1L)).coerceIn(0f, 1f)

    // DESIGN.md: 倒计时禁红（红仅用于破坏性操作）；临界（剩余<15分钟）最多 subtle amber，且不闪烁
    val isCritical = remainingSeconds < 900

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isPaused) YanjiWarningSoft else YanjiLavenderSoft)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isPaused) "考试已暂停" else "全真模拟进行中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) YanjiWarning else YanjiLavender
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = examName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "考试时间：$startStr - $endStr · 请专心答卷",
                fontSize = 13.sp,
                color = YanjiTextSecondary
            )
        }

        // Center Countdown
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(YanjiRadius.HeroCardRadius),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "剩余考试时间",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = YanjiTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = timeFormatted,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCritical) YanjiWarning else YanjiPrimary,
                    letterSpacing = (-1).sp
                )

                if (isCritical) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            .background(YanjiWarningSoft)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "剩余不足 15 分钟 · 请安排收尾检查",
                            fontSize = 12.sp,
                            color = YanjiWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(12.dp)),  // token-exempt: 进度条轨道几何，不是产品组件圆角
                    color = YanjiPrimary,
                    trackColor = YanjiPrimarySoft
                )
            }
        }

        // Bottom Operations
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPaused) "继续考试" else "暂停", fontSize = 15.sp)
                }

                Button(
                    onClick = onEarlyFinish,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("提前交卷", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onQuit) {
                Text("退出考试", fontSize = 13.sp, color = YanjiTextTertiary)
            }
        }
    }
}
