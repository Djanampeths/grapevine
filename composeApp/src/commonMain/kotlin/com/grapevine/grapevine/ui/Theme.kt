package com.grapevine.grapevine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Grapevine palette ────────────────────────────────────────────────────────
// Seed: deep grape purple. Complementary wine-red for secondary.

private val Grape80     = Color(0xFFD0AAFF)   // light-mode primary container
private val Grape40     = Color(0xFF7B3FA0)   // light-mode primary
private val Grape20     = Color(0xFF4A1570)   // dark-mode primary container
private val Grape90     = Color(0xFFEEDAFF)   // light-mode primary container (bg)

private val Wine80      = Color(0xFFFFB3B0)
private val Wine40      = Color(0xFFA0253A)
private val Wine20      = Color(0xFF68001D)

private val Neutral10   = Color(0xFF1C1B1F)
private val Neutral99   = Color(0xFFFFFBFE)
private val NeutralVar30 = Color(0xFF4A4458)

private val LightColors = lightColorScheme(
    primary          = Grape40,
    onPrimary        = Color.White,
    primaryContainer = Grape90,
    onPrimaryContainer = Color(0xFF2A004E),

    secondary        = Wine40,
    onSecondary      = Color.White,
    secondaryContainer = Wine80,
    onSecondaryContainer = Wine20,

    tertiary         = Color(0xFF386A20),
    onTertiary       = Color.White,

    background       = Neutral99,
    onBackground     = Neutral10,
    surface          = Neutral99,
    onSurface        = Neutral10,
    surfaceVariant   = Color(0xFFEADDFF),
    onSurfaceVariant = NeutralVar30,

    outline          = Color(0xFF79747E),
    error            = Color(0xFFB3261E),
    onError          = Color.White,
)

private val DarkColors = darkColorScheme(
    primary          = Grape80,
    onPrimary        = Color(0xFF45007A),
    primaryContainer = Grape20,
    onPrimaryContainer = Grape90,

    secondary        = Wine80,
    onSecondary      = Wine20,
    secondaryContainer = Wine40,
    onSecondaryContainer = Color(0xFFFFDAD6),

    tertiary         = Color(0xFFA9D18A),
    onTertiary       = Color(0xFF1A3A00),

    background       = Neutral10,
    onBackground     = Color(0xFFE6E1E5),
    surface          = Neutral10,
    onSurface        = Color(0xFFE6E1E5),
    surfaceVariant   = Color(0xFF4A4458),
    onSurfaceVariant = Color(0xFFCCC2DC),

    outline          = Color(0xFF958DA5),
    error            = Color(0xFFF2B8B5),
    onError          = Color(0xFF601410),
)

@Composable
fun GrapevineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
