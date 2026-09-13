package com.example.yanji.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Fire
import com.adamglin.phosphoricons.regular.Sparkle
import com.example.yanji.data.CheckIn
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*

@Composable
fun CheckInCard(
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = viewModel { CheckInViewModel(YanjiRepository.getInstance()) },
    onCheckInSuccess: (CheckIn) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Title & Streak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(YanjiPrimarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Fill.Fire,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "每日打卡",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = if (state.currentStreak > 0) "已连续打卡 ${state.currentStreak} 天" else "开启坚持第一步",
                            fontSize = 12.sp,
                            color = YanjiTextSecondary
                        )
                    }
                }

                // Status Tag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.isCheckedInToday) YanjiSuccess.copy(alpha = 0.12f) else YanjiPrimarySoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isCheckedInToday) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = YanjiSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "今日已签",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiSuccess
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(YanjiPrimary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "今日待打卡",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = YanjiPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Past 7 Days Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                state.past7Days.forEach { dayStatus ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = dayStatus.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = if (dayStatus.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (dayStatus.isToday) YanjiPrimary else YanjiTextTertiary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val dotModifier = if (dayStatus.isToday && !dayStatus.isCheckedIn) {
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(YanjiPrimarySoft)
                                .border(1.5.dp, YanjiPrimary, CircleShape)
                        } else {
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (dayStatus.isCheckedIn) YanjiPrimary else YanjiSurfaceSoft)
                        }
                        Box(
                            modifier = dotModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayStatus.isCheckedIn) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (dayStatus.isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(YanjiPrimary)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action / State Area
            // 局部捕获：state 是委托属性，块内多次访问无法 smart cast
            val todayCheckIn = state.todayCheckIn
            if (state.isCheckedInToday && todayCheckIn != null) {
                // Today Checked-In Info Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(YanjiBackground)
                        .clickable { onCheckInSuccess(todayCheckIn) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "已连续打卡 ${todayCheckIn.streak} 天",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = todayCheckIn.note.ifBlank { "稳扎稳打，静待花开。" },
                                fontSize = 11.sp,
                                color = YanjiTextSecondary
                            )
                        }
                        Text(
                            text = "查看 >",
                            fontSize = 12.sp,
                            color = YanjiPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Check-in Action: Direct Button
                Button(
                    onClick = {
                        val checkIn = viewModel.checkInToday(
                            note = "今日按计划踏实复习"
                        )
                        onCheckInSuccess(checkIn)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YanjiPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "今日打卡签到",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
