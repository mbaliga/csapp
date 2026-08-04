package com.mbaliga.csapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CsAppLightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFF33691E),
)

private val CsAppDarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFAED581),
)

@Composable
fun CsAppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) CsAppDarkColors else CsAppLightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
