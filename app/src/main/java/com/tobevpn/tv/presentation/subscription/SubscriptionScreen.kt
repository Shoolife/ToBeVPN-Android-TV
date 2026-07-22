package com.tobevpn.tv.presentation.subscription

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.tobevpn.tv.data.remote.dto.PurchasePlanDto
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.UserPlan
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
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

private data class PurchaseTariff(
    val key: String,
    val title: String,
    val periods: List<PurchasePlan>,
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

    var selectedTariffKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlanKey by rememberSaveable { mutableStateOf("month") }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var lastFocusedTariffIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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

        val sourcePlans = purchasePlans?.plans
            ?.filter { it.durations.orEmpty().any { d -> d.days > 0 } }
            ?.sortedWith(compareBy<PurchasePlanDto> { it.orderIndex }.thenBy { it.name })
            ?: emptyList()

        val shouldLoadLimits = (authState as? AuthState.Authenticated)?.let {
            it.plan == UserPlan.PAID || it.plan == UserPlan.ADMIN
        } == true

        val displayLimits = currentLimits

        val tariffs: List<PurchaseTariff> = sourcePlans.map { sourcePlan ->
            PurchaseTariff(
                key = sourcePlan.id.toString(),
                title = sourcePlan.name,
                periods = buildList {
                val planDescription = buildPlanDescription(
                    trafficLimitGb = sourcePlan.trafficLimit.toInt(),
                    deviceLimit = sourcePlan.deviceLimit.takeIf { it > 0 },
                )
                sourcePlan.durations.orEmpty()
                    .filter { it.days > 0 }
                    .sortedBy { it.orderIndex }
                    .forEach { d ->
                        val durationTitle = planTitle(d.days)
                        add(PurchasePlan(
                            key = "${sourcePlan.id}:${planKey(d.days)}",
                            title = durationTitle,
                            priceDisplay = formatDurationPrice(d, isRussian, rubToUsdRate),
                            description = planDescription,
                            botPaymentUrl = d.botPaymentUrl,
                        ))
                    }
                },
            )
        }
        val selectedTariff = tariffs.firstOrNull { it.key == selectedTariffKey }
            ?: tariffs.firstOrNull()
        val tariffTabFocusRequesters = remember(tariffs.map { it.key }) {
            List(tariffs.size) { FocusRequester() }
        }
        val selectedTariffIndex = tariffs
            .indexOfFirst { it.key == selectedTariff?.key }
            .takeIf { it >= 0 }
            ?: 0
        val rememberedTariffFocusIndex = lastFocusedTariffIndex
            ?.takeIf { it in tariffs.indices }
            ?: selectedTariffIndex
        val rememberedTariffFocusRequester = tariffTabFocusRequesters
            .getOrNull(rememberedTariffFocusIndex)
        val selectedPlan = selectedTariff?.periods?.firstOrNull { it.key == selectedPlanKey }
            ?: selectedTariff?.periods?.firstOrNull { it.key.endsWith(":month") || it.key == "month" }
            ?: selectedTariff?.periods?.firstOrNull()
        val currentPlan = currentPlanUi(authState)
        val currentAuth = authState as? AuthState.Authenticated
        val isPaidAccount = currentAuth?.plan?.let { it != UserPlan.FREE_TRIAL } == true
        val selectedTariffIsCurrent = sameTariffName(
            current = currentAuth?.planDisplayName,
            selected = selectedTariff?.title,
        )
        val isRenewal = isPaidAccount && selectedTariffIsCurrent
        val qrUrl = selectedPlan?.botPaymentUrl
            ?: displayLimits?.renewalUrl?.takeIf { isRenewal }
        val selectedActionTitle = selectedTariff
            ?.takeIf { it.title.isNotBlank() }
            ?.let { "${it.title} · ${selectedPlan?.title.orEmpty()}" }
            ?: selectedPlan?.title.orEmpty()
        val primaryButtonLabel = when {
            isRenewal && selectedPlan != null -> stringResource(R.string.renew_plan, selectedActionTitle, selectedPlan.priceDisplay)
            isPaidAccount && selectedTariff != null && selectedPlan != null -> stringResource(R.string.change_plan, selectedActionTitle, selectedPlan.priceDisplay)
            selectedPlan != null -> stringResource(R.string.buy_plan, selectedActionTitle, selectedPlan.priceDisplay)
            else -> stringResource(R.string.subscription_buy_in_telegram)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvHeaderIconButton(
                    onClick = {
                        if (showQr) showQr = false else onBack()
                    },
                    modifier = Modifier
                        .size(headerButtonSize)
                        .then(
                            if (rememberedTariffFocusRequester != null) {
                                Modifier.focusProperties { down = rememberedTariffFocusRequester }
                            } else {
                                Modifier
                            }
                        ),
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
                            tariffs.isEmpty() -> {
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(cardPad),
                                ) {
                                    TariffTabs(
                                        tariffs = tariffs,
                                        selectedTariffKey = selectedTariff?.key,
                                        tabFocusRequesters = tariffTabFocusRequesters,
                                        focusedTabIndex = lastFocusedTariffIndex,
                                        onFocusedTabIndexChange = { lastFocusedTariffIndex = it },
                                        onSelect = { tariff ->
                                            selectedTariffKey = tariff.key
                                            selectedPlanKey = tariff.periods
                                                .firstOrNull { it.key.endsWith(":month") || it.key == "month" }
                                                ?.key
                                                ?: tariff.periods.firstOrNull()?.key
                                                ?: selectedPlanKey
                                            showQr = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        tabCorner = planCardCorner,
                                        tabTextPaddingH = planCardPad * 0.8f,
                                        tabTextPaddingV = planCardPad * 0.65f,
                                        minTabWidth = planCardPad * 5.5f,
                                        textSize = bodySize,
                                        tightStyle = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height(planListGap))
                                    AnimatedContent(
                                        targetState = selectedTariff?.key.orEmpty(),
                                        transitionSpec = {
                                            fadeIn(
                                                animationSpec = tween(
                                                    durationMillis = 260,
                                                    delayMillis = 70,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            ) togetherWith fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = 160,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        },
                                        label = "TvSubscriptionTariffPeriods",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .animateContentSize(
                                                animationSpec = tween(
                                                    durationMillis = 450,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            ),
                                    ) { tariffKey ->
                                        val periods = tariffs.firstOrNull { it.key == tariffKey }?.periods.orEmpty()
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(planListGap),
                                            contentPadding = PaddingValues(bottom = planListGap),
                                        ) {
                                            items(periods, key = { it.key }) { plan ->
                                                PlanOptionCard(
                                                    plan = plan,
                                                    selected = selectedPlan?.key == plan.key,
                                                    upFocusRequester = rememberedTariffFocusRequester,
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
                                        onClick = {
                                            if (qrUrl != null) {
                                                showQr = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        leftFocusRequester = rememberedTariffFocusRequester,
                                        upFocusRequester = rememberedTariffFocusRequester,
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
private fun TariffTabs(
    tariffs: List<PurchaseTariff>,
    selectedTariffKey: String?,
    tabFocusRequesters: List<FocusRequester>,
    focusedTabIndex: Int?,
    onFocusedTabIndexChange: (Int) -> Unit,
    onSelect: (PurchaseTariff) -> Unit,
    modifier: Modifier = Modifier,
    tabCorner: Dp,
    tabTextPaddingH: Dp,
    tabTextPaddingV: Dp,
    minTabWidth: Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    if (tariffs.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val tabCount = tariffs.size
        val horizontalPaddingPx = with(density) { (tabTextPaddingH * 2).roundToPx() }
        val minTabWidthPx = with(density) { minTabWidth.roundToPx() }
        val safetyPx = with(density) { 8.dp.roundToPx() }
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val naturalTabWidthsPx = remember(
            tariffs,
            textMeasurer,
            tightStyle,
            textSize,
            horizontalPaddingPx,
            minTabWidthPx,
            safetyPx,
        ) {
            tariffs.map { tariff ->
                maxOf(
                    measureTariffTitleWidthPx(
                        title = tariff.title,
                        textMeasurer = textMeasurer,
                        style = tightStyle,
                        fontSize = textSize,
                    ) + horizontalPaddingPx + safetyPx,
                    minTabWidthPx,
                )
            }
        }
        val naturalWidthPx = naturalTabWidthsPx.sum()
        val spareWidthPx = (maxWidthPx - naturalWidthPx).coerceAtLeast(0)
        val extraPerTabPx = spareWidthPx / tabCount
        val extraRemainderPx = spareWidthPx % tabCount
        val tabWidthsPx = naturalTabWidthsPx.mapIndexed { index, width ->
            width + extraPerTabPx + if (index < extraRemainderPx) 1 else 0
        }
        val tabWidths = tabWidthsPx.map { widthPx ->
            with(density) { widthPx.toDp() }
        }
        val tabStripWidthPx = tabWidthsPx.sum()
        val tabStripWidth = with(density) { tabStripWidthPx.toDp() }
        val scrollable = tabStripWidthPx > maxWidthPx
        val startFadeAlpha by animateFloatAsState(
            targetValue = if (scrollable && scrollState.value > 0) 1f else 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutSlowInEasing,
            ),
            label = "TvTariffTabsStartFade",
        )
        val endFadeAlpha by animateFloatAsState(
            targetValue = if (scrollable && scrollState.value < scrollState.maxValue) 1f else 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutSlowInEasing,
            ),
            label = "TvTariffTabsEndFade",
        )
        val selectedSafeIndex = tariffs
            .indexOfFirst { it.key == selectedTariffKey }
            .takeIf { it >= 0 }
            ?: 0
        val selectedOffsetPx = tabWidthsPx.take(selectedSafeIndex).sum()
        val indicatorOffset by animateDpAsState(
            targetValue = with(density) { selectedOffsetPx.toDp() },
            animationSpec = tween(
                durationMillis = 360,
                easing = FastOutSlowInEasing,
            ),
            label = "TvTariffTabIndicatorOffset",
        )
        val indicatorWidth by animateDpAsState(
            targetValue = tabWidths.getOrElse(selectedSafeIndex) { minTabWidth },
            animationSpec = tween(
                durationMillis = 360,
                easing = FastOutSlowInEasing,
            ),
            label = "TvTariffTabIndicatorWidth",
        )

        val visibleSafeIndex = focusedTabIndex
            ?.takeIf { it in tariffs.indices }
            ?: selectedSafeIndex

        LaunchedEffect(scrollable, visibleSafeIndex, tabStripWidthPx, maxWidthPx) {
            if (!scrollable) return@LaunchedEffect
            val selectedStart = tabWidthsPx.take(visibleSafeIndex).sum()
            val selectedWidth = tabWidthsPx[visibleSafeIndex]
            val selectedEnd = selectedStart + selectedWidth
            val selectedCenter = selectedStart + selectedWidth / 2
            val visibleStart = scrollState.value
            val visibleEnd = visibleStart + maxWidthPx
            val edgeComfortPx = maxOf(maxWidthPx / 4, selectedWidth / 2)
            val centeredTarget = selectedCenter - maxWidthPx / 2
            val target = when {
                selectedStart < visibleStart -> centeredTarget
                selectedEnd > visibleEnd -> centeredTarget
                selectedCenter < visibleStart + edgeComfortPx -> centeredTarget
                selectedCenter > visibleEnd - edgeComfortPx -> centeredTarget
                else -> visibleStart
            }.coerceIn(0, scrollState.maxValue)
            if (target != visibleStart) {
                scrollState.animateScrollTo(
                    value = target,
                    animationSpec = tween(
                        durationMillis = 520,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFadingEdges(
                        startAlpha = startFadeAlpha,
                        endAlpha = endFadeAlpha,
                        fadeWidth = 38.dp,
                    ),
            ) {
                Box(
                    modifier = Modifier.then(
                        if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier,
                    ),
                ) {
                    Column(modifier = Modifier.width(tabStripWidth)) {
                        Row(modifier = Modifier.width(tabStripWidth)) {
                            tariffs.forEachIndexed { index, tariff ->
                                val selected = selectedSafeIndex == index
                                var tabFocused by remember { mutableStateOf(false) }
                                val tabShape = RoundedCornerShape(tabCorner)
                                val titleColor by animateColorAsState(
                                    targetValue = when {
                                        selected -> MaterialTheme.colorScheme.onSurface
                                        tabFocused -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    label = "TvTariffTabTitleColor",
                                )
                                val tabBackground by animateColorAsState(
                                    targetValue = when {
                                        tabFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    },
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    label = "TvTariffTabFocusBackground",
                                )
                                val focusColor = MaterialTheme.colorScheme.onSurface
                                val borderColor by animateColorAsState(
                                    targetValue = if (tabFocused) {
                                        focusColor
                                    } else {
                                        Color.Transparent
                                    },
                                    animationSpec = tween(
                                        durationMillis = 120,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    label = "TvTariffTabFocusBorder",
                                )
                                val requestNeighbourFocus: (Int) -> Boolean = { nextIndex ->
                                    if (nextIndex in tariffs.indices) {
                                        onFocusedTabIndexChange(nextIndex)
                                        tabFocusRequesters.getOrNull(nextIndex)?.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                CompositionLocalProvider(
                                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                                ) {
                                    OutlinedButton(
                                        onClick = { onSelect(tariff) },
                                        modifier = Modifier
                                            .width(tabWidths[index])
                                            .focusRequester(tabFocusRequesters[index])
                                            .onFocusChanged {
                                                tabFocused = it.isFocused
                                                if (it.isFocused) onFocusedTabIndexChange(index)
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown) {
                                                    when (event.key) {
                                                        Key.DirectionLeft -> requestNeighbourFocus(index - 1)
                                                        Key.DirectionRight -> requestNeighbourFocus(index + 1)
                                                        else -> false
                                                    }
                                                } else {
                                                    false
                                                }
                                            }
                                            .background(tabBackground, tabShape),
                                        shape = tabShape,
                                        border = BorderStroke(2.dp, borderColor),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = titleColor,
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = tabTextPaddingH,
                                            vertical = tabTextPaddingV,
                                        ),
                                    ) {
                                        Text(
                                            text = tariff.title,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible,
                                            fontSize = textSize,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = titleColor,
                                            style = tightStyle,
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(tabStripWidth)
                                .height(3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorOffset)
                                    .width(indicatorWidth)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }

            ScrollEdgeArrow(
                alpha = startFadeAlpha,
                isStart = true,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
            )
            ScrollEdgeArrow(
                alpha = endFadeAlpha,
                isStart = false,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun ScrollEdgeArrow(
    alpha: Float,
    isStart: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (isStart) {
            Icons.AutoMirrored.Filled.KeyboardArrowLeft
        } else {
            Icons.AutoMirrored.Filled.KeyboardArrowRight
        },
        contentDescription = null,
        modifier = modifier
            .size(22.dp)
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Modifier.horizontalFadingEdges(
    startAlpha: Float,
    endAlpha: Float,
    fadeWidth: Dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val fadeWidthPx = fadeWidth.toPx().coerceAtMost(size.width / 2f)
        if (fadeWidthPx <= 0f) return@drawWithContent

        val coercedStartAlpha = startAlpha.coerceIn(0f, 1f)
        if (coercedStartAlpha > 0.001f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 1f - coercedStartAlpha),
                        Color.Black,
                    ),
                    startX = 0f,
                    endX = fadeWidthPx,
                ),
                topLeft = Offset.Zero,
                size = Size(fadeWidthPx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }

        val coercedEndAlpha = endAlpha.coerceIn(0f, 1f)
        if (coercedEndAlpha > 0.001f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 1f - coercedEndAlpha),
                    ),
                    startX = size.width - fadeWidthPx,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fadeWidthPx, 0f),
                size = Size(fadeWidthPx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private fun measureTariffTitleWidthPx(
    title: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    fontSize: androidx.compose.ui.unit.TextUnit,
): Int {
    return textMeasurer.measure(
        text = title,
        style = style.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
        softWrap = false,
    ).size.width
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
    leftFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
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
                    if (leftFocusRequester != null || upFocusRequester != null) {
                        Modifier.focusProperties {
                            if (leftFocusRequester != null) left = leftFocusRequester
                            if (upFocusRequester != null) up = upFocusRequester
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, shape)
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
                    if (isFocused) Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, shape)
                    else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(
                text = text,
                fontSize = textSize,
                fontWeight = textWeight,
                color = MaterialTheme.colorScheme.onBackground,
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
    upFocusRequester: FocusRequester?,
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
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val defaultContainerColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val containerColor = if (selected) {
        VpnGreen.copy(alpha = if (isLightTheme) 0.16f else 0.18f)
    } else {
        defaultContainerColor
    }
    val borderColor = when {
        isFocused -> MaterialTheme.colorScheme.onSurface
        selected -> VpnGreen
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                }
            )
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
            containerColor = containerColor,
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
    val prices = duration.prices.orEmpty().associateBy { it.currency }

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
            ?: "XXX"
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
            ?: "XXX"
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

private fun sameTariffName(current: String?, selected: String?): Boolean {
    val currentName = normalizeTariffName(current)
    val selectedName = normalizeTariffName(selected)
    if (currentName.isBlank() || selectedName.isBlank()) return false
    return currentName == selectedName ||
        currentName.startsWith("$selectedName ") ||
        selectedName.startsWith("$currentName ")
}

private fun normalizeTariffName(value: String?): String {
    return value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()
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
            val serverPlanName = authState.planDisplayName?.takeIf {
                it.isNotBlank() && authState.plan != UserPlan.EXPIRED
            }
            when (authState.plan) {
                UserPlan.PAID -> CurrentPlanUi(
                    title = serverPlanName ?: stringResource(R.string.plan_unknown_name),
                    subtitle = authState.planExpiresAt?.let {
                        stringResource(R.string.plan_active_until, formatDate(it))
                    } ?: "",
                    accentColor = VpnGreen,
                )
                UserPlan.ADMIN -> CurrentPlanUi(
                    title = serverPlanName ?: stringResource(R.string.plan_unknown_name),
                    subtitle = authState.planExpiresAt?.let {
                        stringResource(R.string.plan_active_until, formatDate(it))
                    } ?: "",
                    accentColor = VpnGreen,
                )
                UserPlan.EXPIRED -> CurrentPlanUi(
                    title = stringResource(R.string.plan_expired),
                    subtitle = stringResource(R.string.renew_in_bot),
                    accentColor = VpnRed,
                )
                UserPlan.FREE_TRIAL -> CurrentPlanUi(
                    title = serverPlanName ?: stringResource(R.string.plan_free),
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
