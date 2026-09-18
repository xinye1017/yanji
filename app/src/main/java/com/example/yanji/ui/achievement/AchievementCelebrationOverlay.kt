package com.example.yanji.ui.achievement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.AchievementRarity
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.achievement.AchievementDef
import com.example.yanji.di.LocalAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 稀有度文本转换 helper
 */
fun getRarityDisplayName(rarity: AchievementRarity): String = when (rarity) {
    AchievementRarity.COMMON -> "普通"
    AchievementRarity.UNCOMMON -> "罕见"
    AchievementRarity.RARE -> "稀有"
    AchievementRarity.EPIC -> "史诗"
    AchievementRarity.LEGENDARY -> "传说"
    AchievementRarity.MYTHIC -> "神话"
}

/**
 * 全局成就达成庆典悬浮窗。
 *
 * 挂载于应用顶层，当后台检测到新成就完成并派发事件时，自动从顶部滑出卡片并播放仪式感动效。
 * 支持多成就排队播放与点击即时关闭。
 */
@Composable
fun AchievementCelebrationOverlay(
    modifier: Modifier = Modifier,
    repository: YanjiRepository = LocalAppContainer.current.repository
) {
    var currentAchievement by remember { mutableStateOf<AchievementDef?>(null) }
    var dismissTrigger by remember { mutableStateOf(0) }

    // 监听 Repository 派发的新成就解锁事件（Channel 保证不漏消息，单协程顺序播放）
    LaunchedEffect(repository) {
        repository.achievementUnlockEvents.collect { def ->
            currentAchievement = def
            val startSignal = dismissTrigger
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 4200 && dismissTrigger == startSignal) {
                delay(50)
            }
            currentAchievement = null
            delay(350)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentAchievement != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(250))
        ) {
            currentAchievement?.let { def ->
                AchievementCelebrationCard(
                    achievement = def,
                    onDismiss = { dismissTrigger++ }
                )
            }
        }
    }
}

/**
 * 成就完成悬浮卡片主体
 */
fun celebrationAccentColor(rarity: AchievementRarity): Color {
    return when (rarity) {
        AchievementRarity.COMMON -> Color(0xFF3B82F6) // 鲜明活力蓝
        AchievementRarity.UNCOMMON -> Color(0xFF10B981) // 翡翠绿
        AchievementRarity.RARE -> Color(0xFF06B6D4) // 青碧蓝
        AchievementRarity.EPIC -> Color(0xFFA855F7) // 史诗紫
        AchievementRarity.LEGENDARY -> Color(0xFFF59E0B) // 传说金
        AchievementRarity.MYTHIC -> Color(0xFFEF4444) // 神话赤红
    }
}

/**
 * 成就完成悬浮卡片主体
 */
