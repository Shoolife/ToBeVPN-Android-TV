package com.tobevpn.tv.presentation.main

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.domain.model.UsageInfo
import com.tobevpn.tv.domain.model.UserPlan
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.countryFlagForUi
import com.tobevpn.tv.presentation.components.subscriptionExpiryDateColor
import com.tobevpn.tv.presentation.components.textWithAccentedDate
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.serverCountryNameForUi
import com.tobevpn.tv.presentation.serverDisplayName
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    onNavigateToServers: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSubscription: (selectCurrentPlan: Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSpeedTest: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val usageInfo by viewModel.usageInfo.collectAsStateWithLifecycle()
    val sessionBytes by viewModel.sessionBytes.collectAsStateWithLifecycle()
    val sessionTimeSeconds by viewModel.sessionTimeSeconds.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val currentServer by viewModel.currentServer.collectAsStateWithLifecycle()
    val automaticServerSelection by viewModel.automaticServerSelection.collectAsStateWithLifecycle()
    val subscriptionUsageBlocked by viewModel.subscriptionUsageBlocked.collectAsStateWithLifecycle()
    val subscriptionReminderSnooze by
        viewModel.subscriptionReminderSnooze.collectAsStateWithLifecycle()
    var showBlockedDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    // Re-sync on every resume (e.g. after payment in Telegram)
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleConnection()
        }
    }

    val onConnectClick: () -> Unit = {
        if (subscriptionUsageBlocked) {
            showBlockedDialog = true
        } else {
            val currentActivity = activity
            if (currentActivity != null) {
                val vpnIntent = viewModel.getVpnPermissionIntent(currentActivity)
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent)
                } else {
                    viewModel.toggleConnection()
                }
            } else {
                viewModel.toggleConnection()
            }
        }
    }

    // TV layout: horizontal split — left side connect button, right side cards
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPadding = maxOf(maxWidth * 0.04f, 16.dp)
        val leftGap1 = (maxHeight * 0.04f) * scale
        val leftGap2 = (maxHeight * 0.03f) * scale
        val colSpacing = maxWidth * 0.03f
        val cardGap = (maxHeight * 0.025f) * scale
        val cardPad = (20 * scale).dp
        val cardCorner = (16 * scale).dp
        val cardIconSize = (32 * scale).dp
        val cardSpacing = (16 * scale).dp
        val flagSize = (36 * scale).sp
        val leftVerticalPadding = maxOf(
            screenPadding - (28 * scale).dp,
            0.dp,
        )

        // Tight text style
        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        // Font sizes
        val headlineSize = (24 * scale).sp
        val titleSize = (16 * scale).sp
        val bodySize = (14 * scale).sp
        val labelSize = (12 * scale).sp
        val statValueSize = (22 * scale).sp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = screenPadding),
        ) {
            // Left panel — connect button + status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = leftVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ConnectButtonLarge(
                    connectionState = connectionState,
                    onClick = onConnectClick,
                    scale = scale,
                )

                Spacer(modifier = Modifier.height(leftGap1))

                val (statusText, statusColor) = when (connectionState) {
                    is ConnectionState.Disconnected -> stringResource(R.string.state_disconnected) to MaterialTheme.colorScheme.onSurfaceVariant
                    is ConnectionState.Connecting -> stringResource(R.string.state_connecting) to VpnOrange
                    is ConnectionState.Connected -> stringResource(R.string.state_connected) to VpnGreen
                    is ConnectionState.Error -> (connectionState as ConnectionState.Error).message to VpnRed
                }
                Text(
                    text = statusText,
                    fontSize = headlineSize,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    style = tightStyle,
                )

                Spacer(modifier = Modifier.height(leftGap2 * 0.5f))

                (authState as? AuthState.Authenticated)?.let { auth ->
                    SubscriptionReminderBanner(
                        auth = auth,
                        snoozedUntilMillis = subscriptionReminderSnooze.untilMillis,
                        snoozedForExpiryMillis = subscriptionReminderSnooze.expiresAtMillis,
                        onRenew = { onNavigateToSubscription(true) },
                        onSnooze = {
                            viewModel.snoozeSubscriptionReminder(auth.planExpiresAt)
                        },
                        scale = scale,
                        cardPad = cardPad,
                        cardCorner = cardCorner,
                        titleSize = titleSize,
                        labelSize = labelSize,
                        tightStyle = tightStyle,
                    )
                }

                Spacer(modifier = Modifier.height(cardGap))

                // Server selector
                ServerSelectorCard(
                    server = currentServer,
                    automatic = automaticServerSelection,
                    onClick = onNavigateToServers,
                    scale = scale,
                    cardPad = cardPad,
                    cardCorner = cardCorner,
                    cardSpacing = cardSpacing,
                    flagSize = flagSize,
                    titleSize = titleSize,
                    bodySize = bodySize,
                    labelSize = labelSize,
                    tightStyle = tightStyle,
                )
            }

            Spacer(modifier = Modifier.width(colSpacing))

            // Right panel — info cards
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = screenPadding),
                verticalArrangement = Arrangement.Center,
            ) {

            TrafficCard(
                usageInfo = usageInfo,
                sessionBytes = sessionBytes,
                sessionTimeSeconds = sessionTimeSeconds,
                authState = authState,
                onClick = onNavigateToStats,
                scale = scale,
                cardPad = cardPad,
                cardCorner = cardCorner,
                cardSpacing = cardSpacing,
                titleSize = titleSize,
                labelSize = labelSize,
                statValueSize = statValueSize,
                tightStyle = tightStyle,
            )

            Spacer(modifier = Modifier.height(cardGap))

            TvMenuCard(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.speed_test_title),
                subtitle = AnnotatedString(stringResource(R.string.speed_test_subtitle)),
                onClick = onNavigateToSpeedTest,
                scale = scale,
                cardPad = cardPad,
                cardCorner = cardCorner,
                cardSpacing = cardSpacing,
                iconSize = cardIconSize,
                titleSize = titleSize,
                bodySize = bodySize,
                tightStyle = tightStyle,
            )

            Spacer(modifier = Modifier.height(cardGap))

            // Auth / Plan
            when (authState) {
                AuthState.Unauthenticated -> {
                    TvMenuCard(
                        icon = Icons.Default.Star,
                        title = stringResource(R.string.subscription),
                        subtitle = AnnotatedString(stringResource(R.string.sign_in_required_hint)),
                        onClick = onNavigateToPairing,
                        scale = scale,
                        cardPad = cardPad,
                        cardCorner = cardCorner,
                        cardSpacing = cardSpacing,
                        iconSize = cardIconSize,
                        titleSize = titleSize,
                        bodySize = bodySize,
                        tightStyle = tightStyle,
                    )
                }
                is AuthState.Authenticated -> {
                    val auth = authState as AuthState.Authenticated
                    val serverPlanName = auth.planDisplayName?.takeIf {
                        it.isNotBlank() && auth.plan != UserPlan.EXPIRED
                    }
                    val (planLabel, planColor) = when (auth.plan) {
                        UserPlan.PAID -> (serverPlanName ?: stringResource(R.string.plan_unknown_name)) to VpnGreen
                        UserPlan.ADMIN -> (serverPlanName ?: stringResource(R.string.plan_unknown_name)) to VpnGreen
                        UserPlan.EXPIRED -> stringResource(R.string.plan_expired) to VpnRed
                        UserPlan.FREE_TRIAL -> (serverPlanName ?: stringResource(R.string.plan_free)) to VpnOrange
                    }
                    val planSubtitle = when (auth.plan) {
                        UserPlan.PAID,
                        UserPlan.ADMIN,
                        -> auth.planExpiresAt?.let { expiresAt ->
                            val date = formatDate(expiresAt)
                            val text = stringResource(R.string.plan_until, date)
                            textWithAccentedDate(
                                text = text,
                                date = date,
                                dateColor = subscriptionExpiryDateColor(
                                    expiresAtMillis = expiresAt,
                                    normalColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        .copy(alpha = 0.7f),
                                ),
                            )
                        } ?: AnnotatedString("")
                        UserPlan.EXPIRED -> AnnotatedString(stringResource(R.string.plan_renew))
                        UserPlan.FREE_TRIAL -> AnnotatedString(
                            stringResource(R.string.plan_limited_traffic),
                        )
                    }
                    TvMenuCard(
                        icon = Icons.Default.Star,
                        title = planLabel,
                        subtitle = planSubtitle,
                        titleColor = planColor,
                        onClick = { onNavigateToSubscription(false) },
                        scale = scale,
                        cardPad = cardPad,
                        cardCorner = cardCorner,
                        cardSpacing = cardSpacing,
                        iconSize = cardIconSize,
                        titleSize = titleSize * 1.15f,
                        bodySize = bodySize,
                        tightStyle = tightStyle,
                        trailingContent = {
                            SubscriptionUsageSummary(
                                usageInfo = usageInfo,
                                scale = scale,
                                bodySize = bodySize,
                                tightStyle = tightStyle,
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(cardGap))

            TvMenuCard(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings),
                subtitle = AnnotatedString(""),
                onClick = onNavigateToSettings,
                scale = scale,
                cardPad = cardPad,
                cardCorner = cardCorner,
                cardSpacing = cardSpacing,
                iconSize = cardIconSize,
                titleSize = titleSize,
                bodySize = bodySize,
                tightStyle = tightStyle,
            )
        }
    }
    }

    if (showBlockedDialog) {
        BlockedDialog(onDismiss = { showBlockedDialog = false })
    }
}

