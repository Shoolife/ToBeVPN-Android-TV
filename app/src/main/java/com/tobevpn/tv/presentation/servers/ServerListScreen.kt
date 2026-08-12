package com.tobevpn.tv.presentation.servers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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

private data class ServerListMetrics(
    val cardVerticalPadding: Dp,
    val cardCornerRadius: Dp,
    val rowHorizontalPadding: Dp,
    val compactRowVerticalPadding: Dp,
    val endpointRowTopPadding: Dp,
    val endpointRowBottomPadding: Dp,
    val flagColumnWidth: Dp,
    val flagTextGap: Dp,
    val flagFontSize: TextUnit,
    val statusGap: Dp,
    val pingWidth: Dp,
    val adminPingWidth: Dp,
    val autoIconSize: Dp,
    val autoRowPadding: Dp,
    val pingChipHeight: Dp,
    val portChipHeight: Dp,
)

private fun serverListMetrics(scale: Float): ServerListMetrics = ServerListMetrics(
    cardVerticalPadding = (7 * scale).dp,
    cardCornerRadius = (18 * scale).dp,
    rowHorizontalPadding = (20 * scale).dp,
    compactRowVerticalPadding = (18 * scale).dp,
    endpointRowTopPadding = (16 * scale).dp,
    endpointRowBottomPadding = (12 * scale).dp,
    flagColumnWidth = (48 * scale).dp,
    flagTextGap = (18 * scale).dp,
    flagFontSize = (38 * scale).sp,
    statusGap = (18 * scale).dp,
    pingWidth = (54 * scale).dp,
    adminPingWidth = (58 * scale).dp,
    autoIconSize = (36 * scale).dp,
    autoRowPadding = (20 * scale).dp,
    pingChipHeight = (38 * scale).dp,
    portChipHeight = (24 * scale).dp,
)

