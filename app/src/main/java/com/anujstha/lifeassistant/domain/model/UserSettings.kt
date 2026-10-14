package com.anujstha.lifeassistant.domain.model

/** How clock times are shown. Display-only: never affects stored instants or reminder schedules. */
enum class TimeFormat {
    SYSTEM,
    H12,
    H24,
}

/** App-wide preferences, persisted in DataStore. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** ISO 4217 code used for new transactions and totals. */
    val currencyCode: String = DEFAULT_CURRENCY,
    /** IANA zone used to decide "today" and to format times for display. */
    val displayZoneId: String = DEFAULT_ZONE_ID,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
) {
    companion object {
        const val DEFAULT_CURRENCY = "NPR"
        const val DEFAULT_ZONE_ID = "Asia/Kathmandu"
    }
}
