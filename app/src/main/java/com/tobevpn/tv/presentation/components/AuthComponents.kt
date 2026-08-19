package com.tobevpn.tv.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tobevpn.tv.presentation.theme.VpnGreen

/**
 * Shared building blocks for the sign-in flow (install, device pairing and the
 * two Telegram variants). They live together so the four screens keep one
 * visual language: a glowing white QR panel, a code chip and a status line,
 * all sized through the caller's TV scale factor.
 */

/**
 * How far the QR halo spreads, as a fraction of the plate's side. The screens
 * need it to work out how much height the whole block takes, so it lives here
 * rather than inline.
 */
const val AUTH_QR_GLOW_FRACTION = 0.11f

/**
 * Paints [accent] inside [full] in the brand green, leaving the rest as-is.
 * The accent word is a separate resource so translators can move it inside
 * the sentence without touching the code.
 */
@Composable
fun accentedText(full: String, accent: String): AnnotatedString = remember(full, accent) {
    val start = full.indexOf(accent)
    buildAnnotatedString {
        if (start < 0) {
            append(full)
            return@buildAnnotatedString
        }
        append(full.substring(0, start))
        withStyle(SpanStyle(color = VpnGreen)) { append(accent) }
        append(full.substring(start + accent.length))
    }
}

/**
 * Layout container for a sign-in screen.
 *
 * It deliberately has no surface or outline: an extra full-screen card made
 * the content look nested and duplicated the TV safe-area frame.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AuthScreenCard(
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}

/**
 * White QR plate with a soft green halo. [footer] renders inside the plate's
 * dark surround — the install screen puts the store badge there.
 */
@Composable
fun AuthQrPanel(
    size: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    // Install screen only: pull the footer into the same panel as the plate.
    // The Telegram screens keep their button as a separate capsule below.
    footerInPanel: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glow = size * AUTH_QR_GLOW_FRACTION
    val plateCorner = size * 0.055f

    val plate: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(plateCorner))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }

    if (footer == null || !footerInPanel) {
        // Bare plate — the halo hugs the QR itself, and any footer sits below
        // it as its own element.
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .drawBehind { drawQrGlow(glowRadius = glow.toPx(), plateCorner = plateCorner.toPx()) },
                contentAlignment = Alignment.Center,
            ) {
                plate()
            }
            if (footer != null) {
                Spacer(modifier = Modifier.height((14 * scale).dp))
                footer()
            }
        }
    } else {
        // Store variant — the plate and the badge share one dark panel and the
        // halo wraps that panel, so the badge reads as part of the QR block
        // rather than as a caption floating underneath it.
        val panelPadding = size * 0.05f
        val panelCorner = size * 0.10f

        Box(
            modifier = modifier
                .drawBehind { drawQrGlow(glowRadius = glow.toPx(), plateCorner = panelCorner.toPx()) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(panelCorner))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(panelPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                plate()
                Spacer(modifier = Modifier.height(panelPadding))
                footer()
            }
        }
    }
}

/**
 * Soft green halo behind the QR plate. Compose has no blur on the API levels
 * these TVs run, so the gradient is built from concentric rounded rectangles:
 * each one is a little smaller and a little more opaque, and stacking them
 * accumulates into a smooth falloff. The innermost ring sits right under the
 * white plate, which hides the flat centre.
 */
private fun DrawScope.drawQrGlow(
    glowRadius: Float,
    plateCorner: Float,
    color: Color = VpnGreen,
    layers: Int = 34,
) {
    if (glowRadius <= 0f) return

    repeat(layers) { index ->
        // 1.0 at the outer edge of the halo, approaching 0 at the plate. The
        // inset is negative: the rings are drawn outside the composable's
        // bounds, which Compose allows because nothing clips here.
        val distance = (layers - index) / layers.toFloat()
        val spread = glowRadius * distance
        val inset = -spread
        // Low per-layer alpha with a gentle exponent: the halo stays faint at
        // the plate edge and keeps a long tail outwards, which reads as blur.
        // A steeper curve or a denser layer looks like a drawn-on ring.
        val alpha = 0.045f * Math.pow((1f - distance).toDouble(), 1.6).toFloat()
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            // A ring sitting `spread` outside the block has to round by exactly
            // that much more, or the outer rings turn into a capsule and stop
            // following the block's shape.
            cornerRadius = CornerRadius(plateCorner + spread),
        )
    }
}

