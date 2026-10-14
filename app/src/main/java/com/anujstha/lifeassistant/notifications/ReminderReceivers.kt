package com.anujstha.lifeassistant.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anujstha.lifeassistant.AppContainer
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.data.repository.AlarmOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private val Context.container: AppContainer
    get() = (applicationContext as LifeAssistantApp).container

/** Runs [block] off the main thread while keeping the broadcast alive (up to ~10 s). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

internal suspend fun AppContainer.displayZone(): ZoneId = ZoneId.of(settingsRepository.settings.first().displayZoneId)

/** An alarm for one reminder occurrence went off. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManagerReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val occurrence = intent.getLongExtra(AlarmManagerReminderScheduler.EXTRA_OCCURRENCE_MILLIS, -1)
        if (occurrence < 0) return
        val container = context.container
        runAsync {
            val outcome = container.reminderRepository.onAlarmFired(reminderId, Instant.ofEpochMilli(occurrence))
            if (outcome is AlarmOutcome.Deliver) {
                container.reminderNotifier.show(outcome.reminder, outcome.event, container.displayZone())
            }
        }
    }
}

/** "Dismiss" action, or the user swiped the notification away. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val container = context.container
        runAsync {
            container.reminderRepository.dismissEvent(eventId)
            if (notificationId >= 0) container.reminderNotifier.cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.anujstha.lifeassistant.action.REMINDER_DISMISS"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}

/**
 * System events after which alarms must be re-registered or re-checked: reboot (alarms are
 * cleared), app update, manual clock change, time-zone change, and exact-alarm permission changes.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> ReminderWork.enqueueReschedule(context)
        }
    }
}
