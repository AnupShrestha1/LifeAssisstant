package com.anujstha.lifeassistant

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.anujstha.lifeassistant.notifications.ReminderWork

class LifeAssistantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderNotifier.createChannels()
        // Alarms are cleared by force-stop as well as reboot, so re-check them on every app start.
        ReminderWork.enqueueReschedule(this)
        ReminderWork.ensurePeriodic(this)
    }
}

/** Looks up the [AppContainer] from inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as LifeAssistantApp).container
