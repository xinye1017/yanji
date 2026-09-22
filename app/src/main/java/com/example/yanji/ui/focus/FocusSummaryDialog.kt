package com.example.yanji.ui.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yanji.data.FocusSession
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.ui.components.YanjiPrimaryButton
import kotlinx.coroutines.launch

/**
 * 专注完成弹窗卡片：
 * 1. 顶部流畅播放矢量打勾动画（弹性放大 + 轨迹绘制）；
 * 2. 标题清晰传达“专注记录已保存”；
 * 3. 极简卡片展示投入学科与时长，无多余产品 Logo 与装饰。
 */
@Composable
fun FocusSummaryDialog(
    session: FocusSession,
    todayFocusSeconds: Long,
    onDismiss: () -> Unit
) {
    val totalH = todayFocusSeconds / 3600
    val totalM = (todayFocusSeconds % 3600) / 60
    val sessionH = session.durationSeconds / 3600
    val sessionM = (session.durationSeconds % 3600) / 60
    val sessionS = session.durationSeconds % 60

    val durationText = when {
        sessionH > 0 -> "${sessionH}小时 ${sessionM}分钟 ${sessionS}秒"
        sessionM > 0 -> "${sessionM}分钟 ${sessionS}秒"
        else -> "${sessionS}秒"
    }

    val todaySummary = when {
        totalH > 0 -> "${totalH}小时 ${totalM}分钟"
        else -> "${totalM}分钟"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedCheckmark()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "专注记录已保存",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.subjectName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (todayFocusSeconds > 0) {
                            Text(
                                text = "今日已累计专注 $todaySummary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            YanjiPrimaryButton(
                text = "好的",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/** 动态打勾矢量动效 */
@Composable
private fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, delayMillis = 100, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .scale(scale.value)
            .size(64.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val strokeWidth = 3.5.dp.toPx()
            val startX = size.width * 0.22f
            val startY = size.height * 0.52f
            val midX = size.width * 0.44f
            val midY = size.height * 0.74f
            val endX = size.width * 0.82f
            val endY = size.height * 0.28f

            val path = Path().apply {
                moveTo(startX, startY)
                if (progress.value <= 0.4f) {
                    val p = progress.value / 0.4f
                    lineTo(startX + (midX - startX) * p, startY + (midY - startY) * p)
                } else {
                    lineTo(midX, midY)
                    val p = (progress.value - 0.4f) / 0.6f
                    lineTo(midX + (endX - midX) * p, midY + (endY - midY) * p)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
