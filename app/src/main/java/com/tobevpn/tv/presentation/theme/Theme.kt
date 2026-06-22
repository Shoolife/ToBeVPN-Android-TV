package com.tobevpn.tv.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvDarkColorScheme = darkColorScheme(
    primary = VpnGreen,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = TvDarkBg,
    surface = TvSurface,
    surfaceVariant = TvSurfaceVariant,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0B0B5),
    primaryContainer = TvCardBg,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
)

private val TvLightColorScheme = lightColorScheme(
    primary = VpnGreen,
    onPrimary = Color.Black,
    secondary = Color(0xFF616161),
    onSecondary = Color.White,
    tertiary = VpnBlue,
    onTertiary = Color.White,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    primaryContainer = Color(0xFFE9EAEC),
    onPrimaryContainer = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE9EAEC),
    onSurfaceVariant = Color(0xFF5C5E6A),
    surfaceContainerHighest = Color(0xFFE9EAEC),
    surfaceContainerHigh = Color(0xFFEDEEF0),
    surfaceContainer = Color(0xFFF0F1F2),
    surfaceContainerLow = Color(0xFFF3F4F5),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF9A9DA5),
    outlineVariant = Color(0xFFD2D4D8),
    surfaceTint = Color.Transparent,
)

@Composable
fun ToBeVPNTvTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TvDarkColorScheme else TvLightColorScheme,
        typography = Typography,
        content = content,
    )
}
