package com.tobevpn.tv.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tobevpn.tv.BuildConfig
import com.tobevpn.tv.R

/**
 * "Check for updates" row inside the About card on TV Settings. Same
 * behaviour as the phone variant; the only difference is the button is
 * focusable and responds to D-pad enter/center.
 */
@Composable
fun SettingsUpdateCheckRow(
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    viewModel: UpdateViewModel = rememberAppUpdateViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val inFlight by viewModel.manualCheckInFlight.collectAsState()
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

    var isFocused by remember { mutableStateOf(false) }
    val buttonTextColor = MaterialTheme.colorScheme.onSurface
    val buttonBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = statusText,
            fontSize = fontSize,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        if (BuildConfig.IN_APP_UPDATES_ENABLED) {
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { viewModel.forceCheck() },
                enabled = !inFlight,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = buttonTextColor,
                    disabledContentColor = buttonTextColor.copy(alpha = 0.82f),
                ),
                border = BorderStroke(1.dp, buttonBorderColor),
                modifier = Modifier
                    .then(
                        if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyUp &&
                            (e.key == Key.DirectionCenter || e.key == Key.Enter)
                        ) {
                            viewModel.forceCheck(); true
                        } else false
                    },
            ) {
                if (inFlight) {
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
