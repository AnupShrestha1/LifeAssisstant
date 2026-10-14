package com.anujstha.lifeassistant.domain.model

/** User-selectable app theme. Persisted in settings from milestone 2 onward. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    /** Resolves whether the dark color scheme should be used given the current system setting. */
    fun useDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDarkTheme
    }
}
