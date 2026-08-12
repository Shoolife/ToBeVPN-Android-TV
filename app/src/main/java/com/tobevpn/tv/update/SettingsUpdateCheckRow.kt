package com.tobevpn.tv.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tobevpn.tv.BuildConfig
import com.tobevpn.tv.R

/**
 * "Check for updates" row inside the About card on TV Settings. The GitHub
 * build checks releases manually; the Play build delegates to Play Core's
 * flexible in-app update flow (ported from the phone app).
 */
@Composable
fun SettingsUpdateCheckRow(
    fontSize: TextUnit = TextUnit.Unspecified,
    onWhatsNew: (() -> Unit)? = null,
    whatsNewFocusModifier: Modifier = Modifier,
    checkFocusModifier: Modifier = Modifier,
) {
    if (BuildConfig.PLAY_DISTRIBUTION) {
        PlayStoreSettingsUpdateCheckRow(
            fontSize = fontSize,
            onWhatsNew = onWhatsNew,
            whatsNewFocusModifier = whatsNewFocusModifier,
            checkFocusModifier = checkFocusModifier,
        )
    } else {
        GithubSettingsUpdateCheckRow(
            fontSize = fontSize,
            onWhatsNew = onWhatsNew,
            whatsNewFocusModifier = whatsNewFocusModifier,
            checkFocusModifier = checkFocusModifier,
        )
    }
}

@Composable
private fun GithubSettingsUpdateCheckRow(
    fontSize: TextUnit,
    onWhatsNew: (() -> Unit)?,
    whatsNewFocusModifier: Modifier,
    checkFocusModifier: Modifier,
    viewModel: UpdateViewModel = rememberAppUpdateViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val checkInFlight by viewModel.manualCheckInFlight.collectAsState()
    val versionName = remember { com.tobevpn.tv.BuildConfig.VERSION_NAME }

    val statusText = when (val s = state) {
        is UpdateUiState.Available ->
            stringResource(R.string.update_available_short, versionName, s.info.versionName)
        is UpdateUiState.Downloading,
        is UpdateUiState.ReadyToInstall,
        is UpdateUiState.Failed,
        UpdateUiState.Idle ->
            stringResource(R.string.update_check_uptodate, versionName)
    }

    SettingsUpdateCheckRowContent(
        fontSize = fontSize,
        onWhatsNew = onWhatsNew,
        whatsNewFocusModifier = whatsNewFocusModifier,
        checkFocusModifier = checkFocusModifier,
        statusText = statusText,
        checkInFlight = checkInFlight,
        showCheckButton = BuildConfig.IN_APP_UPDATES_ENABLED,
        onCheck = viewModel::forceCheck,
    )
}

@Composable
private fun PlayStoreSettingsUpdateCheckRow(
    fontSize: TextUnit,
    onWhatsNew: (() -> Unit)?,
    whatsNewFocusModifier: Modifier,
    checkFocusModifier: Modifier,
) {
    val versionName = remember { com.tobevpn.tv.BuildConfig.VERSION_NAME }
    val playUpdateState = rememberPlayStoreUpdateState(enabled = true)
    val statusText = when (val phase = playUpdateState.phase) {
        PlayStoreUpdatePhase.Checking ->
            stringResource(R.string.update_play_checking)
        is PlayStoreUpdatePhase.Downloading ->
            phase.percent?.let {
                stringResource(R.string.update_play_downloading, it)
            } ?: stringResource(R.string.update_play_downloading_indeterminate)
        PlayStoreUpdatePhase.Ready ->
            stringResource(R.string.update_play_ready)
        PlayStoreUpdatePhase.Idle ->
            stringResource(R.string.update_check_uptodate, versionName)
    }

    SettingsUpdateCheckRowContent(
        fontSize = fontSize,
        onWhatsNew = onWhatsNew,
        whatsNewFocusModifier = whatsNewFocusModifier,
        checkFocusModifier = checkFocusModifier,
        statusText = statusText,
        checkInFlight = playUpdateState.busy,
        showCheckButton = true,
        onCheck = playUpdateState::check,
    )

    if (playUpdateState.showReadyPrompt) {
        AlertDialog(
            onDismissRequest = playUpdateState::dismissReadyPrompt,
            title = {
                Text(text = stringResource(R.string.update_play_ready_title))
            },
            text = {
                Text(text = stringResource(R.string.update_play_ready_message))
            },
            confirmButton = {
                TextButton(onClick = playUpdateState::installDownloadedUpdate) {
                    Text(text = stringResource(R.string.update_play_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = playUpdateState::dismissReadyPrompt) {
                    Text(text = stringResource(R.string.update_play_later))
                }
            },
        )
    }
}

@Composable
private fun SettingsUpdateCheckRowContent(
    fontSize: TextUnit,
    onWhatsNew: (() -> Unit)?,
    whatsNewFocusModifier: Modifier,
    checkFocusModifier: Modifier,
    statusText: String,
    checkInFlight: Boolean,
    showCheckButton: Boolean,
    onCheck: () -> Unit,
) {
    var isCheckFocused by remember { mutableStateOf(false) }
    var isWhatsNewFocused by remember { mutableStateOf(false) }
    val buttonTextColor = MaterialTheme.colorScheme.onSurface
    val buttonBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onWhatsNew != null) {
            OutlinedButton(
                onClick = onWhatsNew,
                shape = RoundedCornerShape(10.dp),
                // Offset only the focus outline. The matching inner padding
                // keeps the text aligned with About and XRay while leaving
                // breathing room between the glyphs and the outline.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor),
                border = BorderStroke(
                    width = if (isWhatsNewFocused) 2.dp else 1.dp,
                    color = if (isWhatsNewFocused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Transparent
                    },
                ),
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .weight(1f)
                    .then(whatsNewFocusModifier)
                    .onFocusChanged { isWhatsNewFocused = it.isFocused },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = statusText,
                        fontSize = fontSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.about_whats_new_current),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Text(
                text = statusText,
                fontSize = fontSize,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
        }
        if (showCheckButton) {
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onCheck,
                enabled = !checkInFlight,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = buttonTextColor,
                    disabledContentColor = buttonTextColor.copy(alpha = 0.82f),
                ),
                border = BorderStroke(
                    width = if (isCheckFocused) 2.dp else 1.dp,
                    color = if (isCheckFocused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        buttonBorderColor
                    },
                ),
                modifier = Modifier
                    .then(checkFocusModifier)
                    .onFocusChanged { isCheckFocused = it.isFocused },
            ) {
                if (checkInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.update_check_button),
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
