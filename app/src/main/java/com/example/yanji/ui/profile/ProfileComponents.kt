package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar
import java.util.Locale

@Composable
fun ProfileIdentityCard(settings: UserSettings) {
    val examYear = settings.targetExamDate
        .substringBefore("-")
        .takeIf { it.length == 4 && it.all(Char::isDigit) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp),
        shape = RoundedCornerShape(20.dp),
        color = YanjiSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JuanjuanAvatar(size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = examYear?.let { "$it 考研备战" } ?: "考研备战",
                    style = MaterialTheme.typography.headlineMedium,
                    color = YanjiTextPrimary
                )
                Text(
                    text = settings.targetSchool,
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = settings.targetMajor,
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiTextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PreparationOverviewCard(
    settings: UserSettings,
    daysRemaining: Int?,
    todayStudySeconds: Long
) {
    val safeDays = daysRemaining?.coerceAtLeast(0)
    val goalSeconds = (settings.dailyGoalHours * 3_600f).coerceAtLeast(1f)
    val progress = (todayStudySeconds / goalSeconds).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = YanjiSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (daysRemaining != null && daysRemaining < 0) {
                            "初试日期已到"
                        } else {
                            "距离初试"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiTextSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = safeDays?.toString() ?: "—",
                            fontSize = 48.sp,
                            lineHeight = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                        if (safeDays != null) {
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.titleMedium,
                                color = YanjiTextSecondary,
                                modifier = Modifier.padding(bottom = 7.dp)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatExamDateCompact(settings.targetExamDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = YanjiTextPrimary
                    )
                    Text(
                        text = "初试",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextTertiary
                    )
                }
            }

            HorizontalDivider(color = YanjiDivider, thickness = 0.8.dp)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiTextSecondary
                    )
                    Text(
                        text = "${formatStudyDuration(todayStudySeconds)} / ${formatGoalHours(settings.dailyGoalHours)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = YanjiTextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(YanjiPrimarySoft)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(YanjiPrimary)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = YanjiTextSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        when {
            actionLabel != null && onAction != null -> {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiPrimary
                    )
                }
            }
            trailingContent != null -> trailingContent()
        }
    }
}

@Composable
fun LocalDataStatus() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = YanjiTextTertiary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "数据仅存本地",
            style = MaterialTheme.typography.labelMedium,
            color = YanjiTextTertiary
        )
    }
}

@Composable
fun ProfileSettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = YanjiSurface,
        content = { Column(content = content) }
    )
}

@Composable
fun ProfileSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) YanjiTextSecondary else YanjiTextTertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) YanjiTextPrimary else YanjiTextTertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = YanjiTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = YanjiTextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

fun formatStudyDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatGoalHours(hours: Float): String =
    if (hours % 1f == 0f) "${hours.toInt()}h" else "${"%.1f".format(Locale.US, hours)}h"

fun formatExamDateCompact(value: String): String =
    if (Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) value.replace("-", ".") else value
