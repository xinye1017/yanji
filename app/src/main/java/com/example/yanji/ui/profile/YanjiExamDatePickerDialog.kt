package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun YanjiExamDatePickerDialog(
    initialDateMillis: Long?,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val initialSelection = initialDateMillis ?: currentLocalDateAsUtcMillis()
    val initialDate = remember(initialSelection) {
        Instant.ofEpochMilli(initialSelection).atZone(ZoneOffset.UTC).toLocalDate()
    }
    var visibleYear by remember { mutableIntStateOf(initialDate.year) }
    var visibleMonth by remember { mutableIntStateOf(initialDate.monthValue - 1) }
    var selectedDateMillis by remember { mutableLongStateOf(initialSelection) }

    val visibleYearMonth = remember(visibleYear, visibleMonth) {
        YearMonth.of(visibleYear, visibleMonth + 1)
    }
    val leadingEmptyCells = remember(visibleYear, visibleMonth) {
        visibleYearMonth.atDay(1).dayOfWeek.value - 1
    }
    val daysInMonth = remember(visibleYear, visibleMonth) {
        visibleYearMonth.lengthOfMonth()
    }
    val weekRowCount = remember(leadingEmptyCells, daysInMonth) {
        ((leadingEmptyCells + daysInMonth + 6) / 7).coerceIn(5, 6)
    }
    val todayMillis = remember { currentLocalDateAsUtcMillis() }
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }

    fun moveMonth(delta: Int) {
        val target = YearMonth.of(visibleYear, visibleMonth + 1).plusMonths(delta.toLong())
        visibleYear = target.year
        visibleMonth = target.monthValue - 1
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择考研初试日期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatExamDateDisplayFromUtcMillis(selectedDateMillis),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${visibleYear} 年 ${visibleMonth + 1} 月",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { moveMonth(-1) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上个月",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { moveMonth(1) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下个月",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { weekday ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = weekday,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = YanjiColors.textTertiary
                            )
                        }
                    }
                }

                repeat(weekRowCount) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val cellIndex = row * 7 + column
                            val day = cellIndex - leadingEmptyCells + 1

                            if (day in 1..daysInMonth) {
                                val dayMillis = utcDateMillis(visibleYear, visibleMonth, day)
                                val isSelected = dayMillis == selectedDateMillis
                                val isToday = dayMillis == todayMillis

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(43.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .semantics {
                                                contentDescription = "${visibleYear}年${visibleMonth + 1}月${day}日"
                                                selected = isSelected
                                            }
                                            .clickable { selectedDateMillis = dayMillis },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || isToday) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(43.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDateSelected(selectedDateMillis) },
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("确定", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

fun parseExamDateToUtcMillis(value: String): Long? = runCatching {
    LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}.getOrNull()

fun formatExamDateFromUtcMillis(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun formatExamDateDisplayFromUtcMillis(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))

private fun currentLocalDateAsUtcMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDateMillis(year: Int, month: Int, day: Int): Long =
    LocalDate.of(year, month + 1, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
