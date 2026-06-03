package com.tobevpn.tv.presentation.pairing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tobevpn.tv.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tobevpn.tv.presentation.rememberTvScreenScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnRed

@Composable
fun PairingScreen(
    onAuthenticated: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PairingUiState.Success) {
            onAuthenticated()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val scale = rememberTvScreenScale(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            largeScreenBaseline = 650f,
        )

        val pad = maxOf(maxWidth * 0.03f, 16.dp)
        // QR size: purely proportional, no absolute dp cap
        val qrSize = min(maxHeight * 0.7f, maxWidth * 0.4f)
        val gap = maxHeight * 0.015f
        val colSpacing = maxWidth * 0.04f
        val titleSize = (38 * scale).sp
        val instructionSize = (18 * scale).sp
        val statusSize = (19 * scale).sp
        val authWaitSize = (19 * scale).sp
        val doneSize = (43 * scale).sp
        val errorTitleSize = (26 * scale).sp
        val errorBodySize = (17 * scale).sp

        Row(
            modifier = Modifier
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(colSpacing),
        ) {
            // Left side — QR code
            Box(
                modifier = Modifier
                    .size(qrSize)
                    .clip(RoundedCornerShape(qrSize * 0.06f))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
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
                                fontSize = authWaitSize,
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

            // Right side — text
            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.pairing_title),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(gap))
                Text(
                    text = stringResource(
                        if (mode == PairingMode.OWN_ACCOUNT) {
                            R.string.pairing_instruction_own_account
                        } else {
                            R.string.pairing_instruction_other_device
                        },
                    ),
                    fontSize = instructionSize,
                    lineHeight = instructionSize * 1.5f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(gap))
                PairingModeSelector(
                    mode = mode,
                    fontSize = instructionSize,
                    onOwnAccount = { viewModel.selectMode(PairingMode.OWN_ACCOUNT) },
                    onOtherDevice = { viewModel.selectMode(PairingMode.OTHER_DEVICE) },
                )

                val pairingCode = (state as? PairingUiState.WaitingForScan)?.code
                if (pairingCode != null) {
                    Spacer(modifier = Modifier.height(gap))
                    Text(
                        text = stringResource(R.string.pairing_code_label),
                        fontSize = instructionSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = pairingCode,
                        fontSize = statusSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Spacer(modifier = Modifier.height(gap))

                val status = when (val s = state) {
                    is PairingUiState.Loading -> stringResource(
                        if (mode == PairingMode.OWN_ACCOUNT) {
                            R.string.pairing_loading_own_account
                        } else {
                            R.string.pairing_loading_other_device
                        },
                    )
                    is PairingUiState.WaitingForScan -> stringResource(
                        if (mode == PairingMode.OWN_ACCOUNT) {
                            R.string.pairing_waiting_own_account
                        } else {
                            R.string.pairing_waiting_other_device
                        },
                    )
                    is PairingUiState.Authenticating -> ""
                    is PairingUiState.Success -> ""
                    is PairingUiState.Error -> s.message ?: stringResource(s.messageRes)
                }
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        fontSize = statusSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state is PairingUiState.Error) {
                    Spacer(modifier = Modifier.height(gap))
                    val retryFocusRequester = remember { FocusRequester() }
                    var retryFocused by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        runCatching { retryFocusRequester.requestFocus() }
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestCode() },
                        modifier = Modifier
                            .focusRequester(retryFocusRequester)
                            .onFocusChanged { retryFocused = it.isFocused }
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                            .then(
                                if (retryFocused) Modifier.border(
                                    2.dp,
                                    androidx.compose.ui.graphics.Color.White,
                                    RoundedCornerShape(12.dp),
                                ) else Modifier
                            ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            fontSize = statusSize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingModeSelector(
    mode: PairingMode,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onOwnAccount: () -> Unit,
    onOtherDevice: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PairingModeButton(
            text = stringResource(R.string.pairing_mode_own_account),
            selected = mode == PairingMode.OWN_ACCOUNT,
            fontSize = fontSize,
            onClick = onOwnAccount,
        )
        PairingModeButton(
            text = stringResource(R.string.pairing_mode_other_device),
            selected = mode == PairingMode.OTHER_DEVICE,
            fontSize = fontSize,
            onClick = onOtherDevice,
        )
    }
}

@Composable
private fun PairingModeButton(
    text: String,
    selected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(text = text, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(text = text, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QrCode(
    data: String,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(data) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(data) { mutableStateOf(false) }

    LaunchedEffect(data) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 0,
                )
                val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
                val w = matrix.width
                val h = matrix.height
                val pixels = IntArray(w * h) { i ->
                    if (matrix[i % w, i / w]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                }
                Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565).asImageBitmap()
            }.getOrNull()
        }
        if (result != null) bitmap = result else error = true
    }

    when {
        error -> Text(stringResource(R.string.error_generic), color = Color.Red)
        bitmap == null -> CircularProgressIndicator(color = Color.Black)
        else -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = "QR",
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}
