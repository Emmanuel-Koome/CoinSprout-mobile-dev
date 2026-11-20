package com.coinsprout.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CoinSproutColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Paper,
    secondary = Sprout,
    onSecondary = Forest,
    tertiary = Mustard,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Moss,
    outline = LineColor,
    error = Coral
)

@Composable
fun CoinSproutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoinSproutColorScheme,
        typography = CoinSproutTypography,
        content = content
    )
}
