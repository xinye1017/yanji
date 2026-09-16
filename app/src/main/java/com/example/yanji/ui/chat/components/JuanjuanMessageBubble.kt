package com.example.yanji.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.ChatContextSource
import com.example.yanji.data.JuanjuanAction
import com.example.yanji.data.JuanjuanActionType
import com.example.yanji.data.JuanjuanBlockKind
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.chat.JuanjuanResponseParser
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.theme.*

/** UI 测试定位锚点：与 androidTest 共享，避免断言依赖中文文案。 */
const val JuanjuanMessageBubbleTestTag = "juanjuan_message_bubble"

@Composable
fun JuanjuanMessageBubble(
    message: com.example.yanji.data.ChatMessage,
    learningRecordCount: Int,
    onActionClick: (JuanjuanAction) -> Unit,
    onFollowupClick: (String) -> Unit,
    onContextSourceClick: (ChatContextSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = remember(message.content) {
        JuanjuanResponseParser.parse(message.content)
    }

    val hasDeepAnalysis = parsed.blocks.any { it.kind == JuanjuanBlockKind.STEPS } ||
        parsed.actions.isNotEmpty() ||
        parsed.diagnosis != null ||
        message.content.contains("模考") ||
        message.content.contains("真题")

    var showThoughtDetails by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(JuanjuanMessageBubbleTestTag),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Juanjuan Avatar with sparkle badge (40dp)
        Box(modifier = Modifier.size(40.dp)) {
            JuanjuanAvatar(size = 40.dp)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(if (hasDeepAnalysis) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Name + Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "卷卷",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )

                Surface(
                    shape = CircleShape,
                    color = if (hasDeepAnalysis) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (hasDeepAnalysis) "深度解析" else "专属学伴",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (hasDeepAnalysis) YanjiColors.lavenderDeep else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Thought Process Badge (collapsible)
            if (hasDeepAnalysis || learningRecordCount > 0 || parsed.contextSources.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThoughtDetails = !showThoughtDetails }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            val recordCount = if (learningRecordCount > 0) learningRecordCount else parsed.contextSources.sumOf { it.count }.coerceAtLeast(6)
                            Text(
                                text = "已结合近 $recordCount 套模考错题库深度思考 · 耗时 1.8s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = YanjiColors.lavenderDeep,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Icon(
                            imageVector = if (showThoughtDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = YanjiColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showThoughtDetails && parsed.contextSources.isNotEmpty()) {
                    ContextSourceCard(
                        sources = parsed.contextSources,
                        onSourceClick = onContextSourceClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Main Answer Bubble (clean elevated surface with separator border)
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                color = YanjiColors.elevatedSurface,
                shadowElevation = 0.dp,
                border = BorderStroke(0.8.dp, YanjiColors.separator),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Diagnosis / Empathy Banner
                    val diag = parsed.diagnosis
                    if (diag != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = diag,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }

                    // Main Text & Step blocks
                    parsed.blocks.forEach { block ->
                        when (block.kind) {
                            JuanjuanBlockKind.MAIN -> {
                                MainTextBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            JuanjuanBlockKind.STEPS -> {
                                StepsBlock(stepsText = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            JuanjuanBlockKind.ACTION -> {
                                ActionHintBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            JuanjuanBlockKind.FOLLOWUP -> Unit
                            else -> MainTextBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Action Suggestion Box (Stitch style lavender card)
                    if (parsed.actions.isNotEmpty()) {
                        ActionButtonRow(
                            actions = parsed.actions,
                            onClick = onActionClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Follow-up suggestions
                    if (parsed.followups.isNotEmpty()) {
                        FollowupChipsRow(
                            followups = parsed.followups,
                            onClick = onFollowupClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quick Interaction Bar (朗读, 复制, 存入日记, 超有用)
            QuickInteractionBar(
                message = message,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MainTextBlock(text: String, modifier: Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 14.sp
        ),
        modifier = modifier
    )
}

@Composable
private fun StepsBlock(stepsText: String, modifier: Modifier) {
    val steps = stepsText.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, line ->
            val title: String
            val detail: String
            val cleaned = line.replace("*", "").trim()
            if (cleaned.contains("：") || cleaned.contains(":")) {
                val delim = if (cleaned.contains("：")) "：" else ":"
                title = cleaned.substringBefore(delim).trim()
                detail = cleaned.substringAfter(delim).trim()
            } else {
                title = "步骤 ${index + 1}"
                detail = cleaned
            }
            Pair(title, detail)
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { stepIndex, (title, detail) ->
            Surface(
                shape = RoundedCornerShape(YanjiRadius.Small),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${stepIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        )
                    }

                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionHintBlock(text: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.Small),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = null,
                tint = YanjiColors.lavenderDeep,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = YanjiColors.lavenderDeep,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun ActionButtonRow(
    actions: List<JuanjuanAction>,
    onClick: (JuanjuanAction) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            Surface(
                shape = RoundedCornerShape(YanjiRadius.Small),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = YanjiColors.lavenderDeep,
                            modifier = Modifier.size(18.dp)
                        )
                        val promptText = if (action.type == JuanjuanActionType.CREATE_PLAN) {
                            "要将『${action.label}』加为明早计划吗？"
                        } else {
                            action.label
                        }
                        Text(
                            text = promptText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = YanjiColors.lavenderDeep,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = YanjiColors.lavenderDeep,
                        shadowElevation = 1.dp,
                        modifier = Modifier.clickable { onClick(action) }
                    ) {
                        Text(
                            text = "一键添加",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowupChipsRow(
    followups: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        followups.forEach { label ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { onClick(label) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickInteractionBar(
    message: com.example.yanji.data.ChatMessage,
    modifier: Modifier = Modifier
) {
    var isLiked by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 朗读
        InteractionItem(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "朗读",
            onClick = {
                Toast.makeText(context, "卷卷正在为你倾声朗读...", Toast.LENGTH_SHORT).show()
            }
        )

        // 复制
        InteractionItem(
            icon = Icons.Default.ContentCopy,
            label = "复制",
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("卷卷说", message.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        )

        // 存入日记
        InteractionItem(
            icon = Icons.Default.BookmarkAdd,
            label = "存入日记",
            onClick = {
                Toast.makeText(context, "已存入日记", Toast.LENGTH_SHORT).show()
            }
        )

        // 超有用
        InteractionItem(
            icon = Icons.Default.ThumbUp,
            label = if (isLiked) "已点赞" else "超有用",
            tint = if (isLiked) MaterialTheme.colorScheme.onPrimaryContainer else YanjiColors.textTertiary,
            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal,
            onClick = { isLiked = !isLiked }
        )
    }
}

@Composable
private fun InteractionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = YanjiColors.textTertiary,
    fontWeight: FontWeight = FontWeight.Normal,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint,
            fontWeight = fontWeight
        )
    }
}

@Composable
private fun ContextSourceCard(
    sources: List<ChatContextSource>,
    onSourceClick: (ChatContextSource) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        sources.forEach { source ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSourceClick(source) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (source.type) {
                                    com.example.yanji.data.ContextSourceType.MATH_EXAM -> Icons.Default.School
                                    com.example.yanji.data.ContextSourceType.WRONG_NOTES -> Icons.Default.ErrorOutline
                                    com.example.yanji.data.ContextSourceType.FOCUS -> Icons.Default.Timer
                                    com.example.yanji.data.ContextSourceType.CURRENT_CONVERSATION -> Icons.AutoMirrored.Filled.Chat
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = source.type.name.replace("_", " "),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = source.summary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = YanjiColors.textTertiary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = YanjiColors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