@Composable
private fun SubscriptionReminderBanner(
    auth: AuthState.Authenticated,
    snoozedUntilMillis: Long,
    snoozedForExpiryMillis: Long?,
    onRenew: () -> Unit,
    onSnooze: () -> Unit,
    scale: Float,
    cardPad: Dp,
    cardCorner: Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    val expiresAt = auth.planExpiresAt
    var nowMillis by remember(
        auth.plan,
        expiresAt,
        snoozedUntilMillis,
        snoozedForExpiryMillis,
    ) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(auth.plan, expiresAt, snoozedUntilMillis, snoozedForExpiryMillis) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val thresholdAt = expiresAt?.minus(3L * SUBSCRIPTION_DAY_MS)
            val nextRelevantEvent = listOfNotNull(
                thresholdAt?.takeIf { it > now },
                expiresAt?.takeIf { it > now },
                snoozedUntilMillis.takeIf { it > now },
            ).minOrNull()
            val untilNextEvent = nextRelevantEvent
                ?.minus(now)
                ?.coerceAtLeast(1_000L)
                ?: SUBSCRIPTION_REMINDER_CLOCK_MAX_DELAY_MS
            delay(minOf(untilNextEvent, SUBSCRIPTION_REMINDER_CLOCK_MAX_DELAY_MS))
        }
    }

    if (!shouldShowSubscriptionReminder(auth.plan, expiresAt, nowMillis)) return

    val persistentlySnoozed = isSubscriptionReminderSnoozed(
        snoozedUntilMillis = snoozedUntilMillis,
        snoozedForExpiryMillis = snoozedForExpiryMillis,
        currentExpiryMillis = expiresAt,
        nowMillis = nowMillis,
    )
    var dismissedLocally by remember(auth.plan, expiresAt) { mutableStateOf(false) }
    if (persistentlySnoozed || dismissedLocally) return

    val title = when {
        auth.plan == UserPlan.EXPIRED -> stringResource(R.string.subscription_expired_title)
        expiresAt == null -> return
        else -> {
            val daysLeft = subscriptionReminderDaysLeft(expiresAt, nowMillis)
            if (daysLeft <= 0) {
                stringResource(R.string.subscription_expiry_today)
            } else {
                pluralStringResource(
                    R.plurals.subscription_expiring_title,
                    daysLeft,
                    daysLeft,
                )
            }
        }
    }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (darkTheme) Color(0xFF2A1A1A) else Color(0xFFFFEBEE)
    val actionShape = RoundedCornerShape((10 * scale).dp)
    val actionHeight = (36 * scale).dp
    val focusBorderWidth = (2 * scale).dp
    var renewFocused by remember { mutableStateOf(false) }
    var laterFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardPad),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = (14 * scale).dp,
                    vertical = (16 * scale).dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = titleSize * 0.9f,
                lineHeight = titleSize,
                fontWeight = FontWeight.Bold,
                color = VpnRed,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height((8 * scale).dp))
            Text(
                text = stringResource(R.string.subscription_renew_reminder_desc),
                fontSize = labelSize,
                lineHeight = labelSize * 1.2f,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height((12 * scale).dp))

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onRenew,
                        modifier = Modifier
                            .weight(1f)
                            .height(actionHeight)
                            .then(
                                if (renewFocused) {
                                    Modifier.border(
                                        focusBorderWidth,
                                        MaterialTheme.colorScheme.onSurface,
                                        actionShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged { renewFocused = it.isFocused },
                        shape = actionShape,
                        contentPadding = PaddingValues(horizontal = (12 * scale).dp),
                        colors = if (darkTheme) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F3F3F),
                                contentColor = Color.White,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_renew_action),
                            fontSize = labelSize,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            style = tightStyle,
                        )
                    }

                    TextButton(
                        onClick = {
                            dismissedLocally = true
                            onSnooze()
                        },
                        modifier = Modifier
                            .height(actionHeight)
                            .then(
                                if (laterFocused) {
                                    Modifier.border(
                                        focusBorderWidth,
                                        MaterialTheme.colorScheme.onSurface,
                                        actionShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged { laterFocused = it.isFocused },
                        shape = actionShape,
                        contentPadding = PaddingValues(horizontal = (12 * scale).dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (darkTheme) Color.White else Color.Black,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.update_banner_later),
                            fontSize = labelSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            style = tightStyle,
                        )
                    }
                }
            }
        }
    }
}

