package com.tobevpn.tv.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnRed

/**
 * Confirmation dialog designed for a TV remote. It owns a full-screen scrim
 * and draws focus through each button's own border, avoiding stacked outlines.
 */
@Composable
fun TvConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    darkTheme: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    var cancelFocused by remember { mutableStateOf(false) }
    var confirmFocused by remember { mutableStateOf(false) }

    val dialogBackground = if (darkTheme) Color(0xFF202020) else Color.White
    val buttonBackground = if (darkTheme) Color(0xFF292929) else Color(0xFFEDEEF0)
    val outlineColor = if (darkTheme) Color(0xFF494949) else Color(0xFFD2D4D8)
    val primaryText = if (darkTheme) Color(0xFFF2F2F2) else Color(0xFF1A1C1E)
    val secondaryText = if (darkTheme) Color(0xFFB8B8B8) else Color(0xFF5C5E6A)
    val confirmColor = if (destructive) VpnRed else VpnGreen
    val confirmText = if (destructive) Color.White else Color.Black

    LaunchedEffect(Unit) {
        // The safe action is focused first so Enter cannot accidentally confirm.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { cancelFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .widthIn(max = 500.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = dialogBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = BorderStroke(1.dp, outlineColor),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = secondaryText,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val buttonShape = RoundedCornerShape(10.dp)
                        OutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .width(150.dp)
                                .height(44.dp)
                                .focusRequester(cancelFocusRequester)
                                .focusProperties { right = confirmFocusRequester }
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionRight
                                    ) {
                                        confirmFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { cancelFocused = it.isFocused },
                            shape = buttonShape,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(
                                width = if (cancelFocused) 2.dp else 1.dp,
                                color = if (cancelFocused) primaryText else outlineColor,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = buttonBackground,
                                contentColor = primaryText,
                            ),
                        ) {
                            Text(
                                text = cancelLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .width(150.dp)
                                .height(44.dp)
                                .focusRequester(confirmFocusRequester)
                                .focusProperties { left = cancelFocusRequester }
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionLeft
                                    ) {
                                        cancelFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { confirmFocused = it.isFocused },
                            shape = buttonShape,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = if (confirmFocused) {
                                BorderStroke(2.dp, primaryText)
                            } else {
                                null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = confirmColor,
                                contentColor = confirmText,
                            ),
                        ) {
                            Text(
                                text = confirmLabel,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 12.sp,
                                    maxFontSize = 15.sp,
                                    stepSize = 0.5.sp,
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
