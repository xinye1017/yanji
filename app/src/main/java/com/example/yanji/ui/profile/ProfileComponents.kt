package com.example.yanji.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AiAvatar
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
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        color = YanjiColors.elevatedSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiAvatar(size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = examYear?.let { "$it 考研备战" } ?: "考研备战",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = settings.targetSchool.ifBlank { "点击设置目标院校" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (settings.targetSchool.isBlank()) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = settings.targetMajor.ifBlank { "未设置专业" },
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary,
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
    val goalSeconds = settings.dailyGoalHours * 3_600f
    val progress = if (goalSeconds > 0f) (todayStudySeconds / goalSeconds).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        color = YanjiColors.elevatedSurface
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (safeDays != null) {
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 7.dp)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatExamDateCompact(settings.targetExamDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "初试",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiColors.textTertiary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatStudyDuration(todayStudySeconds)} / ${formatGoalHours(settings.dailyGoalHours)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.primary
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
            tint = YanjiColors.textTertiary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "本地优先 · 可随系统迁移",
            style = MaterialTheme.typography.labelMedium,
            color = YanjiColors.textTertiary
        )
    }
}

@Composable
fun ProfileSettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        color = YanjiColors.elevatedSurface,
        content = { Column(content = content) }
    )
}

/**
 * 设置行统一的图标底座：32dp 圆角矩形 + 主题色底 + 主色图标。
 *
 * 抽成独立组件而不是在每个设置行里内联，是为了让「外观」这类自定义布局的行
 * 无法再漂移出不同的尺寸/底色/tint —— 只要用这个底座，视觉必然一致。
 */
@Composable
fun ProfileSettingsIcon(
    icon: ImageVector,
    enabled: Boolean = true,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ProfileSettingsIconSize)
            .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
            .background(YanjiColors.fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else YanjiColors.textTertiary,
            modifier = Modifier.size(ProfileSettingsGlyphSize)
        )
    }
}

private val ProfileSettingsIconSize = 32.dp
private val ProfileSettingsGlyphSize = 18.dp

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
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileSettingsIcon(icon = icon, enabled = enabled)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = YanjiTypography.body,
                fontWeight = FontWeight.Medium,
                color = if (enabled) YanjiColors.primaryLabel else YanjiColors.textTertiary
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = YanjiTypography.footnote,
                    color = YanjiColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = YanjiColors.tertiaryLabel,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ProfileSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileSettingsIcon(icon = icon, enabled = enabled)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = YanjiTypography.body,
                fontWeight = FontWeight.Medium,
                color = if (enabled) YanjiColors.primaryLabel else YanjiColors.textTertiary
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = YanjiTypography.footnote,
                    color = YanjiColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

// ---------------------------------------------------------------------------
// 外观（主题）切换
// ---------------------------------------------------------------------------

/** UI 测试定位锚点：与设置页的插桩测试共享，避免依赖中文文案。 */
object ProfileThemeTags {
    fun option(mode: YanjiThemeMode) = "profile_theme_" + mode.name.lowercase()
}

/** 主题模式的中文标签。 */
fun YanjiThemeMode.displayLabel(): String = when (this) {
    YanjiThemeMode.SYSTEM -> "跟随系统"
    YanjiThemeMode.LIGHT -> "浅色"
    YanjiThemeMode.DARK -> "深色"
}

/**
 * 外观选择器：跟随系统 / 浅色 / 深色 三档，一行点选、不弹窗。
 *
 * 用 [Modifier.selectable] 而不是 `clickable`：TalkBack 会朗读「已选中」，
 * 插桩测试也能直接用 `assertIsSelected()` 断言选中态，而不是比对颜色。
 */
@Composable
fun ProfileThemeSelector(
    selected: YanjiThemeMode,
    onSelect: (YanjiThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileSettingsIcon(icon = Icons.Outlined.DarkMode)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 与上方标题列左对齐：16dp 行内边距 + 32dp 图标底座 + 14dp 间距
                .padding(start = 62.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YanjiThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                val shape = RoundedCornerShape(YanjiRadius.ChipRadius)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ProfileThemeTags.option(mode))
                        .clip(shape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = shape
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(mode) }
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun formatStudyDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatGoalHours(hours: Float): String =
    if (hours <= 0f) "未设置"
    else if (hours % 1f == 0f) "${hours.toInt()}h" else "${"%.1f".format(Locale.US, hours)}h"

fun formatExamDateCompact(value: String): String =
    if (value.isBlank()) "未设置"
    else if (Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) value.replace("-", ".") else value
