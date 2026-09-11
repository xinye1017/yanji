package com.example.yanji.ui.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiTextSecondary
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AiThinkingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking-progress"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mini Juanjuan avatar indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(YanjiPrimary.copy(alpha = 0.15f))
        ) {
            // Sparkle indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(YanjiPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Animated dots
        Surface(
            shape = RoundedCornerShape(YanjiRadius.MessageRadius),
            color = YanjiSurface,
            shadowElevation = 1.dp,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 4.dp)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0..2).forEach { i ->
                    val delay = i * 150f
                    val progress = (((animated - delay) % 1200f) + 1200f) % 1200f / 1200f
                    val scale = 0.5f + 0.5f * sin(progress * 2 * PI).toFloat()
                    Box(
                        modifier = Modifier
                            .graphicsLayer { this.scaleX = scale; this.scaleY = scale }
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(YanjiPrimary.copy(alpha = 0.5f + 0.5f * scale))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "思考中…",
            style = MaterialTheme.typography.labelSmall.copy(
                color = YanjiTextSecondary,
                fontSize = 11.sp
            )
        )
    }
}