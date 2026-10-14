package com.anujstha.lifeassistant

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anujstha.lifeassistant.ui.navigation.AppNavigation
import com.anujstha.lifeassistant.ui.theme.LifeAssistantTheme

class MainActivity : ComponentActivity() {
    /** Set when launched from a reminder notification; consumed by the navigation host. */
    private var openReminders by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openReminders = intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)
        val settingsRepository = (application as LifeAssistantApp).container.settingsRepository

        setContent {
            // Null until DataStore's first read, so we never flash the wrong theme.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settings?.themeMode
            if (themeMode != null) {
                val darkTheme = themeMode.useDarkTheme(isSystemInDarkTheme())

                // Keep status/navigation bar icon contrast in sync with the app theme, which can
                // differ from the system theme when the user forces light or dark.
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                    )
                    onDispose {}
                }

                LifeAssistantTheme(themeMode = themeMode) {
                    AppNavigation(openReminders = openReminders, onRemindersOpened = { openReminders = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)) openReminders = true
    }

    companion object {
        const val EXTRA_OPEN_REMINDERS = "com.anujstha.lifeassistant.extra.OPEN_REMINDERS"

        // Same scrims androidx.activity uses by default for 3-button navigation.
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