/** "Sign-in code: HKJDH15DDE" chip with the code in the brand green. */
@Composable
fun AuthCodeChip(
    label: String,
    code: String,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape((12 * scale).dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = (1 * scale).dp,
                color = VpnGreen.copy(alpha = 0.55f),
                shape = RoundedCornerShape((12 * scale).dp),
            )
            .padding(horizontal = (26 * scale).dp, vertical = (15 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
    ) {
        Text(
            text = label,
            fontSize = (21 * scale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = code,
            fontSize = (30 * scale).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VpnGreen,
        )
    }
}

/**
 * Green dot plus a short status sentence, e.g. "Waiting for confirmation…".
 * While [pulsing], the dot emits a slow expanding ripple: sign-in confirmation
 * happens on the phone, so the TV screen is otherwise completely static and
 * gives the user no sign that it is still listening.
 */
@Composable
fun AuthStatusRow(
    text: String,
    scale: Float,
    modifier: Modifier = Modifier,
    dotColor: Color = VpnGreen,
    fontSize: TextUnit = (21 * scale).sp,
    pulsing: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
    ) {
        val dotSize = (18 * scale).dp
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "authStatusPulse")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "authStatusPulsePhase",
            )

            Canvas(modifier = Modifier.size(dotSize * 2.2f)) {
                val outer = size.minDimension / 2f
                val core = outer * 0.45f
                // Ripple grows from the core outwards and fades as it goes.
                drawCircle(
                    color = dotColor.copy(alpha = 0.45f * (1f - phase)),
                    radius = core + (outer - core) * phase,
                )
                drawCircle(color = dotColor, radius = core)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }

        Text(
            text = text,
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Focusable action button in two weights: [primary] fills with the brand
 * colour, otherwise it stays a quiet surface. Focus is shown the same way as
 * the rest of the TV UI — a slight scale-up plus an outline — because a D-pad
 * user has no cursor to tell them where they are.
 */
@Composable
fun AuthActionButton(
    text: AnnotatedString,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    pill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var activationPressed by remember { mutableStateOf(false) }
    var longClickTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val longPressScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { longPressJob?.cancel() }
    }
    val buttonScale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "authActionButtonScale",
    )
    val shape = if (pill) CircleShape else RoundedCornerShape((14 * scale).dp)

    // Built from clickable + focusable rather than Surface(onClick): Material3
    // forces a 48dp minimum height on clickable Surfaces, which is a finger
    // target. On a 270dp-tall TV layout three of those fill half the column,
    // so the button height has to come from our own padding.
    val background = when {
        primary -> VpnGreen
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = if (focused) {
        if (primary) Color.White else MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val contentColor = if (primary) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) (2 * scale).dp else (1 * scale).dp,
                color = borderColor,
                shape = shape,
            )
            .onPreviewKeyEvent { event ->
                val callback = onLongClick ?: return@onPreviewKeyEvent false
                val isActivationKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!isActivationKey) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (!activationPressed) {
                            activationPressed = true
                            longClickTriggered = false
                            longPressJob?.cancel()
                            longPressJob = longPressScope.launch {
                                delay(LONG_PRESS_MILLIS)
                                if (activationPressed && !longClickTriggered) {
                                    longClickTriggered = true
                                    callback()
                                }
                            }
                        } else if (!longClickTriggered) {
                            // A repeated KeyDown already proves the key is held.
                            longPressJob?.cancel()
                            longClickTriggered = true
                            callback()
                        }
                        longClickTriggered
                    }

                    KeyEventType.KeyUp -> {
                        val consume = longClickTriggered
                        activationPressed = false
                        longPressJob?.cancel()
                        longPressJob = null
                        consume
                    }

                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = (22 * scale).dp, vertical = (11 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((14 * scale).dp),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.size((24 * scale).dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    leadingIcon()
                }
            }
        }
        Text(
            text = text,
            fontSize = (22 * scale).sp,
            lineHeight = (26 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            style = AuthTightTextStyle,
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/** How long OK must be held for a long-press action to fire. */
private const val LONG_PRESS_MILLIS = 1_500L

/**
 * Text metrics without the extra font padding: on a 270dp-tall TV layout that
 * slack is the difference between a compact button and one that eats a third
 * of the column.
 */
val AuthTightTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** Plain-text overload — most buttons have no accent word. */
@Composable
fun AuthActionButton(
    text: String,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    pill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    AuthActionButton(
        text = AnnotatedString(text),
        scale = scale,
        onClick = onClick,
        modifier = modifier,
        primary = primary,
        pill = pill,
        onLongClick = onLongClick,
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
    )
}
