package com.example.yanji.ui.achievement

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import com.example.yanji.theme.currentMascotTheme
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiProgressBar
import com.example.yanji.ui.components.YanjiDetailTopBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.Achievement
import com.example.yanji.data.AchievementCategory
import com.example.yanji.data.AchievementRarity
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 成就行几何的唯一出处。
 *
 * 分割线此前写死 `padding(start = 69.dp)` + `outlineVariant.copy(alpha = 0.5f)`：
 * 69 与行内文字列实际起点（14 + 44 + 13 = 71）差 2dp，线永远对不齐标题左缘；
 * `copy(alpha)` 又把 `outlineVariant` 砍到半强度，在暗色下变成全站第三档线强度，
 * 与设置页 / 详情页的 `rowDivider` 对不上（两档分割线的分工见 Color.kt）。
 * 现在分割线的颜色、线宽与起止内缩全部由常量与主题 token 给出。
 */
private val AchievementRowPadding = 14.dp
private val AchievementBadgeSize = 44.dp
private val AchievementBadgeGap = 13.dp

/** 文字列左缘：分割线起点与标题左缘对齐，并按同一数值对称内缩，线不再顶到卡片右缘。 */
private val AchievementRowContentInset = AchievementRowPadding + AchievementBadgeSize + AchievementBadgeGap

/** 行内分割线线宽：与设置列表行（YanjiSettingsRow）取同一档。 */
private val AchievementDividerThickness = 0.6.dp

/** 「已完成」角标尺寸：行内徽章用小号，详情弹窗的大圆用大号。 */
private val AchievementCheckSize = 15.dp

