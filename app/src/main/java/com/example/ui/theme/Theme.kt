package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VedaColorScheme = darkColorScheme(
    primary = VedaCyan,
    secondary = VedaViolet,
    tertiary = VedaMagenta,
    background = VedaDarkBg,
    surface = VedaSurface,
    surfaceVariant = VedaSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = VedaTextPrimary,
    onSurface = VedaTextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VedaColorScheme,
        typography = Typography,
        content = content
    )
}
