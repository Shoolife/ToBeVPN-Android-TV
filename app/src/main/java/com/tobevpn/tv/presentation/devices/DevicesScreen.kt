package com.tobevpn.tv.presentation.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.tobevpn.tv.data.remote.dto.LinkedDeviceDto
import com.tobevpn.tv.presentation.rememberTvScreenScale
import com.tobevpn.tv.presentation.theme.VpnGreen

@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = rememberTvScreenScale(maxWidth = maxWidth, maxHeight = maxHeight)

        val screenPad = (40 * scale).dp
        val cardCorner = (16 * scale).dp
        val cardPad = (20 * scale).dp
        val cardSpacing = (12 * scale).dp
        val gap = (16 * scale).dp
        val smallGap = (6 * scale).dp
        val headlineSize = (26 * scale).sp
        val titleSize = (18 * scale).sp
        val bodySize = (15 * scale).sp
        val metaSize = (13 * scale).sp
        val counterSize = (30 * scale).sp
        val backCorner = (8 * scale).dp
        val borderWidth = (2 * scale).dp
        val headerButtonSize = (44 * scale).dp
        val headerIconSize = (20 * scale).dp
        val deviceIconSize = (28 * scale).dp
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
                .padding(screenPad)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header: back, title, refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var backFocused by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
                ) {
                    IconButton(
                        onClick = onBack,
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
                    Spacer(modifier = Modifier.width(gap))
                    Text(
                        stringResource(R.string.devices_title),
                        fontSize = headlineSize,
                        fontWeight = FontWeight.Bold,
                        color = headerColor,
                        style = tightStyle,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    var refreshFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .size(headerButtonSize)
                            .then(
                                if (refreshFocused) Modifier.border(borderWidth, Color.White, RoundedCornerShape(backCorner))
                                else Modifier
                            )
                            .onFocusChanged { refreshFocused = it.isFocused },
                    ) {
                        com.tobevpn.tv.presentation.components.SpinningRefreshIcon(
                            spinning = state.isLoading,
                            contentDescription = stringResource(R.string.refresh),
                            tint = headerColor,
                            size = headerIconSize,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(gap))

            val devicesCount = state.currentCount ?: state.devices.size

            // Counter N/max
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.maxDevices == 0) {
                        devicesCount.toString()
                    } else {
                        "$devicesCount/${state.maxDevices}"
                    },
                    fontSize = counterSize,
                    fontWeight = FontWeight.Bold,
                    color = VpnGreen,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.width(smallGap))
                Text(
                    text = if (state.maxDevices == 0) {
                        stringResource(R.string.devices_count_unlimited)
                    } else {
                        stringResource(R.string.devices_count)
                    },
                    fontSize = bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = (3 * scale).dp),
                    style = tightStyle,
                )
            }

            state.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(smallGap))
                Text(
                    text = msg,
                    fontSize = metaSize,
                    color = MaterialTheme.colorScheme.error,
                    style = tightStyle,
                )
            }

            Spacer(modifier = Modifier.height(gap))

            val currentDevice = state.devices.firstOrNull {
                it.matchesDeviceAliases(state.currentDeviceAliases)
            }
            val otherDevices = state.devices.filterNot {
                it.matchesDeviceAliases(state.currentDeviceAliases)
            }

            // This device
            if (currentDevice != null) {
                SectionTitle(stringResource(R.string.devices_this_device), titleSize, tightStyle)
                Spacer(modifier = Modifier.height(smallGap))
                DeviceCard(
                    device = currentDevice,
                    isCurrent = true,
                    busy = false,
                    onDisconnect = null,
                    scale = scale,
                    cardCorner = cardCorner,
                    cardPad = cardPad,
                    bodySize = bodySize,
                    metaSize = metaSize,
                    deviceIconSize = deviceIconSize,
                    borderWidth = borderWidth,
                    tightStyle = tightStyle,
                )
                Spacer(modifier = Modifier.height(gap))
            }

            // Other devices
            SectionTitle(stringResource(R.string.devices_other_devices), titleSize, tightStyle)
            Spacer(modifier = Modifier.height(smallGap))
            when {
                // Match the desktop client: any (re)load shows a centered
                // accent spinner in place of the list, not only the first load.
                state.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = gap),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = VpnGreen)
                    }
                }
                otherDevices.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.devices_empty),
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tightStyle,
                    )
                }
                else -> {
                    otherDevices.forEach { device ->
                        DeviceCard(
                            device = device,
                            isCurrent = false,
                            busy = state.busyDeviceId == device.deviceId,
                            onDisconnect = { viewModel.disconnectDevice(device.deviceId) },
                            scale = scale,
                            cardCorner = cardCorner,
                            cardPad = cardPad,
                            bodySize = bodySize,
                            metaSize = metaSize,
                            deviceIconSize = deviceIconSize,
                            borderWidth = borderWidth,
                            tightStyle = tightStyle,
                        )
                        Spacer(modifier = Modifier.height(cardSpacing))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit, tightStyle: TextStyle) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = tightStyle,
    )
}

