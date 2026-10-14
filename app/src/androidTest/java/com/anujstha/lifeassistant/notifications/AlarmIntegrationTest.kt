package com.anujstha.lifeassistant.notifications

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.repository.ReminderInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Real AlarmManager + receiver + notification, end to end, on the device under test.
 * Grants notification and exact-alarm permission via shell first (as a user would in Settings).
 */
@RunWith(AndroidJUnit4::class)
class AlarmIntegrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container = (context.applicationContext as LifeAssistantApp).container
    private val scheduler = AlarmManagerReminderScheduler(context)
    private val created = mutableListOf<String>()

    /** Runs a shell command and waits for it to finish (reading the output to EOF). */
    private fun shell(command: String) {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Before
    fun grantPermissions() {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
    }

    @After
    fun cleanUp() = runBlocking {
        created.forEach { container.reminderRepository.delete(it) }
    }

    private fun fakeReminder(id: String, requestCode: Int) = ReminderEntity(
        id = id, requestCode = requestCode, title = "t", description = null, taskId = null,
        startAt = LocalDateTime.now(), zoneId = "UTC", recurrence = null, nextTriggerAt = null,
        enabled = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun pendingIntentIdentity_isStable_scheduleReplaces_cancelRemoves() {
        val reminder = fakeReminder("identity-test", requestCode = 987_654)
        val other = fakeReminder("identity-other", requestCode = 987_655)
        val future = Instant.now().plusSeconds(3600)

        scheduler.schedule(reminder, future)
        scheduler.schedule(reminder, future.plusSeconds(60)) // replaces, does not add a second
        scheduler.schedule(other, future)
        assertTrue(scheduler.isScheduled(reminder))

        scheduler.cancel(reminder)
        assertFalse(scheduler.isScheduled(reminder))
        assertTrue(scheduler.isScheduled(other)) // cancelling one never touches another
        scheduler.cancel(other)
        assertFalse(scheduler.isScheduled(other))
    }

    @Test
    fun realAlarm_firesReceiver_recordsEvent_andPostsNotification() = runBlocking {
        assertTrue("exact alarms should be allowed after appops grant", scheduler.canScheduleExact())
        val zone = ZoneId.systemDefault()
        // Fire a few seconds from now (seconds are kept, so this is not rounded to the minute).
        val start = LocalDateTime.now(zone).plusSeconds(4)
        val id = container.reminderRepository.save(
            null,
            ReminderInput(title = "Integration test reminder", startAt = start, zoneId = zone.id),
        )
        created += id

        val event = withTimeoutOrNull(30_000) {
            while (true) {
                container.database.reminderEventDao().getForReminder(id).firstOrNull()?.let { return@withTimeoutOrNull it }
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        assertNotNull("alarm did not fire within 30 s", event)

        val reminder = container.reminderRepository.observe(id).first()!!
        // The receiver records the event first, then posts the notification; give it a moment.
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = withTimeoutOrNull(5_000) {
            while (manager.activeNotifications.none {
                    it.id == reminder.requestCode && it.notification.channelId == ReminderNotifier.CHANNEL_REMINDERS
                }
            ) delay(100)
            true
        } ?: false
        assertTrue("notification not posted", posted)
        // One-time reminder is finished and has no alarm left.
        assertEquals(null, reminder.nextTriggerAt)
        assertFalse(scheduler.isScheduled(reminder))
        container.reminderNotifier.cancel(reminder.requestCode)
    }
}
