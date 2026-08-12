package com.tobevpn.tv.presentation.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tobevpn.tv.R
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.AppFilterState
import com.tobevpn.tv.domain.model.AppThemeMode
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.UserPlan
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.util.LocaleManager
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import com.tobevpn.tv.util.DiagnosticLogFileInfo
import com.tobevpn.tv.util.DiagnosticLogState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    onNavigateToDevices: () -> Unit = {},
    onNavigateToAppFilter: () -> Unit = {},
    onNavigateToReferrals: () -> Unit = {},
    onNavigateToPromocodes: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    diagnosticViewModel: AboutViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val appFilterState by viewModel.appFilterState.collectAsStateWithLifecycle()
    val savedThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val diagnosticState by diagnosticViewModel.diagnosticState.collectAsStateWithLifecycle()
    val diagnosticHistoryState by diagnosticViewModel.history.collectAsStateWithLifecycle()
    val systemThemeMode = if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
    val themeMode = savedThemeMode ?: systemThemeMode
    val context = LocalContext.current
    var pendingLanguage by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showDiagnosticInfo by remember { mutableStateOf(false) }
    var showDiagnosticHistory by remember { mutableStateOf(false) }
    var diagnosticDeleteCandidate by remember { mutableStateOf<DiagnosticLogFileInfo?>(null) }
    var diagnosticModeToast by remember { mutableStateOf<Toast?>(null) }
    var settingsPage by rememberSaveable { mutableStateOf(0) }
    var navigationReturnFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var keepPageIndicatorFocus by remember { mutableStateOf(false) }
    var firstPageLeftHeightPx by remember { mutableStateOf(0) }
    val backFocusRequester = remember { FocusRequester() }
    val logoutFocusRequester = remember { FocusRequester() }
    val englishFocusRequester = remember { FocusRequester() }
    val russianFocusRequester = remember { FocusRequester() }
    val whatsNewFocusRequester = remember { FocusRequester() }
    val checkUpdateFocusRequester = remember { FocusRequester() }
    val diagnosticLogoFocusRequester = remember { FocusRequester() }
    val diagnosticInfoFocusRequester = remember { FocusRequester() }
    val diagnosticStartFocusRequester = remember { FocusRequester() }
    val diagnosticHistoryFocusRequester = remember { FocusRequester() }
    val supportFocusRequester = remember { FocusRequester() }
    val devicesFocusRequester = remember { FocusRequester() }
    val appFilterFocusRequester = remember { FocusRequester() }
    val referralsFocusRequester = remember { FocusRequester() }
    val promocodesFocusRequester = remember { FocusRequester() }
    val darkThemeFocusRequester = remember { FocusRequester() }
    val lightThemeFocusRequester = remember { FocusRequester() }
    val pageFocusRequesters = remember { List(3) { FocusRequester() } }
    var dialogReturnFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var initialFocusApplied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRestoreScope = rememberCoroutineScope()

    val diagnosticShareSubject = stringResource(R.string.diagnostics_share_subject)
    val diagnosticShareTitle = stringResource(R.string.diagnostics_share_title)
    val diagnosticExportSaved = stringResource(R.string.diagnostics_export_saved)
    LaunchedEffect(
        diagnosticViewModel,
        diagnosticShareSubject,
        diagnosticShareTitle,
        diagnosticExportSaved,
    ) {
        diagnosticViewModel.events.collect { event ->
            when (event) {
                is DiagnosticUiEvent.ModeChanged -> {
                    diagnosticModeToast?.cancel()
                    diagnosticModeToast = Toast.makeText(
                        context,
                        if (event.enabled) {
                            R.string.diagnostics_mode_enabled
                        } else {
                            R.string.diagnostics_mode_disabled
                        },
                        Toast.LENGTH_SHORT,
                    ).also(Toast::show)
                }
                is DiagnosticUiEvent.ShareLog -> {
                    event.intent.putExtra(Intent.EXTRA_SUBJECT, diagnosticShareSubject)
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(event.intent, diagnosticShareTitle),
                        )
                    }.onFailure {
                        diagnosticViewModel.saveLogToDownloads(event.fileName)
                    }
                }
                is DiagnosticUiEvent.LogExported -> Toast.makeText(
                    context,
                    "$diagnosticExportSaved ${event.location}",
                    Toast.LENGTH_LONG,
                ).show()
                DiagnosticUiEvent.NoLogToExport -> Toast.makeText(
                    context,
                    R.string.diagnostics_no_log_to_export,
                    Toast.LENGTH_SHORT,
                ).show()
                DiagnosticUiEvent.OperationFailed -> Toast.makeText(
                    context,
                    R.string.diagnostics_operation_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val returnTarget = navigationReturnFocus
            if (
                returnTarget != null &&
                (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME)
            ) {
                val requester = when (returnTarget) {
                    "devices" -> devicesFocusRequester
                    "app_filter" -> appFilterFocusRequester
                    "referrals" -> referralsFocusRequester
                    "promocodes" -> promocodesFocusRequester
                    "support" -> supportFocusRequester
                    else -> backFocusRequester
                }
                // The Settings destination stays composed under its child
                // screen, so its focus nodes are already available. Restore
                // focus before the pop transition becomes visible instead of
                // waiting two frames and visibly jumping from Back.
                val restored = runCatching { requester.requestFocus() }
                    .getOrDefault(false)
                if (restored) {
                    initialFocusApplied = true
                    navigationReturnFocus = null
                }
                return@LifecycleEventObserver
            }

            if (event != Lifecycle.Event.ON_RESUME || initialFocusApplied) {
                return@LifecycleEventObserver
            }
            focusRestoreScope.launch {
                // Only the very first Settings composition needs to wait for
                // its focus nodes to be attached.
                withFrameNanos { }
                runCatching { backFocusRequester.requestFocus() }
                initialFocusApplied = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settingsPage, keepPageIndicatorFocus) {
        if (keepPageIndicatorFocus) {
            withFrameNanos { }
            runCatching { pageFocusRequesters.getOrNull(settingsPage)?.requestFocus() }
        }
    }

    LaunchedEffect(
        pendingLanguage,
        showLogoutConfirm,
        showWhatsNew,
        showDiagnosticInfo,
        showDiagnosticHistory,
        diagnosticDeleteCandidate,
        dialogReturnFocusRequester,
    ) {
        val requester = dialogReturnFocusRequester ?: return@LaunchedEffect
        if (
            pendingLanguage != null ||
            showLogoutConfirm ||
            showWhatsNew ||
            showDiagnosticInfo ||
            showDiagnosticHistory ||
            diagnosticDeleteCandidate != null
        ) {
            return@LaunchedEffect
        }
        // A Dialog owns a separate focus window. Wait until it is detached,
        // then return focus to the control that opened it instead of allowing
        // Compose to fall back to the first focusable item (the Back button).
        withFrameNanos { }
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        dialogReturnFocusRequester = null
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)
        val density = LocalDensity.current
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
                TvHeaderIconButton(
                    onClick = onBack,
                    onLongClick = onLongBack,
                    modifier = Modifier
                        .size(headerButtonSize)
                        .focusRequester(backFocusRequester)
                        .focusProperties {
                            down = when (settingsPage) {
                                0 -> logoutFocusRequester
                                1 -> appFilterFocusRequester
                                else -> promocodesFocusRequester
                            }
                            right = when (settingsPage) {
                                0 -> diagnosticLogoFocusRequester
                                1 -> referralsFocusRequester
                                else -> if (diagnosticState.debugModeEnabled) {
                                    diagnosticStartFocusRequester
                                } else {
                                    promocodesFocusRequester
                                }
                            }
                        },
                    shape = RoundedCornerShape(backCorner),
                    borderWidth = borderWidth,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(headerIconSize),
                        tint = headerColor,
                    )
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

            AnimatedContent(
                targetState = settingsPage,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { fullWidth -> fullWidth * direction },
                        ) + fadeIn(animationSpec = tween(180))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(260),
                            targetOffsetX = { fullWidth -> -fullWidth * direction },
                        ) + fadeOut(animationSpec = tween(180)),
                    )
                },
                label = "settings-page",
            ) { page ->
                if (page == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        verticalAlignment = Alignment.Top,
                ) {
                // Left column: Account + Language
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { firstPageLeftHeightPx = it.size.height },
                ) {
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

                                    val serverPlanName = auth.planDisplayName?.takeIf {
                                        it.isNotBlank() && auth.plan != UserPlan.EXPIRED
                                    }
                                    val (planLabel, planColor) = when (auth.plan) {
                                        UserPlan.PAID -> (serverPlanName ?: stringResource(R.string.plan_unknown_name)) to VpnGreen
                                        UserPlan.ADMIN -> (serverPlanName ?: stringResource(R.string.plan_unknown_name)) to VpnGreen
                                        UserPlan.EXPIRED -> stringResource(R.string.plan_expired) to VpnRed
                                        UserPlan.FREE_TRIAL -> (serverPlanName ?: stringResource(R.string.plan_free)) to VpnOrange
                                    }
                                    InfoRow(stringResource(R.string.plan), planLabel, planColor, bodySize, rowPadV, tightStyle)

                                    if ((auth.plan == UserPlan.PAID || auth.plan == UserPlan.ADMIN) && auth.planExpiresAt != null) {
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
                                        onClick = {
                                            dialogReturnFocusRequester = logoutFocusRequester
                                            showLogoutConfirm = true
                                        },
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                        bodySize = bodySize,
                                        scale = scale,
                                        buttonPadH = buttonPadH,
                                        buttonPadV = buttonPadV,
                                        focusRequester = logoutFocusRequester,
                                        upFocusRequester = backFocusRequester,
                                        downFocusRequester = englishFocusRequester,
                                        rightFocusRequester = diagnosticLogoFocusRequester,
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
                                SettingsChoiceChip(
                                    label = stringResource(R.string.language_english),
                                    selected = language == LocaleManager.LANG_EN,
                                    onClick = {
                                        if (language != LocaleManager.LANG_EN) {
                                            dialogReturnFocusRequester = englishFocusRequester
                                            pendingLanguage = LocaleManager.LANG_EN
                                        }
                                    },
                                    bodySize = bodySize,
                                    scale = scale,
                                    focusRequester = englishFocusRequester,
                                    upFocusRequester = logoutFocusRequester,
                                    downFocusRequester = pageFocusRequesters[0],
                                    rightFocusRequester = russianFocusRequester,
                                )
                                Spacer(modifier = Modifier.width((12 * scale).dp))
                                SettingsChoiceChip(
                                    label = stringResource(R.string.language_russian),
                                    selected = language == LocaleManager.LANG_RU,
                                    onClick = {
                                        if (language != LocaleManager.LANG_RU) {
                                            dialogReturnFocusRequester = russianFocusRequester
                                            pendingLanguage = LocaleManager.LANG_RU
                                        }
                                    },
                                    bodySize = bodySize,
                                    scale = scale,
                                    focusRequester = russianFocusRequester,
                                    upFocusRequester = logoutFocusRequester,
                                    downFocusRequester = pageFocusRequesters[0],
                                    leftFocusRequester = englishFocusRequester,
                                    rightFocusRequester = diagnosticLogoFocusRequester,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(cardSpacing))

                // Right column: expanded app identity and version information.
                Column(modifier = Modifier.weight(1f)) {
                    val aboutCardHeight = with(density) {
                        if (firstPageLeftHeightPx > 0) {
                            firstPageLeftHeightPx.toDp()
                        } else {
                            (365 * scale).dp
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(aboutCardHeight),
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
                            Spacer(modifier = Modifier.height((10 * scale).dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DiagnosticUnlockLogo(
                                    onLongClick = diagnosticViewModel::toggleDiagnosticMode,
                                    focusRequester = diagnosticLogoFocusRequester,
                                    size = (68 * scale).dp,
                                    modifier = Modifier.focusProperties {
                                        up = backFocusRequester
                                        down = whatsNewFocusRequester
                                        left = russianFocusRequester
                                    },
                                )
                                Spacer(modifier = Modifier.height((7 * scale).dp))
                                Text(
                                    stringResource(R.string.app_name),
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    style = tightStyle,
                                )
                                Spacer(modifier = Modifier.height((4 * scale).dp))
                                Text(
                                    stringResource(R.string.about_slogan),
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = bodySize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    style = tightStyle,
                                )
                            }

                            // Separate the app identity from the technical
                            // details, while keeping the details visually
                            // grouped together near the bottom of the card.
                            Spacer(modifier = Modifier.height((48 * scale).dp))
                            // Version + "Check for updates" merged into a single
                            // row to stop duplicating the current version.
                            com.tobevpn.tv.update.SettingsUpdateCheckRow(
                                fontSize = bodySize,
                                onWhatsNew = {
                                    dialogReturnFocusRequester = whatsNewFocusRequester
                                    showWhatsNew = true
                                },
                                whatsNewFocusModifier = Modifier
                                    .focusRequester(whatsNewFocusRequester)
                                    .focusProperties {
                                        up = diagnosticLogoFocusRequester
                                        down = pageFocusRequesters[1]
                                        left = logoutFocusRequester
                                        right = checkUpdateFocusRequester
                                    },
                                checkFocusModifier = Modifier
                                    .focusRequester(checkUpdateFocusRequester)
                                    .focusProperties {
                                        up = diagnosticLogoFocusRequester
                                        down = pageFocusRequesters[2]
                                        left = whatsNewFocusRequester
                                    },
                            )
                            Spacer(modifier = Modifier.height((24 * scale).dp))
                            InfoRow(stringResource(R.string.xray), viewModel.xrayVersion, bodySize = bodySize, rowPadV = rowPadV, tightStyle = tightStyle)
                        }
                    }
                }
                    }
                } else if (page == 1) {
                    SettingsAppsPage(
                        appFilterState = appFilterState,
                        onNavigateToAppFilter = {
                            navigationReturnFocus = "app_filter"
                            onNavigateToAppFilter()
                        },
                        onNavigateToReferrals = {
                            navigationReturnFocus = "referrals"
                            onNavigateToReferrals()
                        },
                        onNavigateToSupport = {
                            navigationReturnFocus = "support"
                            onNavigateToSupport()
                        },
                        themeMode = themeMode,
                        onThemeSelected = viewModel::setThemeMode,
                        cardPad = cardPad,
                        cardCorner = cardCorner,
                        cardSpacing = cardSpacing,
                        titleSize = titleSize,
                        bodySize = bodySize,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        scale = scale,
                        tightStyle = tightStyle,
                        backFocusRequester = backFocusRequester,
                        appFilterFocusRequester = appFilterFocusRequester,
                        referralsFocusRequester = referralsFocusRequester,
                        supportFocusRequester = supportFocusRequester,
                        darkThemeFocusRequester = darkThemeFocusRequester,
                        lightThemeFocusRequester = lightThemeFocusRequester,
                        firstPageIndicatorFocusRequester = pageFocusRequesters[0],
                        thirdPageIndicatorFocusRequester = pageFocusRequesters[2],
                    )
                } else {
                    SettingsMorePage(
                        showDevices = authState is AuthState.Authenticated,
                        onNavigateToPromocodes = {
                            navigationReturnFocus = "promocodes"
                            onNavigateToPromocodes()
                        },
                        onNavigateToDevices = {
                            navigationReturnFocus = "devices"
                            onNavigateToDevices()
                        },
                        cardPad = cardPad,
                        cardCorner = cardCorner,
                        cardSpacing = cardSpacing,
                        titleSize = titleSize,
                        bodySize = bodySize,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        scale = scale,
                        tightStyle = tightStyle,
                        backFocusRequester = backFocusRequester,
                        promocodesFocusRequester = promocodesFocusRequester,
                        devicesFocusRequester = devicesFocusRequester,
                        diagnosticInfoFocusRequester = diagnosticInfoFocusRequester,
                        diagnosticStartFocusRequester = diagnosticStartFocusRequester,
                        diagnosticHistoryFocusRequester = diagnosticHistoryFocusRequester,
                        firstPageIndicatorFocusRequester = pageFocusRequesters[0],
                        thirdPageIndicatorFocusRequester = pageFocusRequesters[2],
                        diagnosticState = diagnosticState,
                        onToggleDiagnosticCollection = diagnosticViewModel::toggleCollection,
                        onOpenDiagnosticHistory = {
                            dialogReturnFocusRequester = diagnosticHistoryFocusRequester
                            showDiagnosticHistory = true
                            diagnosticViewModel.loadHistory()
                        },
                        onOpenDiagnosticInfo = {
                            dialogReturnFocusRequester = diagnosticInfoFocusRequester
                            showDiagnosticInfo = true
                        },
                    )
                }
            }

        }

        SettingsPageIndicator(
            currentPage = settingsPage,
            pageCount = 3,
            focusRequesters = pageFocusRequesters,
            upFocusRequesters = when (settingsPage) {
                0 -> listOf(englishFocusRequester, whatsNewFocusRequester, checkUpdateFocusRequester)
                1 -> listOf(supportFocusRequester, darkThemeFocusRequester, darkThemeFocusRequester)
                else -> listOf(
                    if (authState is AuthState.Authenticated) devicesFocusRequester else promocodesFocusRequester,
                    when {
                        diagnosticState.debugModeEnabled -> diagnosticStartFocusRequester
                        authState is AuthState.Authenticated -> devicesFocusRequester
                        else -> promocodesFocusRequester
                    },
                    when {
                        diagnosticState.debugModeEnabled -> diagnosticStartFocusRequester
                        authState is AuthState.Authenticated -> devicesFocusRequester
                        else -> promocodesFocusRequester
                    },
                )
            },
            onPageSelected = {
                keepPageIndicatorFocus = true
                settingsPage = it
            },
            scale = scale,
            bodySize = (15 * scale).sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (12 * scale).dp),
        )

        val pending = pendingLanguage
        if (pending != null) {
            TvConfirmationDialog(
                onDismissRequest = { pendingLanguage = null },
                title = stringResource(R.string.language_restart_title),
                message = stringResource(R.string.language_restart_message),
                confirmLabel = stringResource(R.string.language_restart_button),
                cancelLabel = stringResource(R.string.cancel),
                darkTheme = themeMode == AppThemeMode.DARK,
                onConfirm = {
                    viewModel.setLanguage(pending)
                    pendingLanguage = null
                    LocaleManager.restartApp(context)
                },
            )
        }

        if (showLogoutConfirm) {
            TvConfirmationDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = stringResource(R.string.logout_confirm_title),
                message = stringResource(R.string.logout_confirm_message),
                confirmLabel = stringResource(R.string.logout),
                cancelLabel = stringResource(R.string.cancel),
                darkTheme = themeMode == AppThemeMode.DARK,
                destructive = true,
                onConfirm = {
                    dialogReturnFocusRequester = englishFocusRequester
                    showLogoutConfirm = false
                    viewModel.logout()
                },
            )
        }

        if (showWhatsNew) {
            WhatsNewDialog(
                darkTheme = themeMode == AppThemeMode.DARK,
                onDismiss = { showWhatsNew = false },
            )
        }

        if (showDiagnosticInfo) {
            DiagnosticInfoDialog(
                darkTheme = themeMode == AppThemeMode.DARK,
                onDismiss = { showDiagnosticInfo = false },
            )
        }

        if (showDiagnosticHistory) {
            DiagnosticHistoryDialog(
                state = diagnosticHistoryState,
                darkTheme = themeMode == AppThemeMode.DARK,
                onDismiss = { showDiagnosticHistory = false },
                onShare = diagnosticViewModel::shareLog,
                onDelete = { diagnosticDeleteCandidate = it },
            )
        }

        diagnosticDeleteCandidate?.let { log ->
            TvConfirmationDialog(
                title = stringResource(R.string.diagnostics_history_delete_title),
                message = stringResource(
                    R.string.diagnostics_history_delete_message,
                    log.date.format(SETTINGS_DIAGNOSTIC_DATE_FORMAT),
                ),
                confirmLabel = stringResource(R.string.diagnostics_history_delete_confirm),
                cancelLabel = stringResource(R.string.cancel),
                darkTheme = themeMode == AppThemeMode.DARK,
                destructive = true,
                onConfirm = {
                    diagnosticDeleteCandidate = null
                    diagnosticViewModel.deleteLog(log.fileName)
                },
                onDismissRequest = { diagnosticDeleteCandidate = null },
            )
        }
    }
}

