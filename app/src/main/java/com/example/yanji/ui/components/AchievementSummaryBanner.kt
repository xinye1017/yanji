package com.example.yanji.ui.components

import androidx.compose.foundation.background
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(YanjiPrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PhosphorIcons.Fill.Trophy,
                    contentDescription = null,
                    tint = YanjiPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "$unlockedCount / $totalCount 已解锁",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiTextPrimary
                )

                if (recentUnlocked.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentUnlocked.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(YanjiPrimarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getAchievementIcon(item.iconKey, isUnlocked = true),
                                    contentDescription = item.title,
                                    tint = YanjiPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "完成一次专注，点亮第一枚成就",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看全部成就",
                tint = YanjiTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
