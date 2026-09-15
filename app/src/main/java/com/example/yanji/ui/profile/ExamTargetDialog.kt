package com.example.yanji.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    var school by remember { mutableStateOf(settings.targetSchool) }
    var major by remember { mutableStateOf(settings.targetMajor) }
    var date by remember { mutableStateOf(settings.targetExamDate) }
    var goalH by remember { mutableFloatStateOf(if (settings.dailyGoalHours in 0f..16f) settings.dailyGoalHours else 0f) }
    var showExamDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            targetSchool = school,
                            targetMajor = major,
                            targetExamDate = date,
                            dailyGoalHours = goalH
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("保存设置", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = { Text("考研目标设置", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("目标院校") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("目标专业") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCard(
                    onClick = { showExamDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "考研初试日期",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = date.ifBlank { "点击选择初试日期" },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (date.isBlank()) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "选择考研初试日期",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val goalText = if (goalH <= 0f) "未设置（滑动选择）" else "${goalH.toInt()} 小时"
                Text("每日专注学习目标：$goalText", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Slider(
                    value = goalH,
                    onValueChange = { goalH = it },
                    valueRange = 0f..16f,
                    steps = 15
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
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