fun getAchievementIcon(iconKey: String, isUnlocked: Boolean): ImageVector {
    return when (iconKey) {
        "book" -> if (isUnlocked) RemixIcons.BookOpenFill else RemixIcons.BookOpenLine
        "hourglass" -> if (isUnlocked) RemixIcons.HourglassFill else RemixIcons.HourglassLine
        "compass" -> if (isUnlocked) RemixIcons.CompassFill else RemixIcons.CompassLine
        "crown" -> if (isUnlocked) RemixIcons.VipCrownFill else RemixIcons.VipCrownLine
        "timer" -> if (isUnlocked) RemixIcons.TimerFill else RemixIcons.TimerLine
        "sunrise", "sun_horizon" -> if (isUnlocked) RemixIcons.SunFoggyFill else RemixIcons.SunFoggyLine
        "moon", "moon_stars" -> if (isUnlocked) RemixIcons.MoonFill else RemixIcons.MoonLine
        "award", "trophy" -> if (isUnlocked) RemixIcons.TrophyFill else RemixIcons.TrophyLine
        "shield" -> if (isUnlocked) RemixIcons.ShieldCheckFill else RemixIcons.ShieldCheckLine
        "medal" -> if (isUnlocked) RemixIcons.MedalFill else RemixIcons.MedalLine
        "pencil" -> if (isUnlocked) RemixIcons.Edit2Fill else RemixIcons.Edit2Line
        "feather" -> if (isUnlocked) RemixIcons.QuillPenFill else RemixIcons.QuillPenLine
        "check_circle" -> if (isUnlocked) RemixIcons.CheckboxCircleFill else RemixIcons.CheckboxCircleLine
        "fire", "flame" -> if (isUnlocked) RemixIcons.FireFill else RemixIcons.FireLine
        "diamond", "gem" -> if (isUnlocked) RemixIcons.DiamondFill else RemixIcons.DiamondLine
        "rocket", "rocket_launch" -> if (isUnlocked) RemixIcons.RocketFill else RemixIcons.RocketLine
        "target", "crosshair" -> if (isUnlocked) RemixIcons.Focus3Fill else RemixIcons.Focus3Line
        "clock" -> if (isUnlocked) RemixIcons.TimeFill else RemixIcons.TimeLine
        "drop" -> if (isUnlocked) RemixIcons.DropFill else RemixIcons.DropLine
        "lightning" -> if (isUnlocked) RemixIcons.FlashlightFill else RemixIcons.FlashlightLine
        "sparkle", "sparkles" -> if (isUnlocked) RemixIcons.SparklingFill else RemixIcons.SparklingLine
        "sun" -> if (isUnlocked) RemixIcons.SunFill else RemixIcons.SunLine
        "star", "shooting_star" -> if (isUnlocked) RemixIcons.StarFill else RemixIcons.StarLine
        "sword" -> if (isUnlocked) RemixIcons.SwordFill else RemixIcons.SwordLine
        "mountain", "mountains" -> if (isUnlocked) RemixIcons.LandscapeFill else RemixIcons.LandscapeLine
        "scales" -> if (isUnlocked) RemixIcons.ScalesFill else RemixIcons.ScalesLine
        "scroll" -> if (isUnlocked) RemixIcons.FilePaperFill else RemixIcons.FilePaperLine
        "heart" -> if (isUnlocked) RemixIcons.HeartFill else RemixIcons.HeartLine
        "coffee" -> if (isUnlocked) RemixIcons.CupFill else RemixIcons.CupLine
        "waves" -> if (isUnlocked) RemixIcons.SailboatFill else RemixIcons.SailboatLine
        "first_aid" -> if (isUnlocked) RemixIcons.FirstAidKitFill else RemixIcons.FirstAidKitLine
        "confetti" -> if (isUnlocked) RemixIcons.GiftFill else RemixIcons.GiftLine
        "footprints" -> if (isUnlocked) RemixIcons.FootprintFill else RemixIcons.FootprintLine
        "barbell" -> if (isUnlocked) RemixIcons.WeightFill else RemixIcons.WeightLine
        "brain" -> if (isUnlocked) RemixIcons.BrainFill else RemixIcons.BrainLine
        "notebook" -> if (isUnlocked) RemixIcons.BookletFill else RemixIcons.BookletLine
        "fire_extinguisher" -> if (isUnlocked) RemixIcons.AlarmWarningFill else RemixIcons.AlarmWarningLine
        "infinity" -> if (isUnlocked) RemixIcons.InfinityFill else RemixIcons.InfinityLine
        "eye_slash" -> if (isUnlocked) RemixIcons.EyeOffFill else RemixIcons.EyeOffLine
        else -> if (isUnlocked) RemixIcons.TrophyFill else RemixIcons.TrophyLine
    }
}

fun rarityColor(rarity: AchievementRarity): Color {
    return when (rarity) {
        AchievementRarity.COMMON -> AchievementCommon
        AchievementRarity.UNCOMMON -> AchievementUncommon
        AchievementRarity.RARE -> AchievementRare
        AchievementRarity.EPIC -> AchievementEpic
        AchievementRarity.LEGENDARY -> AchievementLegendary
        AchievementRarity.MYTHIC -> AchievementMythic
    }
}

fun rarityBackground(rarity: AchievementRarity): Color {
    return when (rarity) {
        AchievementRarity.COMMON -> AchievementCommonContainer
        AchievementRarity.UNCOMMON -> AchievementUncommonContainer
        AchievementRarity.RARE -> AchievementRareContainer
        AchievementRarity.EPIC -> AchievementEpicContainer
        AchievementRarity.LEGENDARY -> AchievementLegendaryContainer
        AchievementRarity.MYTHIC -> AchievementMythicContainer
    }
}

