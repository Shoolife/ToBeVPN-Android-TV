package com.tobevpn.tv.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TvDarkColorScheme = darkColorScheme(
    primary = VpnBlue,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = TvDarkBg,
    surface = TvSurface,
    surfaceVariant = TvSurfaceVariant,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0BEC5),
    primaryContainer = TvCardBg,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun ToBeVPNTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
