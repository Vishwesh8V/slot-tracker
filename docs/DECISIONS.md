# Decisions

Choices made where [SPEC.md](../SPEC.md) was silent or ambiguous. User-confirmed decisions are marked ✅.

## Project

| Topic | Decision |
|---|---|
| Application ID | `com.vishwesh.slottracker` ✅ |
| GitHub repo | `slot-tracker`, public ✅ (so the phone can download Release APKs without logging in) |
| Signing keystore | The user generates it and adds the GitHub secrets; the README explains the steps ✅ |
| versionName | From the tag on tag builds (`v1.2.0` → `1.2.0`); `0.0.0-dev.<run number>` on pushes to `main` ✅ |
| versionCode | CI run number (per spec) |
| DI | Manual DI: a single `AppContainer` owned by the `Application` (the spec leaves this to Claude Code) |
| SDK levels | minSdk 31, compileSdk/targetSdk 37 (latest stable at build time) |
| Android auto-backup | Off (`allowBackup="false"`): the app is offline with no accounts, and backup is the in-app JSON export (§9). *Pending user confirmation.* |
| Spec PDF | Not committed (git-ignored); `SPEC.md` is its transcription. |

## Behaviour

| Topic | Decision |
|---|---|
| Template edits vs today | §9 wins over §3: template edits **always** apply to today. Ticks/notes are kept for (slot, item) pairs that still exist and dropped for the rest ✅ |
| Task identity ("same task") | A task is (slot identity, item). Each slot has a stable identity, so editing a slot's label or time keeps its ticks; deleting the slot drops them. An override slot copied from a template slot remembers its source template slot, so ticks carry over when an override is created for today ✅ |
| Clock moves backwards | If the effective date falls before the live day (westward time-zone change or clock set back), the app keeps showing the live day until local time catches up. Days are never logged twice ✅ |

## Data-model refinements (implementation detail)

- `OverrideDay(date)` marks that a date has an override. This lets an override legitimately contain **zero** slots ("nothing today") without falling back to the template.
- `OverrideSlot.sourceTemplateSlotId` (nullable) implements the task-identity rule above.
- `TaskState.slotKey` is `"t:<templateSlotId>"` for template slots and override slots copied from one, and `"o:<overrideSlotId>"` for slots created inside an override.
- Today's task list is always *derived* from the current template/override at read time and merged with `TaskState` rows. A `TaskState` row whose (slotKey, itemId) no longer exists is simply ignored, which drops its tick.
- The live day (the effective date currently shown and not yet logged to history) is stored in Room (`app_meta`) so that the history write and the live-day switch happen in one transaction.
- Times are stored as minutes after midnight; dates as ISO `yyyy-MM-dd` strings.