@Composable
fun ServerListScreen(
    onBack: () -> Unit,
    onLongBack: () -> Unit = onBack,
    viewModel: ServerListViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServerId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val automaticServerSelection by viewModel.automaticServerSelection.collectAsStateWithLifecycle()
    val isAdminProfile by viewModel.isAdminProfile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.setScreenActive(true)
        onPauseOrDispose { viewModel.setScreenActive(false) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)
        val metrics = remember(scale) { serverListMetrics(scale) }

        val screenPad = (40 * scale).dp
        val gap = (16 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (16 * scale).sp
        val bodySize = (14 * scale).sp
        val labelSize = (12 * scale).sp
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
                    onLongClick = onLongBack,
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
                        val offlineText = stringResource(R.string.server_offline)
                        val unavailableText = stringResource(R.string.server_unavailable)
                        val density = LocalDensity.current
                        val textMeasurer = rememberTextMeasurer()
                        val titleMeasureStyle = tightStyle.copy(
                            fontSize = titleSize,
                            fontWeight = FontWeight.Medium,
                        )
                        val labelMeasureStyle = tightStyle.copy(fontSize = labelSize)

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val pingBlockWidthPx = with(density) {
                                (if (isAdminProfile) metrics.adminPingWidth else metrics.pingWidth)
                                    .roundToPx()
                            }
                            val trailingWidthPx = servers.maxOf { server ->
                                when {
                                    !server.isSelectable -> textMeasurer.measure(
                                        text = AnnotatedString(offlineText),
                                        style = labelMeasureStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                    ).size.width
                                    server.ping < 0 -> textMeasurer.measure(
                                        text = AnnotatedString(unavailableText),
                                        style = labelMeasureStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                    ).size.width
                                    else -> pingBlockWidthPx
                                }
                            }
                            val trailingWidth = with(density) { trailingWidthPx.toDp() }
                            val nameWidthPx = with(density) {
                                (
                                    maxWidth -
                                        metrics.rowHorizontalPadding * 2 -
                                        metrics.flagColumnWidth -
                                        metrics.flagTextGap -
                                        metrics.statusGap -
                                        trailingWidth
                                    ).coerceAtLeast(1.dp).roundToPx()
                            }
                            val names = servers.map { serverDisplayName(it.name, it.country) }
                            val serverNameFontSize = remember(
                                names,
                                nameWidthPx,
                                titleSize,
                                titleMeasureStyle,
                                density.fontScale,
                            ) {
                                var candidate = titleSize.value
                                val minCandidate = (13f * scale).coerceAtLeast(11f)
                                while (candidate > minCandidate) {
                                    val widestNamePx = names.maxOf { name ->
                                        textMeasurer.measure(
                                            text = AnnotatedString(name),
                                            style = titleMeasureStyle.copy(fontSize = candidate.sp),
                                            maxLines = 1,
                                            softWrap = false,
                                        ).size.width
                                    }
                                    if (widestNamePx <= nameWidthPx) break
                                    candidate -= 0.5f
                                }
                                candidate.coerceAtLeast(minCandidate).sp
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                        metrics = metrics,
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
                                        showEndpoint = isAdminProfile,
                                        serverNameFontSize = serverNameFontSize,
                                        metrics = metrics,
                                        titleSize = titleSize,
                                        bodySize = bodySize,
                                        labelSize = labelSize,
                                        borderWidth = borderWidth,
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
}

@Composable
private fun AutomaticServerItem(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    metrics: ServerListMetrics,
    titleSize: TextUnit,
    bodySize: TextUnit,
    borderWidth: Dp,
    tightStyle: TextStyle,
) {
    val isDark = isDarkAppTheme()
    var focused by remember { mutableStateOf(false) }
    val selectedContainerColor = if (isDark) {
        VpnGreen.copy(alpha = 0.14f)
    } else {
        Color(0xFFE7F5EA)
    }
    val selectedBorderColor = if (isDark) {
        VpnGreen.copy(alpha = 0.72f)
    } else {
        VpnGreen.copy(alpha = 0.58f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.cardVerticalPadding)
            .onFocusChanged { focused = it.isFocused }
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
        shape = RoundedCornerShape(metrics.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                selectedContainerColor
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = serverCardBorder(
            selected = selected,
            focused = focused,
            selectedBorderColor = selectedBorderColor,
            focusBorderWidth = borderWidth,
            showIdleBorder = !isDark,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.autoRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = VpnGreen,
                modifier = Modifier.size(metrics.autoIconSize),
            )
            Spacer(modifier = Modifier.width(metrics.flagTextGap))
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
    showEndpoint: Boolean,
    serverNameFontSize: TextUnit,
    metrics: ServerListMetrics,
    titleSize: TextUnit,
    bodySize: TextUnit,
    labelSize: TextUnit,
    borderWidth: Dp,
    tightStyle: TextStyle,
) {
    val isDark = isDarkAppTheme()
    var focused by remember { mutableStateOf(false) }
    val selectedContainerColor = if (isDark) {
        VpnGreen.copy(alpha = 0.14f)
    } else {
        Color(0xFFE7F5EA)
    }
    val selectedBorderColor = if (isDark) {
        VpnGreen.copy(alpha = 0.72f)
    } else {
        VpnGreen.copy(alpha = 0.58f)
    }
    val dividerColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color(0xFF1A1C1E).copy(alpha = 0.18f)
    }
    val showCountryLine = showEndpoint || !server.isSelectable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.cardVerticalPadding)
            .onFocusChanged { focused = it.isFocused }
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
        shape = RoundedCornerShape(metrics.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                selectedContainerColor
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = serverCardBorder(
            selected = selected,
            focused = focused,
            selectedBorderColor = selectedBorderColor,
            focusBorderWidth = borderWidth,
            showIdleBorder = !isDark,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = metrics.rowHorizontalPadding,
                        top = if (showEndpoint) {
                            metrics.endpointRowTopPadding
                        } else {
                            metrics.compactRowVerticalPadding
                        },
                        end = metrics.rowHorizontalPadding,
                        bottom = if (showEndpoint) {
                            metrics.endpointRowBottomPadding
                        } else {
                            metrics.compactRowVerticalPadding
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = countryFlagForUi(server.country, server.name),
                    modifier = Modifier.width(metrics.flagColumnWidth),
                    fontSize = metrics.flagFontSize,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.width(metrics.flagTextGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverDisplayName(server.name, server.country),
                        fontSize = serverNameFontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = tightStyle,
                    )
                    if (showCountryLine) {
                        Text(
                            text = if (!server.isSelectable) {
                                stringResource(R.string.server_unavailable)
                            } else {
                                serverCountryNameForUi(server.country, server.name)
                            },
                            fontSize = bodySize,
                            color = if (!server.isSelectable) {
                                VpnRed
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = tightStyle,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(metrics.statusGap))
                ServerStatusBlock(
                    server = server,
                    width = if (showEndpoint) metrics.adminPingWidth else metrics.pingWidth,
                    titleSize = titleSize,
                    labelSize = labelSize,
                    tightStyle = tightStyle,
                )
            }

            if (showEndpoint) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = dividerColor,
                )
                ServerEndpointRow(
                    server = server,
                    metrics = metrics,
                    labelSize = labelSize,
                    tightStyle = tightStyle,
                )
            }
        }
    }
}

@Composable
private fun ServerEndpointRow(
    server: Server,
    metrics: ServerListMetrics,
    labelSize: TextUnit,
    tightStyle: TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = metrics.rowHorizontalPadding,
                top = 8.dp,
                end = metrics.rowHorizontalPadding,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerEndpointMarker(
            modifier = Modifier.width(metrics.flagColumnWidth),
            metrics = metrics,
        )
        Spacer(modifier = Modifier.width(metrics.flagTextGap))
        Text(
            text = server.address,
            modifier = Modifier.weight(1f),
            fontSize = labelSize,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = tightStyle,
        )
        Spacer(modifier = Modifier.width(10.dp))
        EndpointValueChip(
            text = server.port.toString(),
            width = metrics.adminPingWidth,
            height = metrics.portChipHeight,
            labelSize = labelSize,
            tightStyle = tightStyle,
        )
    }
}

@Composable
private fun ServerEndpointMarker(
    modifier: Modifier = Modifier,
    metrics: ServerListMetrics,
) {
    val isDark = isDarkAppTheme()
    val markerColor = if (isDark) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        Color(0xFF1A1C1E).copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier.height(metrics.portChipHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidth = 1.6.dp.toPx()
            val corner = 2.dp.toPx()
            val left = 1.2.dp.toPx()
            val rectWidth = size.width - left * 2
            val rectHeight = 5.dp.toPx()
            val top = 2.4.dp.toPx()
            val bottom = size.height - top - rectHeight
            val dotX = left + 3.6.dp.toPx()
            val dotRadius = 1.1.dp.toPx()

            drawRoundRect(
                color = markerColor,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = strokeWidth),
            )
            drawRoundRect(
                color = markerColor,
                topLeft = Offset(left, bottom),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = strokeWidth),
            )
            drawCircle(
                color = markerColor,
                radius = dotRadius,
                center = Offset(dotX, top + rectHeight / 2f),
            )
            drawCircle(
                color = markerColor,
                radius = dotRadius,
                center = Offset(dotX, bottom + rectHeight / 2f),
            )
        }
    }
}

