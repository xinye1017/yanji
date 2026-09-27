package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.rememberPressScale

/**
 * 研迹主要行动按钮（Primary Button）。
 * 主色容器底 + `onPrimary` 前景（亮色主题为白字，暗色主题为深墨字，两者均实测 ≥4.5:1）、
 * 圆角 12dp (YanjiRadius.ButtonRadius)，挂载 0.97x 触觉弹性缩放动效。
 */
@Composable
fun YanjiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberPressScale(interactionSource)
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .heightIn(min = 48.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        contentPadding = contentPadding
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 研迹次要操作按钮（Secondary Button）。
 * 浅容器底，文字为主色/品牌色，圆角 12dp (YanjiRadius.ButtonRadius)，挂载 0.97x 触觉弹性缩放动效。
 */
@Composable
fun YanjiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberPressScale(interactionSource)
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .heightIn(min = 48.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        // 次要按钮的描边是「可点的边界」，属 WCAG 1.4.11 须可辨识的组件边界 → 用控件边界专职色
        // （outlineVariant 在白卡上只有 1.13:1，视觉上等于没有边界）。
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = contentPadding
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 研迹警示/破坏性操作按钮（Danger Button）。
 * 危险色容器底，用于删除确认等场景，挂载 0.97x 触觉弹性缩放动效。
 */
@Composable
fun YanjiDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberPressScale(interactionSource)
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .heightIn(min = 48.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        contentPadding = contentPadding
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
