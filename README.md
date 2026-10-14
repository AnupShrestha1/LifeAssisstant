# Life Assistant

A native Android app for personal life management — journal, expense & income
tracking, tasks, reminders, and a daily dashboard. Local-first and offline: all data
stays on the device. No AI features, no accounts, no backend.

Built with Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow, WorkManager
and AlarmManager. See [docs/PLAN.md](docs/PLAN.md) for architecture, database schema,
notification design and milestones.

## Status

Milestone 10 of 12 — all features complete: tasks, reminders, journal, expenses, dashboard, reports, settings, and backup/restore. Remaining: final test pass and release build.

## Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35
- Android 8.0 (API 26) or newer device/emulator

## Build

Create `local.properties` in the project root pointing to your SDK
(Android Studio does this automatically):

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

Then:

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented/Compose UI tests (device or emulator required)
./gradlew installDebug           # install on a connected device
```

## Reminders and notifications

- Reminders use Android's `AlarmManager`. With **Alarms & reminders** permission (Android 12+,
  Settings → Apps → Life Assistant) they fire on time; without it Android may delay them by
  a few minutes. The app shows a banner explaining this.
- On Android 13+ the app asks for notification permission when you save a reminder. If you
  decline, fired reminders still appear on the in-app Reminders screen.
- Alarms are re-registered after reboot, app update, clock or time-zone changes, and each
  time the app starts. Reminders that came due while the phone was off are shown once as
  "missed".
- Exact delivery is **not guaranteed** on every device: some manufacturers' battery savers
  can still delay or block alarms. If reminders are late, exclude the app from battery
  optimization in system settings.

## Privacy

- No internet permission; nothing is transmitted off the device.
- Android cloud auto-backup is disabled for this app's data.
- The local database is not separately encrypted; it relies on Android's app sandbox and
  device encryption (use a screen lock).
- Uninstalling the app deletes all of its data. Nothing is stored online, so a lost or reset
  phone can only be restored from backups you exported yourself.
- Settings → Your data → Clear all data deletes everything after typed confirmation.
- Settings → Your data → Backup and restore saves everything to a JSON file you choose and
  restores it (merge or replace). Backup files are not encrypted. Format: [docs/BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md).
