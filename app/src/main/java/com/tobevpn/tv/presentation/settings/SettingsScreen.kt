package com.tobevpn.tv.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.UserPlan
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.util.LocaleManager
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDevices: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingLanguage by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val cardPad = (24 * scale).dp
        val cardCorner = (16 * scale).dp
        val cardSpacing = (24 * scale).dp
        val gap = (16 * scale).dp
        val smallGap = (8 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (22 * scale).sp
        val bodySize = (16 * scale).sp
        val buttonTextSize = (14 * scale).sp
        val rowPadV = (4 * scale).dp
        val backCorner = (8 * scale).dp
        val borderWidth = (2 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val buttonPadH = (18 * scale).dp
        val buttonPadV = (6 * scale).dp
        val headerColor = MaterialTheme.colorScheme.onBackground

        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var backFocused by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(headerButtonSize)
                            .then(
                                if (backFocused) Modifier.border(borderWidth, Color.White, RoundedCornerShape(backCorner))
                                else Modifier
                            )
                            .onFocusChanged { backFocused = it.isFocused },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(headerIconSize),
                            tint = headerColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(gap))
                Text(
                    stringResource(R.string.settings),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
            }

            Spacer(modifier = Modifier.height(cardPad))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Left column: Account + Language
                Column(modifier = Modifier.weight(1f)) {
                    // Account card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(cardPad)) {
                            Text(
                                stringResource(R.string.account),
                                fontSize = titleSize,
                                fontWeight = FontWeight.SemiBold,
                                style = tightStyle,
                            )
                            Spacer(modifier = Modifier.height(gap))
                            when (authState) {
                                AuthState.Unauthenticated -> {
                                    Text(
                                        stringResource(R.string.not_authorized),
                                        fontSize = bodySize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = tightStyle,
                                    )
                                }
                                is AuthState.Authenticated -> {
                                    val auth = authState as AuthState.Authenticated
                                    InfoRow(stringResource(R.string.telegram_id), "${auth.telegramId}", bodySize = bodySize, rowPadV = rowPadV, tightStyle = tightStyle)

                                    val (planLabel, planColor) = when (auth.plan) {
                                        UserPlan.PAID -> stringResource(R.string.plan_standard) to VpnGreen
                                        UserPlan.ADMIN -> stringResource(R.string.plan_admin) to VpnGreen
                                        UserPlan.EXPIRED -> stringResource(R.string.plan_expired) to VpnRed
                                        UserPlan.FREE_TRIAL -> stringResource(R.string.plan_free) to VpnOrange
                                    }
                                    InfoRow(stringResource(R.string.plan), planLabel, planColor, bodySize, rowPadV, tightStyle)

                                    if (auth.plan == UserPlan.PAID && auth.planExpiresAt != null) {
                                        InfoRow(stringResource(R.string.expires), formatDate(auth.planExpiresAt), bodySize = bodySize, rowPadV = rowPadV, tightStyle = tightStyle)
                                    }
                                    if (auth.plan == UserPlan.EXPIRED) {
                                        Spacer(modifier = Modifier.height(smallGap))
                                        Text(
                                            stringResource(R.string.renew_in_bot),
                                            fontSize = bodySize,
                                            color = VpnRed,
                                            style = tightStyle,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(gap))
                                    AccountActionButton(
                                        label = stringResource(R.string.logout),
                                        onClick = { viewModel.logout() },
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                        bodySize = bodySize,
                                        scale = scale,
                                        buttonPadH = buttonPadH,
                                        buttonPadV = buttonPadV,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(cardSpacing))

                    // Language picker card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(cardPad)) {
                            Text(
                                stringResource(R.string.language),
                                fontSize = titleSize,
                                fontWeight = FontWeight.SemiBold,
                                style = tightStyle,
                            )
                            Spacer(modifier = Modifier.height(gap))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LanguageChip(
                                    label = stringResource(R.string.language_english),
                                    selected = language == LocaleManager.LANG_EN,
                                    onClick = {
                                        if (language != LocaleManager.LANG_EN) pendingLanguage = LocaleManager.LANG_EN
                                    },
                                    bodySize = bodySize,
                                    scale = scale,
                                )
                                Spacer(modifier = Modifier.width((12 * scale).dp))
                                LanguageChip(
                                    label = stringResource(R.string.language_russian),
                                    selected = language == LocaleManager.LANG_RU,
                                    onClick = {
                                        if (language != LocaleManager.LANG_RU) pendingLanguage = LocaleManager.LANG_RU
                                    },
                                    bodySize = bodySize,
                                    scale = scale,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(cardSpacing))

                // Right column: About + Devices
                Column(modifier = Modifier.weight(1f)) {
                    // About card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(cardPad)) {
                            Text(
                                stringResource(R.string.about),
                                fontSize = titleSize,
                                fontWeight = FontWeight.SemiBold,
                                style = tightStyle,
                            )
                            Spacer(modifier = Modifier.height(gap))
                            // Version + "Check for updates" merged into a single
                            // row to stop duplicating the current version.
                            com.tobevpn.tv.update.SettingsUpdateCheckRow(fontSize = bodySize)
                            Spacer(modifier = Modifier.height(gap))
                            InfoRow(stringResource(R.string.xray), viewModel.xrayVersion, bodySize = bodySize, rowPadV = rowPadV, tightStyle = tightStyle)
                        }
                    }

                    // Linked devices card, sitting directly under About to fill
                    // the right column (mirrors the dedicated section on phone/desktop).
                    if (authState is AuthState.Authenticated) {
                        Spacer(modifier = Modifier.height(cardSpacing))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(cardCorner),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(cardPad)) {
                                Text(
                                    stringResource(R.string.devices_title),
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.SemiBold,
                                    style = tightStyle,
                                )
                                Spacer(modifier = Modifier.height(smallGap))
                                Text(
                                    stringResource(R.string.devices_manage_hint),
                                    fontSize = bodySize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = tightStyle,
                                )
                                Spacer(modifier = Modifier.height(gap))
                                AccountActionButton(
                                    label = stringResource(R.string.devices_manage),
                                    onClick = onNavigateToDevices,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    bodySize = bodySize,
                                    scale = scale,
                                    buttonPadH = buttonPadH,
                                    buttonPadV = buttonPadV,
                                )
                            }
                        }
                    }
                }
            }
        }

        val pending = pendingLanguage
        if (pending != null) {
            AlertDialog(
                onDismissRequest = { pendingLanguage = null },
                title = { Text(stringResource(R.string.language_restart_title)) },
                text = { Text(stringResource(R.string.language_restart_message)) },
                confirmButton = {
                    var confirmFocused by remember { mutableStateOf(false) }
                    val confirmShape = RoundedCornerShape((10 * scale).dp)
                    Button(
                        onClick = {
                            viewModel.setLanguage(pending)
                            pendingLanguage = null
                            LocaleManager.restartApp(context)
                        },
                        shape = confirmShape,
                        modifier = Modifier
                            .then(
                                if (confirmFocused) Modifier.border((2 * scale).dp, Color.White, confirmShape)
                                else Modifier
                            )
                            .onFocusChanged { confirmFocused = it.isFocused },
                    ) {
                        Text(stringResource(R.string.language_restart_button))
                    }
                },
                dismissButton = {
                    var cancelFocused by remember { mutableStateOf(false) }
                    val cancelShape = RoundedCornerShape((10 * scale).dp)
                    TextButton(
                        onClick = { pendingLanguage = null },
                        shape = cancelShape,
                        modifier = Modifier
                            .then(
                                if (cancelFocused) Modifier.border((2 * scale).dp, Color.White, cancelShape)
                                else Modifier
                            )
                            .onFocusChanged { cancelFocused = it.isFocused },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun AccountActionButton(
    label: String,
    onClick: () -> Unit,
    contentColor: Color,
    bodySize: androidx.compose.ui.unit.TextUnit,
    scale: Float,
    buttonPadH: androidx.compose.ui.unit.Dp,
    buttonPadV: androidx.compose.ui.unit.Dp,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
            .then(
                if (focused) Modifier.border((2 * scale).dp, Color.White, shape)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused },
        shape = shape,
        contentPadding = PaddingValues(horizontal = buttonPadH, vertical = buttonPadV),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        Text(label, fontSize = bodySize)
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    scale: Float,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                .then(
                    if (focused) Modifier.border((2 * scale).dp, Color.White, shape)
                    else Modifier
                )
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            contentPadding = PaddingValues(horizontal = (16 * scale).dp, vertical = (6 * scale).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VpnGreen,
                contentColor = Color.Black,
            ),
        ) {
            Text(label, fontSize = bodySize)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                .then(
                    if (focused) Modifier.border((2 * scale).dp, Color.White, shape)
                    else Modifier
                )
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            contentPadding = PaddingValues(horizontal = (16 * scale).dp, vertical = (6 * scale).dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(label, fontSize = bodySize)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    bodySize: androidx.compose.ui.unit.TextUnit,
    rowPadV: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rowPadV),
    ) {
        Text(
            text = label,
            fontSize = bodySize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            style = tightStyle,
        )
        Text(
            text = value,
            fontSize = bodySize,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            style = tightStyle,
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getDefault()
    return sdf.format(java.util.Date(epochMillis))
}
