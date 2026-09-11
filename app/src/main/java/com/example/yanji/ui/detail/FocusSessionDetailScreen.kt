package com.example.yanji.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DailySessionItem
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*

@Composable
fun FocusSessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repo: YanjiRepository = YanjiRepository.getInstance(),
    statsRepo: StudyStatisticsRepository = StudyStatisticsRepository.getInstance()
) {
    val context = LocalContext.current
    val focusSessions by repo.focusSessions.collectAsStateWithLifecycle()
    val session = focusSessions.find { it.id == sessionId }

    var isEditingNote by remember { mutableStateOf(false) }
    var noteInput by remember(session?.note) { mutableStateOf(session?.note ?: "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (session == null) {
        // Session might have just been deleted or invalid
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(YanjiBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("记录已删除或不存在", color = YanjiTextSecondary)
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
            .background(YanjiBackground)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = YanjiTextPrimary
                )
            }
            Text(
                text = "专注详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Main Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = subjectColor
                            )
                        }

                        Box(
                            modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = session.mode,
                                fontSize = 12.sp,
                                color = YanjiTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = DurationFormatter.formatHoursMinutes(session.durationSeconds),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${DurationFormatter.formatDateChinese(session.startTime)} · ${DurationFormatter.formatTimeRange(session.startTime, session.endTime)}",
                        fontSize = 13.sp,
                        color = YanjiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Details Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRowItem(label = "专注状态", value = "已完成")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(label = "计时模式", value = session.mode)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(
                        label = "暂停次数",
                        value = if (session.pauseCount > 0) "${session.pauseCount} 次" else "无暂停 (全程高度专注)"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(label = "记录 ID", value = session.id.take(8))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Notes Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "学习备注",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                        if (!isEditingNote) {
                            TextButton(
                                onClick = { isEditingNote = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "编辑",
                                    tint = YanjiPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("修改", fontSize = 12.sp, color = YanjiPrimary)
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
                            shape = RoundedCornerShape(12.dp),
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
                                Text("取消", color = YanjiTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    statsRepo.updateSessionNote(session.id, false, noteInput)
                                    isEditingNote = false
                                    Toast.makeText(context, "备注已更新", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("保存")
                            }
                        }
                    } else {
                        Text(
                            text = session.note.ifBlank { "未填写备注" },
                            fontSize = 13.sp,
                            color = if (session.note.isNotBlank()) YanjiTextPrimary else YanjiTextTertiary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Delete Button
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = YanjiError),
                border = androidx.compose.foundation.BorderStroke(1.dp, YanjiError.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除此条专注记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Secondary confirmation for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除记录？", fontWeight = FontWeight.Bold) },
            text = {
                Text("删除后该次专注时长将从今日及各科目统计中扣除，且无法撤销。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        statsRepo.deleteSession(
                            DailySessionItem(
                                id = session.id,
                                title = session.note,
                                subjectId = session.subjectId,
                                subjectName = session.subjectName,
                                subjectColor = "#356AE6",
                                startTime = session.startTime,
                                endTime = session.endTime,
                                durationSeconds = session.durationSeconds,
                                isExam = false
                            )
                        )
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确认删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = YanjiTextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = YanjiSurface
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
            fontSize = 13.sp,
            color = YanjiTextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = YanjiTextPrimary
        )
    }
}
