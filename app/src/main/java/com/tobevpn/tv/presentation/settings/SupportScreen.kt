package com.tobevpn.tv.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tobevpn.tv.R
import com.tobevpn.tv.presentation.components.QrCode
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.rememberTvScreenScale
import kotlinx.coroutines.launch

private data class TvFaq(val question: Int, val answer: Int)

private val tvFaqs = listOf(
    TvFaq(R.string.faq_q_connect, R.string.faq_a_connect),
    TvFaq(R.string.faq_q_connecting_check, R.string.faq_a_connecting_check),
    TvFaq(R.string.faq_q_slow, R.string.faq_a_slow),
    TvFaq(R.string.faq_q_server, R.string.faq_a_server),
    TvFaq(R.string.faq_q_stats, R.string.faq_a_stats),
    TvFaq(R.string.faq_q_pay, R.string.faq_a_pay),
    TvFaq(R.string.faq_q_activate, R.string.faq_a_activate),
    TvFaq(R.string.faq_q_discount, R.string.faq_a_discount),
    TvFaq(R.string.faq_q_devices, R.string.faq_a_devices),
    TvFaq(R.string.faq_q_app_filter, R.string.faq_a_app_filter),
    TvFaq(R.string.faq_q_referrals, R.string.faq_a_referrals),
    TvFaq(R.string.faq_q_updates, R.string.faq_a_updates),
    TvFaq(R.string.faq_q_diagnostics, R.string.faq_a_diagnostics),
    TvFaq(R.string.faq_q_privacy, R.string.faq_a_privacy),
    TvFaq(R.string.faq_q_support_details, R.string.faq_a_support_details),
)

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
) {
    val backFocus = remember { FocusRequester() }
    val firstFaqFocus = remember { FocusRequester() }
    var showSupportQr by remember { mutableStateOf(false) }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val topAlpha by animateFloatAsState(
        if (scrollState.value > 0) 1f else 0f,
        tween(180),
        label = "faq-top",
    )
    val bottomAlpha by animateFloatAsState(
        if (scrollState.value < scrollState.maxValue) 1f else 0f,
        tween(180),
        label = "faq-bottom",
    )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth, maxHeight)
        val screenPad = (40 * scale).dp
        val gap = (18 * scale).dp
        val bodySize = (15 * scale).sp
        val headerColor = MaterialTheme.colorScheme.onBackground

        Column(Modifier.fillMaxSize().padding(screenPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvHeaderIconButton(
                    onClick = onBack,
                    onLongClick = onLongBack,
                    modifier = Modifier.size((44 * scale).dp).focusRequester(backFocus),
                    shape = RoundedCornerShape((8 * scale).dp),
                    borderWidth = (2 * scale).dp,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back),
                        tint = headerColor,
                    )
                }
                Spacer(Modifier.width(gap))
                Text(
                    stringResource(R.string.settings_support),
                    fontSize = (26 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                )
            }
            Spacer(Modifier.height((10 * scale).dp))
            Text(
                stringResource(R.string.support_faq_intro),
                fontSize = bodySize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height((12 * scale).dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = (4 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((9 * scale).dp),
                ) {
                    tvFaqs.forEachIndexed { index, faq ->
                        TvFaqCard(
                            faq = faq,
                            expanded = expandedIndex == index,
                            onToggle = {
                                expandedIndex = if (expandedIndex == index) null else index
                            },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstFaqFocus)
                            } else Modifier,
                            fontSize = bodySize,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = topAlpha },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = bottomAlpha },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height((10 * scale).dp))
            TvSupportButton(
                label = stringResource(R.string.support_contact_button),
                onClick = { showSupportQr = true },
                fontSize = bodySize,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSupportQr) {
        SupportQrDialog(onDismiss = { showSupportQr = false })
    }
}

@Composable
private fun TvFaqCard(
    faq: TvFaq,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    var focused by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(220),
        label = "faq-chevron",
    )
    Card(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .animateContentSize(tween(280)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(faq.question),
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(28.dp).background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        modifier = Modifier.size(20.dp).rotate(rotation),
                    )
                }
            }
            AnimatedVisibility(expanded) {
                Text(
                    stringResource(faq.answer),
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.38f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TvSupportButton(
    label: String,
    onClick: () -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.height(48.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SupportQrDialog(onDismiss: () -> Unit) {
    val link = stringResource(R.string.block_appeal_link)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.support_contact_button),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(300.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp),
                ) {
                    QrCode(data = link, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.block_appeal_scan_hint),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