@Composable
private fun ServerStatusBlock(
    server: Server,
    width: Dp,
    titleSize: TextUnit,
    labelSize: TextUnit,
    tightStyle: TextStyle,
) {
    when {
        !server.isSelectable -> {
            Text(
                text = stringResource(R.string.server_offline),
                fontSize = labelSize,
                color = VpnRed,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                style = tightStyle,
            )
        }
        server.ping < 0 -> {
            Text(
                text = stringResource(R.string.server_unavailable),
                fontSize = labelSize,
                fontWeight = FontWeight.SemiBold,
                color = VpnRed,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                style = tightStyle,
            )
        }
        server.ping > 0 -> {
            PingChip(
                ping = server.ping,
                width = width,
                titleSize = titleSize,
                labelSize = labelSize,
                tightStyle = tightStyle,
            )
        }
        else -> {
            LoadingPingChip(width = width)
        }
    }
}

@Composable
private fun LoadingPingChip(width: Dp) {
    EndpointChipContainer(
        modifier = Modifier
            .width(width)
            .height(38.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PingChip(
    ping: Long,
    width: Dp,
    titleSize: TextUnit,
    labelSize: TextUnit,
    tightStyle: TextStyle,
) {
    EndpointChipContainer(
        modifier = Modifier.width(width),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "$ping",
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = pingColor(ping),
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
        Text(
            text = stringResource(R.string.speed_unit_ms),
            fontSize = labelSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

@Composable
private fun EndpointValueChip(
    text: String,
    width: Dp,
    height: Dp,
    labelSize: TextUnit,
    tightStyle: TextStyle,
) {
    EndpointChipContainer(
        modifier = Modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(99.dp),
    ) {
        Text(
            text = text,
            fontSize = labelSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = tightStyle,
        )
    }
}

@Composable
private fun EndpointChipContainer(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    val isDark = isDarkAppTheme()
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.11f)
    } else {
        Color(0xFF1A1C1E).copy(alpha = 0.11f)
    }
    val backgroundColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }

    Column(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .background(backgroundColor, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content() },
    )
}

@Composable
private fun serverCardBorder(
    selected: Boolean,
    focused: Boolean,
    selectedBorderColor: Color,
    focusBorderWidth: Dp,
    showIdleBorder: Boolean,
): BorderStroke? = when {
    selected -> BorderStroke(1.dp, selectedBorderColor)
    focused -> BorderStroke(focusBorderWidth, MaterialTheme.colorScheme.primary)
    showIdleBorder -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    else -> null
}

@Composable
private fun isDarkAppTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

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
