package com.deedeedev.ytreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val DarkAmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    inverseSurface = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

internal fun resolveDarkTheme(appTheme: AppTheme, systemIsDarkTheme: Boolean): Boolean {
    return when (appTheme) {
        AppTheme.SYSTEM -> systemIsDarkTheme
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.DARK_AMOLED -> true
    }
}

internal fun appColorScheme(appTheme: AppTheme, systemIsDarkTheme: Boolean): ColorScheme {
    return when (appTheme) {
        AppTheme.SYSTEM -> if (systemIsDarkTheme) DarkColorScheme else LightColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.DARK_AMOLED -> DarkAmoledColorScheme
    }
}

@Composable
fun YtReaderTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemIsDarkTheme = isSystemInDarkTheme()
    val colorScheme = appColorScheme(appTheme = appTheme, systemIsDarkTheme = systemIsDarkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
