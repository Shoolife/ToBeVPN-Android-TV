package com.tobevpn.tv.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnGreen

/**
 * Shared presentation for every modal QR code in the TV app.
 *
 * The referral dialog is the visual baseline: a themed surface, a clear white
 * QR plate and the same focusable primary action in both light and dark modes.
 */
@Composable
fun TvQrDialog(
    data: String,
    title: String,
    description: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    contentDescription: String = "QR",
) {
    val actionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { actionFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

            Card(
                modifier = modifier
                    .fillMaxWidth(0.48f)
                    .widthIn(max = (560 * scale).dp),
                shape = RoundedCornerShape((22 * scale).dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    (1 * scale).dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((22 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        fontSize = (22 * scale).sp,
                        lineHeight = (27 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        style = AuthTightTextStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        text = description,
                        fontSize = (14 * scale).sp,
                        lineHeight = (19 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = AuthTightTextStyle,
                    )
                    Spacer(modifier = Modifier.height((15 * scale).dp))
                    Box(
                        modifier = Modifier
                            .size((236 * scale).dp)
                            .clip(RoundedCornerShape((14 * scale).dp))
                            .background(Color.White)
                            .padding((14 * scale).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        QrCode(
                            data = data,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = contentDescription,
                        )
                    }
                    if (!supportingText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height((10 * scale).dp))
                        Text(
                            text = supportingText,
                            fontSize = (13 * scale).sp,
                            lineHeight = (18 * scale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = AuthTightTextStyle,
                        )
                    }
                    Spacer(modifier = Modifier.height((15 * scale).dp))
                    TvQrActionButton(
                        text = actionLabel,
                        onClick = onDismiss,
                        focusRequester = actionFocusRequester,
                        scale = scale,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvQrActionButton(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((11 * scale).dp)

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = (42 * scale).dp)
                .focusRequester(focusRequester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            contentPadding = PaddingValues(
                horizontal = (15 * scale).dp,
                vertical = (7 * scale).dp,
            ),
            border = if (focused) {
                BorderStroke((2 * scale).dp, MaterialTheme.colorScheme.onSurface)
            } else {
                null
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = VpnGreen,
                contentColor = Color.Black,
            ),
        ) {
            Text(
                text = text,
                fontSize = (14 * scale).sp,
                lineHeight = (18 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = AuthTightTextStyle,
            )
        }
    }
}
