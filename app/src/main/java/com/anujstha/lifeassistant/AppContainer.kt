package com.anujstha.lifeassistant

import android.content.Context
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.preferences.settingsDataStore
import java.time.Clock

/**
 * Manual dependency container, created once by [LifeAssistantApp].
 * Holds app-scoped singletons; ViewModels get their dependencies from here via factories.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Single source of "now" so time-dependent logic can be tested with a fixed clock. */
    val clock: Clock = Clock.systemUTC()

    val settingsRepository: SettingsRepository = SettingsRepository(appContext.settingsDataStore)
}
