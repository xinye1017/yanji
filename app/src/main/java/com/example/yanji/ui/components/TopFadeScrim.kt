package com.example.yanji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiSpacing

/**
 * 顶部渐隐遮罩，供随笔编辑页使用。
 *
 * 采用多段平滑背景渐变（从 0.96 柔和过渡到完全透明），无硬边界、无底部分割线、无灰色块，
 * 让正文在向上滚动时自然羽化消融在背景中。
 */
@Composable
fun TopFadeScrim(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarHeight + height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bg.copy(alpha = 0.96f),
                        bg.copy(alpha = 0.85f),
                        bg.copy(alpha = 0.45f),
                        bg.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
    )
}
