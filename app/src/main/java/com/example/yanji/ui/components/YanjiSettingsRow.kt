package com.example.yanji.ui.components

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiIsDarkTheme

/**
 * iOS-inspired SettingsRow (研迹标准设置与导航行组件)
 *
 * 遵循 Android 原生工程原则与 iOS HIG 视觉美学：
 * 1. 无障碍合规：确保最小触控高度 >= 48dp（默认 52dp），且通过 mergeDescendants 使得 TalkBack 将整行作为单个焦点朗读；
 * 2. 视觉层级：左侧支持圆角图标容器，右侧支持值文本、自定义尾部（如 Switch/Badge）与细 CaretRight 箭头；
 * 3. 缩进分割线：支持 iOS 典型的 Inset Separator（默认避让左侧图标与边距 60dp）；
 * 4. 触觉与水波纹：结合 Material3 官方边界受限 ripple 反馈与无障碍降级。
 */
@Composable
fun YanjiSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    showDivider: Boolean = false,
    dividerInset: Dp = if (icon != null) 60.dp else 16.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .minimumInteractiveComponentSize()
                .then(clickableModifier)
                .padding(horizontal = YanjiSpacing.CardPaddingCompact, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .background(if (enabled) iconContainerColor else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) iconTint else YanjiColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(YanjiSpacing.ItemGap))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else YanjiColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = if (showChevron || trailing != null) 6.dp else 0.dp)
                )
            }

            if (trailing != null) {
                trailing()
                if (showChevron) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            if (showChevron && onClick != null) {
                Icon(
                    imageVector = RemixIcons.ArrowRightSLine,
                    contentDescription = null,
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDivider) {
            val isDark = yanjiIsDarkTheme()
            HorizontalDivider(
                modifier = Modifier.padding(start = dividerInset),
                thickness = 0.6.dp,
                color = if (isDark) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
