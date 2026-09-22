package com.example.yanji.ui.note

import com.example.yanji.ui.icons.RemixIcons
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yanji.R
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 滑开侧。NONE = 已收起。 */
private enum class Reveal { NONE, FAVORITE, DELETE }

/** 吸附动画：接近无回弹的平滑收敛，可被下一次手势随时打断。 */
private val RowSettleSpec = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 1f
)

/**
 * 松手吸附决策（对齐 SwipeDelMenuLayout 的 settleOpenOrClose）：
 *  - 甩动速度超过系统最小 fling 速度时，位移越过 1/4 宽度即按甩动方向吸附；
 *  - 否则按 1/2 宽度阈值吸附。
 */
private fun decideSnap(
    offsetPx: Float,
    velocity: Float,
    actionWidthPx: Float,
    flingVelocityPx: Float
): Reveal = when {
    velocity > flingVelocityPx && offsetPx > actionWidthPx * 0.25f -> Reveal.FAVORITE
    velocity < -flingVelocityPx && offsetPx < -actionWidthPx * 0.25f -> Reveal.DELETE
    offsetPx > actionWidthPx * 0.5f -> Reveal.FAVORITE
    offsetPx < -actionWidthPx * 0.5f -> Reveal.DELETE
    else -> Reveal.NONE
}

/** 越过操作块宽度后的橡皮筋阻尼：超出部分按 0.35 系数衰减，最多再露 20%。 */
private fun applyRubberBand(value: Float, limit: Float, max: Float): Float = when {
    value > limit -> limit + (value - limit) * 0.35f
    value < -limit -> -limit + (value + limit) * 0.35f
    else -> value
}.coerceIn(-max, max)

/**
 * 历史页的单条随笔行，支持**左右滑动露出固定宽度的操作块**。
 * 交互对齐 SwipeDelMenuLayout（mcxtzhang）的经典手势：
 *  - **同时只有一行**保持滑开：拖动新行会抢占并收起其它行（宿主持有 openRowId）。
 *  - 松手时结合**甩动速度**与位移决定吸附：快滑过 1/4 宽度即吸附，慢拖需过半。
 *  - 拖动越过操作块宽度后进入**橡皮筋阻尼**，再拖有渐进阻力而非死限位。
 *  - 滑开后点击内容区 = 收起（iOS 习惯）；点击操作块才触发动作，触发后自动收起。
 *  - 列表滚动、点击页面空白处同样会收起滑开行。
 *
 * 样式：操作块是**直角方形**，且与整行等高、紧贴边缘 —— 不是浮在行内的圆角胶囊。
 * 方块之外的区域仍是页面底色，条目内容整体平移让位。
 */
