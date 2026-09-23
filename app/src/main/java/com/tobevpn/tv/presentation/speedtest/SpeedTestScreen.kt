package com.tobevpn.tv.presentation.speedtest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    onNavigateToHistory: () -> Unit = {},
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viaVpn by viewModel.viaVpn.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val gap = (16 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (24 * scale).sp
        val bodySize = (14 * scale).sp
        val labelSize = (14 * scale).sp
        val valueSize = (32 * scale).sp
        val gaugeTextSize = (48 * scale).sp
        val gaugeUnitSize = (14 * scale).sp
        val buttonTextSize = (20 * scale).sp
        val cardCorner = (16 * scale).dp
        val cardPad = (16 * scale).dp
        val cardWidth = (140 * scale).dp
        val buttonWidth = (220 * scale).dp
        val buttonMinHeight = (46 * scale).dp
        val buttonPadH = (26 * scale).dp
        val buttonPadV = (8 * scale).dp
        val borderWidth = (2 * scale).dp
        val backCorner = (8 * scale).dp
        val colSpacing = (96 * scale).dp
        val cardSpacing = (16 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val headerColor = MaterialTheme.colorScheme.onBackground

        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvHeaderIconButton(
                    onClick = onBack,
                    onLongClick = onLongBack,
                    modifier = Modifier.size(headerButtonSize),
                    shape = RoundedCornerShape(backCorner),
                    borderWidth = borderWidth,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(headerIconSize),
                        tint = headerColor,
                    )
                }
                Spacer(modifier = Modifier.width(gap))
                Text(
                    stringResource(R.string.speed_test_title),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.weight(1f))
                var historyFocused by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
                ) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier
                            .height((40 * scale).dp)
                            .onFocusChanged { historyFocused = it.isFocused },
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(
                            start = (16 * scale).dp,
                            end = (12 * scale).dp,
                        ),
                        border = BorderStroke(
                            if (historyFocused) borderWidth else (1 * scale).dp,
                            if (historyFocused) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.speed_history_title),
                            fontSize = bodySize,
                            fontWeight = FontWeight.SemiBold,
                            style = tightStyle,
                        )
                        if (history.isNotEmpty()) {
                            Spacer(modifier = Modifier.width((8 * scale).dp))
                            Surface(
                                modifier = Modifier
                                    .width((30 * scale).dp)
                                    .height((24 * scale).dp),
                                shape = RoundedCornerShape(percent = 50),
                                color = VpnBlue.copy(alpha = 0.14f),
                                contentColor = VpnBlue,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = history.size.toString(),
                                        fontSize = (12 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        style = tightStyle,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width((12 * scale).dp))
                VpnRouteBadge(viaVpn = viaVpn, scale = scale, tightStyle = tightStyle)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = gap),
            ) {
                val gaugeSize = min(maxHeight * 0.85f, maxWidth * 0.35f)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedGauge(
                        speed = state.currentSpeed,
                        phase = state.phase,
                        hasError = state.errorRes != null,
                        successful = state.phase == SpeedTestPhase.Done &&
                            state.errorRes == null && state.downloadSpeed > 0.0,
                        modifier = Modifier.size(gaugeSize),
                        gaugeTextSize = gaugeTextSize,
                        gaugeUnitSize = gaugeUnitSize,
                        tightStyle = tightStyle,
                        scale = scale,
                    )

                    Spacer(modifier = Modifier.width(colSpacing))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedVisibility(
                            visible = state.errorRes != null,
                            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f),
                            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.96f),
                        ) {
                            state.errorRes?.let { errorRes ->
                                Text(
                                    text = stringResource(errorRes),
                                    fontSize = bodySize,
                                    color = VpnRed,
                                    textAlign = TextAlign.Center,
                                    style = tightStyle,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = state.errorRes == null && (
                                state.phase == SpeedTestPhase.Ping ||
                                    state.phase == SpeedTestPhase.Download ||
                                    state.phase == SpeedTestPhase.Done
                                ),
                            enter = fadeIn(tween(250)) +
                                scaleIn(tween(250), initialScale = 0.94f) +
                                expandVertically(
                                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                                    expandFrom = Alignment.Top,
                                ),
                            exit = fadeOut(tween(220)) +
                                scaleOut(tween(220), targetScale = 0.96f) +
                                shrinkVertically(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    shrinkTowards = Alignment.Top,
                                ),
                        ) {
                            MeasurementStages(
                                phase = state.phase,
                                scale = scale,
                                bodySize = bodySize,
                                tightStyle = tightStyle,
                                modifier = Modifier.padding(top = (10 * scale).dp),
                            )
                        }

                        Spacer(modifier = Modifier.height((18 * scale).dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(cardSpacing)) {
                            ResultCard(
                                label = stringResource(R.string.speed_ping),
                                value = if (state.ping > 0) "${state.ping}" else "—",
                                unit = stringResource(R.string.speed_unit_ms),
                                color = pingResultColor(state.ping),
                                modifier = Modifier.width(cardWidth),
                                cardCorner = cardCorner,
                                cardPad = cardPad,
                                labelSize = labelSize,
                                valueSize = valueSize,
                                tightStyle = tightStyle,
                            )
                            ResultCard(
                                label = stringResource(R.string.speed_download),
                                value = if (state.downloadSpeed > 0) "%.1f".format(state.downloadSpeed) else "—",
                                unit = stringResource(R.string.speed_unit_mbps),
                                color = if (state.downloadSpeed > 0) {
                                    downloadResultColor(state.downloadSpeed)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(cardWidth),
                                cardCorner = cardCorner,
                                cardPad = cardPad,
                                labelSize = labelSize,
                                valueSize = valueSize,
                                tightStyle = tightStyle,
                            )
                        }

                        Spacer(modifier = Modifier.height((40 * scale).dp))

                        var startBtnFocused by remember { mutableStateOf(false) }
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
                        ) {
                            Button(
                                onClick = {
                                    if (state.phase == SpeedTestPhase.Done || state.phase == SpeedTestPhase.Idle) {
                                        viewModel.startTest()
                                    } else {
                                        viewModel.reset()
                                    }
                                },
                                modifier = Modifier
                                    .width(buttonWidth)
                                    .defaultMinSize(minWidth = 1.dp, minHeight = buttonMinHeight)
                                    .then(
                                        if (startBtnFocused) {
                                            Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onFocusChanged { startBtnFocused = it.isFocused },
                                shape = RoundedCornerShape(cardCorner),
                                contentPadding = PaddingValues(
                                    horizontal = buttonPadH,
                                    vertical = buttonPadV,
                                ),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(
                                    text = when (state.phase) {
                                        SpeedTestPhase.Idle, SpeedTestPhase.Done -> stringResource(R.string.speed_start_test)
                                        else -> stringResource(R.string.speed_stop)
                                    },
                                    fontSize = buttonTextSize,
                                    fontWeight = FontWeight.Bold,
                                    style = tightStyle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnRouteBadge(
    viaVpn: Boolean,
    scale: Float,
    tightStyle: TextStyle,
) {
    val bg = if (viaVpn) VpnGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (viaVpn) VpnGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val content = if (viaVpn) VpnGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height((40 * scale).dp)
            .border((1.5f * scale).dp, border, RoundedCornerShape(percent = 50))
            .background(bg, RoundedCornerShape(percent = 50))
            .padding(horizontal = (16 * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (viaVpn) R.string.speed_via_vpn else R.string.speed_direct),
            fontSize = (14 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            style = tightStyle,
        )
    }
}

@Composable
private fun SpeedGauge(
    speed: Double,
    phase: SpeedTestPhase,
    hasError: Boolean,
    successful: Boolean,
    modifier: Modifier = Modifier,
    gaugeTextSize: androidx.compose.ui.unit.TextUnit,
    gaugeUnitSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    scale: Float,
) {
    // Same scale as the phone: a 300 Mbps connection must not pin the needle.
    val maxSpeed = 500f
    val fraction = (speed.toFloat() / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "gauge",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "speed-gauge-running")
    val scanFraction by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "speed-gauge-scan",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "speed-gauge-pulse",
    )
    val inactiveVisual = phase == SpeedTestPhase.Idle ||
        phase == SpeedTestPhase.Checking || hasError
    val completionBurst by animateFloatAsState(
        targetValue = if (successful) 1f else 0f,
        animationSpec = tween(720, easing = FastOutSlowInEasing),
        label = "speed-gauge-completion",
    )
    val isRunning = phase == SpeedTestPhase.Ping || phase == SpeedTestPhase.Download
    val visualFraction = if (phase == SpeedTestPhase.Ping) scanFraction else animatedFraction
    val arcColor = when {
        phase == SpeedTestPhase.Ping -> VpnBlue
        speed < 25 -> VpnRed
        speed < 75 -> VpnOrange
        speed < 150 -> VpnGreen
        else -> VpnBlue
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (18 * scale).dp.toPx()
            val padding = strokeWidth / 2 + (8 * scale).dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)

            val startAngle = 150f
            val totalSweep = 240f
            val center = Offset(size.width / 2, size.height / 2)

            if (isRunning) {
                drawCircle(
                    color = VpnBlue.copy(alpha = 0.05f + pulse * 0.07f),
                    radius = size.minDimension * (0.43f + pulse * 0.035f),
                    center = center,
                )
            }

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (visualFraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * visualFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            val radius = arcSize.width / 2
            val tickCount = 10
            for (i in 0..tickCount) {
                val angle = Math.toRadians((startAngle + totalSweep * i / tickCount).toDouble())
                val innerR = radius - strokeWidth / 2 - (6 * scale).dp.toPx()
                val outerR = radius - strokeWidth / 2 - (2 * scale).dp.toPx()
                drawLine(
                    color = trackColor,
                    start = Offset(
                        center.x + innerR * cos(angle).toFloat(),
                        center.y + innerR * sin(angle).toFloat(),
                    ),
                    end = Offset(
                        center.x + outerR * cos(angle).toFloat(),
                        center.y + outerR * sin(angle).toFloat(),
                    ),
                    strokeWidth = (2 * scale).dp.toPx(),
                )
            }

            if (!inactiveVisual) {
                val needleAngle = Math.toRadians((startAngle + totalSweep * visualFraction).toDouble())
                val needleLength = radius - strokeWidth - (16 * scale).dp.toPx()
                drawLine(
                    color = arcColor,
                    start = center,
                    end = Offset(
                        center.x + needleLength * cos(needleAngle).toFloat(),
                        center.y + needleLength * sin(needleAngle).toFloat(),
                    ),
                    strokeWidth = (3 * scale).dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = arcColor,
                    radius = (6 * scale).dp.toPx(),
                    center = center,
                )
            }

            if (completionBurst in 0.001f..0.999f) {
                val particleRadius = radius * (0.82f + completionBurst * 0.22f)
                repeat(12) { index ->
                    val angle = Math.toRadians((index * 30.0) - 90.0)
                    drawCircle(
                        color = VpnGreen.copy(alpha = (1f - completionBurst) * 0.75f),
                        radius = ((2.8f - completionBurst * 1.2f) * scale).dp.toPx(),
                        center = Offset(
                            center.x + particleRadius * cos(angle).toFloat(),
                            center.y + particleRadius * sin(angle).toFloat(),
                        ),
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (inactiveVisual) "0" else "%.1f".format(speed),
                fontSize = gaugeTextSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                style = tightStyle,
            )
            Text(
                text = stringResource(R.string.speed_unit_mbps),
                fontSize = gaugeUnitSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
        }

        AnimatedVisibility(
            visible = successful,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = (20 * scale).dp, end = (20 * scale).dp),
            enter = fadeIn(tween(220, delayMillis = 180)) +
                scaleIn(tween(360, delayMillis = 180), initialScale = 0.35f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f),
        ) {
            Surface(
                modifier = Modifier.size((42 * scale).dp),
                shape = RoundedCornerShape(percent = 50),
                color = VpnGreen,
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size((26 * scale).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementStages(
    phase: SpeedTestPhase,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
    ) {
        MeasurementStageChip(
            label = stringResource(R.string.speed_ping),
            active = phase == SpeedTestPhase.Ping,
            completed = phase == SpeedTestPhase.Download || phase == SpeedTestPhase.Done,
            modifier = Modifier.width((118 * scale).dp),
            scale = scale,
            bodySize = bodySize,
            tightStyle = tightStyle,
        )
        MeasurementStageChip(
            label = stringResource(R.string.speed_download),
            active = phase == SpeedTestPhase.Download,
            completed = phase == SpeedTestPhase.Done,
            modifier = Modifier.width((118 * scale).dp),
            scale = scale,
            bodySize = bodySize,
            tightStyle = tightStyle,
        )
    }
}

@Composable
private fun MeasurementStageChip(
    label: String,
    active: Boolean,
    completed: Boolean,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "speed-stage-$label")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "speed-stage-pulse-$label",
    )
    val accent = when {
        completed -> VpnGreen
        active -> VpnBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = when {
        completed -> VpnGreen.copy(alpha = 0.14f)
        active -> VpnBlue.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = modifier.height((38 * scale).dp),
        shape = RoundedCornerShape(percent = 50),
        color = background,
        contentColor = accent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (12 * scale).dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size((17 * scale).dp),
                )
            } else {
                Canvas(modifier = Modifier.size((12 * scale).dp)) {
                    drawCircle(
                        color = accent.copy(alpha = if (active) pulse else 0.45f),
                        radius = size.minDimension * if (active) 0.4f * pulse else 0.28f,
                    )
                }
            }
            Spacer(modifier = Modifier.width((7 * scale).dp))
            Text(
                text = label,
                fontSize = bodySize,
                fontWeight = if (active || completed) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = tightStyle,
            )
        }
    }
}

private fun downloadResultColor(speed: Double): Color = when {
    speed < 25.0 -> VpnRed
    speed < 75.0 -> VpnOrange
    speed < 150.0 -> VpnGreen
    else -> VpnBlue
}

@Composable
private fun pingResultColor(ping: Long): Color = when (ping) {
    in 1..100 -> VpnGreen
    in 101..200 -> VpnOrange
    in 201..Long.MAX_VALUE -> VpnRed
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ResultCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    cardCorner: androidx.compose.ui.unit.Dp,
    cardPad: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = labelSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.86f)) togetherWith
                        fadeOut(tween(120))
                },
                label = "speed-result-$label",
            ) { animatedValue ->
                Text(
                    text = animatedValue,
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center,
                    style = tightStyle,
                )
            }
            Text(
                text = unit,
                fontSize = labelSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
        }
    }
}
