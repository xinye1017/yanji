package com.example.yanji.ui.note

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.yanji.theme.YanjiLiquidGlass
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.rememberPressScale
import com.example.yanji.ui.components.WarmTooltip
import com.example.yanji.ui.components.WarmTooltipGroup
import com.example.yanji.ui.icons.RemixIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 未点「完成」直接返回时的确认卡片：
 * 采用极简悬浮卡片设计，直接提示「是否将本次修改暂存」，
 * 左侧「放弃」（红底），右侧「保存」（主题色底）。
 */
@Composable
fun NoteDiscardOrDraftDialog(
    onSaveAsDraft: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "是否将本次修改暂存",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDiscard,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(NoteEditorTags.DiscardConfirmButton),
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            text = "放弃",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onSaveAsDraft,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(NoteEditorTags.DiscardDraftButton),
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "保存",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部 Markdown 格式工具栏。
 *
 * 具备 Markdown 核心快捷工具：
 * 标题、加粗、斜体、下划线、删除线、代码、引用、待办、列表、有序列表、分割线、链接。
 * 水平平滑滚动，适应各种屏幕宽度，随软键盘升降。
 */
@Composable
fun NoteFormatToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isHeading: Boolean,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    isStrike: Boolean,
    isCode: Boolean,
    isQuote: Boolean,
    isTask: Boolean,
    isBullet: Boolean,
    isNumbered: Boolean,
    isDivider: Boolean,
    isLink: Boolean,
    onHeading: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onCode: () -> Unit,
    onQuote: () -> Unit,
    onTask: () -> Unit,
    onList: () -> Unit,
    onNumberedList: () -> Unit,
    onDivider: () -> Unit,
    onLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        WarmTooltipGroup(delay = 400, warmWindow = 300, travel = 320) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(YanjiRadius.Small),
                color = surfaceColor,
                shadowElevation = 3.dp
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 撤回 / 反撤回按钮
                        FormatButton(
                            icon = RemixIcons.ArrowGoBackLine,
                            label = "撤回",
                            tag = NoteEditorTags.UndoButton,
                            enabled = canUndo,
                            onClick = onUndo
                        )
                        FormatButton(
                            icon = RemixIcons.ArrowGoForwardLine,
                            label = "反撤回",
                            tag = NoteEditorTags.RedoButton,
                            enabled = canRedo,
                            onClick = onRedo
                        )

                        // 细分隔线：区分历史撤销区与排版样式区
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )

                        // 先放学习随笔最常用的结构动作，再放低频排版能力。
                        FormatButton(
                            icon = RemixIcons.CheckboxLine,
                            activeIcon = RemixIcons.CheckboxFill,
                            label = "待办",
                            tag = NoteEditorTags.TaskButton,
                            active = isTask,
                            onClick = onTask
                        )
                        FormatButton(
                            icon = RemixIcons.ListUnordered,
                            label = "列表",
                            tag = NoteEditorTags.ListButton,
                            active = isBullet,
                            onClick = onList
                        )
                        FormatButton(
                            icon = RemixIcons.Bold,
                            label = "加粗",
                            tag = NoteEditorTags.BoldButton,
                            active = isBold,
                            onClick = onBold
                        )
                        FormatButton(
                            icon = RemixIcons.Heading,
                            label = "标题",
                            tag = NoteEditorTags.HeadingButton,
                            active = isHeading,
                            onClick = onHeading
                        )
                        FormatButton(
                            icon = RemixIcons.DoubleQuotesL,
                            label = "引用",
                            tag = NoteEditorTags.QuoteButton,
                            active = isQuote,
                            onClick = onQuote
                        )
                        FormatButton(
                            icon = RemixIcons.Italic,
                            label = "斜体",
                            tag = NoteEditorTags.ItalicButton,
                            active = isItalic,
                            onClick = onItalic
                        )
                        FormatButton(
                            icon = RemixIcons.ListOrdered,
                            label = "编号",
                            tag = NoteEditorTags.NumberedListButton,
                            active = isNumbered,
                            onClick = onNumberedList
                        )
                        FormatButton(
                            icon = RemixIcons.Underline,
                            label = "下划线",
                            tag = NoteEditorTags.UnderlineButton,
                            active = isUnderline,
                            onClick = onUnderline
                        )
                        FormatButton(
                            icon = RemixIcons.Strikethrough,
                            label = "删除线",
                            tag = NoteEditorTags.StrikeButton,
                            active = isStrike,
                            onClick = onStrike
                        )
                        FormatButton(
                            icon = RemixIcons.CodeLine,
                            label = "代码",
                            tag = NoteEditorTags.CodeButton,
                            active = isCode,
                            onClick = onCode
                        )
                        FormatButton(
                            icon = RemixIcons.Separator,
                            label = "分割线",
                            tag = NoteEditorTags.DividerButton,
                            active = isDivider,
                            onClick = onDivider
                        )
                        FormatButton(
                            icon = RemixIcons.Link,
                            label = "链接",
                            tag = NoteEditorTags.LinkButton,
                            active = isLink,
                            onClick = onLink
                        )
                    }

                    if (scrollState.canScrollBackward) {
                        ToolbarScrollHint(
                            icon = RemixIcons.ArrowLeftSLine,
                            modifier = Modifier.align(Alignment.CenterStart),
                            color = surfaceColor,
                            reverse = true
                        )
                    }
                    if (scrollState.canScrollForward) {
                        ToolbarScrollHint(
                            icon = RemixIcons.ArrowRightSLine,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = surfaceColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarScrollHint(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    reverse: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(26.dp)
            .background(
                Brush.horizontalGradient(
                    colors = if (reverse) {
                        listOf(color, color.copy(alpha = 0f))
                    } else {
                        listOf(color.copy(alpha = 0f), color)
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
    }
}

/** 格式栏中的单个纯图标工具项；激活时切换为粗描边图标与主题色，支持禁用态。 */
@Composable
private fun FormatButton(
    icon: ImageVector,
    activeIcon: ImageVector = icon,
    label: String,
    tag: String,
    active: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isActive = active == true
    val stateLabel = active?.let { if (it) "已开启" else "已关闭" }
    WarmTooltip(content = label) {
        Box(
            modifier = Modifier
                .testTag(tag)
                .size(48.dp)
                .clip(CircleShape)
                .semantics {
                    // 激活态此前只由换图标 + 着色表达，读屏取不到任何状态；48dp 槽位不改变 19dp 图标的可见尺寸。
                    stateLabel?.let { stateDescription = it }
                }
                .then(
                    if (enabled) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            val tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = if (isActive) activeIcon else icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * 随笔编辑页顶栏按钮：无边框半透明磨砂玻璃质感。
 * 挂接 [HazeState] 实时虚化底层滚动穿透的文字与内容，配备微噪点磨砂颗粒。
 * 采用安静克制的触控反馈：移除容易遮挡转场动效的墨水暗色涟漪（indication = null），
 * 改用基于 [rememberPressScale] 的微物理弹性缩放与微透明度变化，触控干脆清爽、动画丝滑不僵硬。
 */
@Composable
fun FrostedTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hazeState: HazeState? = null,
    tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = YanjiLiquidGlass
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberPressScale(interactionSource, targetScale = 0.92f)
    val pressAlpha = animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "frostedButtonPressAlpha"
    )

    val hazeModifier = if (hazeState != null && tokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = tokens.blurRadius
            tints = listOf(HazeTint(tintColor))
            noiseFactor = 0.08f
            backgroundColor = Color.Transparent
        }
    } else {
        Modifier.background(tintColor)
    }

    Box(
        // 外层 48dp 只做触控；玻璃圆仍在内层按 36dp 绘制，可见尺寸与位置不变。
        modifier = modifier
            .size(48.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                    alpha = pressAlpha.value
                }
                .size(36.dp)
                .clip(CircleShape)
                .then(hazeModifier),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
