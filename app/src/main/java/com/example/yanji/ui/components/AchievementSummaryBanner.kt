package com.example.yanji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Trophy
import com.example.yanji.data.AchievementRepository
import com.example.yanji.theme.*
import com.example.yanji.ui.achievement.getAchievementIcon

@Composable
fun AchievementSummaryBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    achievementRepo: AchievementRepository = AchievementRepository.getInstance()
) {
    val achievements by achievementRepo.achievements.collectAsStateWithLifecycle()
    val unlockedCount by achievementRepo.unlockedCount.collectAsStateWithLifecycle()
    val totalCount = achievementRepo.totalCount
    val recentUnlocked = remember(achievements) {
        achievements.filter { it.isUnlocked }.take(4)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(YanjiPrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Fill.Trophy,
                        contentDescription = null,
                        tint = YanjiPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "研途成就勋章",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已点亮 $unlockedCount / $totalCount 枚 · 点击进入殿堂",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Mini badges preview
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentUnlocked.forEach { item ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(YanjiPrimarySoft)
                                .border(1.5.dp, YanjiSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getAchievementIcon(item.iconKey, isUnlocked = true),
                                contentDescription = null,
                                tint = YanjiPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "查看全部",
                    tint = YanjiTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
