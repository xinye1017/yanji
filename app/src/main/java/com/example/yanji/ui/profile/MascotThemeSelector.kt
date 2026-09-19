package com.example.yanji.ui.profile

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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.MascotThemeId
import com.example.yanji.theme.MascotThemeSpec
import com.example.yanji.theme.MascotThemes
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.yanjiIsDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

private val IndicatorSlotSize = 8.dp


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
    val isDark = yanjiIsDarkTheme()

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
        containerColor = MaterialTheme.colorScheme.surface,
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
                contentPadding = PaddingValues(horizontal = 62.dp),
                pageSpacing = 16.dp,
                pageSize = PageSize.Fixed(232.dp),
                beyondViewportPageCount = 0
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
                    modifier = Modifier.testTag("mascot_theme_${spec.id.name.lowercase()}")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                themes.forEachIndexed { index, spec ->
                    val active = index == pagerState.currentPage
                    val palette = spec.palette
                    val dotColor = if (active) {
                        if (isDark) palette.darkPrimary else palette.lightPrimary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(IndicatorSlotSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (active) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MascotPickerPage(
    spec: MascotThemeSpec,
    focusedFraction: Float,
    modifier: Modifier = Modifier
) {
    val focusScale = 0.94f + 0.06f * focusedFraction
    val focusAlpha = 0.62f + 0.38f * focusedFraction

    Column(
        modifier = modifier.height(292.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(228.dp)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                    alpha = focusAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(spec.drawableRes),
                contentDescription = spec.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
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
