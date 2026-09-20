package com.example.yanji.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.YanjiRadius
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 「一团暖意」提示条 —— 原生 Compose 版。
 *
 * 与普通 Tooltip 的差别在于**整组共用一个提示条**：
 * 指针在工具栏图标之间移动时，词条不消失再重弹，而是**滑过去**（位置/宽度一起过渡），
 * 视觉上是一块浮动的暖团，而不是一串各自为政的气泡。
 *
 * 交互契约：
 *  - 冷启动：悬停满 [WarmTooltipGroup] 的 `delay` 毫秒后才出现，避免扫过即弹。
 *  - 热区（warm window）：词条收起后的 `warmWindow` 毫秒内切到别的图标**立即**出现。
 *  - 触摸端：长按 [WarmTooltip] 的 `longPressMs` 毫秒后出现。
 *  - Reduce Motion：关闭滑动位移与缩放，只保留淡入淡出。
 *
 * 无障碍：触发器通过 `contentDescription` 暴露自身语义，供 TalkBack 朗读。
 * 词条是非交互浮层（`focusable = false`），不会抢焦点、不拦截点击。
 */

/** 组内共享状态：谁在亮、热区还剩多久、滑动时长。 */
private class WarmTooltipGroupState(
    val delayMs: Long,
    val warmWindowMs: Long,
    val travelMs: Int
) {
    /** 当前活跃触发器的 id；null 表示没有词条在场。 */
    var activeId by mutableStateOf<Any?>(null)

    /** 锚点（窗口坐标），词条据此定位。 */
    var anchor by mutableStateOf(Rect.Zero)

    /** 触发器向组登记自己的锚点。 */
    fun updateAnchor(id: Any, rect: Rect) {
        if (activeId === id) anchor = rect
        anchors[id] = rect
    }

    private val anchors = mutableMapOf<Any, Rect>()

    /** 最近一次收起的时间戳，用于热区判定。 */
    private var lastDismissAt by mutableLongStateOf(0L)

    /** 组内是否已经热了（有词条在场，或刚收起不久）。 */
    fun isWarm(): Boolean = activeId != null || System.currentTimeMillis() - lastDismissAt < warmWindowMs

    /** 延迟结束后点亮 [id]；热区内则由调用方直接点亮，不走这里。 */
    fun activate(id: Any) {
        val rect = anchors[id]
        if (rect != null) anchor = rect
        activeId = id
    }

    fun dismiss(id: Any) {
        // 只有当前活跃项才能收起，否则会误关刚刚滑过来的新词条。
        if (activeId !== id) return
        lastDismissAt = System.currentTimeMillis()
        activeId = null
    }
}

private val LocalWarmTooltipGroup = staticCompositionLocalOf<WarmTooltipGroupState?> { null }

/**
 * 工具栏容器：让组内所有 [WarmTooltip] 共享一个提示条与热区状态。
 *
 * @param delay 冷启动悬停延迟（毫秒）。
 * @param warmWindow 收起后热区保持时长（毫秒）。
 * @param travel 词条滑向下一个触发点的时长（毫秒）；0 表示不做滑动过渡。
 */
@Composable
fun WarmTooltipGroup(
    modifier: Modifier = Modifier,
    delay: Int = 400,
    warmWindow: Int = 300,
    travel: Int = 320,
    content: @Composable () -> Unit
) {
    val state = remember(delay, warmWindow, travel) {
        WarmTooltipGroupState(
            delayMs = delay.toLong().coerceAtLeast(0L),
            warmWindowMs = warmWindow.toLong().coerceAtLeast(0L),
            travelMs = travel.coerceAtLeast(0)
        )
    }
    CompositionLocalProvider(LocalWarmTooltipGroup provides state) {
        Box(modifier) { content() }
    }
}

/**
 * 组内单个触发器。
 *
 * @param content 词条文案，保持简短。
 * @param shortcut 可选快捷键（如 "⌘B"）；为 null 时不绘制键帽。
 * @param longPressMs 触摸端长按弹出所需时长（毫秒）。
 * @param children 触发控件本体。
 */
