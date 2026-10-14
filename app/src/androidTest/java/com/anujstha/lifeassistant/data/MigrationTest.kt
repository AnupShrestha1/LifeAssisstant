package com.anujstha.lifeassistant.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.data.database.ALL_MIGRATIONS
import com.anujstha.lifeassistant.data.database.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the exported schema files and the migration chain. When a migration is added,
 * add a test that creates the old version, inserts rows, migrates and checks the data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrateFromVersion1ToLatest_matchesExportedSchema() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *ALL_MIGRATIONS).close()
    }

    @Test
    fun roomOpensMigratedDatabase() {
        helper.createDatabase(dbName, 1).close()
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName,
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            openHelper.writableDatabase // forces open + validation
            close()
        }
    }
}