@Composable
fun NoteSwipeableRow(
    entry: NoteEntry,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val flingVelocityPx = ViewConfiguration.get(LocalView.current.context).scaledMinimumFlingVelocity.toFloat()

    // 操作块的固定宽度：滑开后只有这么宽的区域显示图标与底色。
    val actionWidth = 72.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    // 橡皮筋上限：越过操作块后再拖，最多再露出 20% 且带阻尼。
    val overDragMaxPx = actionWidthPx * 1.2f

    // 本地露出侧真相；「是否滑开」由宿主全局裁决，保证同时只有一行可滑开。
    var side by remember { mutableStateOf(Reveal.NONE) }
    var dragging by remember { mutableStateOf(false) }
    // 唯一的位移驱动：拖动时 snapTo、吸附时 animateTo。
    // 吸附起点恒等于松手瞬间的手指位置，避免 animateDpAsState 从旧值补间造成的跳变。
    val offsetAnim = remember { Animatable(0f) }

    /** 收敛到目标露出侧，并同步宿主持有的「当前滑开行」。 */
    fun settleTo(target: Reveal) {
        side = target
        onOpenChange(target != Reveal.NONE)
        scope.launch {
            offsetAnim.animateTo(
                targetValue = when (target) {
                    Reveal.FAVORITE -> actionWidthPx
                    Reveal.DELETE -> -actionWidthPx
                    Reveal.NONE -> 0f
                },
                animationSpec = RowSettleSpec
            )
        }
    }

    // 宿主状态变化（别的行抢占、滚动/点空白收起、状态恢复）时，本地动画跟随归位。
    LaunchedEffect(isOpen) {
        if (dragging) return@LaunchedEffect
        settleTo(if (isOpen) side else Reveal.NONE)
    }

    val offsetPx = offsetAnim.value
    // 露出进度：操作块图标随滑开距离淡入。
    val revealProgress = (abs(offsetPx) / actionWidthPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // ---- 操作块行：matchParentSize 取整行尺寸，两个 72dp 方块分居左右边缘 ----
        // 注意：不能把 matchParentSize 直接加在方块上——它会把子节点约束固定为整行尺寸，
        // 后面的 .width() 会被 coerce 成整行宽，导致两块重叠、后声明的红块盖住蓝块。
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：收藏块，主题色底 + 书签图标
            SquareActionPanel(
                width = actionWidth,
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                icon = RemixIcons.BookmarkFill,
                contentDescription = if (entry.isFavorite) "取消收藏" else "收藏",
                revealProgress = revealProgress,
                onClick = {
                    onToggleFavorite()
                    settleTo(Reveal.NONE)
                }
            )

            // 右：删除块，错误色底 + 删除图标
            SquareActionPanel(
                width = actionWidth,
                container = MaterialTheme.colorScheme.error,
                tint = MaterialTheme.colorScheme.onError,
                icon = RemixIcons.DeleteBinLine,
                contentDescription = "删除",
                revealProgress = revealProgress,
                onClick = {
                    // 不自动确认：点击才进入删除确认流程。
                    onDelete()
                    settleTo(Reveal.NONE)
                }
            )
        }

        // ---- 行内容：整体平移，露出一侧固定宽度的操作块 ----
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(actionWidthPx, overDragMaxPx, flingVelocityPx) {
                    var estimatedVelocityX = 0f
                    var lastEventTime = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            estimatedVelocityX = 0f
                            lastEventTime = 0L
                            // 抢占：本行开始拖动即把「当前滑开行」据为己有，收起其它行。
                            onOpenChange(true)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            val now = change.uptimeMillis
                            if (lastEventTime > 0L) {
                                val dtMillis = (now - lastEventTime).coerceAtLeast(1L)
                                estimatedVelocityX = dragAmount / dtMillis * 1000f
                            }
                            lastEventTime = now
                            scope.launch {
                                offsetAnim.snapTo(
                                    applyRubberBand(
                                        value = offsetAnim.value + dragAmount,
                                        limit = actionWidthPx,
                                        max = overDragMaxPx
                                    )
                                )
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            settleTo(
                                decideSnap(
                                    offsetPx = offsetAnim.value,
                                    velocity = estimatedVelocityX,
                                    actionWidthPx = actionWidthPx,
                                    flingVelocityPx = flingVelocityPx
                                )
                            )
                        },
                        onDragCancel = {
                            dragging = false
                            settleTo(
                                decideSnap(
                                    offsetPx = offsetAnim.value,
                                    velocity = 0f,
                                    actionWidthPx = actionWidthPx,
                                    flingVelocityPx = flingVelocityPx
                                )
                            )
                        }
                    )
                }
        ) {
            NoteRowContent(
                entry = entry,
                onClick = onClick
            )
        }
    }
}

/**
 * **直角方形**操作块：占满整行高度、紧贴边缘，不使用圆角胶囊。
 * 只有点击它才会触发对应动作；图标随露出进度淡入。
 */
@Composable
private fun SquareActionPanel(
    width: Dp,
    container: Color,
    tint: Color,
    icon: ImageVector,
    contentDescription: String,
    revealProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { alpha = revealProgress }
        )
    }
}

/**
 * 条目正文，布局对齐聊天列表：
 *  - 左列：时间（无头像设置, 用时间占位）；收藏随笔在时间与内容之间显示书签符号
 *  - 中列：内容
 *  - 右列：状态打分（星级）
 */
@Composable
private fun NoteRowContent(
    entry: NoteEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // (a) 左：时间（无头像设置，用时间占位）
        Text(
            text = YanjiTime.formatTime(entry.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = YanjiColors.textTertiary,
            modifier = Modifier.width(48.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // (a.5) 收藏书签：只显示一个书签符号（不显示「已收藏」文字），
        // 加粗实心、主题色，位置在时间与随记内容之间。
        if (entry.isFavorite) {
            Icon(
                imageVector = RemixIcons.BookmarkFill,
                contentDescription = "已收藏",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // (b) 中：内容
        // 摘要走 stripNoteMarkdown：`**` / `##` / `———` 是样式标记，不该在列表里露出来
        // （旧卡片一直是剥过的，卡片→行重设计时把这一步漏掉了）。整篇只有标记时剥完为空，
        // 此时退回原文，避免该行看起来是空白。
        val snippet = remember(entry.content) { noteListSnippet(entry.content) }
        Column(modifier = Modifier.weight(1f)) {
            if (entry.content.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isDraft) {
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "草稿",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            } else {
                // 空稿（标题已从列表移除）：给个占位，避免整行看起来像空白。
                Text(
                    text = if (entry.isDraft) "（草稿）" else "（空）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiColors.textTertiary,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // (c) 右：状态打分（与编辑页同一套星形资源）
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(entry.moodScore.coerceIn(0, 5)) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = YanjiColors.warning,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/** 条目之间的分隔线（非卡片式列表的唯一边界）。 */
@Composable
fun NoteRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.8.dp,
        color = YanjiColors.separator
    )
}

/** 删除二次确认对话框。 */
@Composable
fun NoteDeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("删除", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text("删除这篇随笔？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "删除后无法恢复，请确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
