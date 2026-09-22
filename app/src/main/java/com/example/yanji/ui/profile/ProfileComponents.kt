package com.example.yanji.ui.profile

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.UserSettings
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AiAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiSegmentedControl
import java.util.Locale

@Composable
fun ProfileIdentityCard(settings: UserSettings, onClick: () -> Unit) {
    val examYear = settings.targetExamDate
        .substringBefore("-")
        .takeIf { it.length == 4 && it.all(Char::isDigit) }
    val details = buildList {
        settings.targetMajor.takeIf(String::isNotBlank)?.let(::add)
        settings.targetExamDate.takeIf(String::isNotBlank)?.let {
            add("初试 ${formatExamDateCompact(it)}")
        }
    }.joinToString(" · ").ifBlank { "未设置专业与初试日期" }

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
            AiAvatar(size = 56.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = examYear?.let { "$it 考研备战" } ?: "考研备战",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = settings.targetSchool.ifBlank { "设置目标院校" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (settings.targetSchool.isBlank()) YanjiColors.textTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = RemixIcons.Edit2Line,
                contentDescription = "编辑备考信息",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
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
        trailingContent?.invoke()
    }
}

@Composable
fun LocalDataStatus() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = RemixIcons.LockLine,
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
    YanjiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped,
        content = content
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
            ProfileSettingsIcon(icon = RemixIcons.MoonLine)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        YanjiSegmentedControl(
            items = YanjiThemeMode.entries,
            selectedIndex = YanjiThemeMode.entries.indexOf(selected).coerceAtLeast(0),
            onItemSelected = { onSelect(YanjiThemeMode.entries[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp),
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