@Composable
fun WarmTooltip(
    content: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    longPressMs: Int = 500,
    children: @Composable () -> Unit
) {
    // 组外单独使用时退化为自带组，保证组件不会因为没有 Provider 而静默失效。
    val inherited = LocalWarmTooltipGroup.current
    val fallback = remember { WarmTooltipGroupState(400L, 300L, 320) }
    val group = inherited ?: fallback

    val reduceMotion = YanjiMotion.isReduceMotionEnabled()
    val id = remember { Any() }
    val isActive = group.activeId === id

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // 冷启动延迟在 Effect 内等待；热区内立即点亮。
    // 等待期间指针可能已经离开，所以醒来后必须复核 hovered。
    LaunchedEffect(hovered, group.activeId) {
        if (!hovered) return@LaunchedEffect
        if (group.isWarm()) {
            group.activate(id)
        } else {
            delay(group.delayMs)
            if (hovered) group.activate(id)
        }
    }

    LaunchedEffect(hovered) {
        if (!hovered) group.dismiss(id)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { group.updateAnchor(id, it.boundsInWindow()) }
            .hoverable(interaction)
            .pointerInput(id, longPressMs) {
                detectTapGestures(
                    onLongPress = { group.activate(id) }
                )
            }
            .semantics {
                contentDescription = if (shortcut != null) "$content，快捷键 $shortcut" else content
            }
    ) {
        children()
    }

    if (isActive) {
        WarmTooltipBubble(
            content = content,
            shortcut = shortcut,
            anchor = group.anchor,
            travelMs = group.travelMs,
            reduceMotion = reduceMotion
        )
    }
}

/**
 * 词条浮层本体。
 *
 * 定位交给 [PopupPositionProvider]：它拿到的是锚点矩形的**窗口坐标**。
 * 为了让词条在触发器之间「滑过去」而不是瞬移，这里先把锚点坐标动画化
 * （见 [animatedCenterX] 等），再用动画中的坐标去算弹窗位置。
 */
@Composable
private fun WarmTooltipBubble(
    content: String,
    shortcut: String?,
    anchor: Rect,
    travelMs: Int,
    reduceMotion: Boolean
) {
    if (anchor == Rect.Zero) return

    // 入场只在「从无到有」时播放一次；组内滑动时保持 1，避免每次都重弹。
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) pop.snapTo(1f)
        else pop.animateTo(1f, tween(durationMillis = 160))
    }

    // 「滑过去」的核心：把锚点位置做成可插值的状态。
    // Popup 的 PositionProvider 本身不会补间，如果直接把新 anchor 交给它，
    // 词条会在两帧之间瞬移 —— 那就退化成普通 Tooltip 了。
    // 因此先把锚点坐标动画化，再基于动画中的坐标计算弹窗位置。
    val glideSpec = remember(travelMs, reduceMotion) {
        if (reduceMotion || travelMs <= 0) tween(durationMillis = 0)
        else spring<Float>(dampingRatio = 0.85f, stiffness = 420f)
    }
    val animatedCenterX by animateFloatAsState(anchor.center.x, glideSpec, label = "ttCenterX")
    val animatedTop by animateFloatAsState(anchor.top, glideSpec, label = "ttTop")
    val animatedBottom by animateFloatAsState(anchor.bottom, glideSpec, label = "ttBottom")

    val provider = remember(animatedCenterX, animatedTop, animatedBottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // 默认挂在组件上方、水平居中；越界则翻到下方。
                val x = (animatedCenterX.roundToInt() - popupContentSize.width / 2)
                    .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                val above = animatedTop.roundToInt() - popupContentSize.height - 8
                val y = if (above < 8) animatedBottom.roundToInt() + 8 else above
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = true
        )
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val scale = if (reduceMotion) 1f else 0.94f + 0.06f * pop.value
                    scaleX = scale
                    scaleY = scale
                }
                .alpha(if (reduceMotion) 1f else pop.value)
                .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .widthIn(max = 240.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = content,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                if (shortcut != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))  // token-exempt: 键帽端部几何，小于最小 token YanjiRadius.ItemRadius(8dp)
                            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = shortcut,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}
