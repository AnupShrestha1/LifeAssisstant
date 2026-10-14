# Life Assistant — Architecture & Implementation Plan

Native Android personal-life manager: journal, expenses/income, tasks, reminders,
dashboard, reports, settings, backup/restore. Local-first, offline, no AI features,
no backend or accounts in v1.

## 1. Baseline

| Item | Choice | Reason |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Spec requirement |
| minSdk | 26 (Android 8.0) | Native `java.time`, notification channels, adaptive icons; ~97% device coverage |
| compile / target SDK | 35 | Installed platform; Play-compliant |
| Build | Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, JDK 17 | Known-compatible set |
| Persistence | Room 2.7 (KSP2), DataStore Preferences | Relational data in Room; settings in DataStore |
| Async | Coroutines + Flow | Reactive queries drive the UI |
| Background | AlarmManager (reminder delivery) + WorkManager (maintenance) | See §4 |
| DI | Hand-written `AppContainer` + ViewModel factories | ~8 repositories; Hilt's codegen/setup not justified yet. Migration to Hilt is a contained refactor. |
| Charts | Custom Compose `Canvas` (bar, donut) | Avoids a dependency for simple reports |
| Backup format | Versioned JSON (kotlinx.serialization) | Human-inspectable, documented, versioned |

## 2. Architecture

MVVM with a repository layer, single `:app` module, packages by layer then feature:

```
com.anujstha.lifeassistant
├── data/
│   ├── database/      AppDatabase, converters, migrations
│   ├── entity/        Room entities
│   ├── dao/           DAOs (Flow for observable queries)
│   ├── repository/    Repositories (coordinate DAOs, scheduling side effects)
│   └── preferences/   DataStore-backed settings
├── domain/
│   ├── model/         UI-agnostic models, enums (ThemeMode, Priority, Recurrence...)
│   └── usecase/       Pure logic: recurrence, budget maths, overdue, backup validation
├── ui/
│   ├── dashboard/ journal/ expenses/ tasks/ reminders/ settings/
│   ├── components/    Shared composables (empty states, dialogs, pickers)
│   ├── navigation/    NavHost + bottom bar
│   └── theme/
├── notifications/     Channels, AlarmScheduler, receivers, workers
└── util/              Clock, money formatting, date helpers
```

- Screens render immutable `UiState` from `StateFlow`; send events to the ViewModel.
- Business logic lives in `domain/` as plain Kotlin and takes an injected `Clock`,
  so tests are deterministic.
- **Sync readiness:** every primary table uses UUID string ids plus `createdAt` /
  `updatedAt` (UTC epoch millis). Nothing in UI relies on auto-increment ids.

## 3. Database schema (Room v1, schemas exported to `app/schemas/`)

| Entity | Key columns | Relations / rules |
|---|---|---|
| `journal_entries` | id, title, body, entryDate (`LocalDate`), mood?, createdAt, updatedAt | Index on entryDate |
| `journal_tags` | id, name (unique, `NOCASE`) | |
| `journal_entry_tags` | entryId, tagId (composite PK) | FK CASCADE both sides — deletes links only |
| `expense_categories` | id, name, type (EXPENSE/INCOME), isDefault, archivedAt? | Archived, never hard-deleted while referenced |
| `transactions` | id, amountMinor (`Long`), currencyCode, type, categoryId, date, time?, paymentMethod, note?, createdAt, updatedAt | FK RESTRICT on category; indexes (date), (categoryId, date) |
| `budgets` | id, yearMonth, categoryId? (null = overall), limitMinor, warnPercent | Unique (yearMonth, categoryId) |
| `task_categories` | id, name, archivedAt? | |
| `tasks` | id, title, description?, status, priority, dueDate?, dueTime?, categoryId?, completedAt?, seriesId?, occurrenceDate?, recurrence*, createdAt, updatedAt | FK SET NULL on category; unique (seriesId, occurrenceDate) |
| `reminders` | id, requestCode (unique Int), title, description?, taskId?, triggerAt (UTC), zoneId, recurrence*, enabled, lastFiredAt?, createdAt, updatedAt | FK CASCADE on task (alarms cancelled too) |
| `reminder_events` | id, reminderId, occurrenceAt, firedAt, dismissedAt? | Unique (reminderId, occurrenceAt) — dedup + in-app inbox |

- **Money:** `Long` minor units; `BigDecimal` only at input/display boundaries.
  No `Float`/`Double`. Each transaction stores its currency code.
