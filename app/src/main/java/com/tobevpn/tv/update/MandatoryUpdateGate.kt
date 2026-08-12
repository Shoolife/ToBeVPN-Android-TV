package com.tobevpn.tv.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tobevpn.tv.BuildConfig
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.theme.VpnGreen

/** Global, non-dismissible minimum-version gate mounted above the NavHost. */
@Composable
fun MandatoryUpdateGate(
    updateRequired: Boolean,
    onQuit: () -> Unit,
) {
    if (!updateRequired) return
    if (BuildConfig.PLAY_DISTRIBUTION) {
        PlayStoreRequiredUpdateDialog(onQuit = onQuit)
    } else {
        DirectRequiredUpdateDialog(onQuit = onQuit)
    }
}

@Composable
private fun PlayStoreRequiredUpdateDialog(onQuit: () -> Unit) {
    val context = LocalContext.current
    MandatoryUpdateDialogFrame(
        title = stringResource(R.string.update_required_title),
        message = stringResource(R.string.update_required_play_store_message),
        confirmLabel = stringResource(R.string.update_required_button),
        confirmEnabled = true,
        showProgress = false,
        progress = null,
        onConfirm = { openPlayStore(context) },
        onQuit = onQuit,
    )
}

@Composable
private fun DirectRequiredUpdateDialog(
    onQuit: () -> Unit,
    viewModel: UpdateViewModel = rememberAppUpdateViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val checkInFlight by viewModel.manualCheckInFlight.collectAsState()

    LaunchedEffect(Unit) {
        if (state is UpdateUiState.Idle) viewModel.forceCheck()
    }

    val title: String
    val message: String
    val confirmLabel: String?
    val confirmAction: (() -> Unit)?
    val progress: Float?
    val showProgress: Boolean

    when (val current = state) {
        UpdateUiState.Idle -> {
            title = stringResource(R.string.update_required_title)
            message = if (checkInFlight) {
                stringResource(R.string.update_required_checking_github)
            } else {
                stringResource(R.string.update_required_message)
            }
            confirmLabel = if (checkInFlight) null else stringResource(R.string.update_banner_retry)
            confirmAction = if (checkInFlight) null else viewModel::forceCheck
            progress = null
            showProgress = checkInFlight
        }
        is UpdateUiState.Available -> {
            title = stringResource(R.string.update_banner_title, current.info.versionName)
            message = stringResource(R.string.update_required_message)
            confirmLabel = stringResource(R.string.update_banner_download)
            confirmAction = viewModel::startDownload
            progress = null
            showProgress = false
        }
        is UpdateUiState.Downloading -> {
            title = stringResource(R.string.update_banner_downloading_title, current.info.versionName)
            message = formatMandatoryProgress(current.downloadedBytes, current.totalBytes)
            confirmLabel = null
            confirmAction = null
            progress = if (current.totalBytes > 0L) {
                (current.downloadedBytes.toDouble() / current.totalBytes.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            } else null
            showProgress = true
        }
        is UpdateUiState.ReadyToInstall -> {
            title = stringResource(R.string.update_banner_ready_title, current.info.versionName)
            message = stringResource(R.string.update_banner_ready_description)
            confirmLabel = stringResource(R.string.update_banner_install)
            confirmAction = {
                runCatching {
                    if (viewModel.installer.canInstallSilently()) {
                        viewModel.installer.install(
                            viewModel.installer.resolveContentUri(current.localUri),
                        )
                    } else {
                        viewModel.installer.contextualPermissionRequest()
                    }
                }
            }
            progress = 1f
            showProgress = false
        }
        is UpdateUiState.Failed -> {
            title = stringResource(R.string.update_banner_failed_title)
            message = current.reason.take(200).ifBlank {
                stringResource(R.string.update_required_github_failed)
            }
            confirmLabel = stringResource(R.string.update_banner_retry)
            confirmAction = viewModel::retry
            progress = null
            showProgress = false
        }
    }

    MandatoryUpdateDialogFrame(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        confirmEnabled = !checkInFlight,
        showProgress = showProgress,
        progress = progress,
        onConfirm = { confirmAction?.invoke() },
        onQuit = onQuit,
    )
}

@Composable
private fun MandatoryUpdateDialogFrame(
    title: String,
    message: String,
    confirmLabel: String?,
    confirmEnabled: Boolean,
    showProgress: Boolean,
    progress: Float?,
    onConfirm: () -> Unit,
    onQuit: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (dark) Color(0xFF202020) else Color.White
    val outline = if (dark) Color(0xFF494949) else Color(0xFFD2D4D8)
    val primaryText = if (dark) Color(0xFFF2F2F2) else Color(0xFF1A1C1E)
    val secondaryText = if (dark) Color(0xFFB8B8B8) else Color(0xFF5C5E6A)
    val quitFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    var quitFocused by remember { mutableStateOf(false) }
    var confirmFocused by remember { mutableStateOf(false) }

    LaunchedEffect(confirmLabel, confirmEnabled) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching {
            if (confirmLabel != null && confirmEnabled) confirmFocus.requestFocus()
            else quitFocus.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.48f).widthIn(max = 580.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = background),
                border = BorderStroke(1.dp, outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showProgress && progress == null) {
                        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = VpnGreen,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        title,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(9.dp))
                    if (showProgress && progress != null) {
                        val animated by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(400, easing = LinearEasing),
                            label = "mandatory-update-progress",
                        )
                        LinearProgressIndicator(
                            progress = { animated },
                            color = VpnGreen,
                            trackColor = primaryText.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        message,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onQuit,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .focusRequester(quitFocus)
                                .focusProperties { right = confirmFocus }
                                .onFocusChanged { quitFocused = it.isFocused },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                if (quitFocused) 2.dp else 1.dp,
                                if (quitFocused) primaryText else outline,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryText),
                        ) {
                            Text(stringResource(R.string.update_required_quit), maxLines = 1)
                        }
                        if (confirmLabel != null) {
                            Button(
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .focusRequester(confirmFocus)
                                    .focusProperties { left = quitFocus }
                                    .onFocusChanged { confirmFocused = it.isFocused },
                                shape = RoundedCornerShape(10.dp),
                                border = if (confirmFocused) BorderStroke(2.dp, primaryText) else null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VpnGreen,
                                    contentColor = Color.Black,
                                ),
                            ) {
                                Text(
                                    confirmLabel,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun formatMandatoryProgress(downloaded: Long, total: Long): String {
    val downloadedMb = String.format("%.1f", downloaded / (1024.0 * 1024.0))
    if (total <= 0L) return "$downloadedMb MB"
    val totalMb = String.format("%.1f", total / (1024.0 * 1024.0))
    return "$downloadedMb MB / $totalMb MB"
}
