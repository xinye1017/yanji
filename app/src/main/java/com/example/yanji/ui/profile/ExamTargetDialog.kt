package com.example.yanji.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*

@Composable
fun ExamTargetDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (UserSettings) -> Unit
) {
    var school by remember { mutableStateOf(settings.targetSchool) }
    var major by remember { mutableStateOf(settings.targetMajor) }
    var date by remember { mutableStateOf(settings.targetExamDate) }
    var goalH by remember { mutableFloatStateOf(settings.dailyGoalHours) }
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
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存设置", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = YanjiTextSecondary)
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
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("目标专业") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCard(
                    onClick = { showExamDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                                color = YanjiTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleMedium,
                                color = YanjiTextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "选择考研初试日期",
                            tint = YanjiPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("每日专注学习目标：${goalH.toInt()} 小时", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Slider(
                    value = goalH,
                    onValueChange = { goalH = it },
                    valueRange = 4f..16f,
                    steps = 11
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = YanjiSurface
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
