package com.example.yanji.ui.achievement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.currentMascotTheme
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDetailTopBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*
import com.example.yanji.data.Achievement
import com.example.yanji.data.AchievementCategory
import com.example.yanji.data.AchievementRarity
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AiAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun getAchievementIcon(iconKey: String, isUnlocked: Boolean): ImageVector {
    return when (iconKey) {
        "book" -> if (isUnlocked) PhosphorIcons.Fill.BookOpen else PhosphorIcons.Regular.BookOpen
        "hourglass" -> if (isUnlocked) PhosphorIcons.Fill.Hourglass else PhosphorIcons.Regular.Hourglass
        "compass" -> if (isUnlocked) PhosphorIcons.Fill.Compass else PhosphorIcons.Regular.Compass
        "crown" -> if (isUnlocked) PhosphorIcons.Fill.Crown else PhosphorIcons.Regular.Crown
        "timer" -> if (isUnlocked) PhosphorIcons.Fill.Timer else PhosphorIcons.Regular.Timer
        "sunrise", "sun_horizon" -> if (isUnlocked) PhosphorIcons.Fill.SunHorizon else PhosphorIcons.Regular.SunHorizon
        "moon", "moon_stars" -> if (isUnlocked) PhosphorIcons.Fill.Moon else PhosphorIcons.Regular.Moon
        "award", "trophy" -> if (isUnlocked) PhosphorIcons.Fill.Trophy else PhosphorIcons.Regular.Trophy
        "shield" -> if (isUnlocked) PhosphorIcons.Fill.ShieldCheck else PhosphorIcons.Regular.ShieldCheck
        "medal" -> if (isUnlocked) PhosphorIcons.Fill.Medal else PhosphorIcons.Regular.Medal
        "pencil" -> if (isUnlocked) PhosphorIcons.Fill.PencilSimple else PhosphorIcons.Regular.PencilSimple
        "feather" -> if (isUnlocked) PhosphorIcons.Fill.Feather else PhosphorIcons.Regular.Feather
        "check_circle" -> if (isUnlocked) PhosphorIcons.Fill.CheckCircle else PhosphorIcons.Regular.CheckCircle
        "fire", "flame" -> if (isUnlocked) PhosphorIcons.Fill.Fire else PhosphorIcons.Regular.Fire
        "diamond", "gem" -> if (isUnlocked) PhosphorIcons.Fill.Diamond else PhosphorIcons.Regular.Diamond
        "rocket", "rocket_launch" -> if (isUnlocked) PhosphorIcons.Fill.Rocket else PhosphorIcons.Regular.Rocket
        "target", "crosshair" -> if (isUnlocked) PhosphorIcons.Fill.Target else PhosphorIcons.Regular.Target
        "clock" -> if (isUnlocked) PhosphorIcons.Fill.Clock else PhosphorIcons.Regular.Clock
        "drop" -> if (isUnlocked) PhosphorIcons.Fill.Drop else PhosphorIcons.Regular.Drop
        "lightning" -> if (isUnlocked) PhosphorIcons.Fill.Lightning else PhosphorIcons.Regular.Lightning
        "sparkle", "sparkles" -> if (isUnlocked) PhosphorIcons.Fill.Sparkle else PhosphorIcons.Regular.Sparkle
        "sun" -> if (isUnlocked) PhosphorIcons.Fill.Sun else PhosphorIcons.Regular.Sun
        "star", "shooting_star" -> if (isUnlocked) PhosphorIcons.Fill.Star else PhosphorIcons.Regular.Star
        "sword" -> if (isUnlocked) PhosphorIcons.Fill.Sword else PhosphorIcons.Regular.Sword
        "mountain", "mountains" -> if (isUnlocked) PhosphorIcons.Fill.Mountains else PhosphorIcons.Regular.Mountains
        "scales" -> if (isUnlocked) PhosphorIcons.Fill.Scales else PhosphorIcons.Regular.Scales
        "scroll" -> if (isUnlocked) PhosphorIcons.Fill.Scroll else PhosphorIcons.Regular.Scroll
        "heart" -> if (isUnlocked) PhosphorIcons.Fill.Heart else PhosphorIcons.Regular.Heart
        "coffee" -> if (isUnlocked) PhosphorIcons.Fill.Coffee else PhosphorIcons.Regular.Coffee
        "waves" -> if (isUnlocked) PhosphorIcons.Fill.Waves else PhosphorIcons.Regular.Waves
        "first_aid" -> if (isUnlocked) PhosphorIcons.Fill.FirstAid else PhosphorIcons.Regular.FirstAid
        "confetti" -> if (isUnlocked) PhosphorIcons.Fill.Confetti else PhosphorIcons.Regular.Confetti
        "footprints" -> if (isUnlocked) PhosphorIcons.Fill.Footprints else PhosphorIcons.Regular.Footprints
        "barbell" -> if (isUnlocked) PhosphorIcons.Fill.Barbell else PhosphorIcons.Regular.Barbell
        "brain" -> if (isUnlocked) PhosphorIcons.Fill.Brain else PhosphorIcons.Regular.Brain
        "notebook" -> if (isUnlocked) PhosphorIcons.Fill.Notebook else PhosphorIcons.Regular.Notebook
        "fire_extinguisher" -> if (isUnlocked) PhosphorIcons.Fill.FireExtinguisher else PhosphorIcons.Regular.FireExtinguisher
        "infinity" -> if (isUnlocked) PhosphorIcons.Fill.Infinity else PhosphorIcons.Regular.Infinity
        "eye_slash" -> if (isUnlocked) PhosphorIcons.Fill.EyeSlash else PhosphorIcons.Regular.EyeSlash
        else -> if (isUnlocked) PhosphorIcons.Fill.Trophy else PhosphorIcons.Regular.Trophy
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

    val filteredAchievements = remember(achievements, selectedCategory) {
        if (selectedCategory == null || selectedCategory == AchievementCategory.ALL) {
            achievements
        } else {
            achievements.filter { it.category == selectedCategory }
        }
    }

    // Top 3-5 uncompleted non-hidden achievements closest to completion
    val upcomingAchievements = remember(achievements) {
        achievements
            .filter { !it.isUnlocked && !it.isHidden }
            .map { ach ->
                val ratio = if (ach.targetProgress > 0) ach.currentProgress.toFloat() / ach.targetProgress.toFloat() else 0f
                ach to ratio
            }
            .sortedWith(
                compareByDescending<Pair<Achievement, Float>> { it.second }
                    .thenBy { it.first.rarity.ordinal }
            )
            .take(4)
            .map { it.first }
    }

    val progressRatio = if (totalCount > 0) unlockedCount.toFloat() / totalCount.toFloat() else 0f
    val progressPercent = (progressRatio * 100).toInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = YanjiSpacing.PageTopGap,
                bottom = 32.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Bar
            item(span = { GridItemSpan(2) }) {
                YanjiDetailTopBar(
                    title = "考研成就殿堂",
                    subtitle = "记录考研路上的每一个高光时刻",
                    onBack = onBack
                )
            }

            // 2. Banner Showcase Card
            item(span = { GridItemSpan(2) }) {
                YanjiCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = YanjiCardVariant.Hero
                ) {
                    Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                                    .border(1.5.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PhosphorIcons.Fill.Trophy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(YanjiSpacing.InlineGap))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = "已点亮 $unlockedCount / $totalCount 枚徽章",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$progressPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),  // token-exempt: 进度条轨道几何，不是产品组件圆角
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.background,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Rarity Statistics Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AchievementRarity.entries.forEach { rarity ->
                                val totalRarity = achievements.count { it.rarity == rarity }
                                val unlockedRarity = achievements.count { it.rarity == rarity && it.isUnlocked }
                                val color = rarityColor(rarity)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = rarityBackground(rarity),
                                    border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${rarity.title} $unlockedRarity/$totalRarity",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "“每一枚被点亮的徽章，都是你打败拖延与迷茫的铁证。”",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Upcoming Achievements Section (即将点亮)
            if (upcomingAchievements.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Fill.Sparkle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "即将点亮",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "最接近突破的下一个目标",
                                fontSize = 11.sp,
                                color = YanjiColors.textTertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            upcomingAchievements.forEach { ach ->
                                UpcomingAchievementCard(
                                    achievement = ach,
                                    onClick = { viewingAchievement = ach }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Category Filter Chips
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        null to "全部",
                        AchievementCategory.JOURNEY to "研途启程",
                        AchievementCategory.FOCUS to "专注修炼",
                        AchievementCategory.STREAK to "坚持之路",
                        AchievementCategory.EXAM to "模考试炼",
                        AchievementCategory.MATH to "数学征途",
                        AchievementCategory.REVIEW to "复盘沉淀",
                        AchievementCategory.HIDDEN to "隐藏成就"
                    )

                    categories.forEach { (cat, title) ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            val count = if (cat == null) totalCount else achievements.count { it.category == cat }
                            Text(
                                text = "$title ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 5. Achievement Grid Items
            items(filteredAchievements, key = { it.id }) { item ->
                AchievementGridCard(
                    achievement = item,
                    onClick = { viewingAchievement = item }
                )
            }
        }

        // 6. Achievement Detail Dialog
        viewingAchievement?.let { item ->
            AchievementDetailDialog(
                achievement = item,
                allAchievements = achievements,
                onDismiss = { viewingAchievement = null }
            )
        }
    }
}

@Composable
fun UpcomingAchievementCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val icon = getAchievementIcon(achievement.iconKey, false)
    val color = rarityColor(achievement.rarity)
    val remaining = (achievement.targetProgress - achievement.currentProgress).coerceAtLeast(0L)
    val progressRatio = if (achievement.targetProgress > 0) {
        (achievement.currentProgress.toFloat() / achievement.targetProgress.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(180.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),  // token-exempt: 稀有度微徽章（title 10sp）端部几何，小于最小 token 8dp
                    color = rarityBackground(achievement.rarity)
                ) {
                    Text(
                        text = achievement.rarity.title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = achievement.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "还差 $remaining ${achievement.unit}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),  // token-exempt: 进度条轨道几何，不是产品组件圆角
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.background
            )
        }
    }
}

@Composable
fun AchievementGridCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked
    val isHiddenLocked = achievement.isHidden && !isUnlocked
    val icon = if (isHiddenLocked) PhosphorIcons.Regular.Lock else getAchievementIcon(achievement.iconKey, isUnlocked)
    val rarityColor = rarityColor(achievement.rarity)

    YanjiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Grouped,
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isUnlocked && (achievement.rarity == AchievementRarity.MYTHIC || achievement.rarity == AchievementRarity.LEGENDARY)) 1.5.dp else 1.dp,
            brush = rarityBorderBrush(achievement.rarity, isUnlocked)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rarity Tag Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),  // token-exempt: 稀有度微徽章（title 9sp）端部几何，小于最小 token 8dp
                    color = rarityBackground(achievement.rarity)
                ) {
                    Text(
                        text = achievement.rarity.title,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = rarityColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Icon Circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(
                                    rarityBackground(achievement.rarity),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                            )
                        }
                    )
                    .then(
                        if (isUnlocked) {
                            Modifier.border(1.5.dp, rarityColor.copy(alpha = 0.4f), CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isUnlocked) rarityColor else YanjiColors.textTertiary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = if (isHiddenLocked) "???" else achievement.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = if (isHiddenLocked) "这是一项隐藏成就，达成后揭晓" else achievement.description,
                fontSize = 11.sp,
                color = YanjiColors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 2,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress or Unlocked tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = rarityBackground(achievement.rarity)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.Check,
                                contentDescription = null,
                                tint = rarityColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "已点亮",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = rarityColor
                            )
                        }
                    }
                } else if (isHiddenLocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.Lock,
                                contentDescription = null,
                                tint = YanjiColors.textTertiary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "未探索",
                                fontSize = 10.sp,
                                color = YanjiColors.textTertiary
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val progressRatio = (achievement.currentProgress.toFloat() / achievement.targetProgress.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),  // token-exempt: 进度条轨道几何，不是产品组件圆角
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            trackColor = MaterialTheme.colorScheme.background
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${achievement.currentProgress}/${achievement.targetProgress} ${achievement.unit}",
                            fontSize = 10.sp,
                            color = YanjiColors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    allAchievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked
    val isHiddenLocked = achievement.isHidden && !isUnlocked
    val icon = if (isHiddenLocked) PhosphorIcons.Regular.Lock else getAchievementIcon(achievement.iconKey, isUnlocked)
    val rarityColor = rarityColor(achievement.rarity)
    val unlockDateStr = remember(achievement.unlockedAt) {
        achievement.unlockedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
        }
    }

    // Series progression ladder if applicable
    val seriesList = remember(achievement.seriesId, allAchievements) {
        achievement.seriesId?.let { seriesId ->
            allAchievements.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
        } ?: emptyList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Badge Circle with Rarity Glow
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUnlocked) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        rarityBackground(achievement.rarity),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                                )
                            }
                        )
                        .border(
                            2.dp,
                            if (isUnlocked) rarityColor else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isUnlocked) rarityColor else YanjiColors.textTertiary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = if (isHiddenLocked) "???" else achievement.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Pills Row: Rarity + Category
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = rarityBackground(achievement.rarity),
                        border = BorderStroke(1.dp, rarityColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = achievement.rarity.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = rarityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = achievement.category.title,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Requirement / Description
                Text(
                    text = if (isHiddenLocked) "达成条件：这是一项隐藏成就，达成后揭晓。" else "达成条件：${achievement.description}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status info
                if (isUnlocked) {
                    Text(
                        text = "达成时间：${unlockDateStr ?: "已点亮"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isHiddenLocked) {
                    Text(
                        text = "探索状态：未解锁（继续你的考研征途以揭晓）",
                        fontSize = 12.sp,
                        color = YanjiColors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "当前进度：${achievement.currentProgress} / ${achievement.targetProgress} ${achievement.unit}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Series Progression Ladder (if part of a series)
                if (seriesList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "成长阶梯系列",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                seriesList.forEachIndexed { index, def ->
                                    val isUnlockedDef = def.isUnlocked
                                    val isCurrent = def.id == achievement.id

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isUnlockedDef -> MaterialTheme.colorScheme.tertiaryContainer
                                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isUnlockedDef -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                                isCurrent -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isUnlockedDef) "✓" else if (isCurrent) "○" else "🔒",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isUnlockedDef -> MaterialTheme.colorScheme.tertiary
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    else -> YanjiColors.textTertiary
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${def.targetProgress}${def.unit}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isUnlockedDef || isCurrent) MaterialTheme.colorScheme.onSurface else YanjiColors.textTertiary
                                            )
                                        }
                                    }
                                    if (index < seriesList.lastIndex) {
                                        Text(
                                            text = "→",
                                            fontSize = 11.sp,
                                            color = YanjiColors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // JuanJuan Quote Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        AiAvatar(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${currentMascotTheme().name}的话：",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isHiddenLocked) "“坚持做正确的事，惊喜会在不经意间降临。”" else "“${achievement.rewardQuote}”",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("关 闭", fontSize = 14.sp)
                }
            }
        }
    }
}
