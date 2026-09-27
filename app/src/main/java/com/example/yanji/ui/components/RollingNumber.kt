package com.example.yanji.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
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
    // 弹簧不受系统「动画时长缩放」影响（Compose 只对有限时长动画乘这个系数），
    // 因此关闭动画的系统设置下逐位滚动仍会跑 —— 这里显式降级为瞬切。
    val reduceMotion = YanjiMotion.isReduceMotionEnabled()
    val digitSpring: FiniteAnimationSpec<IntOffset> = if (reduceMotion) {
        snap()
    } else {
        spring(dampingRatio = 0.78f, stiffness = 380f)
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            // 逐字符成节点会让读屏把「36小时」念成「3」「6」「小」「时」，
            // 所以整串作为语义单元的 contentDescription 在这里一次性给出。
            contentDescription = text
        },
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
