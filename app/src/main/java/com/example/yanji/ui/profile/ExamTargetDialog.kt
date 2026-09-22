package com.example.yanji.ui.profile

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors

@Composable
fun ExamTargetDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (UserSettings) -> Unit
) {
    var school by rememberSaveable { mutableStateOf(settings.targetSchool) }
    var major by rememberSaveable { mutableStateOf(settings.targetMajor) }
    var date by rememberSaveable { mutableStateOf(settings.targetExamDate) }
    var goalH by rememberSaveable {
        mutableFloatStateOf(settings.dailyGoalHours.takeIf { it in 0f..16f } ?: 0f)
    }
    var showExamDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            targetSchool = school.trim(),
                            targetMajor = major.trim(),
                            targetExamDate = date,
                            dailyGoalHours = goalH
                        )
                    )
                },
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                text = "编辑备考信息",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("目标院校") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.InputRadius),
                    singleLine = true
                )
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("目标专业") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.InputRadius),
                    singleLine = true
                )
                Surface(
                    onClick = { showExamDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.InputRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "初试日期",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = date.takeIf(String::isNotBlank)?.let(::formatExamDateCompact) ?: "选择日期",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (date.isBlank()) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = RemixIcons.CalendarLine,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("每日学习目标", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (goalH <= 0f) "未设置" else formatGoalHours(goalH),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = goalH,
                        onValueChange = { goalH = it },
                        valueRange = 0f..16f,
                        steps = 15
                    )
                }
            }
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showExamDatePicker) {
        YanjiExamDatePickerDialog(
            initialDateMillis = parseExamDateToUtcMillis(date),
            onDismissRequest = { showExamDatePicker = false },
            onDateSelected = {
                date = formatExamDateFromUtcMillis(it)
                showExamDatePicker = false
            }
        )
    }
}
