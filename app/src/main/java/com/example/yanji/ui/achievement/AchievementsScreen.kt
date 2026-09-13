package com.example.yanji.ui.achievement

import androidx.compose.animation.AnimatedVisibility
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
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
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
import com.example.yanji.data.AchievementRepository
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
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
        "sunrise" -> if (isUnlocked) PhosphorIcons.Fill.Sun else PhosphorIcons.Regular.Sun
        "moon" -> if (isUnlocked) PhosphorIcons.Fill.Moon else PhosphorIcons.Regular.Moon
        "award" -> if (isUnlocked) PhosphorIcons.Fill.Trophy else PhosphorIcons.Regular.Trophy
        "shield" -> if (isUnlocked) PhosphorIcons.Fill.ShieldCheck else PhosphorIcons.Regular.ShieldCheck
        "medal" -> if (isUnlocked) PhosphorIcons.Fill.Medal else PhosphorIcons.Regular.Medal
        "pencil" -> if (isUnlocked) PhosphorIcons.Fill.PencilSimple else PhosphorIcons.Regular.PencilSimple
        "feather" -> if (isUnlocked) PhosphorIcons.Fill.Feather else PhosphorIcons.Regular.Feather
        "check_circle" -> if (isUnlocked) PhosphorIcons.Fill.CheckCircle else PhosphorIcons.Regular.CheckCircle
        "fire" -> if (isUnlocked) PhosphorIcons.Fill.Fire else PhosphorIcons.Regular.Fire
        "diamond" -> if (isUnlocked) PhosphorIcons.Fill.Diamond else PhosphorIcons.Regular.Diamond
        else -> if (isUnlocked) PhosphorIcons.Fill.Trophy else PhosphorIcons.Regular.Trophy
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

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }
    var viewingAchievement by remember { mutableStateOf<Achievement?>(null) }

    val filteredAchievements = remember(achievements, selectedCategory) {
        if (selectedCategory == null || selectedCategory == AchievementCategory.ALL) {
            achievements
        } else {
            achievements.filter { it.category == selectedCategory }
        }
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "考研成就殿堂",
                            style = MaterialTheme.typography.titleLarge,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = "记录考研路上的每一个高光时刻",
                            style = MaterialTheme.typography.labelMedium,
                            color = YanjiTextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YanjiSurface)
                            .clickable { showResetConfirmDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "初始化成就系统",
                            tint = YanjiTextSecondary,
                            modifier = Modifier.size(20.dp)
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
                                        color = YanjiTextPrimary
                                    )
                                    Text(
                                        text = "$progressPercent%",
                                        style = MaterialTheme.typography.labelLarge,
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

            // 3. Category Filter Chips
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        null to "全部",
                        AchievementCategory.FOCUS to "专注习惯",
                        AchievementCategory.EXAM to "全真模考",
                        AchievementCategory.JOURNAL to "考研日记",
                        AchievementCategory.STREAK to "坚持打卡"
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
                                color = if (isSelected) Color.White else YanjiTextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 4. Achievement Grid Items
            items(filteredAchievements, key = { it.id }) { item ->
                AchievementGridCard(
                    achievement = item,
                    onClick = { viewingAchievement = item }
                )
            }
        }

        // 5. Achievement Detail Dialog
        viewingAchievement?.let { item ->
            AchievementDetailDialog(
                achievement = item,
                onDismiss = { viewingAchievement = null }
            )
        }

        // 6. Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = {
                    Text(
                        text = "初始化成就与备考数据",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                },
                text = {
                    Text(
                        text = "是否确认将成就系统与历史测试数据完全归零？\n\n• 成就全量重置为 0/15 初始锁定状态\n• 测试专注记录与打卡记录将被清空\n• 目标院校（浙大）、专业与 AI 配置完整保留\n\n从此刻开始，开启全新踏实的考研陪伴之旅！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiTextSecondary,
                        lineHeight = 22.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                achievementRepo.resetAllAchievements()
                                Toast.makeText(context, "成就与记录已初始化，祝考研一战成硕！", Toast.LENGTH_SHORT).show()
                                showResetConfirmDialog = false
                            }
                        }
                    ) {
                        Text("确认归零初始化", color = YanjiPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("取消", color = YanjiTextTertiary)
                    }
                },
                containerColor = YanjiSurface,
                shape = RoundedCornerShape(20.dp)
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
    val icon = getAchievementIcon(achievement.iconKey, isUnlocked)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = YanjiSurface
        ),
        border = if (isUnlocked) {
            CardDefaults.outlinedCardBorder().copy(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(YanjiPrimary.copy(alpha = 0.35f), YanjiPrimarySoft)
                )
            )
        } else {
            CardDefaults.outlinedCardBorder().copy(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(YanjiDivider.copy(alpha = 0.6f), YanjiDivider.copy(alpha = 0.3f))
                )
            )
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(YanjiPrimarySoft, YanjiPrimaryGradientSoft)
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(YanjiBackground, YanjiSurface)
                            )
                        }
                    )
                    .then(
                        if (isUnlocked) {
                            Modifier.border(1.5.dp, YanjiPrimarySoft, CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isUnlocked) YanjiPrimary else YanjiTextTertiary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = achievement.title,
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
                text = achievement.description,
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
                        color = YanjiPrimarySoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = YanjiPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "已点亮",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiPrimary
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
                            color = YanjiPrimary.copy(alpha = 0.5f),
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
    onDismiss: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked
    val icon = getAchievementIcon(achievement.iconKey, isUnlocked)
    val unlockDateStr = remember(achievement.unlockedAt) {
        achievement.unlockedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(26.dp))
                .background(YanjiSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Badge Circle
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUnlocked) {
                                Brush.radialGradient(
                                    colors = listOf(YanjiPrimarySoft, YanjiPrimaryGradientSoft)
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(YanjiBackground, YanjiSurface)
                                )
                            }
                        )
                        .border(
                            2.dp,
                            if (isUnlocked) YanjiPrimary else YanjiDivider,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isUnlocked) YanjiPrimary else YanjiTextTertiary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = achievement.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category pill
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

                Spacer(modifier = Modifier.height(12.dp))

                // Target Requirement
                Text(
                    text = "达成条件：${achievement.description}",
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

                Spacer(modifier = Modifier.height(16.dp))

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
                                text = "“${achievement.rewardQuote}”",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = YanjiTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
