package com.example.yanji.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DURATION_PRESETS = listOf(25, 45, 60, 90, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualFocusLogDialog(
    subjects: List<Subject>,
    initialSubjectId: String? = null,
    initialDateIso: String = YanjiTime.todayIso(),
    onDismissRequest: () -> Unit,
    onConfirm: (subjectId: String, subjectName: String, startTime: Long, endTime: Long, note: String) -> Unit
) {
    val topCategories = remember(subjects) {
        subjects.filter {
            it.parentId == null && it.enabled && it.id != "other" && it.name != "其他" && !it.name.contains("其他")
        }.sortedBy { it.sortOrder }
    }

    var selectedCategoryId by rememberSaveable(subjects) {
        val initialSub = subjects.firstOrNull { it.id == initialSubjectId }
        mutableStateOf(initialSub?.parentId ?: initialSub?.id ?: topCategories.firstOrNull()?.id ?: "math")
    }

    val currentSubcategories = remember(subjects, selectedCategoryId) {
        subjects.filter { it.parentId == selectedCategoryId && it.enabled }.sortedBy { it.sortOrder }
    }

    var selectedSubjectId by rememberSaveable(selectedCategoryId) {
        mutableStateOf(currentSubcategories.firstOrNull()?.id ?: selectedCategoryId)
    }

    val effectiveSubject = remember(subjects, selectedSubjectId, selectedCategoryId) {
        subjects.firstOrNull { it.id == selectedSubjectId }
            ?: subjects.firstOrNull { it.id == selectedCategoryId }
            ?: Subject("math_advanced", "高等数学", "#356AE6")
    }

    // 日期选择：今天 / 昨天 / 传入日期
    val today = remember { LocalDate.now() }
    val yesterday = remember { today.minusDays(1) }
    var selectedDate by rememberSaveable {
        val parsed = YanjiTime.parseIsoDate(initialDateIso) ?: today
        mutableStateOf(parsed)
    }

    // 时间与时长选择
    var durationMinutes by rememberSaveable { mutableIntStateOf(45) }
    val nowTime = remember { LocalTime.now() }
    var endHour by rememberSaveable { mutableIntStateOf(nowTime.hour) }
    var endMinute by rememberSaveable { mutableIntStateOf(nowTime.minute) }
    var noteText by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // 计算真实的 epochMs
    val zoneId = remember { ZoneId.systemDefault() }
    val endDateTime = remember(selectedDate, endHour, endMinute) {
        selectedDate.atTime(endHour, endMinute).atZone(zoneId).toInstant().toEpochMilli()
    }
    val startDateTime = remember(endDateTime, durationMinutes) {
        endDateTime - durationMinutes * 60 * 1000L
    }

    // 动态校验
    LaunchedEffect(startDateTime, endDateTime, durationMinutes) {
        errorMessage = FocusViewModel.validateManualFocusSession(startDateTime, endDateTime)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 440.dp)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶栏标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "补记专注记录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "将离线或未计时的学习投入补录至统计与成就",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = YanjiColors.separator, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // 1. 学科选择
                Text(
                    text = "学习科目",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 大类胶囊
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topCategories.forEach { category ->
                        val isCatSelected = category.id == selectedCategoryId
                        Surface(
                            onClick = {
                                selectedCategoryId = category.id
                                val children = subjects.filter { it.parentId == category.id && it.enabled }
                                selectedSubjectId = children.firstOrNull()?.id ?: category.id
                            },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (isCatSelected) MaterialTheme.colorScheme.primaryContainer else YanjiColors.elevatedSurface,
                            border = BorderStroke(
                                if (isCatSelected) 1.5.dp else 0.8.dp,
                                if (isCatSelected) MaterialTheme.colorScheme.primary else YanjiColors.separator
                            )
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 子类胶囊（若有）
                if (currentSubcategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentSubcategories.forEach { sub ->
                            val isSubSelected = sub.id == selectedSubjectId
                            Surface(
                                onClick = { selectedSubjectId = sub.id },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSubSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isSubSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSubSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSubSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 日期选择
                Text(
                    text = "学习日期",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateOptions = listOf(
                        "今天" to today,
                        "昨天" to yesterday
                    )
                    dateOptions.forEach { (label, dateVal) ->
                        val isDateSelected = selectedDate == dateVal
                        Surface(
                            onClick = { selectedDate = dateVal },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (isDateSelected) MaterialTheme.colorScheme.primaryContainer else YanjiColors.elevatedSurface,
                            border = BorderStroke(
                                if (isDateSelected) 1.5.dp else 0.8.dp,
                                if (isDateSelected) MaterialTheme.colorScheme.primary else YanjiColors.separator
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$label (${dateVal.format(DateTimeFormatter.ofPattern("M/d"))})",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isDateSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDateSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 时长预设
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "专注时长",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${durationMinutes} 分钟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DURATION_PRESETS.forEach { mins ->
                        val isPreset = durationMinutes == mins
                        Surface(
                            onClick = { durationMinutes = mins },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPreset) MaterialTheme.colorScheme.primary else YanjiColors.elevatedSurface,
                            border = BorderStroke(
                                0.8.dp,
                                if (isPreset) MaterialTheme.colorScheme.primary else YanjiColors.separator
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${mins}m",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isPreset) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPreset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 起止时间详情展示卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    color = YanjiColors.elevatedSurface,
                    border = BorderStroke(0.8.dp, YanjiColors.separator)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = YanjiColors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "时段：${YanjiTime.formatTime(startDateTime)} ~ ${YanjiTime.formatTime(endDateTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = DurationFormatter.formatHoursMinutes(durationMinutes * 60L),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 专注备注
                Text(
                    text = "学习心得 / 备注（可选）",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("例如：复习操作系统死锁检测、真题大题订正", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // 错误提示（若有时钟非法情况）
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            if (errorMessage == null) {
                                onConfirm(
                                    effectiveSubject.id,
                                    effectiveSubject.name,
                                    startDateTime,
                                    endDateTime,
                                    noteText.trim()
                                )
                            }
                        },
                        enabled = errorMessage == null,
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
