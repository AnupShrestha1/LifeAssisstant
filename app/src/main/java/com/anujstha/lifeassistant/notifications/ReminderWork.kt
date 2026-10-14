package com.anujstha.lifeassistant.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anujstha.lifeassistant.LifeAssistantApp
import java.util.concurrent.TimeUnit

/**
 * Re-registers all reminder alarms and delivers any occurrences that were missed (device off,
 * app force-stopped, clock moved forward). Runs via WorkManager so it survives process death.
 */
class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LifeAssistantApp).container
        val missed = container.reminderRepository.rescheduleAll()
        if (missed.isNotEmpty()) {
            val zone = container.displayZone()
            missed.forEach { container.reminderNotifier.show(it.reminder, it.event, zone) }
        }
        return Result.success()
    }
}

object ReminderWork {
    private const val ONE_TIME = "reminders-reschedule"
    private const val PERIODIC = "reminders-safety-net"

    /** Reschedule now (deduplicated: a pending request is replaced, never run twice in parallel). */
    fun enqueueReschedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
        )
    }

    /**
     * A twice-daily safety net that catches alarms an OEM battery manager dropped. It is not
     * exact and is not how reminders are normally delivered.
     */
    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}
