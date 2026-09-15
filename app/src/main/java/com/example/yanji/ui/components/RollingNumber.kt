package com.example.yanji.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.yanji.theme.YanjiMotion

/**
 * 滚动数字组件 (RollingNumber)
 *
 * 将输入的数字字符串按字符拆解，针对每个数字字符应用平滑的上下弹性滑入滑出过渡（AnimatedContent），
 * 非数字字符（如 "h", "m", "天", ":" 等单位或符号）保持静止，避免布局跳动。
 */
@Composable
fun RollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val digitSpring = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = 0.78f,
        stiffness = 380f
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        for (char in text) {
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically(animationSpec = digitSpring) { -it } + fadeIn()) togetherWith
                                    (slideOutVertically(animationSpec = digitSpring) { it } + fadeOut())
                        } else {
                            (slideInVertically(animationSpec = digitSpring) { it } + fadeIn()) togetherWith
                                    (slideOutVertically(animationSpec = digitSpring) { -it } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "RollingDigit"
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        style = style,
                        color = color
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    style = style,
                    color = color
                )
            }
        }
    }
}
