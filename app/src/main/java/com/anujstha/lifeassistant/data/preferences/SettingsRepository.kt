package com.anujstha.lifeassistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DateTimeException
import java.time.ZoneId
import java.util.Currency

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes [UserSettings]. Unknown or corrupt stored values fall back to defaults
 * rather than crashing, so a bad value can never lock the user out of the app.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toUserSettings() }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCurrency(code: String) {
        require(isValidCurrency(code)) { "Unknown currency code" }
        dataStore.edit { it[Keys.CURRENCY] = code }
    }

    suspend fun setDisplayZone(zoneId: String) {
        require(isValidZone(zoneId)) { "Unknown time zone" }
        dataStore.edit { it[Keys.DISPLAY_ZONE] = zoneId }
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    /** Back to defaults (theme, NPR, Asia/Kathmandu, system time format). */
    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toUserSettings() = UserSettings(
        themeMode = enumOrDefault(this[Keys.THEME_MODE], ThemeMode.SYSTEM),
        currencyCode = this[Keys.CURRENCY]?.takeIf(::isValidCurrency) ?: UserSettings.DEFAULT_CURRENCY,
        displayZoneId = this[Keys.DISPLAY_ZONE]?.takeIf(::isValidZone) ?: UserSettings.DEFAULT_ZONE_ID,
        timeFormat = enumOrDefault(this[Keys.TIME_FORMAT], TimeFormat.SYSTEM),
    )

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY = stringPreferencesKey("currency_code")
        val DISPLAY_ZONE = stringPreferencesKey("display_zone_id")
        val TIME_FORMAT = stringPreferencesKey("time_format")
    }

    companion object {
        fun isValidCurrency(code: String): Boolean =
            runCatching { Currency.getInstance(code) }.isSuccess

        fun isValidZone(zoneId: String): Boolean = try {
            ZoneId.of(zoneId)
            true
        } catch (e: DateTimeException) {
            false
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
