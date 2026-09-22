package com.example.yanji.ui.stats

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatsHeroCard(
    selectedTimeTab: Int,
    periodDurationSecs: Long,
    activeDays: Int,
    previousWeekSeconds: Long,
    weeklyTotalSeconds: Long,
    dailyAverageSeconds: Long,
    settings: UserSettings
) {
    val goalSeconds = when (selectedTimeTab) {
        0 -> (settings.dailyGoalHours * 7 * 3600f).toLong()
        1 -> (settings.dailyGoalHours * 30 * 3600f).toLong()
        else -> 0L
    }
    val goalLabel = if (selectedTimeTab == 1) "月目标进度" else "周目标进度"

    YanjiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Standard
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 行 1：标签 + 有效天数 + 较上周对比
            // 固定行高：只有「本周」带「较上周」胶囊，其余两个视角没有，若让行高随内容
            // 变化，三张卡片首行高度会不一致，切换视角时页面会有明显跳动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeroHeaderRowHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = when (selectedTimeTab) { 0 -> "本周学习时长"; 1 -> "本月学习时长"; else -> "累计总学时" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "有效学习 ${activeDays} 天",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = YanjiColors.success
                    )
                }

                // 「较上周」仅在周视角显示：两个都是周一至周日的自然周。
                if (selectedTimeTab == 0) {
                    if (previousWeekSeconds > 0L || weeklyTotalSeconds > 0L) {
                        val delta = weeklyTotalSeconds - previousWeekSeconds
                        val isUp = delta > 0
                        val isFlat = delta == 0L
                        val pillBg = when {
                            isFlat -> MaterialTheme.colorScheme.surfaceVariant
                            isUp -> YanjiColors.successSoft
                            else -> YanjiColors.warningSoft
                        }
                        val pillFg = when {
                            isFlat -> MaterialTheme.colorScheme.onSurfaceVariant
                            isUp -> YanjiColors.success
                            else -> YanjiColors.warning
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = RemixIcons.LineChartLine,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isFlat) "较上周 持平" else "较上周 ${if (isUp) "+" else "-"}${formatDeltaCompact(abs(delta))}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = pillFg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 行 2：放大主数字（h / m 单位分离）+ 右侧日均投入
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val hoursValue = periodDurationSecs / 3600
                val minutesValue = (periodDurationSecs % 3600) / 60
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hoursValue",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "h",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                    if (minutesValue > 0) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$minutesValue",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "m",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "日均投入",
                        style = MaterialTheme.typography.labelSmall,
                        color = YanjiColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = DurationFormatter.formatHoursMinutes(dailyAverageSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 行 3：目标进度（设计稿：进度条 + 百分比）
            if (goalSeconds > 0L) {
                Spacer(modifier = Modifier.height(14.dp))
                val goalProgress = (periodDurationSecs.toFloat() / goalSeconds).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$goalLabel (${formatGoalHours(periodDurationSecs)} / ${formatGoalHours(goalSeconds)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(goalProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(goalProgress.coerceAtLeast(0.002f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

private val HeroHeaderRowHeight = 26.dp

private fun formatDeltaCompact(seconds: Long): String {
    if (seconds < 3600L) return "${seconds / 60}m"
    return String.format(Locale.US, "%.1fh", seconds / 3600f)
}

private fun formatGoalHours(seconds: Long): String =
    String.format(Locale.US, "%.1fh", seconds / 3600f)
