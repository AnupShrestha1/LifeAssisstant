# Backup file format (version 1)

Life Assistant backups are single UTF-8 JSON files, created and opened through Android's
system file picker (no storage permission). They are **not encrypted**.

## Top level

| Field | Type | Notes |
|---|---|---|
| `format` | string | Always `"life-assistant-backup"`. Files without it are rejected. |
| `formatVersion` | int | `1`. A higher version is rejected ("made by a newer version of the app"). |
| `appVersion` | string | App version that wrote the file (informational). |
| `exportedAt` | string | ISO-8601 UTC instant. |
| `settings` | object? | `themeMode`, `currencyCode`, `displayZoneId`, `timeFormat`. Applied only by **Replace all**. |
| `journalTags`, `journalEntries`, `journalEntryTags` | arrays | Journal data and tag links. |
| `expenseCategories`, `transactions`, `budgets` | arrays | Finance data. |
| `taskCategories`, `tasks` | arrays | Tasks, including recurring-series fields. |
| `reminders` | array | Reminder definitions (not their schedule state). |

Unknown fields are ignored, so additive changes don't need a version bump. Removing or changing
the meaning of a field does.

## Value conventions

- **ids:** the app's UUID strings; references between records use them.
- **Dates:** `yyyy-MM-dd`. **Times:** `HH:mm[:ss]`. **Wall-clock date-times** (reminder start):
  `yyyy-MM-ddTHH:mm[:ss]` interpreted in the record's `zoneId`. **Instants:** ISO-8601 UTC.
  **Months:** `yyyy-MM`.
- **Money:** `amountMinor` / `limitMinor` are integers in the currency's minor unit (paisa for NPR),
  with an ISO 4217 `currencyCode`. No floating point.
- **Enums:** constant names, e.g. `"EXPENSE"`, `"HIGH"`, `"MONTHLY"`, `"HAPPY"`.
- **Recurrence:** `{ "frequency": "DAILY|WEEKLY|MONTHLY", "interval": 1, "endDate": "yyyy-MM-dd"? }`.

## Not included (by design)

- Which reminders have already fired (device-specific notification history).
- Reminder alarm request codes and next trigger times: recomputed from the current time on
  restore. A one-time reminder whose time has passed is restored switched off.

## Validation before restoring

The whole file is parsed and checked before any data changes: format and version, every field's
type and value (dates, enums, positive amounts, valid currencies and time zones, text lengths),
duplicate ids, duplicate names (tags, categories), and every reference between records
(transaction → category of the same type, budget → expense category, task → task category,
reminder → task, tag link → entry and tag). Any problem rejects the file; up to 20 problems are
listed with their location (e.g. `transactions[3].categoryId`).

## Restore modes

- **Merge** (default): adds records whose id isn't on the device. Existing records are never
  modified. Categories and tags with the same name (ignoring case) are reused instead of
  duplicated, and references are remapped to them. A budget for a month/category that already
  has one is skipped. Current settings are kept.
- **Replace all:** deletes all current data, restores the file exactly, and applies its settings.
  Requires typing `REPLACE`.

Both modes run in a single database transaction: if anything fails, nothing changes. Alarms are
updated only after the transaction commits.
