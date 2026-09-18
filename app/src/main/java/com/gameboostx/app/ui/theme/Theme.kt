package com.gameboostx.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GameBoostColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = BackgroundBlack,
    secondary = AccentGreenDim,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    outline = Divider,
)

/** Spec §1 calls for dark mode by default on an AMOLED-friendly palette; this app doesn't offer a light theme yet. */
@Composable
fun GameBoostXTheme(
    @Suppress("UNUSED_PARAMETER") useSystemTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GameBoostColorScheme,
        typography = GameBoostTypography,
        content = content,
    )
}
