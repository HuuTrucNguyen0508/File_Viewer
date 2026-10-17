package com.fileviewer.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.fileviewer.app.detect.Family

/**
 * "Drafting film": cool grey-green paper, green-black ink, one cobalt for interaction.
 * Each file family owns a hue; the hue appears on its extension tile, its viewer chip,
 * and in the syntax theme, so color always says what kind of file you are looking at.
 */
@Immutable
data class Palette(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val families: Map<Family, Color>,
    val dark: Boolean,
) {
    fun family(f: Family): Color = families.getValue(f)
}

val LightPalette = Palette(
    paper = Color(0xFFEFF1EC),
    surface = Color(0xFFF8F9F6),
    ink = Color(0xFF18201E),
    muted = Color(0xFF59645F),
    rule = Color(0xFFD6DBD3),
    accent = Color(0xFF2B45C4),
    onAccent = Color(0xFFF8F9F6),
    danger = Color(0xFFA3392F),
    families = mapOf(
        Family.Code to Color(0xFF2B45C4),
        Family.Text to Color(0xFF59645F),
        Family.Image to Color(0xFF3C7A46),
        Family.Document to Color(0xFFA3392F),
        Family.Media to Color(0xFFA86E12),
        Family.Archive to Color(0xFF694894),
        Family.Data to Color(0xFF17767C),
        Family.Font to Color(0xFF8A4A6B),
        Family.Binary to Color(0xFF363E3C),
    ),
    dark = false,
)

val DarkPalette = Palette(
    paper = Color(0xFF111615),
    surface = Color(0xFF192120),
    ink = Color(0xFFE1E6E2),
    muted = Color(0xFF8D9893),
    rule = Color(0xFF283130),
    accent = Color(0xFF97A8FF),
    onAccent = Color(0xFF111615),
    danger = Color(0xFFF08A7E),
    families = mapOf(
        Family.Code to Color(0xFF8A9CFF),
        Family.Text to Color(0xFFA3ADA8),
        Family.Image to Color(0xFF7CC489),
        Family.Document to Color(0xFFF08A7E),
        Family.Media to Color(0xFFE8B357),
        Family.Archive to Color(0xFFB79BE3),
        Family.Data to Color(0xFF6BC9CF),
        Family.Font to Color(0xFFE29AC0),
        Family.Binary to Color(0xFFB8C0BC),
    ),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
