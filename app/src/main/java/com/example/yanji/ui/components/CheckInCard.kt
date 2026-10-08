package com.example.yanji.ui.components

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiMotion
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.CheckIn
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant

/**
 * 首页「每日打卡」卡。
 *
 * 两种形态由 [CheckInUiState.isCheckedInToday] 决定：
 *  - 还没签：完整形态（标题 + 近 7 日条 + 打卡按钮），这是它唯一的行动号召；
 *  - 已签：折叠成一行「已累计签到 N 天」。
 *
 * 为什么签完要折叠：首页顶部那几张主卡位是按「今天最该回答的问题」排的，而打卡是
 * 一天里一次性结束的事。签完后继续占一整张大卡，标题、7 日条、打卡按钮讲的都是
 * 同一件已经结束的事，纯占地方。折起来只留累计天数，把位置让给还没做的事。
 */
@Composable
fun CheckInCard(
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = yanjiViewModel { container -> CheckInViewModel(container.repository) },
    onCheckInSuccess: (CheckIn) -> Unit = {},
    /**
     * 折叠态的点击入口：今天已经签过，仍允许再看一次那张打卡卡片。
     * 这个能力原先由「今日已签」角标承担，折叠后整行接手，角标只留那颗勾。
     */
    onTodayCheckInClick: (CheckIn) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 在组合期取一次动效规格：transitionSpec 不是 @Composable 上下文，
    // [YanjiMotion.accessibleFiniteTween] 要读 Reduce Motion 设置，只能在组合期调用。
    // 淡入淡出共用一个 FiniteAnimationSpec<Float>：折叠不是翻页，进出用同一节奏才像「收起来」。
    val foldFadeSpec = YanjiMotion.accessibleFiniteTween<Float>(
        durationMillis = YanjiMotion.DurationFast
    )

    AnimatedContent(
        targetState = state.isCheckedInToday,
        transitionSpec = {
            (fadeIn(foldFadeSpec) togetherWith fadeOut(foldFadeSpec)).using(SizeTransform())
        },
        label = "CheckInCardFold",
        modifier = modifier.fillMaxWidth()
    ) { checkedInToday ->
        if (checkedInToday) {
            // 完成后撤掉卡片背景，只留一行可重新查看的签到状态。
            CheckInSummary(
                days = state.totalCheckInDays,
                onClick = { state.todayCheckIn?.let(onTodayCheckInClick) }
            )
        } else {
            YanjiCard(modifier = Modifier.fillMaxWidth(), variant = YanjiCardVariant.Standard) {
                CheckInPrompt(
                    state = state,
                    onCheckIn = {
                        val checkIn = viewModel.checkInToday(note = "今日按计划踏实复习")
                        onCheckInSuccess(checkIn)
                    }
                )
            }
        }
    }
}

/**
 * 未签到形态：标题 + 近 7 日条 + 打卡按钮。
 *
 * 只在「今天还没签」时组合，所以状态角标永远是「今日待打卡」且不可点 —— 已签到时
 * 「再看一次打卡卡片」的入口由 [CheckInSummary] 承担，这里不需要第二个。
 */
@Composable
private fun CheckInPrompt(
    state: CheckInUiState,
    onCheckIn: () -> Unit
) {
    Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RemixIcons.FireFill,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "每日打卡",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.currentStreak > 0) "已连续打卡 ${state.currentStreak} 天" else "开启坚持第一步",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Tag（未签到态固定为「待打卡」，不可点）
            Surface(
                shape = RoundedCornerShape(YanjiRadius.Small),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "今日待打卡",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        color = if (dayStatus.isToday) MaterialTheme.colorScheme.primary else YanjiColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val dotModifier = if (dayStatus.isToday && !dayStatus.isCheckedIn) {
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (dayStatus.isCheckedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Box(
                        modifier = dotModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayStatus.isCheckedIn) {
                            Icon(
                                imageVector = RemixIcons.CheckLine,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (dayStatus.isToday) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // Action Area
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCheckIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = RemixIcons.CheckLine,
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

/** 已签后仅显示一行轻量状态，累计天数与庆祝入口沿用真实记录。 */
@Composable
private fun CheckInSummary(days: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "查看今日打卡卡片",
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = YanjiSpacing.CardPadding, vertical = YanjiSpacing.ItemGapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YanjiSpacing.ItemGapSmall)
    ) {
        Icon(
            imageVector = RemixIcons.CheckLine,
            contentDescription = null,
            tint = YanjiColors.success,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "今日已打卡 · 累计 $days 天",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = RemixIcons.ArrowRightSLine,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
