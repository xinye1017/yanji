package com.example.yanji.ui.profile

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.R
import com.example.yanji.theme.MascotThemeId
import com.example.yanji.theme.MascotThemeSpec
import com.example.yanji.theme.MascotThemes
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.YanjiRadius
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotThemeBottomSheet(
    selected: MascotThemeId,
    onSelect: (MascotThemeId) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = MascotThemes.all
    val initialPage = themes.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { themes.size })
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val reduceMotion = YanjiMotion.isReduceMotionEnabled()

    LaunchedEffect(selected) {
        val target = themes.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pagerState, selected) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val next = themes.getOrNull(page)?.id ?: return@collect
                if (next != selected) {
                    if (!reduceMotion) {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                        )
                    }
                    onSelect(next)
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "选择学习伙伴",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "左右滑动切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 50.dp),
                pageSpacing = 10.dp,
                pageSize = PageSize.Fixed(248.dp)
            ) { page ->
                val spec = themes[page]
                val offset = (
                    (pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction
                    )
                    .absoluteValue
                    .coerceIn(0f, 1f)

                MascotPickerPage(
                    spec = spec,
                    focusedFraction = 1f - offset,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.testTag("mascot_theme_${spec.id.name.lowercase()}")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                themes.forEachIndexed { index, _ ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MascotPickerPage(
    spec: MascotThemeSpec,
    focusedFraction: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val focusScale = 0.94f + 0.06f * focusedFraction
    val focusAlpha = 0.62f + 0.38f * focusedFraction
    val stageShape = RoundedCornerShape(YanjiRadius.HeroCardRadius)

    Column(
        modifier = modifier.height(292.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(228.dp)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                    alpha = focusAlpha
                },
            shape = stageShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ),
            shadowElevation = if (focusedFraction > 0.85f) 2.dp else 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedMascotArtwork(
                    spec = spec,
                    focusedFraction = focusedFraction,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = spec.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.62f + 0.38f * focusedFraction
            )
        )
    }
}

@Composable
private fun AnimatedMascotArtwork(
    spec: MascotThemeSpec,
    focusedFraction: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "mascot-parts-${spec.id.name}")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot-part-phase-${spec.id.name}"
    )

    val activation = if (reduceMotion) {
        0f
    } else {
        ((focusedFraction - 0.62f) / 0.38f).coerceIn(0f, 1f)
    }
    val signed = (phase * 2f - 1f) * activation
    val pulse = (1f - kotlin.math.abs(phase * 2f - 1f)) * activation
    val twoDp = with(density) { 2.dp.toPx() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (spec.id) {
            MascotThemeId.CLOUD -> {
                MascotLayer(R.drawable.mascot_cloud_base, spec.name)
                MascotLayer(
                    R.drawable.mascot_cloud_left_puff,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.34f, 0.40f)
                        scaleX = 1f + 0.025f * signed
                        scaleY = 1f - 0.018f * signed
                        translationY = -twoDp * pulse
                    }
                )
                MascotLayer(
                    R.drawable.mascot_cloud_right_puff,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.68f, 0.39f)
                        scaleX = 1f - 0.025f * signed
                        scaleY = 1f + 0.018f * signed
                        translationY = -twoDp * (1f - pulse) * activation
                    }
                )
            }

            MascotThemeId.BUNNY -> {
                MascotLayer(R.drawable.mascot_bunny_base, spec.name)
                MascotLayer(
                    R.drawable.mascot_bunny_left_ear,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.39f, 0.44f)
                        rotationZ = 5.5f * signed
                    }
                )
                MascotLayer(
                    R.drawable.mascot_bunny_right_ear,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.62f, 0.43f)
                        rotationZ = -5.5f * signed
                    }
                )
            }

            MascotThemeId.PENGUIN -> {
                MascotLayer(R.drawable.mascot_penguin_base, spec.name)
                MascotLayer(
                    R.drawable.mascot_penguin_left_wing,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.25f, 0.40f)
                        rotationZ = 9f * signed
                    }
                )
            }

            MascotThemeId.SHIBA -> {
                MascotLayer(R.drawable.mascot_shiba_base, spec.name)
                MascotLayer(
                    R.drawable.mascot_shiba_left_ear,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.26f, 0.36f)
                        rotationZ = 3.5f * signed
                    }
                )
                MascotLayer(
                    R.drawable.mascot_shiba_right_ear,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.65f, 0.34f)
                        rotationZ = -3.5f * signed
                    }
                )
            }

            MascotThemeId.FROG -> {
                MascotLayer(R.drawable.mascot_frog_base, spec.name)
                MascotLayer(
                    R.drawable.mascot_frog_left_eye,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.40f, 0.31f)
                        translationY = -twoDp * pulse
                        scaleY = 1f + 0.025f * pulse
                    }
                )
                MascotLayer(
                    R.drawable.mascot_frog_right_eye,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.69f, 0.33f)
                        translationY = -twoDp * (1f - pulse) * activation
                        scaleY = 1f + 0.025f * (1f - pulse) * activation
                    }
                )
            }
        }
    }
}

@Composable
private fun MascotLayer(
    drawableRes: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}
