package com.tobevpn.tv.presentation.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.components.AUTH_QR_GLOW_FRACTION
import com.tobevpn.tv.presentation.components.AuthActionButton
import com.tobevpn.tv.presentation.components.AuthCodeChip
import com.tobevpn.tv.presentation.components.AuthQrPanel
import com.tobevpn.tv.presentation.components.AuthScreenCard
import com.tobevpn.tv.presentation.components.AuthStatusRow
import com.tobevpn.tv.presentation.components.AuthTightTextStyle
import com.tobevpn.tv.presentation.components.QrCode
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.components.accentedText
import com.tobevpn.tv.presentation.navigation.PairingEntry
import com.tobevpn.tv.presentation.rememberTvProportionalScale
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnRed

/**
 * Sign-in screen in three flavours, picked by the button the user pressed on
 * the install screen: pairing with the phone app (QR plus a typed code), or
 * Telegram for an iPhone / for no phone at all. The transport differences live
 * in [PairingViewModel]; this screen only swaps copy and the Telegram button.
 */
@Composable
fun PairingScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry = viewModel.entry
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocusRequester.requestFocus() }
    }

    LaunchedEffect(state) {
        if (state is PairingUiState.Success) {
            onAuthenticated()
        }
    }

    // Hidden entry point for Google Play review: hold OK on Back to open a
    // PIN-gated demo login that never touches the real backend. Counting taps
    // cannot work on this button — the first tap already leaves the screen.
    var showDemoLoginDialog by remember { mutableStateOf(false) }
    val onBackLongPress: () -> Unit = { showDemoLoginDialog = true }

    if (showDemoLoginDialog) {
        DemoLoginDialog(
            onDismiss = { showDemoLoginDialog = false },
            onSubmit = { pin ->
                if (pin == DEMO_LOGIN_PIN) {
                    showDemoLoginDialog = false
                    viewModel.completeDemoLogin()
                    true
                } else {
                    false
                }
            },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val scale = rememberTvProportionalScale(maxHeight = maxHeight)

        // Same adaptive sizing as the rest of the TV UI: one scale factor, every
        // dimension as (n * scale). The Telegram variants carry a caption under
        // the plate, so their QR is a step smaller.
        val horizontalScreenPad = (44 * scale).dp
        val verticalScreenPad = (36 * scale).dp
        val gap = (11 * scale).dp
        val qrSize = (maxHeight * (if (entry.usesTelegram()) 0.72f else 0.70f))
            .coerceAtMost(maxWidth * (if (entry.usesTelegram()) 0.44f else 0.40f))
        val colSpacing = (40 * scale).dp
        val titleSize = (40 * scale).sp
        val instructionSize = (21 * scale).sp
        val statusSize = (21 * scale).sp
        val doneSize = (46 * scale).sp
        val errorTitleSize = (28 * scale).sp
        val errorBodySize = (18 * scale).sp

        AuthScreenCard(
            scale = scale,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = horizontalScreenPad,
                        vertical = verticalScreenPad,
                    ),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(colSpacing),
                ) {
                    AuthQrPanel(
                        size = qrSize,
                        scale = scale,
                        footer = if (entry.usesTelegram()) {
                            {
                                TelegramBotCaption(
                                    scale = scale,
                                    width = qrSize,
                                    modifier = Modifier.offset(y = (6 * scale).dp),
                                )
                            }
                        } else {
                            null
                        },
                    ) {
                        when (val s = state) {
                            is PairingUiState.Loading -> {
                                CircularProgressIndicator(color = Color.Black)
                            }

                            is PairingUiState.WaitingForScan -> {
                                QrCode(
                                    data = s.qrData,
                                    modifier = Modifier
                                        .padding(qrSize * 0.05f)
                                        .fillMaxSize(),
                                )
                            }

                            is PairingUiState.Authenticating -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.Black)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.auth_waiting),
                                        color = Color.Black,
                                        fontSize = statusSize,
                                    )
                                }
                            }

                            is PairingUiState.Success -> {
                                Text(
                                    text = stringResource(R.string.pairing_done),
                                    color = VpnGreen,
                                    fontSize = doneSize,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            is PairingUiState.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(20.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.error_generic),
                                        color = VpnRed,
                                        fontSize = errorTitleSize,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = s.message ?: stringResource(s.messageRes),
                                        color = Color.Black,
                                        fontSize = errorBodySize,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = accentedText(
                                full = stringResource(entry.titleRes()),
                                accent = if (entry == PairingEntry.NO_PHONE) {
                                    stringResource(R.string.pairing_title_telegram_accent)
                                } else {
                                    ""
                                },
                            ),
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = titleSize * 1.12f,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(gap))

                        Text(
                            text = stringResource(entry.instructionRes()),
                            fontSize = instructionSize,
                            lineHeight = instructionSize * 1.5f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val pairingCode = (state as? PairingUiState.WaitingForScan)?.code
                        if (pairingCode != null) {
                            Spacer(modifier = Modifier.height(gap))
                            AuthCodeChip(
                                label = stringResource(R.string.pairing_code_label),
                                code = pairingCode,
                                scale = scale,
                            )
                        }

                        val status = when (val s = state) {
                            is PairingUiState.Loading -> stringResource(entry.loadingRes())
                            is PairingUiState.WaitingForScan -> stringResource(entry.waitingRes())
                            is PairingUiState.Authenticating -> ""
                            is PairingUiState.Success -> ""
                            is PairingUiState.Error -> s.message ?: stringResource(s.messageRes)
                        }
                        if (status.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(gap))
                            AuthStatusRow(
                                text = status,
                                scale = scale,
                                dotColor = if (state is PairingUiState.Error) VpnRed else VpnGreen,
                                fontSize = statusSize,
                                pulsing = state !is PairingUiState.Error,
                            )
                        }

                        if (state is PairingUiState.Error) {
                            Spacer(modifier = Modifier.height(gap))
                            val retryFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                runCatching { retryFocusRequester.requestFocus() }
                            }
                            AuthActionButton(
                                text = stringResource(R.string.retry),
                                scale = scale,
                                modifier = Modifier
                                    .widthIn(max = (280 * scale).dp)
                                    .focusRequester(retryFocusRequester),
                                onClick = { viewModel.requestCode() },
                            )
                        }

                        Spacer(modifier = Modifier.height(gap))
                        AuthActionButton(
                            text = stringResource(R.string.back),
                            scale = scale,
                            modifier = Modifier
                                .widthIn(max = (280 * scale).dp)
                                .focusRequester(backFocusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            onClick = onBack,
                            onLongClick = onBackLongPress,
                        )
                    }
                }
            }
        }
    }
}

