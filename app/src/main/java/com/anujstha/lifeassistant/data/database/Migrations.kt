package com.anujstha.lifeassistant.data.database

import androidx.room.migration.Migration

/**
 * Every schema migration, in order. To change the schema:
 * 1. Bump [AppDatabase.VERSION].
 * 2. Add a `Migration(n, n + 1)` here with the exact SQL.
 * 3. Build once so Room exports `app/schemas/.../n+1.json`, and commit it.
 * 4. Add a case to `MigrationTest` that migrates from n and checks the data survived.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
