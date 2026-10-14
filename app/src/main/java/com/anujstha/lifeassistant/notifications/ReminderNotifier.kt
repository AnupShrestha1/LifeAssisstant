package com.anujstha.lifeassistant.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Posts reminder notifications. Content is shown only in the notification itself and is never
 * logged. If notifications are blocked, nothing is posted, but the fired reminder is still
 * recorded and visible on the in-app Reminders screen.
 */
class ReminderNotifier(private val context: Context) {

    fun createChannels() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** True if the app may post notifications (runtime permission on 13+, and not blocked in settings). */
    fun canPostNotifications(): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Returns true if a notification was posted. */
    fun show(reminder: ReminderEntity, event: ReminderEventEntity, displayZone: ZoneId): Boolean {
        if (!canPostNotifications()) return false
        val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(event.occurrenceAt.atZone(displayZone))
        val text = when {
            event.missed -> context.getString(R.string.notif_missed, time)
            !reminder.description.isNullOrBlank() -> reminder.description
            else -> time
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(event.occurrenceAt.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // hide content on the lock screen
            .setAutoCancel(true)
            .setContentIntent(openRemindersIntent(reminder.requestCode))
            .setDeleteIntent(dismissIntent(reminder.requestCode, event.id))
            .addAction(0, context.getString(R.string.notif_action_dismiss), dismissIntent(reminder.requestCode, event.id))
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(reminder.requestCode, notification)
            true
        } catch (e: SecurityException) {
            false // permission revoked between the check and notify
        }
    }

    fun cancel(notificationId: Int) = NotificationManagerCompat.from(context).cancel(notificationId)

    private fun openRemindersIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dismissIntent(notificationId: Int, eventId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId,
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_DISMISS)
            .putExtra(ReminderActionReceiver.EXTRA_EVENT_ID, eventId)
            .putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}
