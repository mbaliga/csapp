package com.asoc.ping.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ProvenanceColors(val local: Color, val cloud: Color)

val LocalProvenanceColors = staticCompositionLocalOf { ProvenanceColors(HyleTokens.Radium, HyleTokens.Alien) }

private val Dark = darkColorScheme(
    primary = HyleTokens.Violet, onPrimary = HyleTokens.Ground,
    background = HyleTokens.Ground, onBackground = HyleTokens.TextHigh,
    surface = HyleTokens.Ground, onSurface = HyleTokens.TextHigh,
    surfaceVariant = HyleTokens.Surface1, onSurfaceVariant = HyleTokens.TextMuted,
    surfaceContainer = HyleTokens.Surface1, surfaceContainerHigh = HyleTokens.Surface2,
    outline = HyleTokens.Outline, outlineVariant = HyleTokens.Outline,
    secondaryContainer = HyleTokens.Surface2, onSecondaryContainer = HyleTokens.TextHigh,
    error = Color(0xFFFFB4AB), onError = Color(0xFF410002),
)

private val Light = lightColorScheme(
    primary = HyleTokens.LightViolet, onPrimary = Color.White,
    background = HyleTokens.LightGround, onBackground = HyleTokens.LightText,
    surface = HyleTokens.LightGround, onSurface = HyleTokens.LightText,
    surfaceVariant = HyleTokens.LightSurface1, onSurfaceVariant = HyleTokens.LightMuted,
    surfaceContainer = HyleTokens.LightSurface1, surfaceContainerHigh = HyleTokens.LightSurface2,
    outline = HyleTokens.LightOutline, outlineVariant = HyleTokens.LightOutline,
    secondaryContainer = HyleTokens.LightSurface2, onSecondaryContainer = HyleTokens.LightText,
    error = Color(0xFF9C1C1C),
)

private val Type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Dark is primary. The Hyle palette replaces the earlier green Material scheme. */
@Composable
fun CsAppTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val prov = if (useDarkTheme) ProvenanceColors(HyleTokens.Radium, HyleTokens.Alien)
    else ProvenanceColors(HyleTokens.LightRadium, HyleTokens.LightAlien)
    CompositionLocalProvider(LocalProvenanceColors provides prov) {
        MaterialTheme(colorScheme = if (useDarkTheme) Dark else Light, typography = Type, content = content)
    }
}
