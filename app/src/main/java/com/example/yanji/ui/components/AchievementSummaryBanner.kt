package com.example.yanji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.theme.YanjiColors
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
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.achievement.AchievementsViewModel
import com.example.yanji.ui.achievement.getAchievementIcon

@Composable
fun AchievementSummaryBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = yanjiViewModel { container ->
        AchievementsViewModel(container.achievementRepository)
    }
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val unlockedCount by viewModel.unlockedCount.collectAsStateWithLifecycle()
    val totalCount = viewModel.totalCount
    val recentUnlocked = remember(achievements) {
        achievements.filter { it.isUnlocked }.take(4)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    .clip(RoundedCornerShape(YanjiRadius.Small))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PhosphorIcons.Fill.Trophy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (recentUnlocked.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentUnlocked.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getAchievementIcon(item.iconKey, isUnlocked = true),
                                    contentDescription = item.title,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "完成一次专注，点亮第一枚成就",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看全部成就",
                tint = YanjiColors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