@Composable
fun rarityBorderBrush(rarity: AchievementRarity, isUnlocked: Boolean): Brush {
    return if (isUnlocked) {
        when (rarity) {
            AchievementRarity.MYTHIC -> Brush.linearGradient(
                colors = listOf(AchievementMythic, AchievementLegendary, AchievementEpic)
            )
            AchievementRarity.LEGENDARY -> Brush.linearGradient(
                colors = listOf(AchievementLegendary, AchievementLegendaryGradientEnd)
            )
            AchievementRarity.EPIC -> Brush.linearGradient(
                colors = listOf(AchievementEpic, AchievementEpicGradientEnd)
            )
            AchievementRarity.RARE -> Brush.linearGradient(
                colors = listOf(AchievementRare, AchievementRareGradientEnd)
            )
            AchievementRarity.UNCOMMON -> Brush.linearGradient(
                colors = listOf(AchievementUncommon, AchievementUncommonGradientEnd)
            )
            AchievementRarity.COMMON -> Brush.linearGradient(
                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.primaryContainer)
            )
        }
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = yanjiViewModel { container ->
        AchievementsViewModel(container.achievementRepository)
    }
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val unlockedCount by viewModel.unlockedCount.collectAsStateWithLifecycle()
    val totalCount = viewModel.totalCount
    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }
    var viewingAchievement by remember { mutableStateOf<Achievement?>(null) }

    val visibleAchievements = remember(achievements, selectedCategory) {
        selectedCategory?.let { category -> achievements.filter { it.category == category } } ?: achievements
    }
    val groups = remember(visibleAchievements) { visibleAchievements.groupBy { it.category } }
    val nextAchievement = remember(achievements) {
        val available = achievements.filter { !it.isUnlocked && !it.isHidden && it.targetProgress > 0L }
        available.filter { it.currentProgress > 0L }
            .maxByOrNull { it.currentProgress.toDouble() / it.targetProgress.toDouble() }
            ?: available.firstOrNull()
    }
    val progress = if (totalCount > 0) (unlockedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        YanjiDetailTopBar(title = "考研成就殿堂", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        ) {
            item {
                YanjiCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = YanjiCardVariant.Grouped,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$unlockedCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "/ $totalCount 枚已完成",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        YanjiProgressBar(
                            progress = progress,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        )
                        nextAchievement?.let { achievement ->
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { viewingAchievement = achievement }.padding(top = 14.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = RemixIcons.Focus3Line,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "下一枚 · ${achievement.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${achievement.currentProgress}/${achievement.targetProgress}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(RemixIcons.ArrowRightSLine, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf<AchievementCategory?>(null) + AchievementCategory.entries.filter { it != AchievementCategory.ALL }
                    categories.forEach { category ->
                        val selected = selectedCategory == category
                        val chipShape = RoundedCornerShape(YanjiRadius.Small)
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(chipShape)
                                .selectable(
                                    selected = selected,
                                    interactionSource = null,
                                    indication = ripple(),
                                    role = Role.RadioButton,
                                    onClick = { selectedCategory = category }
                                ),
                            shape = chipShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = category?.title ?: "全部",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            groups.forEach { (category, itemsInCategory) ->
                item(key = "header_${category.name}", contentType = "category_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${itemsInCategory.count { it.isUnlocked }} / ${itemsInCategory.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(
                    items = itemsInCategory,
                    key = { _, achievement -> achievement.id },
                    contentType = { _, _ -> "achievement_row" }
                ) { index, achievement ->
                    val isFirst = index == 0
                    val isLast = index == itemsInCategory.lastIndex
                    val shape = when {
                        isFirst && isLast -> RoundedCornerShape(YanjiRadius.GroupedCardRadius)
                        isFirst -> RoundedCornerShape(
                            topStart = YanjiRadius.GroupedCardRadius,
                            topEnd = YanjiRadius.GroupedCardRadius
                        )
                        isLast -> RoundedCornerShape(
                            bottomStart = YanjiRadius.GroupedCardRadius,
                            bottomEnd = YanjiRadius.GroupedCardRadius
                        )
                        else -> androidx.compose.ui.graphics.RectangleShape
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface)
                            .groupedCardBorder(
                                isFirst = isFirst,
                                isLast = isLast,
                                stroke = YanjiCardBorder.stroke(),
                                radius = YanjiRadius.GroupedCardRadius
                            )
                    ) {
                        AchievementListRow(
                            achievement = achievement,
                            onClick = { viewingAchievement = achievement }
                        )
                        if (!isLast) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = AchievementRowContentInset, end = AchievementRowContentInset),
                                thickness = AchievementDividerThickness,
                                color = YanjiColors.rowDivider
                            )
                        }
                    }
                }
            }
        }
    }

    viewingAchievement?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            allAchievements = achievements,
            onDismiss = { viewingAchievement = null }
        )
    }
}

