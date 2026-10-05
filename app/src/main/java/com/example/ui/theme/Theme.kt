package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UbaidLightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = Color.White,
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,
    secondary = AccentPrimary,
    onSecondary = Color.White,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = OnAccentContainer,
    tertiary = TextSecondary,
    onTertiary = Color.White,
    background = BackgroundPureWhite,
    onBackground = TextPrimary,
    surface = SurfacePureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,
    outline = BorderStrong,
    outlineVariant = BorderSubtle,
    error = StatusErrorBorder,
    onError = Color.White,
    errorContainer = StatusErrorBg,
    onErrorContainer = StatusErrorText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UbaidLightColorScheme,
        typography = Typography,
        content = content
    )
}
