package com.deedeedev.ytreader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun fromStorageValue_defaultsToSystemForUnknownValue() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorageValue("unexpected"))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorageValue(null))
    }

    @Test
    fun fromStorageValue_mapsKnownValues() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorageValue("system"))
        assertEquals(AppTheme.LIGHT, AppTheme.fromStorageValue("light"))
        assertEquals(AppTheme.DARK, AppTheme.fromStorageValue("dark"))
        assertEquals(AppTheme.DARK_AMOLED, AppTheme.fromStorageValue("dark_amoled"))
    }

    @Test
    fun resolveDarkTheme_matchesThemeMode() {
        assertTrue(resolveDarkTheme(AppTheme.SYSTEM, systemIsDarkTheme = true))
        assertFalse(resolveDarkTheme(AppTheme.SYSTEM, systemIsDarkTheme = false))
        assertFalse(resolveDarkTheme(AppTheme.LIGHT, systemIsDarkTheme = true))
        assertTrue(resolveDarkTheme(AppTheme.DARK, systemIsDarkTheme = false))
        assertTrue(resolveDarkTheme(AppTheme.DARK_AMOLED, systemIsDarkTheme = false))
    }

    @Test
    fun appColorScheme_usesPureBlackForAmoledBackgrounds() {
        val colorScheme = appColorScheme(AppTheme.DARK_AMOLED, systemIsDarkTheme = false)

        assertEquals(Color.Black, colorScheme.background)
        assertEquals(Color.Black, colorScheme.surface)
        assertEquals(Color.Black, colorScheme.surfaceVariant)
        assertEquals(Color.Black, colorScheme.inverseSurface)
    }

    @Test
    fun appColorScheme_preservesCurrentDarkPalette() {
        val colorScheme = appColorScheme(AppTheme.DARK, systemIsDarkTheme = false)

        assertEquals(Purple80, colorScheme.primary)
        assertEquals(PurpleGrey80, colorScheme.secondary)
        assertEquals(Pink80, colorScheme.tertiary)
    }
}
