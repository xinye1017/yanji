package com.example.yanji.ui.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.icons.RemixIcons

@Composable
internal fun StudyPlanSection(
    subjects: List<Subject>,
    tasks: List<StudyTask>,
    onAdd: (Subject, String, Int) -> Unit,
    onToggle: (StudyTask) -> Unit,
    onDelete: (String) -> Unit,
    onStart: (StudyTask) -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "今日计划",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (tasks.isEmpty()) {
                    "先定一件今天要完成的事"
                } else {
                    "完成 ${tasks.count { it.isCompleted }} / ${tasks.size}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { showAddDialog = true }) {
            Text("添加")
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (tasks.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YanjiRadius.ItemRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Text(
                text = "添加一个学习目标，之后可以直接从这里开始专注。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { task ->
                YanjiCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = YanjiCardVariant.Grouped
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { onToggle(task) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (task.isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${task.subjectName} · ${task.plannedMinutes} 分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = YanjiColors.textTertiary
                            )
                        }
                        if (!task.isCompleted) {
                            TextButton(onClick = { onStart(task) }) {
                                Text("开始")
                            }
                        }
                        IconButton(onClick = { onDelete(task.id) }) {
                            Icon(
                                imageVector = RemixIcons.DeleteBinLine,
                                contentDescription = "删除计划",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        StudyTaskDialog(
            subjects = subjects,
            onDismiss = { showAddDialog = false },
            onConfirm = { subject, title, minutes ->
                onAdd(subject, title, minutes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun StudyTaskDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (Subject, String, Int) -> Unit
) {
    val selectableSubjects = remember(subjects) {
        subjects.filter { subject ->
            subject.enabled && (
                subject.parentId != null ||
                    subjects.none { it.enabled && it.parentId == subject.id }
                )
        }.sortedBy { it.sortOrder }
    }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedSubjectId by rememberSaveable(selectableSubjects) {
        mutableStateOf(selectableSubjects.firstOrNull()?.id.orEmpty())
    }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(45) }
    val selectedSubject = selectableSubjects.firstOrNull { it.id == selectedSubjectId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加今日计划",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    label = { Text("准备完成什么？") },
                    placeholder = { Text("例如：二次型 30 道题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.InputRadius)
                )

                Text(
                    text = "科目",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectableSubjects.forEach { subject ->
                        val selected = subject.id == selectedSubjectId
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .clickable { selectedSubjectId = subject.id },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = subject.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "预计时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(25, 45, 60, 90).forEach { minutes ->
                        val selected = selectedMinutes == minutes
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .clickable { selectedMinutes = minutes },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = "${minutes}m",
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedSubject?.let { onConfirm(it, title.trim(), selectedMinutes) }
                },
                enabled = title.isNotBlank() && selectedSubject != null
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun QuickPresetSection(
    presets: List<QuickStartPreset>,
    onStart: (QuickStartPreset) -> Unit,
    onDelete: (String) -> Unit
) {
    Text(
        text = "快捷专注",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.take(4).forEach { preset ->
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Grouped
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onStart(preset) }
                    ) {
                        Text(
                            text = preset.label.ifBlank { preset.subjectName },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = buildString {
                                append(preset.subLabel.ifBlank { preset.mode })
                                if (preset.note.isNotBlank()) {
                                    append(" · ")
                                    append(preset.note)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onStart(preset) }) {
                        Text("开始")
                    }
                    IconButton(onClick = { onDelete(preset.id) }) {
                        Icon(
                            imageVector = RemixIcons.DeleteBinLine,
                            contentDescription = "删除快捷专注",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
