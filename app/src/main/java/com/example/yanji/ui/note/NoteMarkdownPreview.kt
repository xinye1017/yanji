package com.example.yanji.ui.note

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.example.yanji.theme.YanjiColors
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * 随笔 Markdown 阅读预览。
 *
 * Markdown 解析和渲染交给 multiplatform-markdown-renderer，避免在应用内维护一套
 * 不完整的 CommonMark/GFM AST。编辑页只负责源码编辑、语法高亮和快捷格式操作。
 *
 * 预览直接使用最新 content，因此从编辑模式切换到预览时始终展示当前文本。
 */
@Composable
fun NoteMarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
    onToggleTask: (lineIndex: Int) -> Unit = {}
) {
    if (content.isBlank()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "随笔暂无内容，切换至「编辑」开始书写…",
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = YanjiColors.textTertiary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        return
    }

    val latestOnToggleTask = rememberUpdatedState(onToggleTask)
    val components = remember {
        markdownComponents(
            checkbox = { model ->
                val lineIndex = model.content
                    .take(model.node.startOffset.coerceIn(0, model.content.length))
                    .count { it == '\n' }

                MarkdownCheckBox(
                    content = model.content,
                    node = model.node,
                    style = model.typography.text,
                    checkedIndicator = { checked, _ ->
                        key(lineIndex) {
                            SpringTaskCheckbox(
                                checked = checked,
                                onToggle = { latestOnToggleTask.value(lineIndex) }
                            )
                        }
                    }
                )
            },
            paragraph = { model ->
                val completedTask = isCompletedTaskLineAtOffset(model.content, model.node.startOffset)
                val doneAlpha by animateFloatAsState(
                    targetValue = if (completedTask) 0.42f else 1f,
                    animationSpec = tween(durationMillis = 180),
                    label = "noteTaskDoneOpacity"
                )
                val style = if (completedTask) {
                    model.typography.paragraph.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = doneAlpha),
                        textDecoration = TextDecoration.LineThrough
                    )
                } else {
                    model.typography.paragraph
                }
                MarkdownParagraph(
                    content = model.content,
                    node = model.node,
                    style = style
                )
            }
        )
    }
    val markdownColors = markdownColor()
    val markdownType = markdownTypography()

    Markdown(
        content = content,
        colors = markdownColors,
        typography = markdownType,
        components = components,
        retainState = true,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * React Bits SpringCheck 的 Compose 版交互外观。
 *
 * 参数严格跟随 React Bits 配置：28dp、9dp、Bounce 0.2。
 * 暗色模式 Ink/Fill #ffffff、Check #0b0b0f；亮色模式 Ink/Fill #18181b、Check #ffffff。
 * 由同一 spring progress 驱动中心填充、勾线绘制与轻微 overshoot；按下时额外缩放到 0.95。
 */
@Composable
private fun SpringTaskCheckbox(
    checked: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            // React Bits bounce=0.2 对应阻尼比约 0.456。
            dampingRatio = 0.45595f,
            stiffness = 650f
        ),
        label = "noteSpringCheckProgress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(
            durationMillis = 160,
            easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        ),
        label = "noteSpringCheckPress"
    )

    val ink = MaterialTheme.colorScheme.onSurface
    val fill = ink
    val check = MaterialTheme.colorScheme.surface
    val held = progress.coerceIn(0f, 1f)
    val swell = 1f + 0.35f * max(0f, progress - 1f)

    Box(
        modifier = Modifier
            .width(28.dp)
            .height(24.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pressScale * swell
                    scaleY = pressScale * swell
                }
        ) {
            val ringWidth = 1.5.dp.toPx()
            val radius = 6.5.dp.toPx()

            drawRoundRect(
                color = ink.copy(alpha = 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Stroke(width = ringWidth)
            )

            if (held > 0f) {
                val fillWidth = size.width * held
                val fillHeight = size.height * held
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(
                        x = (size.width - fillWidth) / 2f,
                        y = (size.height - fillHeight) / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(fillWidth, fillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * held, radius * held)
                )
            }

            if (held > 0f) {
                val p1 = Offset(size.width * 0.27f, size.height * 0.52f)
                val p2 = Offset(size.width * 0.44f, size.height * 0.68f)
                val p3 = Offset(size.width * 0.76f, size.height * 0.34f)
                val tickWidth = 2.dp.toPx()
                val firstSegmentShare = 0.36f

                if (held <= firstSegmentShare) {
                    val local = held / firstSegmentShare
                    drawLine(
                        color = check,
                        start = p1,
                        end = Offset(
                            x = p1.x + (p2.x - p1.x) * local,
                            y = p1.y + (p2.y - p1.y) * local
                        ),
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = check,
                        start = p1,
                        end = p2,
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round
                    )
                    val local = (held - firstSegmentShare) / (1f - firstSegmentShare)
                    drawLine(
                        color = check,
                        start = p2,
                        end = Offset(
                            x = p2.x + (p3.x - p2.x) * local,
                            y = p2.y + (p3.y - p2.y) * local
                        ),
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private val CompletedTaskLinePrefix = Regex("""^\s*[-*+•]\s+\[[xX]\]\s+""")

private fun isCompletedTaskLineAtOffset(content: String, offset: Int): Boolean {
    if (content.isEmpty()) return false
    val (lineStart, lineEnd) = lineBoundsOf(content, offset)
    val line = content.substring(lineStart, lineEnd)
    return CompletedTaskLinePrefix.containsMatchIn(line)
}
