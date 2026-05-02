package com.tobevpn.tv.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.tobevpn.tv.R
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF0A1628)
private val Teal = Color(0xFF00E5A0)
private val Cyan = Color(0xFF00BCD4)
private val Blue = Color(0xFF2196F3)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val shieldScale = remember { Animatable(0f) }
    val shieldAlpha = remember { Animatable(0f) }
    val chevronOffset = remember { Animatable(-40f) }
    val chevronAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(20f) }
    val fadeOut = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    LaunchedEffect(Unit) { shieldAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { shieldScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { delay(300); chevronAlpha.animateTo(1f, tween(400)) }
    LaunchedEffect(Unit) { delay(300); chevronOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { delay(600); textAlpha.animateTo(1f, tween(500)) }
    LaunchedEffect(Unit) { delay(600); textOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        delay(1600)
        fadeOut.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeOut.value)
            .background(DarkBg),
        contentAlignment = Alignment.Center,
    ) {
        // Scale: use pixel height to pick baseline — 4K gets larger baseline
        val density = LocalDensity.current.density
        val pxHeight = maxHeight.value * density
        val is4K = pxHeight > 1500f
        val sizeScale = if (is4K) 0.65f else 1.0f
        val shieldBaseline = if (is4K) 500f else 320f

        // Shield: purely proportional, no absolute dp cap
        val shieldSize = min(maxHeight * 0.55f, maxWidth * 0.45f) * sizeScale
        val gap = maxHeight * 0.04f * sizeScale
        val scaleFactor = (shieldSize.value / shieldBaseline).coerceIn(0.5f, 1.3f)
        val titleSize = (42 * scaleFactor).sp
        val taglineSize = (15 * scaleFactor).sp
        val letterSpacingTitle = (5 * scaleFactor).sp
        val letterSpacingTagline = (2.5f * scaleFactor).sp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(shieldSize)
                    .scale(shieldScale.value)
                    .alpha(shieldAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawShield(glowAlpha)
                    drawChevrons(chevronOffset.value, chevronAlpha.value)
                }
            }

            Spacer(modifier = Modifier.height(gap))

            Text(
                text = "ToBeVPN",
                fontSize = titleSize,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = letterSpacingTitle,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .graphicsLayer { translationY = textOffset.value.dp.toPx() },
            )

            Spacer(modifier = Modifier.height(gap * 0.25f))

            Text(
                text = stringResource(R.string.splash_tagline),
                fontSize = taglineSize,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = letterSpacingTagline,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .graphicsLayer { translationY = textOffset.value.dp.toPx() },
            )
        }
    }
}

private fun DrawScope.drawShield(glowAlpha: Float) {
    val w = size.width; val h = size.height; val cx = w / 2; val cy = h / 2

    val glowBrush = Brush.radialGradient(
        colors = listOf(Teal.copy(alpha = glowAlpha * 0.3f), Color.Transparent),
        center = Offset(cx, cy),
        radius = w * 0.7f,
    )
    drawCircle(brush = glowBrush, radius = w * 0.65f, center = Offset(cx, cy))

    val shield = Path().apply {
        moveTo(cx, h * 0.12f)
        cubicTo(cx + w * 0.05f, h * 0.12f, w * 0.78f, h * 0.18f, w * 0.78f, h * 0.22f)
        cubicTo(w * 0.78f, h * 0.5f, w * 0.72f, h * 0.68f, cx, h * 0.88f)
        cubicTo(w * 0.28f, h * 0.68f, w * 0.22f, h * 0.5f, w * 0.22f, h * 0.22f)
        cubicTo(w * 0.22f, h * 0.18f, cx - w * 0.05f, h * 0.12f, cx, h * 0.12f)
        close()
    }

    val strokeWidth = (4.dp.toPx() * (size.minDimension / 300.dp.toPx())).coerceIn(2f, 6f)
    val shieldBrush = Brush.linearGradient(
        colors = listOf(Teal, Cyan, Blue),
        start = Offset(w * 0.22f, h * 0.12f),
        end = Offset(w * 0.78f, h * 0.88f),
    )
    drawPath(
        path = shield,
        brush = shieldBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawChevrons(offset: Float, alpha: Float) {
    if (alpha <= 0f) return
    val w = size.width; val h = size.height; val cx = w / 2; val cy = h / 2
    val offsetPx = offset.dp.toPx()
    val scale = size.minDimension / 300.dp.toPx()

    val chevron1 = Path().apply {
        moveTo(cx - w * 0.06f + offsetPx, cy - h * 0.12f)
        lineTo(cx + w * 0.1f + offsetPx, cy)
        lineTo(cx - w * 0.06f + offsetPx, cy + h * 0.12f)
    }
    drawPath(
        path = chevron1,
        color = Color.White.copy(alpha = alpha),
        style = Stroke(width = (5.dp.toPx() * scale).coerceIn(2f, 8f), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val chevron2 = Path().apply {
        moveTo(cx - w * 0.16f + offsetPx * 0.6f, cy - h * 0.09f)
        lineTo(cx - w * 0.04f + offsetPx * 0.6f, cy)
        lineTo(cx - w * 0.16f + offsetPx * 0.6f, cy + h * 0.09f)
    }
    drawPath(
        path = chevron2,
        color = Color.White.copy(alpha = alpha * 0.4f),
        style = Stroke(width = (3.5f.dp.toPx() * scale).coerceIn(1.5f, 6f), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
