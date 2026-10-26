package com.vishwesh.slottracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colours outside the Material scheme. */
data class ExtraColors(
    val slotTint: Color,
    val highlightTint: Color,
    val warning: Color,
    val danger: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Palette.slotTint.light, Palette.highlightTint.light, Palette.warning.light, Palette.danger.light)
}

private fun scheme(dark: Boolean): ColorScheme {
    fun Palette.Tone.pick() = if (dark) this.dark else light
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Palette.accent.pick(),
        onPrimary = Palette.onAccent.pick(),
        primaryContainer = Palette.highlightTint.pick(),
        onPrimaryContainer = Palette.accent.pick(),
        secondaryContainer = Palette.highlightTint.pick(),
        onSecondaryContainer = Palette.accent.pick(),
        background = Palette.background.pick(),
        onBackground = Palette.onSurface.pick(),
        surface = Palette.background.pick(),
        onSurface = Palette.onSurface.pick(),
        onSurfaceVariant = Palette.secondaryText.pick(),
        surfaceVariant = Palette.slotTint.pick(),
        surfaceContainerLowest = Palette.surface.pick(),
        surfaceContainerLow = Palette.surface.pick(),
        surfaceContainer = Palette.surface.pick(),
        surfaceContainerHigh = Palette.surface.pick(),
        surfaceContainerHighest = Palette.slotTint.pick(),
        outline = Palette.divider.pick(),
        outlineVariant = Palette.divider.pick(),
        error = Palette.danger.pick(),
    )
}

/** App theme: follows the system light/dark mode (spec §8). */
@Composable
fun SlotTrackerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    fun Palette.Tone.pick() = if (dark) this.dark else light
    CompositionLocalProvider(
        LocalExtraColors provides ExtraColors(
            slotTint = Palette.slotTint.pick(),
            highlightTint = Palette.highlightTint.pick(),
            warning = Palette.warning.pick(),
            danger = Palette.danger.pick(),
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme(dark),
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
