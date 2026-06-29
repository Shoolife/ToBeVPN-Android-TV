package com.tobevpn.tv.presentation.appfilter

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobevpn.tv.R
import com.tobevpn.tv.data.InstalledAppItem
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.components.TvHeaderIconButton
import com.tobevpn.tv.presentation.theme.VpnGreen
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed

@Composable
fun AppFilterScreen(
    onBack: () -> Unit,
    viewModel: AppFilterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reconnectBanner by viewModel.reconnectBanner.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)
        val screenPad = (40 * scale).dp
        val cardPad = (22 * scale).dp
        val gap = (16 * scale).dp
        val smallGap = (8 * scale).dp
        val corner = (16 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (18 * scale).sp
        val bodySize = (15 * scale).sp
        val secondarySize = (13 * scale).sp
        val borderWidth = (2 * scale).dp
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvHeaderIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(headerButtonSize),
                    shape = RoundedCornerShape((8 * scale).dp),
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
                    text = stringResource(R.string.app_filter_title),
                    fontSize = headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = headerColor,
                    style = tightStyle,
                )
            }

            Spacer(modifier = Modifier.height((24 * scale).dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeButton(
                    label = stringResource(R.string.app_filter_mode_off),
                    selected = state.mode == AppFilterMode.OFF,
                    onClick = { viewModel.setMode(AppFilterMode.OFF) },
                    bodySize = bodySize,
                    scale = scale,
                )
                Spacer(modifier = Modifier.width((12 * scale).dp))
                ModeButton(
                    label = stringResource(R.string.app_filter_mode_whitelist),
                    selected = state.mode == AppFilterMode.WHITELIST,
                    onClick = { viewModel.setMode(AppFilterMode.WHITELIST) },
                    bodySize = bodySize,
                    scale = scale,
                )
                Spacer(modifier = Modifier.width((12 * scale).dp))
                ModeButton(
                    label = stringResource(R.string.app_filter_mode_blacklist),
                    selected = state.mode == AppFilterMode.BLACKLIST,
                    onClick = { viewModel.setMode(AppFilterMode.BLACKLIST) },
                    bodySize = bodySize,
                    scale = scale,
                )
            }

            Spacer(modifier = Modifier.height(gap))

            if (reconnectBanner) {
                Text(
                    text = stringResource(R.string.app_filter_reconnect_hint),
                    fontSize = bodySize,
                    color = VpnOrange,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height(smallGap))
            }

            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(corner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    Text(
                        text = modeSummary(state),
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.height(smallGap))
                    if (state.mode == AppFilterMode.OFF) {
                        Text(
                            text = stringResource(R.string.app_filter_off_explainer),
                            fontSize = bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = tightStyle,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.app_filter_selected_count,
                                    state.selected.size,
                                    state.visibleApps.size,
                                ),
                                fontSize = bodySize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                style = tightStyle,
                            )
                            SmallActionButton(
                                label = stringResource(R.string.app_filter_show_system),
                                selected = state.showSystem,
                                onClick = viewModel::toggleShowSystem,
                                bodySize = secondarySize,
                                scale = scale,
                            )
                            Spacer(modifier = Modifier.width((10 * scale).dp))
                            SmallActionButton(
                                label = stringResource(R.string.app_filter_select_all),
                                selected = false,
                                onClick = viewModel::selectAllVisible,
                                bodySize = secondarySize,
                                scale = scale,
                            )
                            Spacer(modifier = Modifier.width((10 * scale).dp))
                            SmallActionButton(
                                label = stringResource(R.string.app_filter_clear_all),
                                selected = false,
                                onClick = viewModel::clearAll,
                                bodySize = secondarySize,
                                scale = scale,
                            )
                        }
                        if (state.mode == AppFilterMode.WHITELIST && state.selected.isEmpty()) {
                            Spacer(modifier = Modifier.height(smallGap))
                            Text(
                                text = stringResource(R.string.app_filter_empty_warning),
                                fontSize = bodySize,
                                color = VpnRed,
                                style = tightStyle,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(gap))

                    when {
                        state.loading -> {
                            Text(
                                text = stringResource(R.string.app_filter_loading),
                                fontSize = bodySize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = tightStyle,
                            )
                        }
                        state.mode == AppFilterMode.OFF -> Unit
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = (8 * scale).dp),
                            ) {
                                items(state.visibleApps, key = { it.packageName }) { app ->
                                    AppRow(
                                        app = app,
                                        selected = app.packageName in state.selected,
                                        mode = state.mode,
                                        provider = viewModel.installedAppsProvider,
                                        onToggle = { viewModel.toggle(app.packageName) },
                                        scale = scale,
                                        titleSize = bodySize,
                                        secondarySize = secondarySize,
                                        tightStyle = tightStyle,
                                    )
                                    Spacer(modifier = Modifier.height((8 * scale).dp))
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
private fun modeSummary(state: AppFilterUiState): String =
    when (state.mode) {
        AppFilterMode.OFF -> stringResource(R.string.app_filter_subtitle_off)
        AppFilterMode.WHITELIST -> stringResource(R.string.app_filter_subtitle_whitelist, state.selected.size)
        AppFilterMode.BLACKLIST -> stringResource(R.string.app_filter_subtitle_blacklist, state.selected.size)
    }

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    scale: Float,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    val modifier = Modifier
        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
        .then(
            if (focused) Modifier.border((2 * scale).dp, MaterialTheme.colorScheme.onSurface, shape)
            else Modifier
        )
        .onFocusChanged { focused = it.isFocused }
    val padding = PaddingValues(horizontal = (18 * scale).dp, vertical = (7 * scale).dp)
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        if (selected) {
            Button(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                contentPadding = padding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VpnGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text(label, fontSize = bodySize)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                contentPadding = padding,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text(label, fontSize = bodySize)
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    bodySize: androidx.compose.ui.unit.TextUnit,
    scale: Float,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((9 * scale).dp)
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                .then(
                    if (focused) Modifier.border((2 * scale).dp, MaterialTheme.colorScheme.onSurface, shape)
                    else Modifier
                )
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            contentPadding = PaddingValues(horizontal = (14 * scale).dp, vertical = (6 * scale).dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (selected) VpnGreen else MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(label, fontSize = bodySize, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledAppItem,
    selected: Boolean,
    mode: AppFilterMode,
    provider: com.tobevpn.tv.data.InstalledAppsProvider,
    onToggle: () -> Unit,
    scale: Float,
    titleSize: androidx.compose.ui.unit.TextUnit,
    secondarySize: androidx.compose.ui.unit.TextUnit,
    tightStyle: TextStyle,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((12 * scale).dp)
    val badgeText = if (mode == AppFilterMode.WHITELIST) {
        stringResource(R.string.app_filter_badge_vpn)
    } else {
        stringResource(R.string.app_filter_badge_bypass)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (focused) (2 * scale).dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = (14 * scale).dp, vertical = (10 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = app.packageName,
            label = app.label,
            provider = provider,
            size = (42 * scale).dp,
        )
        Spacer(modifier = Modifier.width((14 * scale).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightStyle,
            )
            Spacer(modifier = Modifier.height((4 * scale).dp))
            Text(
                text = app.packageName,
                fontSize = secondarySize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightStyle,
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width((12 * scale).dp))
            Text(
                text = badgeText,
                fontSize = secondarySize,
                fontWeight = FontWeight.SemiBold,
                color = VpnGreen,
                maxLines = 1,
                style = tightStyle,
            )
        }
    }
}
