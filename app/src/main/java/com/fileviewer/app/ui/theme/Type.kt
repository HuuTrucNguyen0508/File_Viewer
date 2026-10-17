package com.fileviewer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fileviewer.app.R

@OptIn(ExperimentalTextApi::class)
private fun plex(weight: Int, width: Float = 100f) = Font(
    R.font.plex_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: Int) = Font(
    R.font.jetbrains_mono,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PlexSans = FontFamily(plex(400), plex(500), plex(600))

/** Plex at its narrowest width: folder titles and file names in headers. */
val PlexCondensed = FontFamily(plex(500, 85f), plex(600, 85f))

/** Content, never chrome: code, hex, offsets, extension tiles (the extension is literal file content). */
val Mono = FontFamily(mono(400), mono(700))

// Scale: 12 / 14 / 16 / 18 / 21 / 24 / 36 (classical typographic scale).
val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = PlexCondensed, fontWeight = FontWeight(600), fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = PlexCondensed, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = PlexCondensed, fontWeight = FontWeight(600), fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(500), fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp),
)

val MonoBody = TextStyle(fontFamily = Mono, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 18.sp)