private const val SUBSCRIPTION_REMINDER_CLOCK_MAX_DELAY_MS = 60L * 60L * 1000L

@Composable
private fun BlockedDialog(onDismiss: () -> Unit) {
    // "Contact support" opens a second dialog with a large, easy-to-scan QR
    // code — on TV there is no browser/Telegram to open the link directly.
    var showQr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.error_usage_blocked),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.block_appeal_message),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { showQr = true }) {
                Text(stringResource(R.string.block_appeal_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )

    if (showQr) {
        SupportQrDialog(onDismiss = { showQr = false })
    }
}

@Composable
private fun SupportQrDialog(onDismiss: () -> Unit) {
    val link = stringResource(R.string.block_appeal_link)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.block_appeal_button),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    com.tobevpn.tv.presentation.components.QrCode(
                        data = link,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.block_appeal_scan_hint),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun ConnectButtonLarge(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    scale: Float,
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val targetColor = when {
        isConnected -> VpnGreen
        isConnecting -> VpnOrange
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(400),
        label = "bg",
    )
    val iconColor = when {
        isConnected || isConnecting -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animScale by animateFloatAsState(
        targetValue = if (isConnecting) 0.95f else 1f,
        animationSpec = tween(300),
        label = "scale",
    )

    var isFocused by remember { mutableStateOf(false) }

    BoxWithConstraints {
        val buttonSize = min(maxWidth * 0.55f, maxHeight * 0.45f).coerceAtLeast(80.dp)
        val iconSize = buttonSize * 0.36f
        val borderWidth = (4 * scale).dp

        Box(
            modifier = Modifier
                .size(buttonSize)
                .scale(animScale)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        onClick()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun ServerSelectorCard(
    server: Server?,
    automatic: Boolean,
    onClick: () -> Unit,
    scale: Float,
    cardPad: Dp,
    cardCorner: Dp,
    cardSpacing: Dp,
    flagSize: androidx.compose.ui.unit.TextUnit,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderWidth = (2 * scale).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardPad)
            .then(
                if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayCountryName = if (automatic && server != null) {
                stringResource(R.string.server_auto_selected)
            } else {
                server?.let { serverCountryNameForUi(it.country, it.name) }.orEmpty()
            }
            Text(
                text = if (server != null) countryFlagForUi(server.country, server.name) else "\uD83C\uDF10",
                fontSize = flagSize,
            )
            Spacer(modifier = Modifier.width(cardSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server?.let { serverDisplayName(it.name, it.country) }
                        ?: stringResource(R.string.server_select),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = tightStyle,
                )
                if (displayCountryName.isNotEmpty()) {
                    Text(
                        text = displayCountryName,
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = tightStyle,
                    )
                }
            }
            if (server != null && server.ping > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${server.ping}",
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = pingColor(server.ping),
                        style = tightStyle,
                    )
                    Text(
                        text = stringResource(R.string.speed_unit_ms),
                        fontSize = labelSize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = tightStyle,
                    )
                }
                Spacer(modifier = Modifier.width((8 * scale).dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size((24 * scale).dp),
            )
        }
    }
}

@Composable
private fun TrafficCard(
    usageInfo: UsageInfo,
    sessionBytes: Long,
    sessionTimeSeconds: Long,
    authState: AuthState,
    onClick: () -> Unit,
    scale: Float,
    cardPad: Dp,
    cardCorner: Dp,
    cardSpacing: Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    statValueSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderWidth = (2 * scale).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.current_session),
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    style = tightStyle,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((24 * scale).dp),
                )
            }
            Spacer(modifier = Modifier.height(cardSpacing))

            if (authState is AuthState.Authenticated) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatItem(
                        label = stringResource(R.string.traffic),
                        value = formatBytes(sessionBytes),
                        modifier = Modifier.weight(1f),
                        valueSize = statValueSize,
                        labelSize = labelSize,
                        tightStyle = tightStyle,
                    )
                    StatItem(
                        label = stringResource(R.string.time),
                        value = formatTime(sessionTimeSeconds),
                        modifier = Modifier.weight(1f),
                        valueSize = statValueSize,
                        labelSize = labelSize,
                        tightStyle = tightStyle,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.traffic), fontSize = labelSize, style = tightStyle)
                    Text(
                        "${formatBytes(usageInfo.bytesUsed)} / ${formatBytes(usageInfo.bytesLimit)}",
                        fontSize = labelSize,
                        style = tightStyle,
                    )
                }
                Spacer(modifier = Modifier.height((6 * scale).dp))
                LinearProgressIndicator(
                    progress = { usageInfo.trafficProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor(usageInfo.trafficProgress),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(cardSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.time), fontSize = labelSize, style = tightStyle)
                    Text(
                        "${formatTime(usageInfo.timeUsedSeconds)} / ${formatTime(usageInfo.timeLimitSeconds)}",
                        fontSize = labelSize,
                        style = tightStyle,
                    )
                }
                Spacer(modifier = Modifier.height((6 * scale).dp))
                LinearProgressIndicator(
                    progress = { usageInfo.timeProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor(usageInfo.timeProgress),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TvMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: AnnotatedString,
    titleColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit,
    scale: Float,
    cardPad: Dp,
    cardCorner: Dp,
    cardSpacing: Dp,
    iconSize: Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderWidth = (2 * scale).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(cardSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    style = tightStyle,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = tightStyle,
                    )
                }
            }
            trailingContent?.invoke()
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size((24 * scale).dp),
            )
        }
    }
}

