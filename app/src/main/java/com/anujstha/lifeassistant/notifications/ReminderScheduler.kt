package com.anujstha.lifeassistant.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import java.time.Instant

/** Registers / removes the OS alarm for a reminder's next occurrence. */
interface ReminderScheduler {
    /** Replaces any existing alarm for [reminder] with one at [at]. */
    fun schedule(reminder: ReminderEntity, at: Instant)

    fun cancel(reminder: ReminderEntity)

    /** Whether alarms can be exact right now (always true below Android 12). */
    fun canScheduleExact(): Boolean
}

/**
 * AlarmManager implementation. One PendingIntent per reminder, identified by the reminder's
 * stable `requestCode` plus a per-reminder data URI, so scheduling again replaces the previous
 * alarm and cancelling always targets the right one.
 *
 * Exact alarms (`setExactAndAllowWhileIdle`) are used when the user has granted them; otherwise
 * `setAndAllowWhileIdle`, which Android may defer by several minutes. Neither is guaranteed on
 * every device: aggressive battery savers or force-stopping the app can still delay or drop alarms.
 */
class AlarmManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService())

    override fun schedule(reminder: ReminderEntity, at: Instant) {
        val intent = pendingIntent(reminder, at.toEpochMilli(), PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    override fun cancel(reminder: ReminderEntity) {
        pendingIntent(reminder, occurrenceMillis = 0, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Exposed for tests: whether an alarm intent is currently registered for [reminder]. */
    fun isScheduled(reminder: ReminderEntity): Boolean =
        pendingIntent(reminder, 0, PendingIntent.FLAG_NO_CREATE) != null

    private fun pendingIntent(reminder: ReminderEntity, occurrenceMillis: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The data URI makes the intent unique per reminder (requestCode alone is not part of
            // Intent.filterEquals); extras carry the occurrence and are refreshed by UPDATE_CURRENT.
            .setData(Uri.parse("lifeassistant://reminder/${reminder.id}"))
            .putExtra(EXTRA_REMINDER_ID, reminder.id)
            .putExtra(EXTRA_OCCURRENCE_MILLIS, occurrenceMillis)
        return PendingIntent.getBroadcast(context, reminder.requestCode, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_FIRE = "com.anujstha.lifeassistant.action.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrenceMillis"
    }
}