@Composable
private fun SettingsAppsPage(
    appFilterState: AppFilterState,
    onNavigateToAppFilter: () -> Unit,
    onNavigateToReferrals: () -> Unit,
    onNavigateToSupport: () -> Unit,
    themeMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    cardPad: androidx.compose.ui.unit.Dp,
    cardCorner: androidx.compose.ui.unit.Dp,
    cardSpacing: androidx.compose.ui.unit.Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    buttonPadH: androidx.compose.ui.unit.Dp,
    buttonPadV: androidx.compose.ui.unit.Dp,
    scale: Float,
    tightStyle: TextStyle,
    backFocusRequester: FocusRequester,
    appFilterFocusRequester: FocusRequester,
    referralsFocusRequester: FocusRequester,
    supportFocusRequester: FocusRequester,
    darkThemeFocusRequester: FocusRequester,
    lightThemeFocusRequester: FocusRequester,
    firstPageIndicatorFocusRequester: FocusRequester,
    thirdPageIndicatorFocusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(cardCorner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    Text(
                        stringResource(R.string.settings_app_filter),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        stringResource(R.string.settings_app_filter_hint),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((14 * scale).dp))
                    Text(
                        appFilterSummary(appFilterState),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((18 * scale).dp))
                    AccountActionButton(
                        label = stringResource(R.string.settings_app_filter_manage),
                        onClick = onNavigateToAppFilter,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bodySize = bodySize,
                        scale = scale,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        focusRequester = appFilterFocusRequester,
                        upFocusRequester = backFocusRequester,
                        downFocusRequester = supportFocusRequester,
                        rightFocusRequester = referralsFocusRequester,
                    )
                }
            }

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
                        stringResource(R.string.settings_support),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        stringResource(R.string.settings_support_desc),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((18 * scale).dp))
                    AccountActionButton(
                        label = stringResource(R.string.settings_support_open),
                        onClick = onNavigateToSupport,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bodySize = bodySize,
                        scale = scale,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        focusRequester = supportFocusRequester,
                        upFocusRequester = appFilterFocusRequester,
                        downFocusRequester = firstPageIndicatorFocusRequester,
                        rightFocusRequester = darkThemeFocusRequester,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(cardSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(cardCorner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    Text(
                        stringResource(R.string.settings_referrals),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        stringResource(R.string.settings_referrals_hint),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((18 * scale).dp))
                    AccountActionButton(
                        label = stringResource(R.string.settings_referrals_manage),
                        onClick = onNavigateToReferrals,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bodySize = bodySize,
                        scale = scale,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        focusRequester = referralsFocusRequester,
                        upFocusRequester = backFocusRequester,
                        downFocusRequester = darkThemeFocusRequester,
                        leftFocusRequester = appFilterFocusRequester,
                    )
                }
            }

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
                        stringResource(R.string.settings_theme),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        stringResource(R.string.settings_theme_hint),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((18 * scale).dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsChoiceChip(
                            label = stringResource(R.string.theme_dark),
                            selected = themeMode == AppThemeMode.DARK,
                            onClick = { onThemeSelected(AppThemeMode.DARK) },
                            bodySize = bodySize,
                            scale = scale,
                            focusRequester = darkThemeFocusRequester,
                            upFocusRequester = referralsFocusRequester,
                            downFocusRequester = thirdPageIndicatorFocusRequester,
                            leftFocusRequester = supportFocusRequester,
                            rightFocusRequester = lightThemeFocusRequester,
                        )
                        Spacer(modifier = Modifier.width((12 * scale).dp))
                        SettingsChoiceChip(
                            label = stringResource(R.string.theme_light),
                            selected = themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeSelected(AppThemeMode.LIGHT) },
                            bodySize = bodySize,
                            scale = scale,
                            focusRequester = lightThemeFocusRequester,
                            upFocusRequester = referralsFocusRequester,
                            downFocusRequester = thirdPageIndicatorFocusRequester,
                            leftFocusRequester = darkThemeFocusRequester,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingsMorePage(
    showDevices: Boolean,
    onNavigateToPromocodes: () -> Unit,
    onNavigateToDevices: () -> Unit,
    cardPad: androidx.compose.ui.unit.Dp,
    cardCorner: androidx.compose.ui.unit.Dp,
    cardSpacing: androidx.compose.ui.unit.Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    buttonPadH: androidx.compose.ui.unit.Dp,
    buttonPadV: androidx.compose.ui.unit.Dp,
    scale: Float,
    tightStyle: TextStyle,
    backFocusRequester: FocusRequester,
    promocodesFocusRequester: FocusRequester,
    devicesFocusRequester: FocusRequester,
    diagnosticInfoFocusRequester: FocusRequester,
    diagnosticStartFocusRequester: FocusRequester,
    diagnosticHistoryFocusRequester: FocusRequester,
    firstPageIndicatorFocusRequester: FocusRequester,
    thirdPageIndicatorFocusRequester: FocusRequester,
    diagnosticState: DiagnosticLogState,
    onToggleDiagnosticCollection: () -> Unit,
    onOpenDiagnosticHistory: () -> Unit,
    onOpenDiagnosticInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(cardCorner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    Text(
                        stringResource(R.string.settings_promocodes),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        stringResource(R.string.settings_promocodes_desc),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((18 * scale).dp))
                    AccountActionButton(
                        label = stringResource(R.string.settings_promocodes),
                        onClick = onNavigateToPromocodes,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bodySize = bodySize,
                        scale = scale,
                        buttonPadH = buttonPadH,
                        buttonPadV = buttonPadV,
                        focusRequester = promocodesFocusRequester,
                        upFocusRequester = backFocusRequester,
                        downFocusRequester = if (showDevices) {
                            devicesFocusRequester
                        } else {
                            firstPageIndicatorFocusRequester
                        },
                        rightFocusRequester = if (diagnosticState.debugModeEnabled) {
                            diagnosticStartFocusRequester
                        } else {
                            null
                        },
                    )
                }
            }

            if (showDevices) {
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
                        Spacer(modifier = Modifier.height((8 * scale).dp))
                        Text(
                            stringResource(R.string.devices_manage_hint),
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = tightStyle,
                        )
                        Spacer(modifier = Modifier.height((18 * scale).dp))
                        AccountActionButton(
                            label = stringResource(R.string.devices_manage),
                            onClick = onNavigateToDevices,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            bodySize = bodySize,
                            scale = scale,
                            buttonPadH = buttonPadH,
                            buttonPadV = buttonPadV,
                            focusRequester = devicesFocusRequester,
                            upFocusRequester = promocodesFocusRequester,
                            downFocusRequester = firstPageIndicatorFocusRequester,
                            rightFocusRequester = if (diagnosticState.debugModeEnabled) {
                                diagnosticStartFocusRequester
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(cardSpacing))

        Column(modifier = Modifier.weight(1f)) {
            if (diagnosticState.debugModeEnabled) {
                DiagnosticCard(
                    state = diagnosticState,
                    bodySize = bodySize,
                    cardPadding = cardPad * 0.75f,
                    onToggleCollection = onToggleDiagnosticCollection,
                    onHistory = onOpenDiagnosticHistory,
                    onInfo = onOpenDiagnosticInfo,
                    infoButtonModifier = Modifier
                        .focusRequester(diagnosticInfoFocusRequester)
                        .focusProperties {
                            up = backFocusRequester
                            down = diagnosticStartFocusRequester
                            left = promocodesFocusRequester
                        },
                    startButtonModifier = Modifier
                        .focusRequester(diagnosticStartFocusRequester)
                        .focusProperties {
                            up = diagnosticInfoFocusRequester
                            down = thirdPageIndicatorFocusRequester
                            left = if (showDevices) devicesFocusRequester else promocodesFocusRequester
                            right = diagnosticHistoryFocusRequester
                        },
                    historyButtonModifier = Modifier
                        .focusRequester(diagnosticHistoryFocusRequester)
                        .focusProperties {
                            up = diagnosticInfoFocusRequester
                            down = thirdPageIndicatorFocusRequester
                            left = diagnosticStartFocusRequester
                            right = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

@Composable
private fun appFilterSummary(state: AppFilterState): String =
    when (state.mode) {
        AppFilterMode.OFF -> stringResource(R.string.app_filter_subtitle_off)
        AppFilterMode.WHITELIST -> stringResource(R.string.app_filter_subtitle_whitelist, state.selectedPackages.size)
        AppFilterMode.BLACKLIST -> stringResource(R.string.app_filter_subtitle_blacklist, state.selectedPackages.size)
    }

@Composable
private fun SettingsPageIndicator(
    currentPage: Int,
    pageCount: Int,
    focusRequesters: List<FocusRequester>,
    upFocusRequesters: List<FocusRequester>,
    onPageSelected: (Int) -> Unit,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            if (index > 0) Spacer(modifier = Modifier.width((8 * scale).dp))
            PageIndicatorButton(
                label = "${index + 1}",
                selected = index == currentPage,
                focusRequester = focusRequesters.getOrNull(index),
                upFocusRequester = upFocusRequesters.getOrNull(index),
                leftFocusRequester = focusRequesters.getOrNull(index - 1),
                rightFocusRequester = focusRequesters.getOrNull(index + 1),
                onClick = { onPageSelected(index) },
                scale = scale,
                bodySize = bodySize,
            )
        }
    }
}

@Composable
private fun PageIndicatorButton(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester?,
    rightFocusRequester: FocusRequester?,
    onClick: () -> Unit,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((8 * scale).dp)
    val size = (42 * scale).dp
    val modifier = Modifier
        .size(size)
        .settingsChoiceFocus(
            focusRequester = focusRequester,
            upFocusRequester = upFocusRequester,
            leftFocusRequester = leftFocusRequester,
            rightFocusRequester = rightFocusRequester,
        )
        .onFocusChanged { focused = it.isFocused }
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            contentPadding = PaddingValues(0.dp),
            border = when {
                focused -> BorderStroke(
                    (2 * scale).dp,
                    MaterialTheme.colorScheme.onSurface,
                )
                selected -> null
                else -> ButtonDefaults.outlinedButtonBorder(enabled = true)
            },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) VpnGreen else Color.Transparent,
                contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(label, fontSize = bodySize, fontWeight = FontWeight.SemiBold)
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
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                .settingsChoiceFocus(
                    focusRequester = focusRequester,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = downFocusRequester,
                    leftFocusRequester = leftFocusRequester,
                    rightFocusRequester = rightFocusRequester,
                )
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            border = if (focused) {
                BorderStroke((2 * scale).dp, MaterialTheme.colorScheme.onSurface)
            } else {
                ButtonDefaults.outlinedButtonBorder(enabled = true)
            },
            contentPadding = PaddingValues(horizontal = buttonPadH, vertical = buttonPadV),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        ) {
            Text(label, fontSize = bodySize)
        }
    }
}

@Composable
private fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    scale: Float,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                .settingsChoiceFocus(
                    focusRequester = focusRequester,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = downFocusRequester,
                    leftFocusRequester = leftFocusRequester,
                    rightFocusRequester = rightFocusRequester,
                )
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            border = when {
                focused -> BorderStroke(
                    (2 * scale).dp,
                    MaterialTheme.colorScheme.onSurface,
                )
                selected -> null
                else -> ButtonDefaults.outlinedButtonBorder(enabled = true)
            },
            contentPadding = PaddingValues(horizontal = (16 * scale).dp, vertical = (6 * scale).dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) VpnGreen else Color.Transparent,
                contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(label, fontSize = bodySize)
        }
    }
}

private fun Modifier.settingsChoiceFocus(
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester?,
    rightFocusRequester: FocusRequester?,
): Modifier = this
    .then(
        if (focusRequester != null) Modifier.focusRequester(focusRequester)
        else Modifier
    )
    .then(
        if (
            upFocusRequester != null ||
            downFocusRequester != null ||
            leftFocusRequester != null ||
            rightFocusRequester != null
        ) {
            Modifier.focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            }
        } else Modifier
    )

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

private val SETTINGS_DIAGNOSTIC_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")
