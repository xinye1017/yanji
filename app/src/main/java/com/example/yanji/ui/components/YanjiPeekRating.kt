package com.example.yanji.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.YanjiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 考研状态打分组件：参考 React Bits 的 <PeekRating /> 动效精心实现。
 *
 * 核心动效：
 *  - **滑动/触摸预览 (Hover/Drag Preview)**：手指在星星上滑动时，已划过的星星集体上浮（lift），
 *    当前触摸的星星微放大（magnify）。
 *  - **跟随气泡提示 (Peek Tip)**：顶部胶囊气泡跟随手指所在星星平滑滑动，展示等级标签（如「循序渐进」）。
 *  - **确认弹性爆破动效 (Pop Scale)**：手指抬起确认打分时，该选中的星星触发弹性弹簧缩放。
 *  - **自适应配色**：高亮星采用暖金黄，暗星采用静雅次级暗调，符合研迹 Midnight 主题规范。
 */
@Composable
fun YanjiPeekRating(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 5,
    labels: List<String> = listOf("状态欠佳", "勉强跟上", "循序渐进", "渐入佳境", "状态极佳"),
    activeColor: Color = YanjiColors.warning,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    tipColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tipTextColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 26.dp,
    lift: Dp = 6.dp,
    magnify: Float = 1.18f,
    popScale: Float = 1.35f,
    showTip: Boolean = true,
    allowClear: Boolean = false,
    readOnly: Boolean = false
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 当前手势预览所在索引 (0 until count) 或 null
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    // 气泡是否展示（预览中，或刚提交后的短暂保留态）
    var isTipVisible by remember { mutableStateOf(false) }

    // 记录整排星星的宽度与水平 slot
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    // 弹性 Pop 动效控制器（针对每个星星）
    val popAnimatables = remember(count) {
        List(count) { Animatable(1f) }
    }

    val currentEffectiveRating = hoverIndex?.let { it + 1 } ?: value

    // 气泡水平位移动画
    val targetTipSlot = hoverIndex ?: (if (value > 0) (value - 1).coerceIn(0, count - 1) else 0)
    val slotWidthPx = if (rowWidthPx > 0f) rowWidthPx / count else with(density) { (size + 6.dp).toPx() }
    val animatedTipOffsetXPx by animateFloatAsState(
        targetValue = slotWidthPx * (targetTipSlot + 0.5f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TipOffsetAnimation"
    )

    fun commit(selected: Int) {
        val next = if (allowClear && selected == value) 0 else selected
        onValueChange(next)
        if (next in 1..count) {
            coroutineScope.launch {
                val anim = popAnimatables[next - 1]
                anim.snapTo(popScale)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
        // 提交后让气泡短暂停留后淡出
        coroutineScope.launch {
            delay(500)
            if (hoverIndex == null) {
                isTipVisible = false
            }
        }
    }

    fun calculateIndex(xPx: Float): Int {
        if (rowWidthPx <= 0f) return 0
        val clampedX = xPx.coerceIn(0f, rowWidthPx - 1f)
        return ((clampedX / rowWidthPx) * count).toInt().coerceIn(0, count - 1)
    }

    val gestureModifier = if (!readOnly) {
        Modifier
            .pointerInput(count, rowWidthPx) {
                detectTapGestures(
                    onPress = { offset ->
                        val index = calculateIndex(offset.x)
                        hoverIndex = index
                        isTipVisible = true
                        tryAwaitRelease()
                        commit(index + 1)
                        hoverIndex = null
                    }
                )
            }
            .pointerInput(count, rowWidthPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        hoverIndex = calculateIndex(offset.x)
                        isTipVisible = true
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        hoverIndex = calculateIndex(change.position.x)
                        isTipVisible = true
                    },
                    onDragEnd = {
                        val current = hoverIndex
                        if (current != null) {
                            commit(current + 1)
                        }
                        hoverIndex = null
                    },
                    onDragCancel = {
                        hoverIndex = null
                        isTipVisible = false
                    }
                )
            }
    } else Modifier

    Column(
        modifier = modifier.wrapContentSize(),
        // Tip 容器最小宽度大于星星行本身；必须居中子项，否则星星会视觉左偏。
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showTip) {
            // 顶部跟随气泡 Tip。紧凑场景关闭提示时不再预留空高度。
            Box(
                modifier = Modifier
                    .width(with(density) { rowWidthPx.toDp() }.coerceAtLeast(130.dp))
                    .height(20.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isTipVisible,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(180))
                ) {
                    val labelText = labels.getOrNull(targetTipSlot) ?: "${targetTipSlot + 1} 星"
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (animatedTipOffsetXPx - with(density) { 32.dp.toPx() }).roundToInt(),
                                    y = 0
                                )
                            }
                            .shadow(elevation = 3.dp, shape = CircleShape)
                            .background(tipColor, shape = CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = labelText,
                            color = tipTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        // 星星行
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    rowWidthPx = coordinates.size.width.toFloat()
                }
                .then(gestureModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until count) {
                val isLit = i < currentEffectiveRating
                val isHovered = hoverIndex == i
                val isLifted = hoverIndex != null && i <= hoverIndex!!

                // 上浮动效
                val animatedLiftY by animateDpAsState(
                    targetValue = if (isLifted) -lift else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "StarLift_$i"
                )

                // 触摸放大动效
                val animatedMagnify by animateFloatAsState(
                    targetValue = if (isHovered) magnify else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "StarMagnify_$i"
                )

                val popScaleCurrent = popAnimatables[i].value
                val totalScale = animatedMagnify * popScaleCurrent

                Box(
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            translationY = animatedLiftY.toPx()
                            scaleX = totalScale
                            scaleY = totalScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "评星 ${i + 1}",
                        tint = if (isLit) activeColor else idleColor,
                        modifier = Modifier.size(size)
                    )
                }
            }
        }
    }
}
