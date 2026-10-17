package com.fileviewer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

@Composable
fun FileViewerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.accent.copy(alpha = 0.14f), onPrimaryContainer = p.ink,
        secondary = p.muted, onSecondary = p.paper,
        secondaryContainer = p.rule, onSecondaryContainer = p.ink,
        background = p.paper, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink,
        surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.rule,
        outline = p.muted, outlineVariant = p.rule,
        error = p.danger, onError = p.paper,
        inverseSurface = p.ink, inverseOnSurface = p.paper,
    )
    // No Surface wraps the screens, so set the default content color here or text falls back to black.
    CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides p.ink) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp),
            ),
            content = content,
        )
    }
}

object Fv {
    val palette: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
}
