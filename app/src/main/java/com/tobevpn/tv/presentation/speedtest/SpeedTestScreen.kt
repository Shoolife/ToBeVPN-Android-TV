package com.tobevpn.tv.presentation.speedtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
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
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        val colSpacing = (48 * scale).dp
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                var backFocused by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(headerButtonSize)
                            .then(
                                if (backFocused) Modifier.border(borderWidth, Color.White, RoundedCornerShape(backCorner))
                                else Modifier
                            )
                            .onFocusChanged { backFocused = it.isFocused },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(headerIconSize),
                            tint = headerColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(gap))
                Text(
                    stringResource(R.string.speed_test_title),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
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
                        val errorRes = state.errorRes
                        Text(
                            text = when {
                                errorRes != null -> stringResource(errorRes)
                                state.phase == SpeedTestPhase.Idle -> stringResource(R.string.speed_press_start)
                                state.phase == SpeedTestPhase.Ping -> stringResource(R.string.speed_measuring_ping)
                                state.phase == SpeedTestPhase.Download -> stringResource(R.string.speed_downloading)
                                state.phase == SpeedTestPhase.Done -> stringResource(R.string.speed_done)
                                else -> ""
                            },
                            fontSize = titleSize,
                            color = if (errorRes != null) VpnRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = tightStyle,
                        )

                        Spacer(modifier = Modifier.height((32 * scale).dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(cardSpacing)) {
                            ResultCard(
                                label = stringResource(R.string.speed_ping),
                                value = if (state.ping > 0) "${state.ping}" else "—",
                                unit = stringResource(R.string.speed_unit_ms),
                                color = if (state.ping in 1..100) VpnGreen
                                else if (state.ping in 101..200) VpnOrange
                                else if (state.ping > 200) VpnRed
                                else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = VpnGreen,
                                modifier = Modifier.width(cardWidth),
                                cardCorner = cardCorner,
                                cardPad = cardPad,
                                labelSize = labelSize,
                                valueSize = valueSize,
                                tightStyle = tightStyle,
                            )
                        }

                        Spacer(modifier = Modifier.height((40 * scale).dp))

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
                                    .defaultMinSize(minWidth = 1.dp, minHeight = buttonMinHeight),
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
private fun SpeedGauge(
    speed: Double,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier,
    gaugeTextSize: androidx.compose.ui.unit.TextUnit,
    gaugeUnitSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    scale: Float,
) {
    val maxSpeed = 100f
    val fraction = (speed.toFloat() / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(300),
        label = "gauge",
    )

    val arcColor = when {
        speed < 10 -> VpnRed
        speed < 30 -> VpnOrange
        speed < 60 -> VpnGreen
        else -> VpnBlue
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (18 * scale).dp.toPx()
            val padding = strokeWidth / 2 + (8 * scale).dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)

            val startAngle = 150f
            val totalSweep = 240f

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (animatedFraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            val center = Offset(size.width / 2, size.height / 2)
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

            if (phase != SpeedTestPhase.Idle) {
                val needleAngle = Math.toRadians((startAngle + totalSweep * animatedFraction).toDouble())
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
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (phase == SpeedTestPhase.Idle) "0" else "%.1f".format(speed),
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
    }
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
        modifier = modifier,
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
            Text(
                text = value,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                style = tightStyle,
            )
            Text(
                text = unit,
                fontSize = labelSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
        }
    }
}
