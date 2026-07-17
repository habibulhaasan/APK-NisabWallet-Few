package com.hasan.nisabwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 1. Initialize the color first
private val Color_White = Color.White

// 2. Now LightColors can safely use it
private val LightColors = lightColorScheme(
    primary = NisabPrimary,
    onPrimary = Color_White,
    background = NisabBackground,
    onBackground = NisabOnSurface,
    surface = NisabSurface,
    onSurface = NisabOnSurface,
    surfaceVariant = NisabSurfaceVariant,
    onSurfaceVariant = NisabOnSurfaceVariant,
    error = NisabError,
)

private val DarkColors = darkColorScheme(
    primary = NisabPrimary,
)

@Composable
fun NisabWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}