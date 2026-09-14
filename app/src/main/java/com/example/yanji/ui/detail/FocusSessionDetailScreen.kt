package com.example.yanji.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Trash
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDangerButton
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.components.YanjiPrimaryButton

@Composable
fun FocusSessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusSessionDetailViewModel = yanjiViewModel { container ->
        FocusSessionDetailViewModel(
            container.repository,
            container.statisticsRepository
        )
    }
) {
    val context = LocalContext.current
    val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val session = focusSessions.find { it.id == sessionId }

    var isEditingNote by rememberSaveable(sessionId) { mutableStateOf(false) }
    var noteInput by rememberSaveable(sessionId, session?.note) { mutableStateOf(session?.note ?: "") }
    var showDeleteConfirmDialog by rememberSaveable(sessionId) { mutableStateOf(false) }

    if (session == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("记录已删除或不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val subjectColor = remember(session.subjectId) {
        when (SubjectCatalog.categoryIdOf(session.subjectId)) {
            "math" -> SubjectMath
            "major" -> SubjectMajor
            "english" -> SubjectEnglish
            "politics" -> SubjectPolitics
            else -> SubjectOther
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Detail TopBar
        YanjiDetailTopBar(
            title = "专注详情",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding, vertical = 8.dp)
        ) {
            // Main Overview Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Standard
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(subjectColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = session.subjectName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = subjectColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = session.mode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = DurationFormatter.formatHoursMinutes(session.durationSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${DurationFormatter.formatDateChinese(session.startTime)} · ${DurationFormatter.formatTimeRange(session.startTime, session.endTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Details Info Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Compact
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    DetailRowItem(label = "专注状态", value = "已完成")
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(label = "计时模式", value = session.mode)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(
                        label = "暂停次数",
                        value = if (session.pauseCount > 0) "${session.pauseCount} 次" else "无暂停 (全程高度专注)"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(label = "记录 ID", value = session.id.take(8))
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Notes Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Compact
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "学习备注",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isEditingNote) {
                            TextButton(
                                onClick = { isEditingNote = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = PhosphorIcons.Regular.PencilSimple,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "修改",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isEditingNote) {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("记录本次专注的内容、章节或感悟") },
                            shape = RoundedCornerShape(YanjiRadius.InputRadius),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                noteInput = session.note
                                isEditingNote = false
                            }) {
                                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            YanjiPrimaryButton(
                                text = "保存",
                                onClick = {
                                    viewModel.updateSessionNote(session.id, noteInput)
                                    isEditingNote = false
                                    Toast.makeText(context, "备注已更新", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        Text(
                            text = session.note.ifBlank { "未填写备注" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (session.note.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Delete Button
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Trash,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "删除此条专注记录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Secondary confirmation for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    "确认删除记录？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "删除后该次专注时长将从今日及各科目统计中扣除，且无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                YanjiDangerButton(
                    text = "确认删除",
                    onClick = {
                        viewModel.deleteSession(session.id)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun DetailRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
