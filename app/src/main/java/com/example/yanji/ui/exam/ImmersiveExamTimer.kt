package com.example.yanji.ui.exam

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
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
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiProgressBar
import java.util.Locale

import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState

/**
 * 考研全真模拟沉浸式倒计时页面。
 *
 * 性能约定（规范 §37）：每秒推进的秒数通过 [State] 传入，并在子组件 [ExamCountdownCenter]
 * 叶子节点内读取，避免长达 3 小时的模考过程中每秒引起外层 Header、Card 容器及底部按钮的整页重组。
 */
@Composable
fun ImmersiveExamTimer(
    examName: String,
    remainingSeconds: State<Long>,
    totalSeconds: Long,
    startTime: Long,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onEarlyFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val startStr = remember(startTime) { YanjiTime.formatTime(startTime) }
    val endStr = remember(startTime, totalSeconds) {
        YanjiTime.formatTime(startTime + totalSeconds * 1000L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Info (零重组：考试期间固定不变)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isPaused) YanjiColors.warningSoft else MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isPaused) "考试已暂停" else "全真模拟进行中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) YanjiColors.warning else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = examName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "考试时间：$startStr - $endStr · 请专心答卷",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Center Countdown (仅此子节点随秒数高频局部重组)
        ExamCountdownCenter(
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds
        )

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
                        imageVector = if (isPaused) RemixIcons.PlayFill else RemixIcons.PauseFill,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = RemixIcons.CheckLine, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("提前交卷", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onQuit) {
                Text("退出考试", fontSize = 13.sp, color = YanjiColors.textTertiary)
            }
        }
    }
}

/**
 * 倒计时中央卡片（局部重组区域）。
 * 只有此组件在每秒变化时重组，外部容器不受牵连。
 */
@Composable
private fun ExamCountdownCenter(
    remainingSeconds: State<Long>,
    totalSeconds: Long
) {
    val remaining = remainingSeconds.value
    val hours = remaining / 3600
    val mins = (remaining % 3600) / 60
    val secs = remaining % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

    val progress = (remaining.toFloat() / totalSeconds.coerceAtLeast(1L)).coerceIn(0f, 1f)

    // DESIGN.md: 倒计时禁红（红仅用于破坏性操作）；临界（剩余<15分钟）最多 subtle amber，且不闪烁
    val isCritical = remaining < 900

    YanjiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        variant = YanjiCardVariant.Hero
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "剩余考试时间",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = timeFormatted,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCritical) YanjiColors.warning else MaterialTheme.colorScheme.primary,
                letterSpacing = (-1).sp
            )

            if (isCritical) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .background(YanjiColors.warningSoft)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "剩余不足 15 分钟 · 请安排收尾检查",
                        fontSize = 12.sp,
                        color = YanjiColors.warning
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            YanjiProgressBar(
                progress = progress,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

/**
 * 保持兼容的重载，供 Preview 或测试直接传 Long 使用。
 */
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
    val state = rememberUpdatedState(remainingSeconds)
    ImmersiveExamTimer(
        examName = examName,
        remainingSeconds = state,
        totalSeconds = totalSeconds,
        startTime = startTime,
        isPaused = isPaused,
        onPauseResume = onPauseResume,
        onEarlyFinish = onEarlyFinish,
        onQuit = onQuit
    )
}