/**
 * 「已完成」角标：叠在成就图标右上角的绿色勾。
 *
 * 刻意不加圆形底盘：暗色下 success token 是亮薄荷绿，白勾压上去只有 1.92:1，
 * 要救回来就得再造一个「onSuccess」墨色 token；而裸勾本身已有
 * 5.00:1（亮）/ 7.97:1（暗），零新增 token 就够清楚。
 */
@Composable
private fun AchievementDoneCheck(size: Dp, modifier: Modifier = Modifier) {
    Icon(
        imageVector = RemixIcons.CheckLine,
        contentDescription = null,
        tint = YanjiColors.success,
        modifier = modifier.size(size)
    )
}

@Composable
private fun AchievementListRow(achievement: Achievement, onClick: () -> Unit) {
    val isHiddenLocked = achievement.isHidden && !achievement.isUnlocked
    val accent = if (achievement.isUnlocked) rarityColor(achievement.rarity) else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (isHiddenLocked) RemixIcons.LockLine else getAchievementIcon(achievement.iconKey, achievement.isUnlocked)
    val progress = if (achievement.targetProgress > 0L) {
        (achievement.currentProgress.toFloat() / achievement.targetProgress).coerceIn(0f, 1f)
    } else 0f

    Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = AchievementRowPadding, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(AchievementBadgeSize)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        // 完成与未完成共用同一层中性灰底：状态差异交给右上角的绿色勾，
                        // 免得一行里同时出现稀有度染色与完成色两套色相。
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                }
                if (achievement.isUnlocked) {
                    AchievementDoneCheck(
                        size = AchievementCheckSize,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.width(AchievementBadgeGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isHiddenLocked) "隐藏成就" else achievement.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (achievement.isUnlocked) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        achievement.isUnlocked -> "已完成 · ${achievement.rarity.title}"
                        isHiddenLocked -> "达成后揭晓"
                        else -> "${achievement.currentProgress} / ${achievement.targetProgress} ${achievement.unit}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (achievement.isUnlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!achievement.isUnlocked && !isHiddenLocked && achievement.currentProgress > 0L) {
                    Spacer(Modifier.height(7.dp))
                    YanjiProgressBar(
                        progress = progress,
                        color = MaterialTheme.colorScheme.primary,
                        height = 6.dp
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(RemixIcons.ArrowRightSLine, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
}

@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    allAchievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val isHiddenLocked = achievement.isHidden && !achievement.isUnlocked
    val icon = if (isHiddenLocked) RemixIcons.LockLine else getAchievementIcon(achievement.iconKey, achievement.isUnlocked)
    val accent = if (achievement.isUnlocked) rarityColor(achievement.rarity) else MaterialTheme.colorScheme.onSurfaceVariant
    val unlockDate = remember(achievement.unlockedAt) {
        achievement.unlockedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
        }
    }
    val series = remember(achievement.seriesId, allAchievements) {
        achievement.seriesId?.let { id -> allAchievements.filter { it.seriesId == id }.sortedBy { it.seriesOrder } } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 620.dp)
                .clip(RoundedCornerShape(YanjiRadius.DialogRadius))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
                }
                if (achievement.isUnlocked) {
                    // 20dp 勾压在 64dp 圆的 45° 边缘上，中心基本落在圆周上，
                    // 于是「盖章」而不是「贴纸」；外层 Column 未裁剪，不会切角。
                    AchievementDoneCheck(size = 20.dp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isHiddenLocked) "隐藏成就" else achievement.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isHiddenLocked) achievement.category.title else "${achievement.category.title} · ${achievement.rarity.title}",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isHiddenLocked) "达成后揭晓" else achievement.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            when {
                achievement.isUnlocked -> Text(
                    text = unlockDate?.let { "完成于 $it" } ?: "已完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                isHiddenLocked -> Text("尚未解锁", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text(
                        text = "进度 ${achievement.currentProgress} / ${achievement.targetProgress} ${achievement.unit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    YanjiProgressBar(
                        progress = if (achievement.targetProgress > 0L) (achievement.currentProgress.toFloat() / achievement.targetProgress).coerceIn(0f, 1f) else 0f,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!isHiddenLocked && series.size > 1) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(thickness = AchievementDividerThickness, color = YanjiColors.rowDivider)
                Spacer(Modifier.height(14.dp))
                Text(
                    "成长阶梯 · ${series.count { it.isUnlocked }} / ${series.size} 已完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    series.forEach { milestone ->
                        Text(
                            "${milestone.targetProgress}${milestone.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (milestone.id == achievement.id) FontWeight.Bold else FontWeight.Normal,
                            color = if (milestone.isUnlocked) rarityColor(milestone.rarity) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (achievement.isUnlocked && achievement.rewardQuote.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(thickness = AchievementDividerThickness, color = YanjiColors.rowDivider)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "${currentMascotTheme().name} · ${achievement.rewardQuote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

/**
 * 为分组卡片的各个独立 Item 绘制连续的外轮廓描边。
 * 暗色模式下 stroke 为 null，零开销直接跳过绘制。
 */
private fun Modifier.groupedCardBorder(
    isFirst: Boolean,
    isLast: Boolean,
    stroke: BorderStroke?,
    radius: Dp
): Modifier = if (stroke == null) this else this.drawBehind {
    val strokePx = stroke.width.toPx()
    val halfStroke = strokePx / 2f
    val rPx = radius.toPx()
    val w = size.width
    val h = size.height

    if (isFirst && isLast) {
        drawRoundRect(
            brush = stroke.brush,
            topLeft = Offset(halfStroke, halfStroke),
            size = Size(w - strokePx, h - strokePx),
            cornerRadius = CornerRadius(rPx, rPx),
            style = Stroke(width = strokePx)
        )
    } else if (isFirst) {
        val path = Path().apply {
            moveTo(halfStroke, h)
            lineTo(halfStroke, rPx)
            arcTo(
                rect = Rect(halfStroke, halfStroke, 2 * rPx - halfStroke, 2 * rPx - halfStroke),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(w - rPx, halfStroke)
            arcTo(
                rect = Rect(w - 2 * rPx + halfStroke, halfStroke, w - halfStroke, 2 * rPx - halfStroke),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(w - halfStroke, h)
        }
        drawPath(path, brush = stroke.brush, style = Stroke(width = strokePx))
    } else if (isLast) {
        val path = Path().apply {
            moveTo(halfStroke, 0f)
            lineTo(halfStroke, h - rPx)
            arcTo(
                rect = Rect(halfStroke, h - 2 * rPx + halfStroke, 2 * rPx - halfStroke, h - halfStroke),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(w - rPx, h - halfStroke)
            arcTo(
                rect = Rect(w - 2 * rPx + halfStroke, h - 2 * rPx + halfStroke, w - halfStroke, h - halfStroke),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(w - halfStroke, 0f)
        }
        drawPath(path, brush = stroke.brush, style = Stroke(width = strokePx))
    } else {
        drawLine(stroke.brush, Offset(halfStroke, 0f), Offset(halfStroke, h), strokeWidth = strokePx)
        drawLine(stroke.brush, Offset(w - halfStroke, 0f), Offset(w - halfStroke, h), strokeWidth = strokePx)
    }
}

