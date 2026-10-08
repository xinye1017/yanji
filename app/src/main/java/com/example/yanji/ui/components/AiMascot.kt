package com.example.yanji.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.*

@Composable
fun MascotAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape
) {
    val mascot = currentMascotTheme()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = mascot.drawableRes),
            contentDescription = mascot.name,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.15f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun AiAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape
) = MascotAvatar(modifier = modifier, size = size, shape = shape)

/** 首页收尾反馈：使用普通文字，避免与核心统计争抢视觉重量。 */
@Composable
fun AiEncouragementBanner(
    message: String,
    modifier: Modifier = Modifier,
    subMessage: String? = null,
    onClick: (() -> Unit)? = null
) {
    val mascot = currentMascotTheme()
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = "查看学情分析",
            onClick = onClick
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = YanjiSpacing.CardPadding, vertical = YanjiSpacing.ItemGapSmall)
    ) {
        Text(
            text = "${mascot.name}说 · $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subMessage != null) {
            Spacer(modifier = Modifier.height(YanjiSpacing.TightGap))
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
