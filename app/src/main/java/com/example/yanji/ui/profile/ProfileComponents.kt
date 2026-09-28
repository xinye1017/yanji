package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AiAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiSegmentedControl
import com.example.yanji.ui.icons.RemixIcons
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ProfileIdentityCard(settings: UserSettings, onClick: () -> Unit) {
    val targetDate = remember(settings.targetExamDate) {
        YanjiTime.parseIsoDate(settings.targetExamDate)
    }
    val hasTargetInfo = settings.targetSchool.isNotBlank() ||
        settings.targetMajor.isNotBlank() ||
        settings.targetExamDate.isNotBlank()
    val examYear = targetDate?.year?.toString()
    val daysRemaining = remember(targetDate) {
        targetDate?.let {
            ChronoUnit.DAYS.between(YanjiTime.today(), it).toInt()
        }
    }
    val examIsTodayOrUpcoming = daysRemaining != null && daysRemaining >= 0

    YanjiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiAvatar(
                size = 64.dp,
                shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = settings.targetSchool.ifBlank {
                            if (hasTargetInfo) "目标院校未设置" else "备考目标"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (settings.targetSchool.isNotBlank() || !hasTargetInfo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            YanjiColors.textTertiary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (examYear != null) {
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = examYear + "届",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = settings.targetMajor.ifBlank {
                        if (hasTargetInfo) "专业未设置" else "设置院校、专业与初试日期"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.targetMajor.isNotBlank() || !hasTargetInfo) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        YanjiColors.textTertiary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasTargetInfo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = RemixIcons.CalendarLine,
                            contentDescription = null,
                            tint = if (examIsTodayOrUpcoming) MaterialTheme.colorScheme.primary else YanjiColors.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        val dateText = when {
                            targetDate == null && settings.targetExamDate.isNotBlank() -> "初试日期待确认"
                            daysRemaining != null && daysRemaining > 0 -> "初试 ${formatExamDateCompact(settings.targetExamDate)} · 剩 $daysRemaining 天"
                            daysRemaining == 0 -> "今日初试 · 旗开得胜！"
                            daysRemaining != null && daysRemaining < 0 -> "初试已过 · ${formatExamDateCompact(settings.targetExamDate)}"
                            else -> "初试日期未设置"
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (examIsTodayOrUpcoming) MaterialTheme.colorScheme.primary else YanjiColors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RemixIcons.Edit2Line,
                        contentDescription = if (hasTargetInfo) "编辑备考信息" else "设置备考信息",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** 个人页「AI API 配置」入口与简洁状态。 */
@Composable
fun ProfileAiCard(
    settings: UserSettings,
    onClick: () -> Unit
) {
    val aiConfigured = settings.isAiConfigured

    YanjiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI API 配置",
                    style = YanjiTypography.body,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiColors.primaryLabel
                )

                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = if (aiConfigured) {
                        YanjiColors.success.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (aiConfigured) "已配置" else "未配置",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (aiConfigured) YanjiColors.success else YanjiColors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSectionHeader(
    title: String,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        trailingContent?.invoke()
    }
}

@Composable
fun LocalDataStatus() {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = RemixIcons.LockLine,
                contentDescription = null,
                tint = YanjiColors.textTertiary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "本地优先 · 安全无忧",
                style = MaterialTheme.typography.labelSmall,
                color = YanjiColors.textTertiary
            )
        }
    }
}

@Composable
fun ProfileSettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    YanjiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped,
        content = content
    )
}

/**
 * 组内统一分割线：起始位置与文字起始对齐（paddingStart = 64dp），结束位置与内容右边距对齐（paddingEnd = 16dp）。
 */
@Composable
fun ProfileSettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        // 走 rowDivider token，与设置页其它行分隔同值同档。
        // 原先的 separator.copy(alpha = 0.5f) 在暗色下等于白@2.5%，是与设置行分行的第三种强度。
        color = YanjiColors.rowDivider,
        thickness = 0.6.dp,
        modifier = modifier.padding(start = 64.dp, end = 16.dp)
    )
}

/**
 * 设置行统一的图标底座：34dp 圆角矩形 + 柔和主题底色 + 主色图标。
 */
@Composable
fun ProfileSettingsIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
            .background(if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else YanjiColors.fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else YanjiColors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ProfileSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileSettingsIcon(icon = icon, enabled = enabled)
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
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
        if (!trailingText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = YanjiTypography.subheadline,
                color = YanjiColors.secondaryLabel
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = RemixIcons.ArrowRightSLine,
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
            .defaultMinSize(minHeight = 52.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                // 交互视觉只交给 Switch 滑块：整行不再出现按压涟漪/阴影，
                // 但保留整行为触控与无障碍（TalkBack）目标。
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileSettingsIcon(icon = icon, enabled = enabled)
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
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
            onCheckedChange = null,
            enabled = enabled,
            // 无边框线开关：轨道四周不描边（Material3 未选中态默认带 outline），更干净。
            colors = SwitchDefaults.colors(
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedBorderColor = Color.Transparent,
                disabledUncheckedBorderColor = Color.Transparent
            )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileSettingsIcon(icon = RemixIcons.MoonLine)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "界面外观",
                style = YanjiTypography.body,
                fontWeight = FontWeight.Medium,
                color = YanjiColors.primaryLabel,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = selected.displayLabel(),
                style = YanjiTypography.subheadline,
                color = YanjiColors.secondaryLabel
            )
        }

        YanjiSegmentedControl(
            items = YanjiThemeMode.entries,
            selectedIndex = YanjiThemeMode.entries.indexOf(selected).coerceAtLeast(0),
            onItemSelected = { onSelect(YanjiThemeMode.entries[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            height = 36.dp,
            itemLabel = { it.displayLabel() },
            itemModifier = { mode, _ -> Modifier.testTag(ProfileThemeTags.option(mode)) }
        )
    }
}

fun formatGoalHours(hours: Float): String =
    if (hours <= 0f) "未设置"
    else if (hours % 1f == 0f) "${hours.toInt()}h" else "${"%.1f".format(Locale.US, hours)}h"

fun formatExamDateCompact(value: String): String =
    if (value.isBlank()) "未设置"
    else if (Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) value.replace("-", ".") else value
