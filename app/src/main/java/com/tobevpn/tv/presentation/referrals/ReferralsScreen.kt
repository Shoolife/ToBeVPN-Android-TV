package com.tobevpn.tv.presentation.referrals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.data.remote.dto.ReferralListItemDto
import com.tobevpn.tv.data.remote.dto.ReferralUserDto
import com.tobevpn.tv.data.remote.dto.ReferralsDto
import com.tobevpn.tv.presentation.components.QrCode
import com.tobevpn.tv.presentation.components.SpinningRefreshIcon
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import java.text.DateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReferralsScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    viewModel: ReferralsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restoreFocusScope = rememberCoroutineScope()

    val backFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val copyFocusRequester = remember { FocusRequester() }
    val qrFocusRequester = remember { FocusRequester() }
    val invitedFocusRequester = remember { FocusRequester() }
    val inputFocusRequester = remember { FocusRequester() }
    val assignFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val inlineRetryFocusRequester = remember { FocusRequester() }

    var referrerIdInput by rememberSaveable { mutableStateOf("") }
    var pendingReferrerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showInvitedFriends by rememberSaveable { mutableStateOf(false) }
    var showReferralQr by rememberSaveable { mutableStateOf(false) }
    var copyEvent by remember { mutableIntStateOf(0) }
    var showCopyNotice by remember { mutableStateOf(false) }

    val data = uiState.data
    val referralUrl = data?.referralUrl.orEmpty()
    val clipboardLabel = stringResource(R.string.referrals_clipboard_label)
    val hasInitialErrorAction = uiState.isAuthResolved &&
        uiState.isAuthenticated &&
        data == null &&
        !uiState.isInitialLoading
    val backDownFocusRequester = when {
        data?.referralUrl?.isNotBlank() == true -> copyFocusRequester
        data != null -> invitedFocusRequester
        hasInitialErrorAction -> retryFocusRequester
        else -> FocusRequester.Cancel
    }
    val refreshDownFocusRequester = when {
        data != null -> invitedFocusRequester
        hasInitialErrorAction -> retryFocusRequester
        else -> FocusRequester.Cancel
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocusRequester.requestFocus() }
    }

    LaunchedEffect(copyEvent) {
        if (copyEvent == 0) return@LaunchedEffect
        showCopyNotice = true
        delay(COPY_NOTICE_DURATION_MS)
        showCopyNotice = false
    }

    fun restoreFocus(requester: FocusRequester) {
        restoreFocusScope.launch {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }

    fun copyReferralLink() {
        if (referralUrl.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, referralUrl))
        copyEvent += 1
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)
        val screenPad = (40 * scale).dp
        val gap = (16 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val headerCorner = (8 * scale).dp
        val borderWidth = (2 * scale).dp
        val headlineSize = (26 * scale).sp
        val headerColor = MaterialTheme.colorScheme.onBackground
        val tightStyle = rememberTightTextStyle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvHeaderIconButton(
                    onClick = onBack,
                    onLongClick = onLongBack,
                    modifier = Modifier
                        .size(headerButtonSize)
                        .focusRequester(backFocusRequester)
                        .referralDirectionalFocus(
                            up = FocusRequester.Cancel,
                            down = backDownFocusRequester,
                            left = FocusRequester.Cancel,
                            right = if (uiState.isAuthenticated) {
                                refreshFocusRequester
                            } else {
                                FocusRequester.Cancel
                            },
                        ),
                    shape = RoundedCornerShape(headerCorner),
                    borderWidth = borderWidth,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(headerIconSize),
                        tint = headerColor,
                    )
                }
                Spacer(modifier = Modifier.width(gap))
                Text(
                    text = stringResource(R.string.referrals_title),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.isAuthenticated) {
                    TvHeaderIconButton(
                        onClick = viewModel::refresh,
                        enabled = !uiState.isAssigningReferrer,
                        modifier = Modifier
                            .size(headerButtonSize)
                            .focusRequester(refreshFocusRequester)
                            .referralDirectionalFocus(
                                up = FocusRequester.Cancel,
                                down = refreshDownFocusRequester,
                                left = backFocusRequester,
                                right = FocusRequester.Cancel,
                            ),
                        shape = RoundedCornerShape(headerCorner),
                        borderWidth = borderWidth,
                    ) {
                        SpinningRefreshIcon(
                            spinning = uiState.isInitialLoading || uiState.isRefreshing,
                            contentDescription = stringResource(R.string.refresh),
                            tint = headerColor,
                            size = headerIconSize,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((20 * scale).dp))

            when {
                !uiState.isAuthResolved || (uiState.isInitialLoading && data == null) -> {
                    ReferralCenteredLoading(modifier = Modifier.weight(1f))
                }

                !uiState.isAuthenticated -> {
                    ReferralMessageState(
                        title = stringResource(R.string.referrals_auth_title),
                        message = stringResource(R.string.referrals_auth_description),
                        modifier = Modifier.weight(1f),
                    )
                }

                data == null -> {
                    ReferralErrorState(
                        error = uiState.error ?: ReferralLoadError.UNKNOWN,
                        onRetry = viewModel::refresh,
                        focusRequester = retryFocusRequester,
                        upFocusRequester = backFocusRequester,
                        scale = scale,
                        tightStyle = tightStyle,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    ReferralDashboard(
                        data = data,
                        error = uiState.error,
                        referrerAssignmentError = uiState.referrerAssignmentError,
                        isRefreshing = uiState.isRefreshing,
                        isAssigningReferrer = uiState.isAssigningReferrer,
                        referrerIdInput = referrerIdInput,
                        onReferrerIdChange = { rawValue ->
                            referrerIdInput = rawValue
                                .filter(Char::isDigit)
                                .take(MAX_TELEGRAM_ID_DIGITS)
                            viewModel.clearReferrerAssignmentError()
                        },
                        onRequestAssign = { pendingReferrerId = it },
                        onCopy = ::copyReferralLink,
                        onShowQr = { showReferralQr = true },
                        onShowInvited = { showInvitedFriends = true },
                        onRetry = viewModel::refresh,
                        copyFocusRequester = copyFocusRequester,
                        qrFocusRequester = qrFocusRequester,
                        invitedFocusRequester = invitedFocusRequester,
                        inputFocusRequester = inputFocusRequester,
                        assignFocusRequester = assignFocusRequester,
                        inlineRetryFocusRequester = inlineRetryFocusRequester,
                        backFocusRequester = backFocusRequester,
                        refreshFocusRequester = refreshFocusRequester,
                        scale = scale,
                        tightStyle = tightStyle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showCopyNotice,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (26 * scale).dp),
        ) {
            Surface(
                shape = RoundedCornerShape((12 * scale).dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = (8 * scale).dp,
            ) {
                Text(
                    text = stringResource(R.string.referrals_link_copied),
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = (18 * scale).dp,
                        vertical = (10 * scale).dp,
                    ),
                )
            }
        }

        if (showInvitedFriends && data != null) {
            InvitedFriendsDialog(
                items = data.referrals.orEmpty(),
                total = data.total,
                error = uiState.error,
                isRefreshing = uiState.isRefreshing,
                isLoadingMore = uiState.isLoadingMore,
                onRetry = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onDismiss = {
                    showInvitedFriends = false
                    restoreFocus(invitedFocusRequester)
                },
                scale = scale,
                tightStyle = tightStyle,
            )
        }

        if (showReferralQr && referralUrl.isNotBlank()) {
            ReferralQrDialog(
                referralUrl = referralUrl,
                onDismiss = {
                    showReferralQr = false
                    restoreFocus(qrFocusRequester)
                },
                scale = scale,
                tightStyle = tightStyle,
            )
        }

        pendingReferrerId?.let { referrerId ->
            ReferrerConfirmationDialog(
                referrerId = referrerId,
                onDismiss = {
                    pendingReferrerId = null
                    restoreFocus(assignFocusRequester)
                },
                onConfirm = {
                    pendingReferrerId = null
                    viewModel.assignReferrer(referrerId)
                    restoreFocus(assignFocusRequester)
                },
                scale = scale,
                tightStyle = tightStyle,
            )
        }
    }
}

@Composable
private fun ReferrerConfirmationDialog(
    referrerId: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    scale: Float,
    tightStyle: TextStyle,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    var cancelFocused by remember { mutableStateOf(false) }
    var confirmFocused by remember { mutableStateOf(false) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dialogShape = RoundedCornerShape((21 * scale).dp)
    val buttonShape = RoundedCornerShape((13 * scale).dp)
    val outlineColor = if (darkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    } else {
        Color(0xFFD0D0D0)
    }
    val secondaryButtonColor = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White
    }

    LaunchedEffect(Unit) {
        // The safe action is selected first so Enter cannot bind an ID by accident.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { cancelFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.40f)
                    .widthIn(max = (500 * scale).dp),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    (1 * scale).dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = (12 * scale).dp,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((23 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size((46 * scale).dp)
                            .clip(CircleShape)
                            .background(VpnGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = VpnGreen,
                            modifier = Modifier.size((25 * scale).dp),
                        )
                    }

                    Spacer(modifier = Modifier.height((13 * scale).dp))

                    Text(
                        text = stringResource(R.string.referrals_referrer_confirm_title),
                        fontSize = (21 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = tightStyle,
                    )

                    Spacer(modifier = Modifier.height((8 * scale).dp))

                    Text(
                        text = stringResource(
                            R.string.referrals_referrer_confirm_description,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = (14 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = tightStyle,
                    )

                    Spacer(modifier = Modifier.height((13 * scale).dp))

                    Surface(
                        shape = RoundedCornerShape((11 * scale).dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.referrals_referrer_id_value,
                                referrerId,
                            ),
                            modifier = Modifier.padding(
                                horizontal = (14 * scale).dp,
                                vertical = (10 * scale).dp,
                            ),
                            fontSize = (15 * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (darkTheme) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Black
                            },
                            maxLines = 1,
                            style = tightStyle,
                        )
                    }

                    Spacer(modifier = Modifier.height((18 * scale).dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height((46 * scale).dp)
                                .focusRequester(cancelFocusRequester)
                                .referralDirectionalFocus(
                                    up = FocusRequester.Cancel,
                                    down = FocusRequester.Cancel,
                                    left = FocusRequester.Cancel,
                                    right = confirmFocusRequester,
                                )
                                .onFocusChanged { cancelFocused = it.isFocused },
                            shape = buttonShape,
                            border = BorderStroke(
                                width = if (cancelFocused) {
                                    (2 * scale).dp
                                } else {
                                    (1 * scale).dp
                                },
                                color = if (cancelFocused) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    outlineColor
                                },
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = secondaryButtonColor,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = (12 * scale).dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontSize = (14 * scale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                style = tightStyle,
                            )
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height((46 * scale).dp)
                                .focusRequester(confirmFocusRequester)
                                .referralDirectionalFocus(
                                    up = FocusRequester.Cancel,
                                    down = FocusRequester.Cancel,
                                    left = cancelFocusRequester,
                                    right = FocusRequester.Cancel,
                                )
                                .onFocusChanged { confirmFocused = it.isFocused },
                            shape = buttonShape,
                            border = if (confirmFocused) {
                                BorderStroke(
                                    (2 * scale).dp,
                                    MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VpnGreen,
                                contentColor = Color.Black,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = (12 * scale).dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.referrals_referrer_confirm,
                                ),
                                fontSize = (14 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                style = tightStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferralDashboard(
    data: ReferralsDto,
    error: ReferralLoadError?,
    referrerAssignmentError: ReferrerAssignmentError?,
    isRefreshing: Boolean,
    isAssigningReferrer: Boolean,
    referrerIdInput: String,
    onReferrerIdChange: (String) -> Unit,
    onRequestAssign: (Long) -> Unit,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    onShowInvited: () -> Unit,
    onRetry: () -> Unit,
    copyFocusRequester: FocusRequester,
    qrFocusRequester: FocusRequester,
    invitedFocusRequester: FocusRequester,
    inputFocusRequester: FocusRequester,
    assignFocusRequester: FocusRequester,
    inlineRetryFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    refreshFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val columnGap = (18 * scale).dp
    val cardGap = (14 * scale).dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ReferralHeroCard(
            referralUrl = data.referralUrl.orEmpty(),
            enabled = data.referralUrl?.isNotBlank() == true,
            onCopy = onCopy,
            onShowQr = onShowQr,
            copyFocusRequester = copyFocusRequester,
            qrFocusRequester = qrFocusRequester,
            invitedFocusRequester = invitedFocusRequester,
            backFocusRequester = backFocusRequester,
            qrRightFocusRequester = if (data.referrer == null) {
                inputFocusRequester
            } else if (error != null) {
                inlineRetryFocusRequester
            } else {
                invitedFocusRequester
            },
            scale = scale,
            tightStyle = tightStyle,
            modifier = Modifier
                .weight(1.08f)
                .fillMaxHeight(),
        )

        Spacer(modifier = Modifier.width(columnGap))

        Column(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxHeight(),
        ) {
            ReferralSummaryCard(
                total = data.total,
                isRefreshing = isRefreshing,
                onOpenList = onShowInvited,
                focusRequester = invitedFocusRequester,
                upFocusRequester = refreshFocusRequester,
                downFocusRequester = if (data.referrer == null) {
                    inputFocusRequester
                } else if (error != null) {
                    inlineRetryFocusRequester
                } else {
                    qrFocusRequester
                },
                leftFocusRequester = qrFocusRequester,
                scale = scale,
                tightStyle = tightStyle,
            )

            Spacer(modifier = Modifier.height(cardGap))

            if (data.referrer == null) {
                ReferrerInputCard(
                    value = referrerIdInput,
                    error = referrerAssignmentError,
                    isSubmitting = isAssigningReferrer,
                    onValueChange = onReferrerIdChange,
                    onRequestSubmit = onRequestAssign,
                    inputFocusRequester = inputFocusRequester,
                    assignFocusRequester = assignFocusRequester,
                    invitedFocusRequester = invitedFocusRequester,
                    qrFocusRequester = qrFocusRequester,
                    downFocusRequester = if (error != null) {
                        inlineRetryFocusRequester
                    } else {
                        FocusRequester.Cancel
                    },
                    scale = scale,
                    tightStyle = tightStyle,
                )
            } else {
                ReferrerCard(
                    referrer = data.referrer,
                    scale = scale,
                    tightStyle = tightStyle,
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(cardGap))
                ReferralInlineError(
                    error = error,
                    onRetry = onRetry,
                    focusRequester = inlineRetryFocusRequester,
                    upFocusRequester = if (data.referrer == null) {
                        assignFocusRequester
                    } else {
                        invitedFocusRequester
                    },
                    leftFocusRequester = qrFocusRequester,
                    scale = scale,
                    tightStyle = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun ReferralHeroCard(
    referralUrl: String,
    enabled: Boolean,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    copyFocusRequester: FocusRequester,
    qrFocusRequester: FocusRequester,
    invitedFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    qrRightFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val cardCorner = (18 * scale).dp
    val cardPad = (22 * scale).dp
    val gap = (14 * scale).dp
    val titleSize = (22 * scale).sp
    val bodySize = (15 * scale).sp
    val metaSize = (13 * scale).sp

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(cardPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size((48 * scale).dp)
                        .clip(RoundedCornerShape((14 * scale).dp))
                        .background(VpnGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = VpnGreen,
                        modifier = Modifier.size((27 * scale).dp),
                    )
                }
                Spacer(modifier = Modifier.width(gap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.referrals_hero_title),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((4 * scale).dp))
                    Text(
                        text = stringResource(R.string.referrals_hero_description),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            }

            Spacer(modifier = Modifier.height((20 * scale).dp))

            Text(
                text = stringResource(R.string.referrals_your_link),
                fontSize = metaSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height((8 * scale).dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape((12 * scale).dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    (1 * scale).dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    text = referralUrl.ifBlank {
                        stringResource(R.string.referrals_link_unavailable)
                    },
                    modifier = Modifier.padding(
                        horizontal = (14 * scale).dp,
                        vertical = (12 * scale).dp,
                    ),
                    fontSize = bodySize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = tightStyle,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height((18 * scale).dp))

            Row(horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                ReferralActionButton(
                    text = stringResource(R.string.referrals_copy),
                    icon = Icons.Filled.ContentCopy,
                    onClick = onCopy,
                    enabled = enabled,
                    focusRequester = copyFocusRequester,
                    upFocusRequester = backFocusRequester,
                    downFocusRequester = FocusRequester.Cancel,
                    leftFocusRequester = FocusRequester.Cancel,
                    rightFocusRequester = qrFocusRequester,
                    scale = scale,
                    tightStyle = tightStyle,
                    modifier = Modifier.weight(1f),
                )
                ReferralActionButton(
                    text = stringResource(R.string.referrals_show_qr),
                    icon = Icons.Filled.QrCode2,
                    onClick = onShowQr,
                    enabled = enabled,
                    focusRequester = qrFocusRequester,
                    upFocusRequester = backFocusRequester,
                    downFocusRequester = FocusRequester.Cancel,
                    leftFocusRequester = copyFocusRequester,
                    rightFocusRequester = qrRightFocusRequester,
                    primary = true,
                    scale = scale,
                    tightStyle = tightStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReferralSummaryCard(
    total: Int,
    isRefreshing: Boolean,
    onOpenList: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((18 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((18 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size((48 * scale).dp)
                    .clip(CircleShape)
                    .background(VpnBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = VpnBlue,
                    modifier = Modifier.size((27 * scale).dp),
                )
            }
            Spacer(modifier = Modifier.width((14 * scale).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRefreshing) "…" else total.toString(),
                    fontSize = (28 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    style = tightStyle,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.referrals_invited_count,
                        total,
                        total,
                    ),
                    fontSize = (14 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
            }
            ReferralActionButton(
                text = stringResource(R.string.referrals_open_list),
                onClick = onOpenList,
                focusRequester = focusRequester,
                upFocusRequester = upFocusRequester,
                downFocusRequester = downFocusRequester,
                leftFocusRequester = leftFocusRequester,
                rightFocusRequester = FocusRequester.Cancel,
                scale = scale,
                tightStyle = tightStyle,
            )
        }
    }
}

@Composable
private fun ReferrerInputCard(
    value: String,
    error: ReferrerAssignmentError?,
    isSubmitting: Boolean,
    onValueChange: (String) -> Unit,
    onRequestSubmit: (Long) -> Unit,
    inputFocusRequester: FocusRequester,
    assignFocusRequester: FocusRequester,
    invitedFocusRequester: FocusRequester,
    qrFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
) {
    val referrerId = value.toLongOrNull()?.takeIf { it > 0 }
    val hasLocalError = value.isNotEmpty() && referrerId == null
    val fieldShape = RoundedCornerShape((12 * scale).dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((18 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding((18 * scale).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = VpnGreen,
                    modifier = Modifier.size((24 * scale).dp),
                )
                Spacer(modifier = Modifier.width((10 * scale).dp))
                Text(
                    text = stringResource(R.string.referrals_referrer_input_title),
                    fontSize = (18 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    style = tightStyle,
                )
            }
            Spacer(modifier = Modifier.height((7 * scale).dp))
            Text(
                text = stringResource(R.string.referrals_referrer_input_description),
                fontSize = (13 * scale).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height((13 * scale).dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inputFocusRequester)
                        .referralDirectionalFocus(
                            up = invitedFocusRequester,
                            down = downFocusRequester,
                            left = qrFocusRequester,
                            right = assignFocusRequester,
                        ),
                    enabled = !isSubmitting,
                    singleLine = true,
                    isError = hasLocalError || error != null,
                    label = {
                        Text(
                            text = stringResource(R.string.referrals_referrer_id_label),
                            fontSize = (12 * scale).sp,
                        )
                    },
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { referrerId?.let(onRequestSubmit) },
                    ),
                    textStyle = TextStyle(fontSize = (15 * scale).sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                ReferralActionButton(
                    text = if (isSubmitting) {
                        stringResource(R.string.referrals_referrer_assigning)
                    } else {
                        stringResource(R.string.referrals_referrer_assign)
                    },
                    onClick = { referrerId?.let(onRequestSubmit) },
                    enabled = referrerId != null && !isSubmitting,
                    focusRequester = assignFocusRequester,
                    upFocusRequester = invitedFocusRequester,
                    downFocusRequester = downFocusRequester,
                    leftFocusRequester = inputFocusRequester,
                    rightFocusRequester = FocusRequester.Cancel,
                    primary = true,
                    scale = scale,
                    tightStyle = tightStyle,
                )
            }

            val errorText = when {
                hasLocalError -> stringResource(R.string.referrals_referrer_id_invalid)
                error != null -> referrerAssignmentErrorText(error)
                else -> null
            }
            if (errorText != null) {
                Spacer(modifier = Modifier.height((7 * scale).dp))
                Text(
                    text = errorText,
                    fontSize = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.error,
                    style = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun ReferrerCard(
    referrer: ReferralUserDto,
    scale: Float,
    tightStyle: TextStyle,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((18 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((18 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size((44 * scale).dp)
                    .clip(CircleShape)
                    .background(VpnGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = VpnGreen,
                    modifier = Modifier.size((24 * scale).dp),
                )
            }
            Spacer(modifier = Modifier.width((13 * scale).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.referrals_referred_by),
                    fontSize = (13 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height((4 * scale).dp))
                Text(
                    text = referrer.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: referrer.telegramId?.let {
                            stringResource(R.string.referrals_referrer_id_value, it)
                        }
                        ?: stringResource(R.string.referrals_unknown_user),
                    fontSize = (17 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun InvitedFriendsDialog(
    items: List<ReferralListItemDto>,
    total: Int,
    error: ReferralLoadError?,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    scale: Float,
    tightStyle: TextStyle,
) {
    val closeFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }
    val actionFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val hasMore = items.size < total
    val hasBottomAction = error != null || hasMore

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.80f)
                    .widthIn(max = (980 * scale).dp),
                shape = RoundedCornerShape(
                    topStart = (24 * scale).dp,
                    topEnd = (24 * scale).dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    (1 * scale).dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding((22 * scale).dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.referrals_invited_title),
                                fontSize = (23 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                style = tightStyle,
                            )
                            Spacer(modifier = Modifier.height((4 * scale).dp))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.referrals_invited_count,
                                    total,
                                    total,
                                ),
                                fontSize = (14 * scale).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = tightStyle,
                            )
                        }
                        TvHeaderIconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size((42 * scale).dp)
                                .focusRequester(closeFocusRequester)
                                .referralDirectionalFocus(
                                    up = FocusRequester.Cancel,
                                    down = listFocusRequester,
                                    left = FocusRequester.Cancel,
                                    right = FocusRequester.Cancel,
                                ),
                            shape = RoundedCornerShape((9 * scale).dp),
                            borderWidth = (2 * scale).dp,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size((20 * scale).dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height((16 * scale).dp))

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRequester(listFocusRequester)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (listState.canScrollForward) {
                                            scrollScope.launch {
                                                listState.animateScrollBy((170 * scale))
                                            }
                                        } else if (hasBottomAction) {
                                            actionFocusRequester.requestFocus()
                                        }
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        if (listState.canScrollBackward) {
                                            scrollScope.launch {
                                                listState.animateScrollBy((-170 * scale))
                                            }
                                        } else {
                                            closeFocusRequester.requestFocus()
                                        }
                                        true
                                    }

                                    Key.DirectionLeft,
                                    Key.DirectionRight,
                                    -> true

                                    else -> false
                                }
                            },
                        contentPadding = PaddingValues(bottom = (8 * scale).dp),
                        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
                    ) {
                        if (isRefreshing) {
                            items(3) { index ->
                                ReferralListLoadingRow(
                                    key = index,
                                    scale = scale,
                                )
                            }
                        } else if (items.isEmpty()) {
                            item {
                                ReferralEmptyList(scale = scale, tightStyle = tightStyle)
                            }
                        } else {
                            itemsIndexed(
                                items = items,
                                key = { index, item ->
                                    "${item.telegramId}-${item.createdAt}-${item.displayName}-$index"
                                },
                            ) { _, item ->
                                ReferralListRow(
                                    item = item,
                                    scale = scale,
                                    tightStyle = tightStyle,
                                )
                            }
                        }
                    }

                    if (hasBottomAction) {
                        Spacer(modifier = Modifier.height((12 * scale).dp))
                        ReferralActionButton(
                            text = if (error != null) {
                                stringResource(R.string.retry)
                            } else {
                                stringResource(R.string.referrals_load_more)
                            },
                            onClick = if (error != null) onRetry else onLoadMore,
                            enabled = !isRefreshing && !isLoadingMore,
                            focusRequester = actionFocusRequester,
                            upFocusRequester = listFocusRequester,
                            downFocusRequester = FocusRequester.Cancel,
                            leftFocusRequester = FocusRequester.Cancel,
                            rightFocusRequester = FocusRequester.Cancel,
                            primary = true,
                            showProgress = isLoadingMore,
                            scale = scale,
                            tightStyle = tightStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferralQrDialog(
    referralUrl: String,
    onDismiss: () -> Unit,
    scale: Float,
    tightStyle: TextStyle,
) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.48f)
                    .widthIn(max = (560 * scale).dp),
                shape = RoundedCornerShape((22 * scale).dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    (1 * scale).dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((22 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.referrals_qr_title),
                        fontSize = (22 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((8 * scale).dp))
                    Text(
                        text = stringResource(R.string.referrals_qr_description),
                        fontSize = (14 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((15 * scale).dp))
                    Box(
                        modifier = Modifier
                            .size((236 * scale).dp)
                            .clip(RoundedCornerShape((14 * scale).dp))
                            .background(Color.White)
                            .padding((14 * scale).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        QrCode(
                            data = referralUrl,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height((15 * scale).dp))
                    ReferralActionButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        focusRequester = closeFocusRequester,
                        upFocusRequester = FocusRequester.Cancel,
                        downFocusRequester = FocusRequester.Cancel,
                        leftFocusRequester = FocusRequester.Cancel,
                        rightFocusRequester = FocusRequester.Cancel,
                        primary = true,
                        scale = scale,
                        tightStyle = tightStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferralListRow(
    item: ReferralListItemDto,
    scale: Float,
    tightStyle: TextStyle,
) {
    val formattedDate = remember(item.createdAt) {
        formatReferralDate(item.createdAt)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((14 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (16 * scale).dp, vertical = (12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size((40 * scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((21 * scale).dp),
                )
            }
            Spacer(modifier = Modifier.width((12 * scale).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.referrals_unknown_user),
                    fontSize = (16 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightStyle,
                )
                if (formattedDate != null) {
                    Spacer(modifier = Modifier.height((3 * scale).dp))
                    Text(
                        text = stringResource(R.string.referrals_joined, formattedDate),
                        fontSize = (12 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = VpnGreen.copy(alpha = 0.15f),
                contentColor = VpnGreen,
            ) {
                Text(
                    text = stringResource(
                        R.string.referrals_level,
                        item.level.coerceAtLeast(1),
                    ),
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = (10 * scale).dp,
                        vertical = (5 * scale).dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReferralListLoadingRow(
    key: Int,
    scale: Float,
) {
    val transition = rememberInfiniteTransition(label = "referrals-loading-$key")
    val alpha by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "referrals-loading-alpha-$key",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape((14 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = (16 * scale).dp,
                vertical = (12 * scale).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size((40 * scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
            )
            Spacer(modifier = Modifier.width((12 * scale).dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy((7 * scale).dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height((15 * scale).dp)
                        .clip(RoundedCornerShape((5 * scale).dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.32f)
                        .height((11 * scale).dp)
                        .clip(RoundedCornerShape((5 * scale).dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)),
                )
            }
        }
    }
}

@Composable
private fun ReferralEmptyList(
    scale: Float,
    tightStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (34 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((40 * scale).dp),
        )
        Spacer(modifier = Modifier.height((10 * scale).dp))
        Text(
            text = stringResource(R.string.referrals_empty_title),
            fontSize = (18 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
        Spacer(modifier = Modifier.height((5 * scale).dp))
        Text(
            text = stringResource(R.string.referrals_empty_description),
            fontSize = (14 * scale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

@Composable
private fun ReferralInlineError(
    error: ReferralLoadError,
    onRetry: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((14 * scale).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding((13 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = referralErrorText(error),
                modifier = Modifier.weight(1f),
                fontSize = (12 * scale).sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.width((8 * scale).dp))
            ReferralActionButton(
                text = stringResource(R.string.retry),
                onClick = onRetry,
                focusRequester = focusRequester,
                upFocusRequester = upFocusRequester,
                downFocusRequester = FocusRequester.Cancel,
                leftFocusRequester = leftFocusRequester,
                rightFocusRequester = FocusRequester.Cancel,
                scale = scale,
                tightStyle = tightStyle,
            )
        }
    }
}

@Composable
private fun ReferralCenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VpnGreen)
    }
}

@Composable
private fun ReferralMessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Groups,
            contentDescription = null,
            tint = VpnBlue,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun ReferralErrorState(
    error: ReferralLoadError,
    onRetry: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    scale: Float,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.referrals_error_title),
            fontSize = (21 * scale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
        Spacer(modifier = Modifier.height((7 * scale).dp))
        Text(
            text = referralErrorText(error),
            fontSize = (14 * scale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
        Spacer(modifier = Modifier.height((18 * scale).dp))
        ReferralActionButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
            focusRequester = focusRequester,
            upFocusRequester = upFocusRequester,
            downFocusRequester = FocusRequester.Cancel,
            leftFocusRequester = FocusRequester.Cancel,
            rightFocusRequester = FocusRequester.Cancel,
            primary = true,
            scale = scale,
            tightStyle = tightStyle,
        )
    }
}

@Composable
private fun ReferralActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    primary: Boolean = false,
    showProgress: Boolean = false,
    scale: Float,
    tightStyle: TextStyle,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((11 * scale).dp)
    val buttonModifier = modifier
        .defaultMinSize(minWidth = 1.dp, minHeight = (42 * scale).dp)
        .then(
            if (focusRequester != null) {
                Modifier.focusRequester(focusRequester)
            } else {
                Modifier
            },
        )
        .referralDirectionalFocus(
            up = upFocusRequester,
            down = downFocusRequester,
            left = leftFocusRequester,
            right = rightFocusRequester,
        )
        .onFocusChanged { focused = it.isFocused }

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        if (primary) {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                shape = shape,
                contentPadding = PaddingValues(
                    horizontal = (15 * scale).dp,
                    vertical = (7 * scale).dp,
                ),
                border = if (focused) {
                    BorderStroke((2 * scale).dp, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VpnGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                ReferralButtonContent(
                    text = text,
                    icon = icon,
                    showProgress = showProgress,
                    scale = scale,
                    tightStyle = tightStyle,
                )
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                shape = shape,
                contentPadding = PaddingValues(
                    horizontal = (15 * scale).dp,
                    vertical = (7 * scale).dp,
                ),
                border = BorderStroke(
                    width = if (focused) (2 * scale).dp else (1 * scale).dp,
                    color = if (focused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (focused) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color.Transparent
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                ReferralButtonContent(
                    text = text,
                    icon = icon,
                    showProgress = showProgress,
                    scale = scale,
                    tightStyle = tightStyle,
                )
            }
        }
    }
}

private fun Modifier.referralDirectionalFocus(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier {
    if (up == null && down == null && left == null && right == null) {
        return this
    }

    return this
        .focusProperties {
            if (up != null) this.up = up
            if (down != null) this.down = down
            if (left != null) this.left = left
            if (right != null) this.right = right
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }

            val target = when (event.key) {
                Key.DirectionUp -> up
                Key.DirectionDown -> down
                Key.DirectionLeft -> left
                Key.DirectionRight -> right
                else -> null
            } ?: return@onPreviewKeyEvent false

            if (target !== FocusRequester.Cancel) {
                runCatching { target.requestFocus() }
            }
            true
        }
}

@Composable
private fun ReferralButtonContent(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    showProgress: Boolean,
    scale: Float,
    tightStyle: TextStyle,
) {
    if (showProgress) {
        CircularProgressIndicator(
            modifier = Modifier.size((17 * scale).dp),
            strokeWidth = (2 * scale).dp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.width((8 * scale).dp))
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size((18 * scale).dp),
        )
        Spacer(modifier = Modifier.width((8 * scale).dp))
    }
    Text(
        text = text,
        fontSize = (14 * scale).sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = tightStyle,
    )
}

@Composable
private fun referralErrorText(error: ReferralLoadError): String = when (error) {
    ReferralLoadError.NETWORK -> stringResource(R.string.referrals_error_network)
    ReferralLoadError.UNAVAILABLE -> stringResource(R.string.referrals_error_unavailable)
    ReferralLoadError.UNKNOWN -> stringResource(R.string.referrals_error_unknown)
}

@Composable
private fun referrerAssignmentErrorText(error: ReferrerAssignmentError): String = when (error) {
    ReferrerAssignmentError.NETWORK ->
        stringResource(R.string.referrals_referrer_error_network)

    ReferrerAssignmentError.NOT_FOUND ->
        stringResource(R.string.referrals_referrer_error_not_found)

    ReferrerAssignmentError.CONFLICT ->
        stringResource(R.string.referrals_referrer_error_conflict)

    ReferrerAssignmentError.UNAVAILABLE ->
        stringResource(R.string.referrals_referrer_error_unavailable)

    ReferrerAssignmentError.UNKNOWN ->
        stringResource(R.string.referrals_referrer_error_unknown)
}

private fun formatReferralDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val instant = runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .getOrNull()
        ?: return null
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(instant))
}

@Composable
private fun rememberTightTextStyle(): TextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private const val MAX_TELEGRAM_ID_DIGITS = 19
private const val COPY_NOTICE_DURATION_MS = 1_800L
