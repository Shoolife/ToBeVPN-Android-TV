package com.tobevpn.tv.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LARGE_SCREEN_THRESHOLD_PX = 1500
private const val HD_WIDTH_PX = 1280
private const val HD_HEIGHT_PX = 720
private const val HD_120_DPI = 120
private const val HD_PROFILE_TOLERANCE_PX = 8
private const val HD_120_SCALE_BOOST = 1.15f

/**
 * Strictly proportional scale: a layout tuned once keeps the exact same share
 * of the screen at any resolution or density.
 *
 * [rememberTvScreenScale] deliberately does not do this — it switches baselines
 * above [LARGE_SCREEN_THRESHOLD_PX] and clamps into a narrow band, so the same
 * element occupies 7% of one screen and 5% of another. The sign-in flow is
 * designed against a fixed composition (QR plate, text column, three buttons),
 * so it needs the plain ratio instead.
 */
@Composable
fun rememberTvProportionalScale(
    maxHeight: Dp,
    baseline: Float = 540f,
    minScale: Float = 0.25f,
    maxScale: Float = 4f,
): Float = remember(maxHeight, baseline, minScale, maxScale) {
    (maxHeight.value / baseline).coerceIn(minScale, maxScale)
}

@Composable
fun rememberTvScreenScale(
    maxWidth: Dp,
    maxHeight: Dp,
    largeScreenBaseline: Float = 810f,
    defaultBaseline: Float = 540f,
    minScale: Float = 0.4f,
    maxScale: Float = 1.5f,
): Float {
    val density = LocalDensity.current.density
    val densityDpi = LocalConfiguration.current.densityDpi

    return remember(
        maxWidth,
        maxHeight,
        density,
        densityDpi,
        largeScreenBaseline,
        defaultBaseline,
        minScale,
        maxScale,
    ) {
        val widthPx = (maxWidth.value * density).roundToInt()
        val heightPx = (maxHeight.value * density).roundToInt()
        val baseline = if (heightPx > LARGE_SCREEN_THRESHOLD_PX) {
            largeScreenBaseline
        } else {
            defaultBaseline
        }
        val baseScale = (maxHeight.value / baseline).coerceIn(minScale, maxScale)

        if (isHd120Profile(widthPx, heightPx, densityDpi)) {
            (baseScale * HD_120_SCALE_BOOST).coerceAtMost(maxScale * HD_120_SCALE_BOOST)
        } else {
            baseScale
        }
    }
}

private fun isHd120Profile(widthPx: Int, heightPx: Int, densityDpi: Int): Boolean {
    return densityDpi == HD_120_DPI &&
        abs(widthPx - HD_WIDTH_PX) <= HD_PROFILE_TOLERANCE_PX &&
        abs(heightPx - HD_HEIGHT_PX) <= HD_PROFILE_TOLERANCE_PX
}
