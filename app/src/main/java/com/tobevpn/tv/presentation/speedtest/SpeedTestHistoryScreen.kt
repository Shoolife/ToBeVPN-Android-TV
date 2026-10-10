package com.tobevpn.tv.presentation.speedtest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.components.VerticalScrollCues
import com.tobevpn.tv.presentation.components.rememberVerticalScrollCueState
import com.tobevpn.tv.presentation.components.verticalFadingEdges
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnBlue
import com.tobevpn.tv.presentation.theme.VpnGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpeedTestHistoryScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val deleteScope = rememberCoroutineScope()
    var deletingEntryId by remember { mutableStateOf<Long?>(null) }

    androidx.compose.runtime.LaunchedEffect(history, deletingEntryId) {
        val deletingId = deletingEntryId ?: return@LaunchedEffect
        if (history.none { it.timestampMillis == deletingId }) {
            deletingEntryId = null
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth, maxHeight)
        val screenPad = (40 * scale).dp
        val gap = (16 * scale).dp
        val titleSize = (26 * scale).sp
        val bodySize = (15 * scale).sp
        val valueSize = (18 * scale).sp
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
                    modifier = Modifier.size((44 * scale).dp),
                    shape = RoundedCornerShape((8 * scale).dp),
                    borderWidth = (2 * scale).dp,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size((20 * scale).dp),
                    )
                }
                Spacer(modifier = Modifier.width(gap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.speed_history_title),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height((5 * scale).dp))
                    Text(
                        text = stringResource(R.string.speed_history_local_hint),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
                Text(
                    text = history.size.toString(),
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    color = VpnBlue,
                    style = tightStyle,
                )
            }
            Spacer(modifier = Modifier.height(gap))
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.speed_history_empty),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val cues = rememberVerticalScrollCueState(
                    state = listState,
                    fadeLength = (38 * scale).dp,
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalFadingEdges(
                                topAlpha = cues.topAlpha,
                                bottomAlpha = cues.bottomAlpha,
                                fadeHeight = (38 * scale).dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
                    ) {
                        items(history, key = SpeedTestHistoryEntry::timestampMillis) { entry ->
                            AnimatedSpeedHistoryCard(
                                visible = deletingEntryId != entry.timestampMillis,
                                modifier = Modifier.animateItem(),
                                entry = entry,
                                onDelete = {
                                    if (deletingEntryId == null) {
                                        deletingEntryId = entry.timestampMillis
                                        deleteScope.launch {
                                            delay(HISTORY_DELETE_ANIMATION_MS.toLong())
                                            viewModel.deleteHistoryEntry(entry.timestampMillis)
                                        }
                                    }
                                },
                                scale = scale,
                                bodySize = bodySize,
                                valueSize = valueSize,
                                tightStyle = tightStyle,
                            )
                        }
                    }
                    VerticalScrollCues(state = cues, scale = scale)
                }
            }
        }
    }
}

@Composable
private fun AnimatedSpeedHistoryCard(
    visible: Boolean,
    entry: SpeedTestHistoryEntry,
    onDelete: () -> Unit,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        exit = slideOutHorizontally(
            animationSpec = tween(
                durationMillis = HISTORY_DELETE_ANIMATION_MS,
                easing = FastOutSlowInEasing,
            ),
            targetOffsetX = { fullWidth -> -fullWidth },
        ) + fadeOut(
            animationSpec = tween(durationMillis = HISTORY_DELETE_FADE_MS),
        ) + shrinkVertically(
            animationSpec = tween(
                durationMillis = HISTORY_DELETE_ANIMATION_MS,
                easing = FastOutSlowInEasing,
            ),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        SpeedHistoryCard(
            entry = entry,
            onDelete = onDelete,
            scale = scale,
            bodySize = bodySize,
            valueSize = valueSize,
            tightStyle = tightStyle,
        )
    }
}

@Composable
private fun SpeedHistoryCard(
    entry: SpeedTestHistoryEntry,
    onDelete: () -> Unit,
    scale: Float,
    bodySize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var focused by remember { mutableStateOf(false) }
    val cardFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape((16 * scale).dp)
    val timestamp = remember(entry.timestampMillis) {
        HISTORY_DATE_FORMAT.format(
            Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()),
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(cardFocusRequester)
            .focusProperties { right = deleteFocusRequester }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                when {
                    focused && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        deleteFocusRequester.requestFocus()
                        true
                    }
                    focused && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> true
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Delete) {
                    onDelete()
                    true
                } else false
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = if (focused) BorderStroke((2 * scale).dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (20 * scale).dp, vertical = (15 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timestamp,
                    fontSize = bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height((7 * scale).dp))
                Row(horizontalArrangement = Arrangement.spacedBy((28 * scale).dp)) {
                    Text(
                        text = stringResource(R.string.speed_history_download_value, entry.downloadMbps),
                        fontSize = valueSize,
                        fontWeight = FontWeight.Bold,
                        color = VpnGreen,
                        style = tightStyle,
                    )
                    Text(
                        text = stringResource(R.string.speed_history_ping_value, entry.pingMs),
                        fontSize = valueSize,
                        fontWeight = FontWeight.SemiBold,
                        style = tightStyle,
                    )
                    Text(
                        text = stringResource(
                            if (entry.viaVpn) R.string.speed_via_vpn else R.string.speed_direct,
                        ),
                        fontSize = bodySize,
                        color = VpnBlue,
                        style = tightStyle,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .focusRequester(deleteFocusRequester)
                    .focusProperties { left = cardFocusRequester },
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.speed_history_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size((24 * scale).dp),
                )
            }
        }
    }
}

private val HISTORY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd.MM.yyyy  HH:mm",
    Locale.getDefault(),
)

private const val HISTORY_DELETE_ANIMATION_MS = 320
private const val HISTORY_DELETE_FADE_MS = 250
