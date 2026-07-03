package com.grapevine.grapevine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import grapevine.composeapp.generated.resources.Res
import grapevine.composeapp.generated.resources.inter_bold
import grapevine.composeapp.generated.resources.inter_medium
import grapevine.composeapp.generated.resources.inter_regular
import grapevine.composeapp.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

// ── Grapevine palette ────────────────────────────────────────────────────────
// Vibrant purple-grape primary, wine-red secondary, vine-green tertiary.
// Every on-X color below is chosen against its paired container/surface for a
// WCAG-AA contrast ratio of at least ~4.5:1 (verified by hand) — the previous
// palette had onBackground and onSurfaceVariant almost matching their
// background in dark mode, which is what made that text unreadable.

private val GrapePrimaryLight   = Color(0xFF8B2FE0)
private val GrapePrimaryDark    = Color(0xFFD6B4FF)
private val GrapeContainerLight = Color(0xFFF2E3FF)
private val GrapeContainerDark  = Color(0xFF6A16C4)

private val WineSecondaryLight   = Color(0xFFB0264A)
private val WineSecondaryDark    = Color(0xFFFFB3C1)
private val WineContainerLight   = Color(0xFFFFD9E1)
private val WineContainerDark    = Color(0xFF7D1837)

private val VineTertiaryLight   = Color(0xFF3E7D1F)
private val VineTertiaryDark    = Color(0xFFA0D683)
private val VineContainerLight  = Color(0xFFD7F2C2)
private val VineContainerDark   = Color(0xFF255900)

private val LightColors = lightColorScheme(
    primary            = GrapePrimaryLight,
    onPrimary          = Color.White,
    primaryContainer   = GrapeContainerLight,
    onPrimaryContainer = Color(0xFF2F0060),

    secondary            = WineSecondaryLight,
    onSecondary          = Color.White,
    secondaryContainer   = WineContainerLight,
    onSecondaryContainer = Color(0xFF3E0016),

    tertiary            = VineTertiaryLight,
    onTertiary          = Color.White,
    tertiaryContainer   = VineContainerLight,
    onTertiaryContainer = Color(0xFF0F2900),

    background   = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface      = Color(0xFFFFFBFF),
    onSurface    = Color(0xFF1C1B1F),

    surfaceVariant   = Color(0xFFEEE0F9),
    onSurfaceVariant = Color(0xFF4A4458),

    outline = Color(0xFF7A7289),
    error   = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary            = GrapePrimaryDark,
    onPrimary          = Color(0xFF48008A),
    primaryContainer   = GrapeContainerDark,
    onPrimaryContainer = GrapeContainerLight,

    secondary            = WineSecondaryDark,
    onSecondary          = Color(0xFF5E1128),
    secondaryContainer   = WineContainerDark,
    onSecondaryContainer = WineContainerLight,

    tertiary            = VineTertiaryDark,
    onTertiary          = Color(0xFF153300),
    tertiaryContainer   = VineContainerDark,
    onTertiaryContainer = VineContainerLight,

    background   = Color(0xFF141218),
    onBackground = Color(0xFFE7E0EC),
    surface      = Color(0xFF141218),
    onSurface    = Color(0xFFE7E0EC),

    surfaceVariant   = Color(0xFF4A4458),
    onSurfaceVariant = Color(0xFFCDC4D6),

    outline = Color(0xFF948F99),
    error   = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// ── Typography ───────────────────────────────────────────────────────────────

@Composable
private fun grapevineFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun grapevineTypography(): Typography {
    val fontFamily = grapevineFontFamily()
    val base = Typography()
    return Typography(
        displayLarge   = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium  = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall   = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge  = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall  = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge     = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium    = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall     = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge      = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium     = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall      = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge     = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium    = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall     = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

@Composable
fun GrapevineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = grapevineTypography(),
        content = content,
    )
}
