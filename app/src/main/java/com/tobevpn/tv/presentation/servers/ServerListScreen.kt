package com.tobevpn.tv.presentation.servers

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.tobevpn.tv.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.presentation.countryFlagForUi
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.serverCountryNameForUi
import com.tobevpn.tv.presentation.serverDisplayName
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed
import kotlinx.coroutines.launch

@Composable
fun ServerListScreen(
    onBack: () -> Unit,
    viewModel: ServerListViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServerId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val automaticServerSelection by viewModel.automaticServerSelection.collectAsStateWithLifecycle()
    val isAdminProfile by viewModel.isAdminProfile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val cardPad = (20 * scale).dp
        val cardCorner = (16 * scale).dp
        val gap = (16 * scale).dp
        val itemPadV = (4 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (16 * scale).sp
        val bodySize = (14 * scale).sp
        val labelSize = (12 * scale).sp
        val flagSize = (36 * scale).sp
        val borderWidth = (2 * scale).dp
        val backCorner = (8 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TvHeaderIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(headerButtonSize),
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
                    stringResource(R.string.server_select),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    modifier = Modifier.weight(1f),
                    style = tightStyle,
                )
                TvHeaderIconButton(
                    onClick = { viewModel.refreshServers() },
                    modifier = Modifier.size(headerButtonSize),
                    shape = RoundedCornerShape(backCorner),
                    borderWidth = borderWidth,
                ) {
                    com.tobevpn.tv.presentation.components.SpinningRefreshIcon(
                        spinning = isLoading,
                        contentDescription = stringResource(R.string.refresh),
                        tint = headerColor,
                        size = headerIconSize,
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = gap))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && servers.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = com.tobevpn.tv.presentation.theme.VpnGreen,
                        )
                    }
                    error != null && servers.isEmpty() -> {
                        Text(
                            text = error ?: stringResource(R.string.servers_load_error),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = bodySize,
                            style = tightStyle,
                        )
                    }
                    servers.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.servers_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = bodySize,
                            style = tightStyle,
                        )
                    }
                    else -> {
                        LazyColumn {
                            item(key = "automatic") {
                                AutomaticServerItem(
                                    selected = automaticServerSelection,
                                    enabled = servers.any { it.isSelectable },
                                    onClick = {
                                        scope.launch {
                                            if (viewModel.selectAutomaticServer()) {
                                                onBack()
                                            }
                                        }
                                    },
                                    scale = scale,
                                    cardPad = cardPad,
                                    cardCorner = cardCorner,
                                    itemPadV = itemPadV,
                                    gap = gap,
                                    titleSize = titleSize,
                                    bodySize = bodySize,
                                    borderWidth = borderWidth,
                                    tightStyle = tightStyle,
                                )
                            }
                            items(servers, key = { serverListItemKey(it) }) { server ->
                                val selectable = server.isSelectable
                                ServerItem(
                                    server = server,
                                    selected = !automaticServerSelection && selectable && server.id == selectedServerId,
                                    enabled = selectable,
                                    onClick = {
                                        scope.launch {
                                            if (viewModel.selectServer(server)) {
                                                onBack()
                                            }
                                        }
                                    },
                                    scale = scale,
                                    cardPad = cardPad,
                                    cardCorner = cardCorner,
                                    itemPadV = itemPadV,
                                    gap = gap,
                                    flagSize = flagSize,
                                    titleSize = titleSize,
                                    bodySize = bodySize,
                                    labelSize = labelSize,
                                    borderWidth = borderWidth,
                                    tightStyle = tightStyle,
                                    showEndpoint = isAdminProfile,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomaticServerItem(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    scale: Float,
    cardPad: androidx.compose.ui.unit.Dp,
    cardCorner: androidx.compose.ui.unit.Dp,
    itemPadV: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    borderWidth: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = itemPadV)
            .then(
                when {
                    isFocused -> Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                    selected -> Modifier.border(borderWidth, VpnGreen, RoundedCornerShape(cardCorner))
                    else -> Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .onKeyEvent { event ->
                if (enabled &&
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFocused -> MaterialTheme.colorScheme.primaryContainer
                selected -> VpnGreen.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = VpnGreen,
                modifier = Modifier.size((36 * scale).dp),
            )
            Spacer(modifier = Modifier.width(gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.server_auto),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    style = tightStyle,
                )
                Text(
                    text = stringResource(R.string.server_auto_description),
                    fontSize = bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: Server,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    scale: Float,
    cardPad: androidx.compose.ui.unit.Dp,
    cardCorner: androidx.compose.ui.unit.Dp,
    itemPadV: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    flagSize: androidx.compose.ui.unit.TextUnit,
    titleSize: androidx.compose.ui.unit.TextUnit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    borderWidth: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
    showEndpoint: Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = itemPadV)
            .then(
                when {
                    isFocused -> Modifier.border(borderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(cardCorner))
                    selected -> Modifier.border(borderWidth, VpnGreen, RoundedCornerShape(cardCorner))
                    else -> Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .onKeyEvent { event ->
                if (enabled &&
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFocused -> MaterialTheme.colorScheme.primaryContainer
                selected -> VpnGreen.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayCountryName = serverCountryNameForUi(server.country, server.name)
            Text(
                text = countryFlagForUi(server.country, server.name),
                fontSize = flagSize,
            )
            Spacer(modifier = Modifier.width(gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    serverDisplayName(server.name, server.country),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    style = tightStyle,
                )
                if (displayCountryName.isNotEmpty()) {
                    Text(
                        buildString {
                            append(displayCountryName)
                            if (showEndpoint) {
                                append(" · ")
                                append(server.address)
                                append(":")
                                append(server.port)
                            }
                        },
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                } else if (showEndpoint) {
                    Text(
                        "${server.address}:${server.port}",
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            }
            // Ping, unreachable or unavailable placeholder — same precedence as the phone client.
            if (!server.isSelectable) {
                Text(
                    text = stringResource(R.string.server_offline),
                    fontSize = labelSize,
                    color = VpnRed,
                    style = tightStyle,
                )
            } else if (server.ping < 0) {
                Text(
                    text = stringResource(R.string.server_unavailable),
                    fontSize = labelSize,
                    color = VpnRed,
                    style = tightStyle,
                )
            } else if (server.ping > 0) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
            }
        }
    }
}

private fun pingColor(ping: Long) = when {
    ping < 100 -> VpnGreen
    ping < 200 -> VpnOrange
    else -> VpnRed
}

private fun serverListItemKey(server: Server): String = buildString {
    append(server.id)
    append('|')
    append(server.name)
    append('|')
    append(server.country)
    append('|')
    append(server.address)
    append(':')
    append(server.port)
    append('|')
    append(server.uuid)
    append('|')
    append(server.sni)
    append('|')
    append(server.publicKey)
    append('|')
    append(server.shortId)
}
