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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
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
import com.example.yanji.data.AchievementRepository
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.JuanjuanAvatar
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
        AchievementRarity.COMMON -> Color(0xFF64748B) // Slate
        AchievementRarity.UNCOMMON -> Color(0xFF10B981) // Emerald
        AchievementRarity.RARE -> Color(0xFF2563EB) // Royal Blue
        AchievementRarity.EPIC -> Color(0xFF8B5CF6) // Purple
        AchievementRarity.LEGENDARY -> Color(0xFFF59E0B) // Amber Gold
        AchievementRarity.MYTHIC -> Color(0xFFEF4444) // Crimson Red
    }
}

fun rarityBackground(rarity: AchievementRarity): Color {
    return when (rarity) {
        AchievementRarity.COMMON -> Color(0xFFF1F5F9)
        AchievementRarity.UNCOMMON -> Color(0xFFECFDF5)
        AchievementRarity.RARE -> Color(0xFFEFF6FF)
        AchievementRarity.EPIC -> Color(0xFFF5F3FF)
        AchievementRarity.LEGENDARY -> Color(0xFFFFFBEB)
        AchievementRarity.MYTHIC -> Color(0xFFFEF2F2)
    }
}

fun rarityBorderBrush(rarity: AchievementRarity, isUnlocked: Boolean): Brush {
    return if (isUnlocked) {
        when (rarity) {
            AchievementRarity.MYTHIC -> Brush.linearGradient(
                colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF8B5CF6))
            )
            AchievementRarity.LEGENDARY -> Brush.linearGradient(
                colors = listOf(Color(0xFFF59E0B), Color(0xFFFCD34D))
            )
            AchievementRarity.EPIC -> Brush.linearGradient(
                colors = listOf(Color(0xFF8B5CF6), Color(0xFFC4B5FD))
            )
            AchievementRarity.RARE -> Brush.linearGradient(
                colors = listOf(Color(0xFF2563EB), Color(0xFF93C5FD))
            )
            AchievementRarity.UNCOMMON -> Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFFA7F3D0))
            )
            AchievementRarity.COMMON -> Brush.linearGradient(
                colors = listOf(YanjiPrimary.copy(alpha = 0.35f), YanjiPrimarySoft)
            )
        }
    } else {
        Brush.linearGradient(
            colors = listOf(YanjiDivider.copy(alpha = 0.6f), YanjiDivider.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    achievementRepo: AchievementRepository = AchievementRepository.getInstance()
) {
    val achievements by achievementRepo.achievements.collectAsStateWithLifecycle()
    val unlockedCount by achievementRepo.unlockedCount.collectAsStateWithLifecycle()
    val totalCount = achievementRepo.totalCount

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
            .background(YanjiBackground)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YanjiSurface)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = YanjiTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "考研成就殿堂",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = "记录考研路上的每一个高光时刻",
                            style = MaterialTheme.typography.labelMedium,
                            color = YanjiTextSecondary
                        )
                    }
                }
            }

            // 2. Banner Showcase Card
            item(span = { GridItemSpan(2) }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
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
                                                YanjiPrimarySoft,
                                                YanjiSurface
                                            )
                                        )
                                    )
                                    .border(1.5.dp, YanjiPrimarySoft, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PhosphorIcons.Fill.Trophy,
                                    contentDescription = null,
                                    tint = YanjiPrimary,
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
                                        color = YanjiTextPrimary
                                    )
                                    Text(
                                        text = "$progressPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = YanjiPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = YanjiPrimary,
                                    trackColor = YanjiBackground,
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(YanjiBackground)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "“每一枚被点亮的徽章，都是你打败拖延与迷茫的铁证。”",
                                style = MaterialTheme.typography.labelMedium,
                                color = YanjiTextSecondary
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
                                tint = YanjiPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "即将点亮",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "最接近突破的下一个目标",
                                fontSize = 11.sp,
                                color = YanjiTextTertiary
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) YanjiPrimary else YanjiSurface,
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            val count = if (cat == null) totalCount else achievements.count { it.category == cat }
                            Text(
                                text = "$title ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else YanjiTextSecondary,
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
                achievementRepo = achievementRepo,
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
        color = YanjiSurface,
        border = BorderStroke(1.dp, YanjiDivider.copy(alpha = 0.5f)),
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
                        .background(YanjiBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = YanjiTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
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
                color = YanjiTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "还差 $remaining ${achievement.unit}",
                fontSize = 11.sp,
                color = YanjiPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = YanjiPrimary,
                trackColor = YanjiBackground
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
    val icon = if (isHiddenLocked) Icons.Default.Lock else getAchievementIcon(achievement.iconKey, isUnlocked)
    val rarityColor = rarityColor(achievement.rarity)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = YanjiSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isUnlocked && (achievement.rarity == AchievementRarity.MYTHIC || achievement.rarity == AchievementRarity.LEGENDARY)) 1.5.dp else 1.dp,
            brush = rarityBorderBrush(achievement.rarity, isUnlocked)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    shape = RoundedCornerShape(6.dp),
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
                                    YanjiSurface
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(YanjiBackground, YanjiSurface)
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
                    tint = if (isUnlocked) rarityColor else YanjiTextTertiary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = if (isHiddenLocked) "???" else achievement.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) YanjiTextPrimary else YanjiTextSecondary,
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
                color = YanjiTextTertiary,
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
                                imageVector = Icons.Default.Check,
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
                        color = YanjiBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = YanjiTextTertiary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "未探索",
                                fontSize = 10.sp,
                                color = YanjiTextTertiary
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
                                .clip(RoundedCornerShape(3.dp)),
                            color = YanjiPrimary.copy(alpha = 0.6f),
                            trackColor = YanjiBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${achievement.currentProgress}/${achievement.targetProgress} ${achievement.unit}",
                            fontSize = 10.sp,
                            color = YanjiTextTertiary,
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
    achievementRepo: AchievementRepository,
    allAchievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked
    val isHiddenLocked = achievement.isHidden && !isUnlocked
    val icon = if (isHiddenLocked) Icons.Default.Lock else getAchievementIcon(achievement.iconKey, isUnlocked)
    val rarityColor = rarityColor(achievement.rarity)
    val unlockDateStr = remember(achievement.unlockedAt) {
        achievement.unlockedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
        }
    }

    // Series progression ladder if applicable
    val seriesList = remember(achievement.seriesId) {
        achievement.seriesId?.let { achievementRepo.getSeries(it) } ?: emptyList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(26.dp))
                .background(YanjiSurface)
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
                                        YanjiSurface
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(YanjiBackground, YanjiSurface)
                                )
                            }
                        )
                        .border(
                            2.dp,
                            if (isUnlocked) rarityColor else YanjiDivider,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isUnlocked) rarityColor else YanjiTextTertiary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = if (isHiddenLocked) "???" else achievement.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary,
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
                        color = YanjiBackground
                    ) {
                        Text(
                            text = achievement.category.title,
                            fontSize = 11.sp,
                            color = YanjiTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Requirement / Description
                Text(
                    text = if (isHiddenLocked) "达成条件：这是一项隐藏成就，达成后揭晓。" else "达成条件：${achievement.description}",
                    fontSize = 13.sp,
                    color = YanjiTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status info
                if (isUnlocked) {
                    Text(
                        text = "达成时间：${unlockDateStr ?: "已点亮"}",
                        fontSize = 12.sp,
                        color = YanjiSuccess,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isHiddenLocked) {
                    Text(
                        text = "探索状态：未解锁（继续你的考研征途以揭晓）",
                        fontSize = 12.sp,
                        color = YanjiTextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "当前进度：${achievement.currentProgress} / ${achievement.targetProgress} ${achievement.unit}",
                        fontSize = 12.sp,
                        color = YanjiPrimary,
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiBackground)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "成长阶梯系列",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextSecondary
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
                                    val fullAch = allAchievements.find { it.id == def.id }
                                    val isUnlockedDef = fullAch?.isUnlocked == true
                                    val isCurrent = def.id == achievement.id

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isUnlockedDef -> YanjiSuccessSoft
                                            isCurrent -> YanjiPrimarySoft
                                            else -> YanjiSurface
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isUnlockedDef -> YanjiSuccess.copy(alpha = 0.5f)
                                                isCurrent -> YanjiPrimary
                                                else -> YanjiDivider.copy(alpha = 0.6f)
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
                                                    isUnlockedDef -> YanjiSuccess
                                                    isCurrent -> YanjiPrimary
                                                    else -> YanjiTextTertiary
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${def.target}${def.unit}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isUnlockedDef || isCurrent) YanjiTextPrimary else YanjiTextTertiary
                                            )
                                        }
                                    }
                                    if (index < seriesList.lastIndex) {
                                        Text(
                                            text = "→",
                                            fontSize = 11.sp,
                                            color = YanjiTextTertiary
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
                        .background(YanjiBackground)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        JuanjuanAvatar(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "卷卷的话：",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isHiddenLocked) "“坚持做正确的事，惊喜会在不经意间降临。”" else "“${achievement.rewardQuote}”",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = YanjiTextSecondary
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YanjiPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("关 闭", fontSize = 14.sp)
                }
            }
        }
    }
}
