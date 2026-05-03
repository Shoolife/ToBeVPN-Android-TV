package com.tobevpn.tv.update

import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.UpdateCheckResult
import com.tobevpn.tv.presentation.theme.VpnGreen

/**
 * Returns the [UpdateViewModel] scoped to the host Activity. Settings'
 * "Check for updates" button and the dialog overlay both have to see the
 * same state instance — without an Activity-scoped owner each composable
 * subtree gets its own ViewModel and the dialog never reacts to the
 * Settings probe result.
 */
@Composable
internal fun rememberAppUpdateViewModel(): UpdateViewModel {
    val owner = LocalActivity.current as? ViewModelStoreOwner
        ?: error("Update dialog requires an Activity context")
    return hiltViewModel(owner)
}

/**
 * Side-effect-only composable: triggers a one-shot GitHub probe the first
 * time the host activity starts. Mount once near the root.
 */
@Composable
fun UpdateBannerCheck(viewModel: UpdateViewModel = rememberAppUpdateViewModel()) {
    LaunchedEffect(Unit) { viewModel.checkOnce() }
}

/**
 * Modal dialog overlay. Renders nothing when state is Idle; otherwise dims
 * the background and shows the appropriate update card centred on screen.
 * Mount once at the activity level so it overlays every NavHost route.
 */
@Composable
fun UpdateBannerHost(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = rememberAppUpdateViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val launchInstall: (android.net.Uri) -> Unit = { localUri ->
        try {
            val contentUri = viewModel.installer.resolveContentUri(localUri)
            viewModel.installer.install(contentUri)
        } catch (_: ActivityNotFoundException) {
            // Some Android TV launchers expose no PackageInstaller; the user
            // can still install via the DownloadManager notification panel.
        }
    }

    if (state is UpdateUiState.Idle) return

    // Scrim swallows clicks behind the dialog. We don't dismiss on outside
    // click because TV remotes don't have a back-button feel for it; user
    // explicitly chooses Later/Cancel/Install.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            UpdateUiState.Idle -> Unit
            is UpdateUiState.Available -> AvailableCard(
                info = s.info,
                onDownload = viewModel::startDownload,
                onDismiss = viewModel::dismiss,
            )
            is UpdateUiState.Downloading -> DownloadingCard(
                info = s.info,
                downloadedBytes = s.downloadedBytes,
                totalBytes = s.totalBytes,
                onCancel = viewModel::dismiss,
            )
            is UpdateUiState.ReadyToInstall -> ReadyCard(
                info = s.info,
                onInstall = {
                    if (viewModel.installer.canInstallSilently()) {
                        launchInstall(s.localUri)
                    } else {
                        runCatching { context.startActivity(viewModel.installer.buildPermissionIntent()) }
                    }
                },
                onDismiss = viewModel::dismiss,
            )
            is UpdateUiState.Failed -> FailedCard(
                reason = s.reason,
                onRetry = viewModel::retry,
                onDismiss = viewModel::dismiss,
            )
        }
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────

@Composable
private fun AvailableCard(
    info: UpdateCheckResult.Available,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard {
        Text(
            text = stringResource(R.string.update_banner_title, info.versionName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = bannerContentColor(),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(12.dp))
            // Download is the recommended action — auto-focus it so the user
            // can hit OK on the remote without first navigating right.
            FocusablePrimaryButton(
                textRes = R.string.update_banner_download,
                onClick = onDownload,
                autoFocus = true,
            )
        }
    }
}

@Composable
private fun DownloadingCard(
    info: UpdateCheckResult.Available,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    DialogCard {
        Text(
            text = stringResource(R.string.update_banner_downloading_title, info.versionName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = bannerContentColor(),
        )
        Spacer(Modifier.height(16.dp))
        val trackColour = bannerContentColor().copy(alpha = 0.18f)
        if (totalBytes > 0L) {
            val fraction = (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                color = VpnGreen,
                trackColor = trackColour,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        } else {
            LinearProgressIndicator(
                color = VpnGreen,
                trackColor = trackColour,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatProgress(downloadedBytes, totalBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = bannerContentColor().copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_cancel, onClick = onCancel, autoFocus = true)
        }
    }
}

@Composable
private fun ReadyCard(
    info: UpdateCheckResult.Available,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard {
        Text(
            text = stringResource(R.string.update_banner_ready_title, info.versionName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = bannerContentColor(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_banner_ready_description),
            style = MaterialTheme.typography.bodyMedium,
            color = bannerContentColor().copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(12.dp))
            FocusablePrimaryButton(
                textRes = R.string.update_banner_install,
                onClick = onInstall,
                autoFocus = true,
            )
        }
    }
}

@Composable
private fun FailedCard(
    reason: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard {
        Text(
            text = stringResource(R.string.update_banner_failed_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = bannerContentColor(),
        )
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = bannerContentColor().copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(12.dp))
            FocusableOutlinedButton(textRes = R.string.update_banner_retry, onClick = onRetry, autoFocus = true)
        }
    }
}

// ─── Card frame & colours ────────────────────────────────────────────────

// Hard-coded greys so the dialog doesn't pick up the violet/pink tint that
// dynamicDarkColorScheme adds to surfaceVariant on Material You devices.
private val BannerDarkBg = Color(0xFF202020)
private val BannerLightBg = Color(0xFFFFFFFF)
private val BannerLightBorder = Color(0xFFD8D8D8)
private val BannerDarkContent = Color(0xFFE4E4E4)
private val BannerLightContent = Color(0xFF1A1A1A)

@Composable
private fun bannerContentColor(): Color =
    if (isSystemInDarkTheme()) BannerDarkContent else BannerLightContent

@Composable
private fun DialogCard(
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) BannerDarkBg else BannerLightBg
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 420.dp, max = 720.dp)
            .padding(horizontal = 32.dp)
            .shadow(elevation = 12.dp, shape = cardShape, clip = false)
            .clip(cardShape)
            .background(containerColor)
            .then(
                if (isDark) Modifier
                else Modifier.border(1.dp, BannerLightBorder, cardShape)
            )
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            content()
        }
    }
}

// ─── Focusable buttons (D-pad navigation) ────────────────────────────────

@Composable
private fun FocusablePrimaryButton(
    textRes: Int,
    onClick: () -> Unit,
    autoFocus: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VpnGreen,
            contentColor = Color.White,
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            },
    ) {
        Text(stringResource(textRes), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FocusableTextButton(
    textRes: Int,
    onClick: () -> Unit,
    autoFocus: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = bannerContentColor().copy(alpha = 0.85f),
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            },
    ) {
        Text(stringResource(textRes))
    }
}

@Composable
private fun FocusableOutlinedButton(
    textRes: Int,
    onClick: () -> Unit,
    autoFocus: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            },
    ) {
        Text(stringResource(textRes), fontWeight = FontWeight.SemiBold)
    }
}

private fun formatProgress(downloaded: Long, total: Long): String {
    val mb = 1024.0 * 1024.0
    val left = String.format("%.1f", downloaded / mb)
    return if (total > 0) {
        val right = String.format("%.1f", total / mb)
        "$left МБ / $right МБ"
    } else {
        "$left МБ"
    }
}
