package com.tobevpn.tv.presentation.subscription

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tobevpn.tv.R
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.UserPlan
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class PurchasePlan(
    val key: String,
    val title: String,
    val priceDisplay: String,
    val description: String,
    val botPaymentUrl: String? = null,
)

private data class CurrentPlanUi(
    val title: String,
    val subtitle: String,
    val accentColor: Color,
)

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val rubToUsdRate by viewModel.rubToUsdRate.collectAsStateWithLifecycle()
    val purchasePlans by viewModel.purchasePlans.collectAsStateWithLifecycle()
    val currentLimits by viewModel.currentLimits.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isRussian = configuration.locales[0]?.language == "ru"

    var selectedPlanKey by rememberSaveable { mutableStateOf("month") }
    var showQr by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showQr) {
        showQr = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val columnGap = (24 * scale).dp
        val cardGap = (20 * scale).dp
        val smallGap = (10 * scale).dp
        val cardPad = (22 * scale).dp
        val cardCorner = (18 * scale).dp
        val planCardCorner = (14 * scale).dp
        val planCardPad = (16 * scale).dp
        val planListGap = (12 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (20 * scale).sp
        val bodySize = (15 * scale).sp
        val labelSize = (12 * scale).sp
        val priceSize = (28 * scale).sp
        val buttonTextSize = (18 * scale).sp
        val borderWidth = (2 * scale).dp
        val backCorner = (8 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val buttonHeight = (48 * scale).dp
        val buttonPadH = (24 * scale).dp
        val buttonPadV = (10 * scale).dp
        val qrDialogWidth = min(maxWidth * 0.56f, (520 * scale).dp)
        val qrDialogSize = min(maxHeight * 0.52f, maxWidth * 0.34f)
        val qrDialogCorner = (24 * scale).dp
        val qrDialogPad = (28 * scale).dp
        val headerColor = MaterialTheme.colorScheme.onBackground

        val tightStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )

        // Pick the paid plan (with real durations) from backend.
        // Skip UNLIMITED / free plans that have no days-based durations.
        val sourcePlan = purchasePlans?.plans
            ?.filter { it.durations.any { d -> d.days > 0 } }
            ?.maxByOrNull { it.durations.size }

        val shouldLoadLimits = (authState as? AuthState.Authenticated)?.let {
            it.plan == UserPlan.PAID || it.plan == UserPlan.ADMIN
        } == true

        val displayLimits = currentLimits
        val planDescription = buildPlanDescription(
            trafficLimitGb = sourcePlan?.trafficLimit?.toInt(),
            deviceLimit = sourcePlan?.deviceLimit?.takeIf { it > 0 },
        )

        val plans: List<PurchasePlan> = if (sourcePlan != null) {
            sourcePlan.durations
                .filter { it.days > 0 }
                .sortedBy { it.orderIndex }
                .map { d ->
                    PurchasePlan(
                        key = planKey(d.days),
                        title = planTitle(d.days),
                        priceDisplay = formatDurationPrice(d, isRussian, rubToUsdRate),
                        description = planDescription,
                        botPaymentUrl = d.botPaymentUrl,
                    )
                }
        } else {
            emptyList()
        }
        val selectedPlan = plans.firstOrNull { it.key == selectedPlanKey }
            ?: plans.firstOrNull { it.key == "month" }
            ?: plans.firstOrNull()
        val currentPlan = currentPlanUi(authState)
        val qrUrl = selectedPlan?.botPaymentUrl
        val primaryButtonLabel = when (authState) {
            is AuthState.Authenticated -> {
                when ((authState as AuthState.Authenticated).plan) {
                    UserPlan.PAID, UserPlan.EXPIRED -> stringResource(R.string.subscription_renew_in_telegram)
                    UserPlan.ADMIN -> stringResource(R.string.subscription_renew_in_telegram)
                    UserPlan.FREE_TRIAL -> stringResource(R.string.subscription_buy_in_telegram)
                }
            }
            AuthState.Unauthenticated -> stringResource(R.string.subscription_buy_in_telegram)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var backFocused by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    IconButton(
                        onClick = {
                            if (showQr) showQr = false else onBack()
                        },
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
                Spacer(modifier = Modifier.width(cardGap))
                Text(
                    text = stringResource(R.string.subscription),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
            }

            Spacer(modifier = Modifier.height(cardGap))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(columnGap),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(cardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        when {
                            purchasePlans == null -> {
                                LoadingCardBody(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(cardPad),
                                    text = stringResource(R.string.subscription_loading_plans),
                                    bodySize = bodySize,
                                    gap = smallGap,
                                    tightStyle = tightStyle,
                                )
                            }
                            plans.isEmpty() -> {
                                EmptyCardBody(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(cardPad),
                                    text = stringResource(R.string.subscription_no_plans),
                                    bodySize = bodySize,
                                    tightStyle = tightStyle,
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(cardPad),
                                    verticalArrangement = Arrangement.spacedBy(planListGap),
                                    contentPadding = PaddingValues(bottom = planListGap),
                                ) {
                                    items(plans, key = { it.key }) { plan ->
                                        PlanOptionCard(
                                            plan = plan,
                                            selected = selectedPlan?.key == plan.key,
                                            onClick = {
                                                selectedPlanKey = plan.key
                                                showQr = false
                                            },
                                            corner = planCardCorner,
                                            cardPad = planCardPad,
                                            borderWidth = borderWidth,
                                            titleSize = bodySize,
                                            labelSize = labelSize,
                                            tightStyle = tightStyle,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(cardGap),
                ) {
                    CurrentPlanCard(
                        modifier = Modifier.fillMaxWidth(),
                        currentPlan = currentPlan,
                        limits = displayLimits,
                        showLimits = shouldLoadLimits,
                                showLimitsLoading = shouldLoadLimits && displayLimits == null,
                        cardCorner = cardCorner,
                        cardPad = cardPad,
                        iconSize = (20 * scale).dp,
                        titleSize = titleSize,
                        bodySize = bodySize,
                        labelSize = labelSize,
                        statValueSize = (22 * scale).sp,
                        smallGap = smallGap,
                        tightStyle = tightStyle,
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                            when {
                                purchasePlans == null -> {
                                    LoadingCardBody(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(R.string.subscription_loading_plans),
                                        bodySize = bodySize,
                                        gap = smallGap,
                                        tightStyle = tightStyle,
                                    )
                                }
                                selectedPlan == null -> {
                                    EmptyCardBody(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(R.string.subscription_no_plans),
                                        bodySize = bodySize,
                                        tightStyle = tightStyle,
                                    )
                                }
                                else -> {
                                    Text(
                                        text = stringResource(R.string.payment_via_telegram),
                                        fontSize = labelSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height(smallGap))
                                    Text(
                                        text = selectedPlan.title,
                                        fontSize = titleSize,
                                        fontWeight = FontWeight.Bold,
                                        style = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height((6 * scale).dp))
                                    Text(
                                        text = selectedPlan.priceDisplay,
                                        fontSize = priceSize,
                                        fontWeight = FontWeight.Bold,
                                        color = VpnGreen,
                                        style = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height((6 * scale).dp))
                                    Text(
                                        text = selectedPlan.description,
                                        fontSize = bodySize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height(cardGap))
                                    SubscriptionActionButton(
                                        text = primaryButtonLabel,
                                        onClick = { showQr = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        minHeight = buttonHeight,
                                        corner = planCardCorner,
                                        padding = PaddingValues(horizontal = buttonPadH, vertical = buttonPadV),
                                        borderWidth = borderWidth,
                                        textSize = buttonTextSize,
                                        textWeight = FontWeight.Bold,
                                        tightStyle = tightStyle,
                                    )

                                    Spacer(modifier = Modifier.height(cardGap))
                                    Text(
                                        text = stringResource(R.string.subscription_sync_hint),
                                        fontSize = labelSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = tightStyle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showQr && qrUrl != null) {
            PurchaseQrOverlay(
                qrUrl = qrUrl,
                onDismiss = { showQr = false },
                dialogWidth = qrDialogWidth,
                qrSize = qrDialogSize,
                cardCorner = qrDialogCorner,
                qrCorner = planCardCorner,
                cardPad = qrDialogPad,
                cardGap = cardGap,
                smallGap = smallGap,
                bodySize = bodySize,
                labelSize = labelSize,
                buttonHeight = buttonHeight,
                buttonPadH = buttonPadH,
                buttonPadV = buttonPadV,
                borderWidth = borderWidth,
                buttonCorner = planCardCorner,
                tightStyle = tightStyle,
            )
        }
    }
}

@Composable
private fun PurchaseQrOverlay(
    qrUrl: String,
    onDismiss: () -> Unit,
    dialogWidth: Dp,
    qrSize: Dp,
    cardCorner: Dp,
    qrCorner: Dp,
    cardPad: Dp,
    cardGap: Dp,
    smallGap: Dp,
    bodySize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    buttonHeight: Dp,
    buttonPadH: Dp,
    buttonPadV: Dp,
    borderWidth: Dp,
    buttonCorner: Dp,
    tightStyle: TextStyle,
) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.width(dialogWidth),
            shape = RoundedCornerShape(cardCorner),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(cardPad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(qrSize)
                        .clip(RoundedCornerShape(qrCorner))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCode(
                        data = qrUrl,
                        modifier = Modifier
                            .padding(cardPad * 0.55f)
                            .fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(cardGap))
                Text(
                    text = stringResource(R.string.subscription_qr_hint),
                    fontSize = labelSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height(smallGap))
                Text(
                    text = stringResource(R.string.subscription_sync_hint),
                    fontSize = labelSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height(cardGap))
                SubscriptionOutlinedActionButton(
                    text = stringResource(R.string.subscription_change_plan),
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(closeFocusRequester),
                    minHeight = buttonHeight,
                    corner = buttonCorner,
                    padding = PaddingValues(horizontal = buttonPadH, vertical = buttonPadV),
                    borderWidth = borderWidth,
                    textSize = bodySize,
                    textWeight = FontWeight.Medium,
                    tightStyle = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp,
    corner: Dp,
    padding: PaddingValues,
    borderWidth: Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    textWeight: FontWeight,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = minHeight)
                .then(
                    if (isFocused) Modifier.border(borderWidth, Color.White, shape)
                    else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(
                text = text,
                fontSize = textSize,
                fontWeight = textWeight,
                style = tightStyle,
            )
        }
    }
}

@Composable
private fun SubscriptionOutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp,
    corner: Dp,
    padding: PaddingValues,
    borderWidth: Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    textWeight: FontWeight,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = minHeight)
                .then(
                    if (isFocused) Modifier.border(borderWidth, Color.White, shape)
                    else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            shape = shape,
            contentPadding = padding,
        ) {
            Text(
                text = text,
                fontSize = textSize,
                fontWeight = textWeight,
                style = tightStyle,
            )
        }
    }
}

@Composable
private fun CurrentPlanCard(
    modifier: Modifier = Modifier,
    currentPlan: CurrentPlanUi,
    limits: CurrentPlanLimits?,
    showLimits: Boolean,
    showLimitsLoading: Boolean,
    cardCorner: Dp,
    cardPad: Dp,
    iconSize: Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    statValueSize: androidx.compose.ui.unit.TextUnit,
    smallGap: Dp,
    tightStyle: TextStyle,
) {
    val trafficGb = limits?.trafficLimitBytes
        ?.takeIf { it > 0 }
        ?.let { (it / (1024L * 1024L * 1024L)).toInt() }
    val deviceLimit = limits?.deviceLimit?.takeIf { it > 0 }
    val hasLimits = showLimits && (trafficGb != null || deviceLimit != null)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.current_plan),
                    fontSize = labelSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height(smallGap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(smallGap),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = currentPlan.accentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    Text(
                        text = currentPlan.title,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = currentPlan.accentColor,
                        style = tightStyle,
                    )
                }
                if (currentPlan.subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(smallGap))
                    Text(
                        text = currentPlan.subtitle,
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            }

            if (hasLimits) {
                Spacer(modifier = Modifier.width(cardPad))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(cardPad),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trafficGb != null) {
                        LimitStat(
                            value = "$trafficGb ${stringResource(R.string.unit_gb)}",
                            label = stringResource(R.string.per_month_short),
                            valueSize = statValueSize,
                            labelSize = labelSize,
                            tightStyle = tightStyle,
                        )
                    }
                    if (trafficGb != null && deviceLimit != null) {
                        Text(
                            text = "·",
                            fontSize = statValueSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = tightStyle,
                        )
                    }
                    if (deviceLimit != null) {
                        LimitStat(
                            value = deviceLimit.toString(),
                            label = stringResource(R.string.devices_label),
                            valueSize = statValueSize,
                            labelSize = labelSize,
                            tightStyle = tightStyle,
                        )
                    }
                }
            } else if (showLimitsLoading) {
                Spacer(modifier = Modifier.width(cardPad))
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun LoadingCardBody(
    modifier: Modifier = Modifier,
    text: String,
    bodySize: androidx.compose.ui.unit.TextUnit,
    gap: Dp,
    tightStyle: TextStyle,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(gap))
        Text(
            text = text,
            fontSize = bodySize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

@Composable
private fun EmptyCardBody(
    modifier: Modifier = Modifier,
    text: String,
    bodySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = bodySize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

@Composable
private fun LimitStat(
    value: String,
    label: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun PlanOptionCard(
    plan: PurchasePlan,
    selected: Boolean,
    onClick: () -> Unit,
    corner: Dp,
    cardPad: Dp,
    borderWidth: Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    val borderColor = when {
        isFocused -> Color.White
        selected -> VpnGreen
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused || selected) Modifier.border(borderWidth, borderColor, shape)
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
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                VpnGreen.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = plan.description,
                    fontSize = labelSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
            }
            Spacer(modifier = Modifier.width(cardPad))
            Text(
                text = plan.priceDisplay,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = VpnGreen,
                style = tightStyle,
            )
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
        bitmap == null -> Text("...", color = Color.Black)
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

@Composable
private fun buildPlanDescription(trafficLimitGb: Int?, deviceLimit: Int?): String {
    val trafficPart = when {
        trafficLimitGb == null -> stringResource(R.string.plan_conditions_loading)
        trafficLimitGb <= 0 -> stringResource(R.string.plan_unlimited_traffic)
        else -> stringResource(R.string.plan_traffic_month_fmt, trafficLimitGb)
    }
    val devicePart = deviceLimit
        ?.takeIf { it > 0 }
        ?.let { stringResource(R.string.plan_devices_fmt, it) }
    return if (devicePart != null) "$trafficPart · $devicePart" else trafficPart
}

private fun formatDurationPrice(
    duration: com.tobevpn.tv.data.remote.dto.PurchaseDurationDto,
    isRussian: Boolean,
    rubToUsdRate: Double?,
): String {
    val prices = duration.prices.associateBy { it.currency }

    fun formatRub(amount: String): String {
        val value = amount.toDoubleOrNull() ?: return "$amount\u20BD"
        val intPart = value.toInt()
        val formatted = if (intPart >= 1000) "%,d".format(intPart).replace(',', ' ') else intPart.toString()
        return "$formatted\u20BD"
    }

    fun formatUsd(amount: String): String {
        val value = amount.toDoubleOrNull() ?: return "$$amount"
        return "$%.2f".format(Locale.US, value)
    }

    fun formatStars(amount: String): String {
        val value = amount.toDoubleOrNull() ?: return "$amount \u2B50"
        return "${value.toInt()} \u2B50"
    }

    return when {
        isRussian -> prices["RUB"]?.amount?.let { formatRub(it) }
            ?: prices["USD"]?.amount?.let { formatUsd(it) }
            ?: prices["XTR"]?.amount?.let { formatStars(it) }
            ?: "—"
        else -> prices["USD"]?.amount?.let { formatUsd(it) }
            ?: prices["RUB"]?.amount?.let { rub ->
                val rubValue = rub.toDoubleOrNull()
                if (rubValue != null && rubToUsdRate != null) {
                    "$%.2f".format(Locale.US, rubValue * rubToUsdRate)
                } else {
                    formatRub(rub)
                }
            }
            ?: prices["XTR"]?.amount?.let { formatStars(it) }
            ?: "—"
    }
}

private fun planKey(days: Int): String = when (days) {
    1 -> "day"
    7 -> "week"
    30 -> "month"
    90 -> "3month"
    365 -> "year"
    else -> "d$days"
}

@Composable
private fun planTitle(days: Int): String = when (days) {
    1 -> stringResource(R.string.plan_day)
    7 -> stringResource(R.string.plan_week)
    30 -> stringResource(R.string.plan_month)
    90 -> stringResource(R.string.plan_3month)
    365 -> stringResource(R.string.plan_year)
    else -> "$days"
}

@Composable
private fun currentPlanUi(authState: AuthState): CurrentPlanUi {
    return when (authState) {
        AuthState.Unauthenticated -> CurrentPlanUi(
            title = stringResource(R.string.plan_free),
            subtitle = stringResource(R.string.sign_in_required_hint),
            accentColor = VpnOrange,
        )
        is AuthState.Authenticated -> {
            when (authState.plan) {
                UserPlan.PAID -> CurrentPlanUi(
                    title = stringResource(R.string.plan_standard),
                    subtitle = authState.planExpiresAt?.let {
                        stringResource(R.string.plan_active_until, formatDate(it))
                    } ?: "",
                    accentColor = VpnGreen,
                )
                UserPlan.ADMIN -> CurrentPlanUi(
                    title = stringResource(R.string.plan_admin),
                    subtitle = stringResource(R.string.plan_unlimited_access),
                    accentColor = VpnGreen,
                )
                UserPlan.EXPIRED -> CurrentPlanUi(
                    title = stringResource(R.string.plan_expired),
                    subtitle = stringResource(R.string.renew_in_bot),
                    accentColor = VpnRed,
                )
                UserPlan.FREE_TRIAL -> CurrentPlanUi(
                    title = stringResource(R.string.plan_free),
                    subtitle = stringResource(R.string.plan_limited_traffic),
                    accentColor = VpnOrange,
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(Date(epochMillis))
}
