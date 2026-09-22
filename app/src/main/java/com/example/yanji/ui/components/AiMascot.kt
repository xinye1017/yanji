package com.example.yanji.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant

@Composable
fun MascotAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val mascot = currentMascotTheme()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = mascot.drawableRes),
            contentDescription = mascot.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun AiAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) = MascotAvatar(modifier = modifier, size = size)

@Composable
fun AiEncouragementBanner(
    message: String,
    modifier: Modifier = Modifier,
    subMessage: String? = null,
    onClick: (() -> Unit)? = null
) {
    val mascot = currentMascotTheme()

    val cardContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(YanjiRadius.ItemRadius))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = mascot.drawableRes),
                    contentDescription = mascot.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${mascot.name}说",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))  // token-exempt: 行内微标签（1dp 垂直内边距）端部几何，非产品组件圆角
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (onClick != null) "点击找${mascot.name}聊聊 ›" else "研迹陪伴",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                if (subMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (onClick != null) {
        YanjiCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            variant = YanjiCardVariant.Grouped,
            colors = CardDefaults.cardColors(
                containerColor = YanjiColors.surfaceBlue,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            content = cardContent
        )
    } else {
        YanjiCard(
            modifier = modifier.fillMaxWidth(),
            variant = YanjiCardVariant.Grouped,
            colors = CardDefaults.cardColors(
                containerColor = YanjiColors.surfaceBlue,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            content = cardContent
        )
    }
}
