package com.anujstha.lifeassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.database.DefaultDataCallback
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

/** In-memory database with the same default-data seeding as production. */
fun inMemoryDatabase(clock: Clock, ids: IdGenerator): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(DefaultDataCallback(clock, ids))
        .build()