@Composable
private fun AchievementCelebrationCard(
    achievement: AchievementDef,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = celebrationAccentColor(achievement.rarity)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = accentColor.copy(alpha = 0.4f),
                    ambientColor = Color.Black.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(
                width = 1.2.dp,
                brush = rarityBorderBrush(achievement.rarity, isUnlocked = true)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：仪式感 Canvas 动效徽章（顺时针镀满圈 + 动态对勾 + 材质微粒光晕铺散）
                Box(
                    modifier = Modifier.size(58.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AchievementBadgeAnimation(
                        achievement = achievement,
                        accentColor = accentColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右侧：成就内容信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 顶部标签行：成就达成 + 稀有度
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎉 成就达成",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = rarityBackground(achievement.rarity),
                            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = getRarityDisplayName(achievement.rarity),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 成就标题
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 励志语录或描述
                    val quoteOrDesc = achievement.rewardQuote.ifBlank { achievement.description }
                    Text(
                        text = quoteOrDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * 核心动效徽章组件：
 * 1. 顺时针镀满圆圈（0° -> 360°）
 * 2. 动态打对勾（路径平滑绘制）+ 轻微弹性回弹
 * 3. 材质铺散效果（双层径向波纹 + 10 枚微粒星芒向外迸发）
 */
@Composable
private fun AchievementBadgeAnimation(
    achievement: AchievementDef,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val ringProgress = remember { Animatable(0f) }
    val checkmarkProgress = remember { Animatable(0f) }
    val diffusionProgress = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(0.85f) }

    LaunchedEffect(achievement.id) {
        // 重置所有动效状态
        ringProgress.snapTo(0f)
        checkmarkProgress.snapTo(0f)
        diffusionProgress.snapTo(0f)
        badgeScale.snapTo(0.85f)

        // 阶段 1：顺时针镀满进度圈 (0ms ~ 650ms)
        launch {
            badgeScale.animateTo(1.0f, tween(300, easing = FastOutSlowInEasing))
        }
        ringProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))

        // 阶段 2：镀满瞬间动态打对勾 (550ms ~ 880ms) 并带有物理弹性微弹
        launch {
            badgeScale.animateTo(1.14f, tween(140, easing = FastOutSlowInEasing))
            badgeScale.animateTo(
                1.0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        launch {
            checkmarkProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }

        // 阶段 3：材质光晕与微粒向外铺散 (650ms ~ 1400ms)
        diffusionProgress.animateTo(1f, tween(750, easing = LinearOutSlowInEasing))
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .scale(badgeScale.value)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.minDimension / 2f) - 6.dp.toPx()
        val strokeWidth = 3.2.dp.toPx()

        // -------------------------------------------------------------
        // 阶段 3 效果（在底层绘制）：材质光晕波纹与迸发散落微粒
        // -------------------------------------------------------------
        if (diffusionProgress.value > 0f) {
            val d = diffusionProgress.value

            // 1. 径向内光晕扩散
            val glowRadius = r * (1f + 1.1f * d)
            val glowAlpha = (0.35f * (1f - d)).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = glowAlpha),
                        accentColor.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )

            // 2. 主波纹扩散（半径由 r 扩至 2.1r，透明度衰减至 0）
            val wave1R = r + (r * 1.1f) * d
            val wave1Alpha = (0.6f * (1f - d)).coerceIn(0f, 1f)
            drawCircle(
                color = accentColor.copy(alpha = wave1Alpha),
                radius = wave1R,
                center = Offset(cx, cy),
                style = Stroke(width = (2.2f * (1f - d * 0.7f)).dp.toPx())
            )

            // 3. 次波纹扩散（略微滞后，柔和扩散）
            if (d > 0.15f) {
                val d2 = (d - 0.15f) / 0.85f
                val wave2R = r + (r * 1.35f) * d2
                val wave2Alpha = (0.4f * (1f - d2)).coerceIn(0f, 1f)
                drawCircle(
                    color = accentColor.copy(alpha = wave2Alpha),
                    radius = wave2R,
                    center = Offset(cx, cy),
                    style = Stroke(width = (1.6f * (1f - d2 * 0.7f)).dp.toPx())
                )
            }

            // 4. 10 枚微粒与星芒沿 360° 辐射散开
            for (i in 0 until 10) {
                val baseAngle = i * 36.0 + ((i % 3) * 6.0)
                val angleRad = Math.toRadians(baseAngle)
                val spreadDist = r + (12.dp.toPx() + (i % 4) * 4.dp.toPx()) * d
                val px = cx + (spreadDist * cos(angleRad)).toFloat()
                val py = cy + (spreadDist * sin(angleRad)).toFloat()
                val pAlpha = ((1f - d) * (0.75f + (i % 3) * 0.12f)).coerceIn(0f, 1f)
                val pSize = (2.6f - 1.2f * d).dp.toPx()

                if (i % 2 == 0) {
                    // 圆形高光微粒
                    drawCircle(
                        color = accentColor.copy(alpha = pAlpha),
                        radius = pSize,
                        center = Offset(px, py)
                    )
                } else {
                    // 菱形星芒微粒
                    val sparkPath = Path().apply {
                        moveTo(px, py - pSize * 1.3f)
                        lineTo(px + pSize, py)
                        lineTo(px, py + pSize * 1.3f)
                        lineTo(px - pSize, py)
                        close()
                    }
                    drawPath(
                        path = sparkPath,
                        color = accentColor.copy(alpha = pAlpha)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 底层圆形浅色容器背景与半透明微光底色
        // -------------------------------------------------------------
        drawCircle(
            color = accentColor.copy(alpha = 0.14f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.22f), accentColor.copy(alpha = 0.05f)),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // -------------------------------------------------------------
        // 阶段 1：顺时针镀满圆圈（从 12 点钟方向 -90° 顺时针镀至 360°）
        // -------------------------------------------------------------
        if (ringProgress.value > 0f) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        accentColor.copy(alpha = 0.7f),
                        accentColor,
                        accentColor
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * ringProgress.value,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // -------------------------------------------------------------
        // 阶段 2：在圆内平滑打出动态对勾（分段路径插值）
        // -------------------------------------------------------------
        if (checkmarkProgress.value > 0f) {
            val p = checkmarkProgress.value
            // 对勾三点坐标
            val p0 = Offset(cx - r * 0.44f, cy + r * 0.02f)
            val p1 = Offset(cx - r * 0.12f, cy + r * 0.38f)
            val p2 = Offset(cx + r * 0.46f, cy - r * 0.30f)

            val d1 = kotlin.math.hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat()
            val d2 = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
            val totalDist = d1 + d2
            val t1 = d1 / totalDist

            val checkPath = Path()
            checkPath.moveTo(p0.x, p0.y)

            if (p <= t1) {
                val segT = p / t1
                checkPath.lineTo(
                    p0.x + (p1.x - p0.x) * segT,
                    p0.y + (p1.y - p0.y) * segT
                )
            } else {
                checkPath.lineTo(p1.x, p1.y)
                val segT = (p - t1) / (1f - t1)
                checkPath.lineTo(
                    p1.x + (p2.x - p1.x) * segT,
                    p1.y + (p2.y - p1.y) * segT
                )
            }

            drawPath(
                path = checkPath,
                color = accentColor,
                style = Stroke(
                    width = 3.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