private fun deviceIcon(device: LinkedDeviceDto): ImageVector = when (device.inferredKind()) {
    DeviceKind.Tv -> Icons.Default.Tv
    DeviceKind.Desktop -> Icons.Default.Computer
    else -> Icons.Default.Smartphone
}

private fun LinkedDeviceDto.matchesDeviceAliases(aliases: Set<String>): Boolean {
    if (aliases.isEmpty()) return false
    val normalizedAliases = aliases.mapTo(mutableSetOf()) {
        it.trim().lowercase(java.util.Locale.ROOT)
    }
    return listOf(deviceId, hwid).any { value ->
        value?.trim()?.lowercase(java.util.Locale.ROOT) in normalizedAliases
    }
}

private enum class DeviceKind {
    Phone,
    Desktop,
    Tv,
}

private val technicalDesktopNameRegex = Regex("^[a-z0-9][a-z0-9._-]{1,31}$")

private fun LinkedDeviceDto.inferredKind(): DeviceKind {
    val type = deviceType?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
    val platform = platform?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
    val userAgent = userAgent?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
    if (type == "tv" || platform == "android tv" || userAgent.contains("/androidtv/")) {
        return DeviceKind.Tv
    }
    if (
        type == "desktop" ||
        platform == "linux" ||
        platform == "windows" ||
        platform == "macos" ||
        userAgent.contains("/linux/") ||
        userAgent.contains("/windows/") ||
        userAgent.contains("/macos/")
    ) {
        return DeviceKind.Desktop
    }
    return DeviceKind.Phone
}

private fun isTechnicalDesktopName(value: String): Boolean =
    technicalDesktopNameRegex.matches(value) && value == value.lowercase(java.util.Locale.ROOT)

private fun cleanDesktopModel(model: String?, platform: String?): String {
    val trimmed = model?.trim().orEmpty()
    if (trimmed.isBlank()) return ""
    val normalized = trimmed.lowercase(java.util.Locale.ROOT)
    val normalizedPlatform = platform?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
    if (normalized == "desktop" || normalized == "pc" || normalized == normalizedPlatform) return ""
    return trimmed
}

@Composable
private fun deviceTypeLabel(device: LinkedDeviceDto): String = when (device.inferredKind()) {
    DeviceKind.Tv -> stringResource(R.string.devices_type_tv)
    DeviceKind.Desktop -> stringResource(R.string.devices_type_desktop)
    DeviceKind.Phone -> stringResource(R.string.devices_type_phone)
}

@Composable
private fun deviceTitle(device: LinkedDeviceDto): String {
    val kind = device.inferredKind()
    if (kind == DeviceKind.Desktop) {
        val name = device.deviceName?.trim().orEmpty()
        if (name.isNotBlank() && !isTechnicalDesktopName(name)) return name
        val model = cleanDesktopModel(device.deviceModel, device.platform)
        if (model.isNotBlank()) return model
        val platform = device.platform?.trim()?.takeIf { it.isNotBlank() }
        val fallback = stringResource(R.string.devices_type_desktop)
        return if (platform != null) "$fallback $platform" else fallback
    }
    return device.deviceName?.takeIf { it.isNotBlank() }
        ?: device.deviceModel?.takeIf { it.isNotBlank() }
        ?: deviceTypeLabel(device)
}

@Composable
private fun DeviceCard(
    device: LinkedDeviceDto,
    isCurrent: Boolean,
    busy: Boolean,
    onDisconnect: (() -> Unit)?,
    scale: Float,
    cardCorner: androidx.compose.ui.unit.Dp,
    cardPad: androidx.compose.ui.unit.Dp,
    bodySize: androidx.compose.ui.unit.TextUnit,
    metaSize: androidx.compose.ui.unit.TextUnit,
    deviceIconSize: androidx.compose.ui.unit.Dp,
    borderWidth: androidx.compose.ui.unit.Dp,
    tightStyle: TextStyle,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = deviceIcon(device),
                contentDescription = null,
                modifier = Modifier.size(deviceIconSize),
                tint = if (isCurrent) VpnGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width((14 * scale).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceTitle(device),
                    fontSize = bodySize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = tightStyle,
                )
                Spacer(modifier = Modifier.height((2 * scale).dp))
                val meta = buildString {
                    append(deviceTypeLabel(device))
                    device.platform?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                }
                Text(
                    text = meta,
                    fontSize = metaSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tightStyle,
                )
            }
            Spacer(modifier = Modifier.width((12 * scale).dp))
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.devices_current_badge),
                    fontSize = metaSize,
                    fontWeight = FontWeight.SemiBold,
                    color = VpnGreen,
                    style = tightStyle,
                )
            } else if (onDisconnect != null) {
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape((10 * scale).dp)
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !busy,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        .then(
                            if (focused) Modifier.border(borderWidth, Color.White, shape)
                            else Modifier
                        )
                        .onFocusChanged { focused = it.isFocused },
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = (16 * scale).dp, vertical = (6 * scale).dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((16 * scale).dp),
                            strokeWidth = (2 * scale).dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(stringResource(R.string.devices_disconnect), fontSize = metaSize)
                    }
                }
            }
        }
    }
}
