package com.tobevpn.tv.presentation.promocodes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.data.remote.dto.PromocodeHistoryItemDto
import com.tobevpn.tv.presentation.components.SpinningRefreshIcon
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnGreen
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PromocodesScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    viewModel: PromocodesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val backFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }

    LaunchedEffect(state.activationResult) {
        if (state.activationResult != null) code = ""
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth, maxHeight)
        val screenPad = (40 * scale).dp
        val gap = (18 * scale).dp
        val cardPad = (20 * scale).dp
        val titleSize = (26 * scale).sp
        val bodySize = (16 * scale).sp
        val labelSize = (14 * scale).sp
        val headerColor = MaterialTheme.colorScheme.onBackground

        Column(Modifier.fillMaxSize().padding(screenPad)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvHeaderIconButton(
                    onClick = onBack,
                    onLongClick = onLongBack,
                    modifier = Modifier
                        .size((44 * scale).dp)
                        .focusRequester(backFocus)
                        .focusProperties { right = refreshFocus; down = codeFocus },
                    shape = RoundedCornerShape((8 * scale).dp),
                    borderWidth = (2 * scale).dp,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = headerColor,
                    )
                }
                Spacer(Modifier.width(gap))
                Text(
                    text = stringResource(R.string.promocodes_title),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    modifier = Modifier.weight(1f),
                )
                TvHeaderIconButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier
                        .size((44 * scale).dp)
                        .focusRequester(refreshFocus)
                        .focusProperties { left = backFocus; down = codeFocus },
                    shape = RoundedCornerShape((8 * scale).dp),
                    borderWidth = (2 * scale).dp,
                ) {
                    SpinningRefreshIcon(
                        spinning = state.isLoading,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.onBackground,
                        size = (20 * scale).dp,
                    )
                }
            }
            Spacer(Modifier.height(gap))

            when {
                !state.isAuthResolved || state.isLoading && state.history == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !state.isAuthenticated -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.promocodes_auth_description),
                            fontSize = bodySize,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape((18 * scale).dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(cardPad)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = PromocodeAccent,
                                    modifier = Modifier.size((28 * scale).dp),
                                )
                                Spacer(Modifier.width((10 * scale).dp))
                                Text(
                                    stringResource(R.string.promocodes_activate_title),
                                    fontSize = (21 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height((8 * scale).dp))
                            Text(
                                stringResource(R.string.promocodes_activate_description),
                                fontSize = labelSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(gap))
                            OutlinedTextField(
                                value = code,
                                onValueChange = {
                                    code = it.replace("\n", "").replace("\r", "").take(64)
                                    viewModel.clearActivationFeedback()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(codeFocus)
                                    .focusProperties { up = backFocus; down = applyFocus },
                                enabled = !state.isActivating,
                                singleLine = true,
                                label = { Text(stringResource(R.string.promocodes_code_label)) },
                                leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (code.isNotBlank()) {
                                            focusManager.clearFocus()
                                            viewModel.activate(code)
                                        }
                                    },
                                ),
                                shape = RoundedCornerShape((12 * scale).dp),
                            )
                            state.activationError?.let {
                                Spacer(Modifier.height((6 * scale).dp))
                                Text(
                                    activationErrorText(it),
                                    fontSize = labelSize,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height((12 * scale).dp))
                            Button(
                                onClick = { viewModel.activate(code) },
                                enabled = code.isNotBlank() && !state.isActivating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(applyFocus)
                                    .focusProperties { up = codeFocus },
                                shape = RoundedCornerShape((12 * scale).dp),
                            ) {
                                if (state.isActivating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size((18 * scale).dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width((8 * scale).dp))
                                }
                                Text(
                                    if (state.isActivating) {
                                        stringResource(R.string.promocodes_activating)
                                    } else {
                                        stringResource(R.string.promocodes_activate_button)
                                    },
                                    fontSize = bodySize,
                                )
                            }
                            if (state.effectiveDiscountPercent > 0) {
                                Spacer(Modifier.height(gap))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = VpnGreen.copy(alpha = 0.14f),
                                    ),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding((14 * scale).dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Percent, null, tint = VpnGreen)
                                        Spacer(Modifier.width((9 * scale).dp))
                                        Text(
                                            stringResource(R.string.promocodes_current_discount_title),
                                            fontSize = bodySize,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "−${state.effectiveDiscountPercent}%",
                                            fontSize = (20 * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VpnGreen,
                                        )
                                    }
                                }
                            }
                            state.activationResult?.let {
                                Spacer(Modifier.height((10 * scale).dp))
                                Text(
                                    stringResource(R.string.promocodes_success_description),
                                    fontSize = labelSize,
                                    color = VpnGreen,
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape((18 * scale).dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.fillMaxSize().padding(cardPad)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null)
                                Spacer(Modifier.width((9 * scale).dp))
                                Text(
                                    stringResource(R.string.promocodes_history_title),
                                    fontSize = (21 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${state.history?.total ?: 0}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height((10 * scale).dp))
                            if (state.history?.promocodes.orEmpty().isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.promocodes_empty_description),
                                        fontSize = bodySize,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
                                ) {
                                    items(
                                        state.history?.promocodes.orEmpty(),
                                        key = { it.activationId ?: "${it.code}:${it.activatedAt}" },
                                    ) { item ->
                                        PromocodeHistoryCard(item, scale, bodySize, labelSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromocodeHistoryCard(
    item: PromocodeHistoryItemDto,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        border = if (focused) BorderStroke((2 * scale).dp, MaterialTheme.colorScheme.onSurface) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding((14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocalOffer, null, tint = PromocodeAccent)
            Spacer(Modifier.width((10 * scale).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.code?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.promocodes_unknown_code),
                    fontSize = bodySize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rewardText(item),
                    fontSize = labelSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                formatDate(item.activatedAt)?.let {
                    Text(
                        it,
                        fontSize = labelSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun rewardText(item: PromocodeHistoryItemDto): String {
    val reward = item.reward ?: 0
    return when (item.rewardType.orEmpty().uppercase(Locale.ROOT)) {
        "PERSONAL_DISCOUNT" -> stringResource(R.string.promocodes_reward_personal_discount, reward)
        "PURCHASE_DISCOUNT" -> stringResource(R.string.promocodes_reward_purchase_discount, reward)
        "TRAFFIC" -> stringResource(R.string.promocodes_reward_traffic, reward)
        "DEVICES" -> stringResource(R.string.promocodes_reward_devices, reward)
        "DURATION" -> stringResource(R.string.promocodes_reward_duration_days, reward)
        else -> stringResource(R.string.promocodes_reward_applied)
    }
}

@Composable
private fun activationErrorText(error: PromocodeActivationError): String = stringResource(
    when (error) {
        PromocodeActivationError.NETWORK -> R.string.promocodes_activation_error_network
        PromocodeActivationError.NOT_FOUND -> R.string.promocodes_activation_error_not_found
        PromocodeActivationError.EXPIRED -> R.string.promocodes_activation_error_expired
        PromocodeActivationError.ALREADY_ACTIVATED ->
            R.string.promocodes_activation_error_already_activated
        PromocodeActivationError.AUTH_REQUIRED -> R.string.promocodes_activation_error_auth
        PromocodeActivationError.TOO_MANY_REQUESTS -> R.string.promocodes_activation_error_too_many
        else -> R.string.promocodes_activation_error_not_available
    },
)

private fun formatDate(raw: String?): String? = raw?.let {
    runCatching {
        OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }.getOrNull()
}

// Keep the promo screen aligned with the final phone palette. This is the
// action/highlight accent; it deliberately remains identical in both themes.
private val PromocodeAccent = Color(0xFFE09A2D)