@Composable
private fun SubscriptionUsageSummary(
    usageInfo: UsageInfo,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    if (usageInfo.bytesLimit <= 0L) return

    val progress = usageInfo.trafficProgress
    Column(
        modifier = Modifier
            .widthIn(
                min = (116 * scale).dp,
                max = (148 * scale).dp,
            )
            .padding(
                start = (12 * scale).dp,
                end = (8 * scale).dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${formatBytes(usageInfo.bytesUsed)} / ${formatBytes(usageInfo.bytesLimit)}",
            fontSize = bodySize,
            autoSize = TextAutoSize.StepBased(
                minFontSize = (11 * scale).sp,
                maxFontSize = bodySize,
                stepSize = (0.5f * scale).sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = tightStyle,
        )
        Spacer(modifier = Modifier.height((4 * scale).dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height((9 * scale).dp)
                .clip(RoundedCornerShape(99.dp)),
            color = progressColor(progress),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
        Text(
            text = label,
            fontSize = labelSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getDefault()
    return sdf.format(java.util.Date(epochMillis))
}

private fun progressColor(progress: Float) = when {
    progress < 0.7f -> VpnGreen
    progress < 0.9f -> VpnOrange
    else -> VpnRed
}

private fun pingColor(ping: Long) = when {
    ping < 100 -> VpnGreen
    ping < 200 -> VpnOrange
    else -> VpnRed
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
