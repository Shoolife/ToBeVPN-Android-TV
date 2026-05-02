package com.tobevpn.tv.update

import android.content.ActivityNotFoundException
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
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
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.UpdateCheckResult

/**
 * Side-effect-only composable: triggers a one-shot GitHub probe the first
 * time the home tree is composed. Pair with [UpdateBannerHost].
 */
@Composable
fun UpdateBannerCheck(viewModel: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.checkOnce() }
}

/**
 * Renders the appropriate update card or nothing when idle. TV variant uses
 * focusable buttons that respond to D-pad center/enter — matches the rest of
 * the TV UI's interaction model.
 */
@Composable
fun UpdateBannerHost(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
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

    when (val s = state) {
        UpdateUiState.Idle -> Unit
        is UpdateUiState.Available -> AvailableCard(
            info = s.info,
            onDownload = viewModel::startDownload,
            onDismiss = viewModel::dismiss,
            modifier = modifier,
        )
        is UpdateUiState.Downloading -> DownloadingCard(
            info = s.info,
            downloadedBytes = s.downloadedBytes,
            totalBytes = s.totalBytes,
            onCancel = viewModel::dismiss,
            modifier = modifier,
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
            modifier = modifier,
        )
        is UpdateUiState.Failed -> FailedCard(
            reason = s.reason,
            onRetry = viewModel::retry,
            onDismiss = viewModel::dismiss,
            modifier = modifier,
        )
    }
}

@Composable
private fun AvailableCard(
    info: UpdateCheckResult.Available,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(modifier) {
        Text(
            text = stringResource(R.string.update_banner_title, info.versionName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (info.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = info.releaseNotes.lineSequence().take(4).joinToString("\n").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            FocusablePrimaryButton(textRes = R.string.update_banner_download, onClick = onDownload)
        }
    }
}

@Composable
private fun DownloadingCard(
    info: UpdateCheckResult.Available,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(modifier) {
        Text(
            text = stringResource(R.string.update_banner_downloading_title, info.versionName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        if (totalBytes > 0L) {
            val fraction = (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatProgress(downloadedBytes, totalBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_cancel, onClick = onCancel)
        }
    }
}

@Composable
private fun ReadyCard(
    info: UpdateCheckResult.Available,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(modifier) {
        Text(
            text = stringResource(R.string.update_banner_ready_title, info.versionName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.update_banner_ready_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            FocusablePrimaryButton(textRes = R.string.update_banner_install, onClick = onInstall)
        }
    }
}

@Composable
private fun FailedCard(
    reason: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(R.string.update_banner_failed_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusableTextButton(textRes = R.string.update_banner_later, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            FocusableOutlinedButton(textRes = R.string.update_banner_retry, onClick = onRetry)
        }
    }
}

@Composable
private fun BannerCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

/**
 * D-pad enter / center triggers the same onClick as a touch tap. White border
 * appears on focus so the TV remote user always sees which control they're on.
 */
@Composable
private fun FocusablePrimaryButton(textRes: Int, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
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
private fun FocusableTextButton(textRes: Int, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
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
private fun FocusableOutlinedButton(textRes: Int, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
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
