# Life Assistant

A native Android app for personal life management: journal, expense and income tracking,
tasks, reminders with real notifications, a daily dashboard and reports. Everything is stored
on the device; the app has no internet access, accounts or backend, and uses no AI.

Version **1.0.0** · Android 8.0 (API 26) and newer · Kotlin, Jetpack Compose (Material 3), Room,
Coroutines/Flow, WorkManager, AlarmManager. Architecture, schema and design decisions:
[docs/PLAN.md](docs/PLAN.md). Backup file format: [docs/BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md).

## Features

- **Home dashboard** — today's tasks (done / pending), overdue tasks, upcoming reminders, this
  month's income and expenses, today's spending, remaining budget, recent journal entries, and
  quick add for entry / expense / task / reminder. Live from the database.
- **Journal** — long-form entries with optional title, mood and tags; search; mood, tag and date
  filters; calendar view; created / edited timestamps; drafts survive the app being closed by Android.
- **Expenses & income** — transactions in integer minor units (no floating point), categories
  (archived instead of deleted when in use), monthly and per-category budgets with warning
  thresholds, today / week / month totals, CSV export. Default currency NPR.
- **Tasks** — priorities, statuses, categories, due date/time, daily / weekly / monthly
  recurrence with end dates, Today / Upcoming / Overdue / All / Completed views, search and sort.
- **Reminders** — Android notifications via AlarmManager, repeating schedules, linked to tasks,
  in-app inbox when notifications are off, rescheduled after reboot, update and time changes.
- **Reports** — spending by category, six-month income vs expenses, task and journal counts.
- **Settings** — theme (light / dark / system), currency, display time zone (default
  Asia/Kathmandu), 12/24-hour time, notification status, categories, budgets, backup and
  restore, CSV export, clear all data, about & privacy.

## Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35 (Android Studio installs these)
- A device or emulator running Android 8.0+; Android 13+ to exercise the notification-permission flow

## Build

Create `local.properties` in the project root (Android Studio does this automatically):

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

Debug build:

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Release build

The release build is shrunk and optimized with R8 and signed with your own key.

1. Create a key once. Keep the file and passwords safe: every future update must be signed with
   the same key.

   ```bash
   keytool -genkeypair -v -keystore life-assistant-release.jks -alias life-assistant -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` in the project root (git-ignored; never commit it):

   ```properties
   storeFile=C:/path/to/life-assistant-release.jks
   storePassword=...
   keyAlias=life-assistant
   keyPassword=...
   ```

3. Build:

   ```bash
   ./gradlew assembleRelease
   ```

Output: `app/build/outputs/apk/release/app-release.apk` (about 2.4 MB). Without
`keystore.properties` the release APK is produced unsigned.

## Install

With USB debugging enabled on the phone:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it (allow installing from that source when asked).

**Switching between debug and release builds:** they are signed with different keys, so Android
won't update one with the other. You must uninstall first, **which deletes the app's data**.
Make a backup first (Settings → Backup and restore → Create backup), uninstall, install the
other build, then restore the backup.

### Recommended phone settings

- Allow notifications when asked.
- Allow **Alarms & reminders** (Settings → Apps → Life Assistant) for on-time reminders.
- On phones with aggressive battery savers (Xiaomi / HyperOS, Samsung, Huawei, …) set the app's
  battery usage to **No restrictions** and, on Xiaomi, enable **Autostart**. Otherwise the system
  may delay or drop reminders. Exact delivery cannot be guaranteed on every device.

## Tests

```bash
./gradlew testDebugUnitTest            # 83 JVM unit tests
./gradlew connectedDebugAndroidTest    # 99 instrumented tests (needs a device or emulator)
./gradlew lintDebug                    # Android lint
```

With more than one device connected, choose one, e.g. `ANDROID_SERIAL=emulator-5554`.
Instrumented tests install and afterwards uninstall the app on that device, so its app data is
lost: run them on an emulator, not your everyday phone.

Coverage includes recurrence and reminder schedule math (DST, gaps, downtime, clock changes),
task status rules and series generation, money parsing and budget calculations, CSV escaping,
backup validation and restore (merge, replace, rollback), Room DAOs, relationships and
migrations, reminder deduplication, a real AlarmManager → receiver → notification round trip,
ViewModel state, and Compose UI flows for every module.

Last verified on an API 37 emulator: all unit and instrumented tests passing, lint clean, and
more than 24,000 random-input (monkey) events across debug and release builds without an app crash.

## Project structure

```
app/src/main/java/com/anujstha/lifeassistant/
├── data/          database, entities, DAOs, repositories, preferences, backup, export
├── domain/        models and pure logic (recurrence, schedules, finance, reports)
├── notifications/ AlarmManager scheduler, receivers, WorkManager rescheduling, notifier
├── ui/            Compose screens and ViewModels per feature, shared components, navigation, theme
└── util/          money, ids, time flows, calendar grid
app/schemas/       exported Room schemas (used by migration tests)
docs/              design plan and backup format
```

## Privacy and your data

- No internet permission: nothing leaves the device unless you export it.
- Excluded from Android cloud backup and device-to-device transfer.
- The database lives in app-private storage and is **not separately encrypted**; it relies on
  Android's app sandbox and device encryption, so use a screen lock.
- Uninstalling deletes all data. A lost or reset phone can only be restored from backups you
  exported. Backup and CSV files are **not encrypted**; keep them private.
- Settings → Clear all data deletes everything after typed confirmation.

## Known limitations

- Single device only; no sync (the data layer uses UUIDs and timestamps so sync could be added).
- Totals are per currency; transactions in another currency are listed but excluded from totals
  (the app says how many).
- Reminder delivery depends on Android and manufacturer battery policies.
- No database encryption, app lock, widgets or attachments in 1.0.
- Tested on Android 13+ (API 37 emulator, Android 16 phone). The Android 8–12 code paths are
  implemented but were not run on a device.