private fun PairingEntry.usesTelegram(): Boolean =
    this == PairingEntry.IPHONE || this == PairingEntry.NO_PHONE

private fun PairingEntry.titleRes(): Int = when (this) {
    PairingEntry.MOBILE_APP -> R.string.pairing_title_app
    PairingEntry.IPHONE -> R.string.pairing_title_iphone
    PairingEntry.NO_PHONE -> R.string.pairing_title_telegram
}

private fun PairingEntry.instructionRes(): Int = when (this) {
    PairingEntry.MOBILE_APP -> R.string.pairing_instruction_app
    PairingEntry.IPHONE -> R.string.pairing_instruction_iphone
    PairingEntry.NO_PHONE -> R.string.pairing_instruction_telegram
}

private fun PairingEntry.loadingRes(): Int = when (this) {
    PairingEntry.MOBILE_APP -> R.string.pairing_loading_app
    PairingEntry.IPHONE, PairingEntry.NO_PHONE -> R.string.pairing_loading_telegram
}

private fun PairingEntry.waitingRes(): Int = when (this) {
    PairingEntry.MOBILE_APP -> R.string.pairing_waiting_app
    PairingEntry.IPHONE, PairingEntry.NO_PHONE -> R.string.pairing_waiting_telegram
}

/**
 * Caption under the QR saying where the code leads. Deliberately neither
 * focusable nor clickable: a TV has no Telegram to open, and a capsule that
 * looks pressable but does nothing on OK reads as a broken button.
 */
@Composable
private fun TelegramBotCaption(
    scale: Float,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(width)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = (14 * scale).dp, vertical = (13 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centre the pair inside the pill instead of hugging the left edge.
        horizontalArrangement = Arrangement.spacedBy(
            (12 * scale).dp,
            Alignment.CenterHorizontally,
        ),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_telegram),
            contentDescription = null,
            modifier = Modifier.size((30 * scale).dp),
        )
        Text(
            text = accentedText(
                full = stringResource(R.string.pairing_open_telegram_bot),
                accent = stringResource(R.string.pairing_open_telegram_bot_accent),
            ),
            // The pill is as wide as the QR plate, which is sized to keep this
            // caption on one line at full size.
            fontSize = (22 * scale).sp,
            lineHeight = (26 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface,
            // Without trimming, the font's own padding sits inside the pill and
            // makes the vertical spacing look uneven.
            style = AuthTightTextStyle,
        )
    }
}

// Not a real secret — this only gates a local-only stub session used for
// Google Play review, never a backend account. See AuthRepository.completeDemoLogin.
private const val DEMO_LOGIN_PIN = "483920"

@Composable
private fun DemoLoginDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { fieldFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.demo_login_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.demo_login_description))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        showError = false
                    },
                    modifier = Modifier.focusRequester(fieldFocusRequester),
                    singleLine = true,
                    isError = showError,
                    label = { Text(text = stringResource(R.string.demo_login_pin_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { showError = !onSubmit(pin) },
                    ),
                    textStyle = TextStyle(fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.demo_login_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showError = !onSubmit(pin) }) {
                Text(text = stringResource(R.string.demo_login_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.demo_login_cancel))
            }
        },
    )
}
