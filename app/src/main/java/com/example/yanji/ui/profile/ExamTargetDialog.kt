package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiFormFieldLabel
import com.example.yanji.ui.components.yanjiBorderlessTextFieldColors
import com.example.yanji.ui.icons.RemixIcons

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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YanjiFormFieldLabel("目标院校")
                    TextField(
                        value = school,
                        onValueChange = { school = it },
                        placeholder = { Text("填写目标院校") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "目标院校" },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        colors = yanjiBorderlessTextFieldColors(),
                        singleLine = true
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YanjiFormFieldLabel("目标专业")
                    TextField(
                        value = major,
                        onValueChange = { major = it },
                        placeholder = { Text("填写目标专业") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "目标专业" },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        colors = yanjiBorderlessTextFieldColors(),
                        singleLine = true
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YanjiFormFieldLabel("初试日期")
                    Surface(
                        onClick = { showExamDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        color = YanjiColors.inputFill
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.takeIf(String::isNotBlank)?.let(::formatExamDateCompact) ?: "选择日期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (date.isBlank()) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = RemixIcons.CalendarLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        YanjiFormFieldLabel("每日学习目标")
                        Text(
                            text = "0 小时可关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoalStepperButton(
                            description = "减少每日学习目标",
                            enabled = goalH > 0f,
                            symbol = "−",
                            onClick = { goalH = (goalH - 1f).coerceAtLeast(0f) }
                        )
                        Text(
                            text = if (goalH <= 0f) "未设置" else formatGoalHours(goalH),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 80.dp)
                        )
                        GoalStepperButton(
                            description = "增加每日学习目标",
                            enabled = goalH < 16f,
                            icon = RemixIcons.AddLine,
                            onClick = { goalH = (goalH + 1f).coerceAtMost(16f) }
                        )
                    }
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

@Composable
private fun GoalStepperButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    symbol: String? = null,
    icon: ImageVector? = null
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) YanjiColors.inputFill
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = symbol.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