- **Recurrence:** type (NONE/DAILY/WEEKLY/MONTHLY), interval, optional end date.
  Monthly clamps to month end (Jan 31 → Feb 28/29).
- **Time:** instants stored as UTC millis; date-only values as `LocalDate`
  (ISO string); wall-clock times as `LocalTime`.
- **Migrations:** explicit `Migration` objects + `MigrationTestHelper` tests.
  No `fallbackToDestructiveMigration`.
- **Settings** (DataStore): theme, currency (default NPR), display timezone
  (default Asia/Kathmandu), date/time format, notification prefs, budget warning default.

## 4. Reminder & notification design

1. **Delivery via AlarmManager.** One pending alarm per reminder (its next occurrence).
   `PendingIntent` request code = the reminder's stable `requestCode`, so edit/cancel
   always targets the right alarm.
   - Exact alarms granted (`canScheduleExactAlarms()`) → `setExactAndAllowWhileIdle`.
   - Otherwise → `setAndAllowWhileIdle` (may be deferred by several minutes).
   - Requests `SCHEDULE_EXACT_ALARM` with an in-app explanation; not `USE_EXACT_ALARM`
     (Play restricts it to alarm-clock/calendar apps).
2. **Firing.** `ReminderAlarmReceiver` inserts a `reminder_events` row; a unique-key
   conflict means the occurrence was already handled → no duplicate notification.
   Then posts the notification (if permitted) and schedules the next occurrence.
3. **WorkManager.** `RescheduleWorker` on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
   `TIME_SET`, `TIMEZONE_CHANGED`; a daily periodic worker records missed reminders
   and catches up recurring tasks.
4. **Time zones.** A reminder fires at a UTC instant; recurrence is computed in the
   reminder's stored `zoneId` wall-clock, so "08:00 daily" stays 08:00. Changing the
   *display* timezone preference only changes formatting, never trigger instants.
5. **Permission denied (Android 13+).** Fired reminders still appear in the in-app
   Reminders list and dashboard badge. Permission is requested with an explanation.
6. **Limits stated in-app:** Doze, OEM battery optimisation and force-stop can delay or
   suppress alarms; exact delivery is not guaranteed on every device.

## 5. Recurring tasks

Completing a recurring task inserts the next occurrence as a new row sharing
`seriesId`; the completed row stays as history. The unique
(seriesId, occurrenceDate) index + `INSERT OR IGNORE` prevents double generation.
Generation stops after the end date. Cancelled ≠ completed.

## 6. Privacy

- No `INTERNET` permission. No data leaves the device except user-initiated exports.
- `allowBackup="false"` + data-extraction rules exclude all app data from Google
  cloud backup and device transfer.
- Journal text and money values are never logged.
- Database is **not encrypted** in v1 (Room does not encrypt). SQLCipher with
  Keystore-held keys is a possible later addition.
- Uninstalling the app deletes all data. A lost device's data is only as safe as the
  device lock. Users should keep their own exported backups.
- Backups are plaintext JSON; the app warns before export.

## 7. Backup & restore

- Export/import via Storage Access Framework (`CreateDocument` / `OpenDocument`),
  no storage permissions.
- File: `{ formatVersion, appVersion, exportedAt, settings, journal…, tasks…, reminders… }`.
- Import: parse → validate everything (version, required fields, FK integrity, amounts)
  → show summary → apply in one Room transaction (all-or-nothing).
- Modes: **Replace all** (explicit confirmation) or **Merge** (skip existing ids,
  report skipped count). Alarms are rescheduled after restore.

## 8. Milestones

Each milestone: build, run relevant tests, report what actually ran.

1. ✅ Project setup, Material 3 theme (light/dark/system), adaptive icon, privacy manifest
2. ✅ Navigation shell + DataStore settings (theme, currency, time zone, time format)
3. ✅ Room database, entities, DAOs, repositories, `Clock`; DAO tests
4. Task manager (CRUD, filters, sort, recurrence, overdue)
5. Reminders: AlarmManager, receivers, WorkManager, permissions, dedup
6. Journal: editor, tags, moods, search/filter, calendar view
7. Expenses: categories, transactions, budgets + warnings, CSV export
8. Reports & charts; live dashboard
9. Settings completion, clear-all-data
10. Backup & restore
11. Test pass (unit, DAO, migration, Compose UI), defect fixes
12. Release build, signing, install instructions

**Deferred beyond MVP:** database encryption, app lock/biometrics, home-screen
widgets, attachments, cloud sync.
