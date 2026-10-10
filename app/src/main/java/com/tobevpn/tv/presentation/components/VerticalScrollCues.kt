package com.tobevpn.tv.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The same animated scroll affordance used by the phone UI. */
@Immutable
data class VerticalScrollCueState(
    val topAlpha: Float,
    val bottomAlpha: Float,
)

@Composable
fun rememberVerticalScrollCueState(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): VerticalScrollCueState {
    val topAlpha by animateFloatAsState(
        targetValue = if (canScrollBackward) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "vertical-scroll-top-cue",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (canScrollForward) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "vertical-scroll-bottom-cue",
    )
    return VerticalScrollCueState(topAlpha, bottomAlpha)
}

/*
 * Cue strength (mask and arrow) that follows the scroll: it grows over the
 * first [fadeLength] of scroll instead of popping in at the first pixel, and
 * the bottom cue fades out the same way (phone and desktop do the same).
 */

@Composable
fun rememberVerticalScrollCueState(
    state: LazyListState,
    fadeLength: Dp,
): VerticalScrollCueState {
    val fadePx = with(LocalDensity.current) { fadeLength.toPx() }
    val top by remember(state, fadePx) {
        derivedStateOf {
            when {
                !state.canScrollBackward -> 0f
                state.firstVisibleItemIndex > 0 -> 1f
                else -> edgeFraction(state.firstVisibleItemScrollOffset.toFloat(), fadePx)
            }
        }
    }
    val bottom by remember(state, fadePx) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            when {
                !state.canScrollForward || last == null -> 0f
                last.index < info.totalItemsCount - 1 -> 1f
                else -> edgeFraction(
                    (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset)
                        .toFloat(),
                    fadePx,
                )
            }
        }
    }
    return VerticalScrollCueState(top, bottom)
}

@Composable
fun rememberVerticalScrollCueState(
    state: ScrollState,
    fadeLength: Dp,
): VerticalScrollCueState {
    val fadePx = with(LocalDensity.current) { fadeLength.toPx() }
    val top by remember(state, fadePx) {
        derivedStateOf { edgeFraction(state.value.toFloat(), fadePx) }
    }
    val bottom by remember(state, fadePx) {
        derivedStateOf { edgeFraction((state.maxValue - state.value).toFloat(), fadePx) }
    }
    return VerticalScrollCueState(top, bottom)
}

private fun edgeFraction(distancePx: Float, fadePx: Float): Float = when {
    distancePx <= 0f -> 0f
    fadePx <= 0f -> 1f
    else -> (distancePx / fadePx).coerceIn(0f, 1f)
}

@Composable
fun BoxScope.VerticalScrollCues(
    state: VerticalScrollCueState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    VerticalScrollEdgeArrow(
        alpha = state.topAlpha,
        isTop = true,
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(top = (1 * scale).dp),
        scale = scale,
    )
    VerticalScrollEdgeArrow(
        alpha = state.bottomAlpha,
        isTop = false,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (1 * scale).dp),
        scale = scale,
    )
}

@Composable
fun VerticalScrollEdgeArrow(
    alpha: Float,
    isTop: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Icon(
        imageVector = if (isTop) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        modifier = modifier
            .size((22 * scale).dp)
            .alpha(alpha.coerceIn(0f, 1f)),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Masks content into transparency only at an edge that has more content. */
fun Modifier.verticalFadingEdges(
    topAlpha: Float,
    bottomAlpha: Float,
    fadeHeight: Dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val fadeHeightPx = fadeHeight.toPx().coerceAtMost(size.height / 2f)
        if (fadeHeightPx <= 0f) return@drawWithContent

        val topA = topAlpha.coerceIn(0f, 1f)
        if (topA > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - topA), Color.Black),
                    startY = 0f,
                    endY = fadeHeightPx,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fadeHeightPx),
                blendMode = BlendMode.DstIn,
            )
        }

        val bottomA = bottomAlpha.coerceIn(0f, 1f)
        if (bottomA > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomA)),
                    startY = size.height - fadeHeightPx,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fadeHeightPx),
                size = Size(size.width, fadeHeightPx),
                blendMode = BlendMode.DstIn,
            )
        }
    }
