package com.anujstha.lifeassistant.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun light_isNeverDark() {
        assertFalse(ThemeMode.LIGHT.useDarkTheme(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.useDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun dark_isAlwaysDark() {
        assertTrue(ThemeMode.DARK.useDarkTheme(systemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.useDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun system_followsSystemSetting() {
        assertFalse(ThemeMode.SYSTEM.useDarkTheme(systemInDarkTheme = false))
        assertTrue(ThemeMode.SYSTEM.useDarkTheme(systemInDarkTheme = true))
    }
}
