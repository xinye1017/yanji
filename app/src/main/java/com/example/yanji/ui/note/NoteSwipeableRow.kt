package com.example.yanji.ui.note

import com.example.yanji.ui.icons.RemixIcons
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.R
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiIsDarkTheme
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.toShape
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.yanji.theme.YanjiMotion
import kotlinx.coroutines.launch

/** 滑开侧。NONE = 已收起。 */
private enum class Reveal { NONE, FAVORITE, DELETE }

/** 吸附动画：接近无回弹的平滑收敛，可被下一次手势随时打断。 */
@Composable
private fun rememberRowSettleSpec(): AnimationSpec<Float> = YanjiMotion.accessibleSpring(
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
 *  - **长按 = 滑动的单指针替代路径**：WCAG 2.2 要求作者自定义拖拽必须有按钮 / 菜单等价物，
 *    否则读屏与运动障碍用户完全够不到「收藏 / 删除」。长按弹出的菜单复用同一对回调。
 *
 * 样式：整行是一个**圆角矩形**，由两层搭出来 —— 外层 Box 只给 20dp 页边距，
 * 内层 Box 只给圆角裁剪，随笔卡片与左右两个操作块都活在这个圆角里：
 *  - 页边距与圆角**不能挂在同一个节点上**：Modifier.clip 按节点自身容积取圆角矩形，
 *    两者同链时圆角会被推到屏幕两角，操作块仍是直角，静止时从卡片的四个圆角里漏出
 *    蓝 / 粉色块，看起来像「卡片和整行圆角没接上」；
 *  - 静止时卡片严严实实盖住操作底色，四周不留一丝；
 *  - 滑开时露出的那一侧由**卡片自己的圆角**描边：卡片的圆角弧相对外框往里收 16dp，
 *    所以操作底色必须铺满半行、不能只画 72dp 竖条 —— 只画竖条时竖条贴卡片那一侧仍是
 *    一条直角边，而且卡片圆角与竖条之间会漏出一小条页面底色（行顶 / 行底各 16dp 宽、
 *    行中也有约 2dp）。铺满半行后「卡片 ∪ 操作底色」正好等于整行的圆角矩形，
 *    接缝处既没有直角也没有空隙；
 *  - 两半底色的接缝落在半行处，永远被卡片盖住：最大露出 = 72dp 操作块再加 20% 橡皮筋
 *    阻尼 = 86.4dp，加上卡片 16dp 圆角也只到 102.4dp，够不到半行。
 * 卡片之外是页面底色，卡片整体平移让位。
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

    // 长按菜单展开态。与滑开态分开：滑开露出的是「行内快捷块」，长按是「行级操作表」。
    var menuOpen by remember { mutableStateOf(false) }
    // 唯一的位移驱动：拖动时 snapTo、吸附时 animateTo。
    // 吸附起点恒等于松手瞬间的手指位置，避免 animateDpAsState 从旧值补间造成的跳变。
    val offsetAnim = remember { Animatable(0f) }
    val rowSettleSpec = rememberRowSettleSpec()

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
                animationSpec = rowSettleSpec
            )
        }
    }

    // 宿主状态变化（别的行抢占、滚动/点空白收起、状态恢复）时，本地动画跟随归位。
    LaunchedEffect(isOpen) {
        // 手动滑开任意一侧时收起长按菜单：两个浮层同时挂着会互相遮挡。
        if (isOpen) menuOpen = false
        if (dragging) return@LaunchedEffect
        settleTo(if (isOpen) side else Reveal.NONE)
    }

    // 露出进度：作为 State 传给操作块，只在图标的 graphicsLayer 里读。
    // 若在组合期读成 Float，拖动会逐帧重组整行、重排两个面板与正文。
    val revealProgress = remember(actionWidthPx) {
        derivedStateOf { (abs(offsetAnim.value) / actionWidthPx).coerceIn(0f, 1f) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 页边距只负责把整行从屏幕左右缘收进来，**不在这里给圆角**。
            // Modifier.clip 的裁剪按**节点自身容积**取圆角矩形：页边距与圆角挂进同一条
            // modifier 链时，圆角会被推到屏幕两角，行内的操作块仍是直角 —— 静止时两个方块
            // 就从卡片的四个圆角里漏出蓝 / 粉色块，看起来像「卡片和整行圆角没接上」。
            .padding(horizontal = YanjiSpacing.PageHorizontalPadding)
    ) {
        // ---- 整行圆角容器：卡片与两个操作块共用一个裁剪层 ----
        // 这一层不背页边距，自身容积正好是行矩形，圆角才落在行的四个角上。
        // 半径取卡片变体自己的形状，行容器与卡片永远是同一个圆角，不会各自漂移；
        // 滑开时卡片整体平移，越过这个圆角的部分也由它裁掉，不会探到页边距上。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(YanjiCardVariant.Compact.toShape())
        ) {
            // ---- 操作底色行：matchParentSize 取整行尺寸，左右各占半行 ----
            // 必须铺满半行，不能只画两条 72dp 竖条：卡片自己有 16dp 圆角，滑开时它的
            // 圆角弧相对卡片外框往里收 16dp。只画竖条的话，竖条贴卡片那一侧就是一条
            // 直角边，而且卡片圆角与竖条之间会漏出一小条页面底色。铺满半行后，露出
            // 区域的形状完全由卡片自己的圆角描出来，卡片与底色拼起来正好是整行的
            // 圆角矩形 —— 接缝处既没有直角也没有空隙。
            // 两半的接缝落在半行处，永远被卡片盖住，理由见函数上方的样式说明。
            // 注意：不能把 matchParentSize 直接加在底色块上——它会把子节点约束固定为
            // 整行尺寸，后面的 weight / width 会被 coerce 成整行宽，两块互相重叠覆盖。
            Row(
                modifier = Modifier.matchParentSize()
            ) {
                ActionRevealSide(
                    modifier = Modifier.weight(1f),
                    tapWidth = actionWidth,
                    alignment = Alignment.CenterStart,
                    // 底色：亮暗都用 `primary`。暗色下它被刻意升调成亮蓝（#4F7DF3），
                    // 滑开时是整块高饱和色块，识别度足够。
                    container = MaterialTheme.colorScheme.primary,
                    // 图标：暗色用 primaryLabel（柔白冷灰 #F0F4FC，Color.kt 的定义就是
                    // 「杜绝纯白 #FFF 的刺眼」），压亮蓝 3.78:1，过图标 3:1 线。
                    // 不用 onPrimary：主题按 M3「亮主色 + 暗前景」惯例把它压成 #0D111A 深墨，
                    // 深墨压在饱和亮蓝上视觉上就是一个「黑图标」，与预期完全相反。
                    // 亮色维持 onPrimary 纯白（白压 #356AE6 = 4.82:1）。
                    tint = if (yanjiIsDarkTheme()) YanjiColors.primaryLabel
                           else MaterialTheme.colorScheme.onPrimary,
                    icon = RemixIcons.BookmarkFill,
                    contentDescription = if (entry.isFavorite) "取消收藏" else "收藏",
                    revealProgress = revealProgress,
                    onClick = {
                        onToggleFavorite()
                        settleTo(Reveal.NONE)
                    }
                )

                // 右：删除底色，错误色底 + 删除图标
                ActionRevealSide(
                    modifier = Modifier.weight(1f),
                    tapWidth = actionWidth,
                    alignment = Alignment.CenterEnd,
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

            // ---- 行内容：圆角卡片整体平移，露出一侧固定宽度的操作块 ----
            YanjiCard(
                modifier = Modifier
                    .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                    .fillMaxWidth()
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
                    },
                    // 16dp 圆角 + colorScheme.surface 容器色：列表条目卡片档，与全站其它
                    // 卡片同一个基元、同一个底色（见函数上方 KDoc 的样式说明）。
                    variant = YanjiCardVariant.Compact
            ) {
                NoteRowContent(
                    entry = entry,
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
            }
        }

        // ---- 长按菜单：滑动的单指针替代路径（WCAG 2.2 AA）----
        // 复用滑动操作块背后的同一对 onToggleFavorite / onDelete，两条路径不会行为分叉。
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (entry.isFavorite) "取消收藏" else "收藏") },
                leadingIcon = { Icon(RemixIcons.BookmarkFill, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onToggleFavorite()
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(RemixIcons.DeleteBinLine, contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/**
 * 半行宽的操作底色：**底色铺满半行**，只有外沿 [tapWidth] 一条是可点的。
 *
 * 底色为什么不能只画 [tapWidth] 那么宽：卡片自己有 16dp 圆角，滑开时卡片的圆角弧
 * 相对卡片外框往里收 16dp。只画竖条的话，竖条贴卡片那一侧就是一条直角边，而且卡片
 * 圆角与竖条之间会漏出一小条页面底色。铺满半行后，露出区域的形状完全由卡片自己的
 * 圆角描出来 —— 卡片与底色拼起来正好是整行的圆角矩形。
 *
 * 可点区刻意收在 [tapWidth] 而不是铺满半行：触控目标仍是原来那条竖条，不会因为
 * 「铺满」变成一个横跨半行的按钮（读播报的节点边界也跟着变大）。
 * 只有点击它才会触发对应动作；图标随露出进度淡入。
 */
@Composable
private fun ActionRevealSide(
    alignment: Alignment,
    container: Color,
    tint: Color,
    icon: ImageVector,
    contentDescription: String,
    revealProgress: State<Float>,
    onClick: () -> Unit,
    tapWidth: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(container),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .width(tapWidth)
                .fillMaxHeight()
                .clickable(onClick = onClick)
                // 名称必须挂在**可点节点自己**身上：只写在 Icon 上时，clickable 仍是一个无名按钮，
                // TalkBack 会先念一个空「按钮」、再单独念一次图标名，焦点被拆成两跳。
                // mergeDescendants 把图标并进来，焦点落成「收藏 按钮」这一个节点。
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { alpha = revealProgress.value }
            )
        }
    }
}

/**
 * 条目正文，四列：时间 | 收藏书签 | 摘要 | 状态打分（星级）。
 *
 * 摘要的可用宽度**恒定**，与是否收藏、打了多少星都无关：
 * 左边界 = 时间列固定 40dp + 书签恒定槽 16dp + 两段 8dp 间隙；
 * 右边界 = 恒定画满的 5 星槽 + 12dp 间隙。
 * 此前书签不占位、星级只画点亮的，两处都会让摘要的边界随行状态浮动。
 *
 * 若沿用容器的 [Alignment.Top]，字框顶对齐会让首行基线互相错开约 3dp，
 * 肉眼表现为时间「浮」在摘要上方。
 *
 * 为什么不用 alignByFirstBaseline：Compose 1.12 的 RowScope 只剩 alignBy / alignByBaseline，
 * 而 alignByBaseline 对齐的是**末**行基线 —— 摘要折成两行时会把单行的时间拽到第二行去。
 * 这里改用「等高居中」达到同样的首行对齐：时间列高度取自 bodyMedium 的 lineHeight，
 * 内部垂直居中，Type.kt 改字号时自动跟随，不写死 dp。
 */
@Composable
private fun NoteRowContent(
    entry: NoteEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 时间列的「一行高」：直接取 bodyMedium 的 lineHeight（TextUnit→Dp 会带上当前 fontScale），
    // 不写死 22.dp —— Type.kt 调整字号体系时这一行自动跟随。
    val bodyLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp()
    }

    // 星级跟随系统 Dynamic Type 缩放：写成固定 dp 时，2× 字号下相邻正文撑开，
    // 星级却纹丝不动，评分会被文字淹没。用 sp 表达基准尺寸，toDp() 会带上当前 fontScale。
    val starSize = with(LocalDensity.current) { 13.sp.toDp() }

    // 心情分微标注：列表页原来只有一排星，用户无从判断 5 颗星说的是心情、精力还是
    // 复习状态。就近加一个 labelSmall 文本锚点，念法与语义树 contentDescription 一致。
    val moodScore = entry.moodScore.coerceIn(0, 5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 长按 = 滑动的单指针替代路径。onLongClickLabel 让读屏念「更多操作」而不是「长按」。
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "更多操作"
            )
            .padding(horizontal = YanjiSpacing.CardPaddingCompact, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 中列：时间 + 收藏书签（左）与草稿徽标 / 摘要（右）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            // (a) 左：时间
            // 用一层 heightIn(min = bodyLineHeight) + 垂直居中的包裹，让比正文小的
            // labelMedium 时间坐在摘要**首行**上（详见本函数上方关于首行对齐的说明）。
            // 宽度**不再固定 40dp**：那是以最长时间文案估的常数槽，短时刻整列吃白；
            // 改贴内容收缩，省下的纵深全部还给摘要（“每条只见半句话”的病灶之一）。
            Row(
                modifier = Modifier.heightIn(min = bodyLineHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = YanjiTime.formatTime(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))
            }

            // (a.5) 收藏书签：留在时间右侧，但占**恒定**的 16dp 槽位。
            // 未收藏时槽位空着而不是不画 —— 槽位恒定，摘要起始 x 就不随收藏状态跳动，
            // 同一屏里的左缘是一条直线；同时书签仍然贴着时间，视觉上仍属左侧。
            Box(
                modifier = Modifier.width(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (entry.isFavorite) {
                    Icon(
                        imageVector = RemixIcons.BookmarkFill,
                        contentDescription = "已收藏",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // (b) 内容
            // 摘要走 stripNoteMarkdown：`**` / `##` / `———` 是样式标记，不该在列表里露出来
            // （旧卡片一直是剥过的，卡片→行重设计时把这一步漏掉了）。整篇只有标记时剥完为空，
            // 此时退回原文，避免该行看起来是空白。
            val snippet = remember(entry.content) { noteListSnippet(entry.content) }
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
                if (entry.content.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    // 空稿（标题已从列表移除）：给个占位，避免整行看起来像空白。
                    // 左侧「草稿」徽标继续显示身份，这里只补「空」这个状态：
                    // 旧实现写的是「（草稿）」，和徽标连着念两遍「草稿」。
                    Text(
                        text = if (entry.isDraft) "（已空）" else "（空）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // (c) 右：状态打分 + 心情分微标注（星星与编辑页同一套资源）
        // 恒定画满 5 颗，未点亮的那几颗压到极低透明度当占位：
        // 之前只画点亮的星星，星级 0~5 会让右侧宽度在 0~63dp 之间浮动，
        // 摘要的**右缘**就跟着每一行的心情分变化。占位满格后左右留白恒定。
        // 星形本身是装饰（contentDescription = null）。
        //
        // 星下加一行「心情 N/5」：列表页原来只有一排星，用户无从判断它说的是
        // 心情、精力还是复习状态。字段真名是 moodScore，标注就照它念；
        // 文案与语义树 contentDescription 同源，读屏与可视不再各说各话。
        Column(
            modifier = Modifier.semantics {
                contentDescription = "心情评分 $moodScore / 5"
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lit = moodScore
                repeat(5) { index ->
                    if (index > 0) Spacer(modifier = Modifier.width(1.dp))
                    Icon(
                        painter = painterResource(R.drawable.star),
                        contentDescription = null,
                        tint = if (index < lit) {
                            YanjiColors.warning
                        } else {
                            YanjiColors.warning.copy(alpha = 0.16f)
                        },
                        modifier = Modifier.size(starSize)
                    )
                }
            }
            Text(
                text = "心情 $moodScore/5",
                style = MaterialTheme.typography.labelSmall,
                color = YanjiColors.textTertiary,
                maxLines = 1
            )
        }
    }
}

/**
 * 页眉规则线：只在「随笔」大标题下方画一条。
 *
 * 列表卡片化之后它不再承担分组边界 —— 每篇随笔的圆角卡片自己就是边界，
 * 组与组、同日的多篇之间都不再画线；跨日期只靠分组头和它上方的那段间距区分。
 *
 * 用 [YanjiColors.listSeparator]（结构分组线档），不再把 quaternaryLabel
 * 稀释到 50% 凑出 1.48:1：页眉没有卡片边界兜底，这条线本身就是留白分隔。
 * 与「内部分行线」[YanjiColors.rowDivider] 分属两档，亮暗两侧一一对应。
 */
@Composable
fun NoteRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.8.dp,
        color = YanjiColors.listSeparator
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
