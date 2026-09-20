package com.example.yanji.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.journal.JournalHeaderConfig
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.ui.components.YanjiPeekRating
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 天气预设选项 */
val WeatherPresets = listOf("☀️ 晴朗", "⛅ 多云", "☁️ 阴天", "🌧️ 阴雨", "❄️ 飘雪", "💨 微风")

/**
 * 随笔编辑页顶部元信息栏。
 *
 * 设计目标是把「今日状态 / 时间 / 天气」收敛成一条轻量信息带，
 * 不再堆叠多排 chip，让编辑页更接近专注、安静的写作界面。
 */
@Composable
fun JournalEditorHeaderBar(
    moodScore: Int,
    onMoodScoreChange: (Int) -> Unit,
    createdAt: Long,
    config: JournalHeaderConfig,
    weather: String,
    onWeatherChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWeatherPicker by remember { mutableStateOf(false) }

    val formattedTime = remember(createdAt) {
        val instant = Instant.ofEpochMilli(createdAt)
        val zone = ZoneId.systemDefault()
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(zone))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "状态",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            YanjiPeekRating(
                value = moodScore,
                onValueChange = onMoodScoreChange,
                size = 16.dp,
                lift = 2.dp,
                showTip = false
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 可编辑项用浅色底明确可点击；记录时间保持纯文本，避免假按钮。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (config.showTime) {
                HeaderMetaCell(
                    icon = Icons.Outlined.AccessTime,
                    value = "$formattedTime 记录"
                )
            }

            if (config.showWeather) {
                HeaderMetaCell(
                    icon = Icons.Outlined.WbSunny,
                    value = weather.displayPresetLabel().ifEmpty { "添加天气" },
                    placeholder = weather.isEmpty(),
                    onClick = { showWeatherPicker = true }
                )
            }
        }
    }

    if (showWeatherPicker) {
        HeaderMetaPickerSheet(
            title = "选择今日天气",
            options = WeatherPresets,
            selected = weather,
            onSelect = {
                onWeatherChange(it)
                showWeatherPicker = false
            },
            onDismiss = { showWeatherPicker = false }
        )
    }
}

@Composable
private fun HeaderMetaCell(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(YanjiRadius.RowRadius))
            .background(
                if (onClick != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (placeholder) YanjiColors.textTertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (placeholder) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.displayPresetLabel(): String = substringAfter(' ', this)

/** 轻量级选择底部浮层 / 对话框 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderMetaPickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(if (isSelected) "" else option) },
                        label = { Text(option) },
                        shape = RoundedCornerShape(YanjiRadius.Small),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
