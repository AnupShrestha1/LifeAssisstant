package com.anujstha.lifeassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.database.DefaultDataCallback
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import com.anujstha.lifeassistant.util.IdGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests can move forward explicitly. */
class MutableClock(var now: Instant = Instant.parse("2026-10-14T06:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Predictable ids: "id-1", "id-2", ... */
class SequentialIds : IdGenerator {
    private var next = 1
    override fun newId(): String = "id-${next++}"
}

/** Records alarm calls instead of touching AlarmManager. */
class FakeScheduler(var exact: Boolean = true) : ReminderScheduler {
    /** reminderId -> scheduled instant, for every reminder that currently has an alarm. */
    val scheduled = mutableMapOf<String, Instant>()
    val cancelled = mutableListOf<String>()

    override fun schedule(reminder: ReminderEntity, at: Instant) {
        scheduled[reminder.id] = at
    }

    override fun cancel(reminder: ReminderEntity) {
        scheduled.remove(reminder.id)
        cancelled += reminder.id
    }

    override fun canScheduleExact(): Boolean = exact
}

/** In-memory database with the same default-data seeding as production. */
fun inMemoryDatabase(clock: Clock, ids: IdGenerator): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(DefaultDataCallback(clock, ids))
        .build()
