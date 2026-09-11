package com.example.yanji.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.ChatContextSource
import com.example.yanji.data.JuanjuanAction
import com.example.yanji.data.JuanjuanResponse
import com.example.yanji.ui.chat.JuanjuanResponseParser
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.theme.YanjiLavender
import com.example.yanji.theme.YanjiLavenderSoft
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiPrimaryStrong
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiSurfaceBlue
import com.example.yanji.theme.YanjiSurfaceSoft
import com.example.yanji.theme.YanjiTextPrimary
import com.example.yanji.theme.YanjiTextSecondary
import com.example.yanji.theme.YanjiTextTertiary

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Juanjuan Avatar (no auto_awesome badge - weakens persona)
        Box(modifier = Modifier.size(40.dp)) {
            JuanjuanAvatar(size = 40.dp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Name + lightweight mode chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "卷卷",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        color = YanjiTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ChipRadius),
                    color = YanjiPrimarySoft.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "专属学伴",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            color = YanjiPrimaryStrong,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 5.dp,
                    topEnd = YanjiRadius.MessageRadius,
                    bottomStart = YanjiRadius.MessageRadius,
                    bottomEnd = YanjiRadius.MessageRadius
                ),
                color = YanjiSurfaceBlue,
                border = androidx.compose.foundation.BorderStroke(1.dp, YanjiPrimary.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (learningRecordCount > 0) {
                        StudyContextNote(recordCount = learningRecordCount)
                    }

                    if (parsed.contextSources.isNotEmpty()) {
                        ContextSourceCard(
                            sources = parsed.contextSources,
                            onSourceClick = onContextSourceClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (parsed.diagnosis != null || parsed.evidence != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsed.diagnosis?.let {
                                DiagnosisBlock(text = it, modifier = Modifier.fillMaxWidth())
                            }
                            parsed.evidence?.let {
                                EvidenceBlock(text = it, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    parsed.blocks.forEach { block ->
                        when (block.kind) {
                            com.example.yanji.data.JuanjuanBlockKind.MAIN -> {
                                MainTextBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            com.example.yanji.data.JuanjuanBlockKind.STEPS -> {
                                StepsBlock(stepsText = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            com.example.yanji.data.JuanjuanBlockKind.ACTION -> {
                                ActionHintBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                            }
                            com.example.yanji.data.JuanjuanBlockKind.FOLLOWUP -> Unit
                            else -> MainTextBlock(text = block.text, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (parsed.actions.isNotEmpty()) {
                        ActionButtonRow(
                            actions = parsed.actions,
                            onClick = onActionClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (parsed.followups.isNotEmpty()) {
                        FollowupChipsRow(
                            followups = parsed.followups,
                            onClick = onFollowupClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            QuickInteractionBar(message = message, onContextSourceClick = onContextSourceClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StudyContextNote(recordCount: Int) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        color = YanjiPrimarySoft,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = YanjiPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "本次回答已参考 $recordCount 项学习记录",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = YanjiPrimaryStrong,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun DiagnosisBlock(text: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        color = YanjiSurfaceSoft,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(YanjiPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalInformation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "卷卷判断：$text",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                    color = YanjiPrimaryStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EvidenceBlock(text: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        color = YanjiSurfaceSoft,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = YanjiPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Text(
                text = "原因：$text",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    color = YanjiTextSecondary,
                    lineHeight = 20.sp,
                    fontSize = 13.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MainTextBlock(text: String, modifier: Modifier) {
    if (text.isBlank()) return
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            text = androidx.compose.ui.text.AnnotatedString(text),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                color = YanjiTextPrimary,
                lineHeight = 22.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { stepIndex, (title, detail) ->
            Surface(
                shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
                color = YanjiSurfaceSoft
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(YanjiPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stepIndex + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = YanjiTextSecondary,
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
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        color = YanjiLavenderSoft,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
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
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = null,
                    tint = Color(0xFF5C4BC3),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF5C4BC3),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            Surface(
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                color = YanjiPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(action) }
                    .padding(vertical = 10.dp, horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        followups.forEach { label ->
            Surface(
                shape = RoundedCornerShape(YanjiRadius.ChipRadius),
                color = YanjiSurfaceSoft,
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clickable { onClick(label) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        color = YanjiTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickInteractionBar(
    message: com.example.yanji.data.ChatMessage,
    onContextSourceClick: (ChatContextSource) -> Unit,
    modifier: Modifier
) {
    var isLiked by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 朗读
        InteractionItem(
            icon = Icons.Default.VolumeUp,
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
            icon = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUp,
            label = if (isLiked) "已点赞" else "超有用",
            tint = if (isLiked) YanjiPrimary else YanjiTextTertiary,
            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal,
            onClick = { isLiked = !isLiked }
        )
    }
}

@Composable
private fun InteractionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = YanjiTextTertiary,
    fontWeight: androidx.compose.ui.text.font.FontWeight = FontWeight.Normal,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
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
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        color = YanjiSurfaceSoft,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = YanjiPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (expanded) "已参考你的 ${sources.sumOf { it.count }} 项学习记录（点击收起）" else "已参考你的 ${sources.sumOf { it.count }} 项学习记录（点击展开）",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            color = YanjiPrimaryStrong,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = YanjiTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.forEach { source ->
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                            color = YanjiSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSourceClick(source) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                                com.example.yanji.data.ContextSourceType.CURRENT_CONVERSATION -> Icons.Default.Chat
                                            },
                                            contentDescription = null,
                                            tint = YanjiTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = source.type.name.replace("_", " "),
                                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                                                color = YanjiTextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = source.summary,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                                            color = YanjiTextTertiary,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = YanjiTextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
