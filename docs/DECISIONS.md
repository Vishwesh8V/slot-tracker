# Decisions

Choices made where [SPEC.md](../SPEC.md) was silent or ambiguous. Decisions the user confirmed, or delegated to Claude, are marked ✅.

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
| Android auto-backup | Off (`allowBackup="false"`): the app is offline with no accounts, and backup is the in-app JSON export (§9) ✅ |
| App icon | Left to Claude ✅. Adaptive vector icon: two "slot" pills on a deep-indigo gradient, the first ticked, the next with an amber item dot. Includes a monochrome layer for One UI themed icons |
| Spec PDF | Not committed (git-ignored); `SPEC.md` is its transcription. |

## Behaviour

| Topic | Decision |
|---|---|
| Template edits vs today | §9 wins over §3: template edits **always** apply to today. Ticks/notes are kept for (slot, item) pairs that still exist and dropped for the rest ✅ |
| Task identity ("same task") | A task is (slot identity, item). Each slot has a stable identity, so editing a slot's label or time keeps its ticks; deleting the slot drops them. An override slot copied from a template slot remembers its source template slot, so ticks carry over when an override is created for today ✅ |
| Slot order within a day | Time order along the *effective* day, which starts at the rollover time: with rollover 06:00, a 02:00 slot is after midnight and sorts after a 20:00 slot. Equal start times keep their stored order ✅ |
| Slot reordering (§6.2 vs §5) | Slots always show in time order everywhere. There is no manual slot reordering; you move a slot by changing its time. Items within a slot can be reordered ✅ |
| Empty slots | A slot can't exist without items. The slot editor requires at least one item, and archiving or deleting an item removes any slot it leaves empty; the confirmation lists those slots ✅ |
| Override dates | "Edit a specific date" offers today and future dates only; past days are fixed in History ✅ |
| Copy day to… | Replaces the selected days' slots with copies of the source day's slots and items ✅ |
| Clock moves backwards | If the effective date falls before the live day (westward time-zone change or clock set back), the app keeps showing the live day until local time catches up. Days are never logged twice ✅ |

## Phases 2–6: recommended options (user asked for these without further questions) ✅

Partway through phases 2–3, the user asked Claude to pick the recommended option wherever the spec is silent, without asking. These are those choices.

**Widget and background work**

| Topic | Decision |
|---|---|
| Size breakpoints | Small below 230dp wide; large from 280dp tall. Launchers report different heights per row (a 4×2 is ~215dp on Pixel, taller on One UI), so "large" starts between typical 2-row and 3-row heights |
| Small layout divider | Thin line with the slot's start time; accent-coloured for the current/next slot |
| Refresh timing | One WorkManager job, rescheduled each run, wakes at whichever comes first: the next rollover or the next slot start/end (when the current/next highlight changes). The widget's 30-minute system update is a safety net; every widget update also runs the catch-up |
| Tap just after rollover | A tick sent for a day that has already rolled over is ignored, and the widget redraws |
| Network permission | WorkManager's `ACCESS_NETWORK_STATE` is removed from the manifest (no network, spec §2) |
| Theme changes | The activity handles rotation/dark-mode changes itself, so open editors keep their state |

**App: Today and Schedule**

| Topic | Decision |
|---|---|
| Peeking at other days | Swipe or arrows, with no limit either way; a "Today" button jumps back. Upcoming days are read-only previews; past days show a link to History |
| Opening an override editor | Opening it doesn't create an override; the first change does (copying that date's template) |
| Date picker | A custom month grid that always starts on Monday (the Material picker follows the phone's locale). Dates that already have an override get a dot and are also listed under "Edited dates" in Schedule → Week |
| New slot defaults | 09:00–10:00, no label (a label is required) |
| Times | 24-hour everywhere, as in the spec's examples; dates use the phone's language |
| Deleting an item | No history → deleted after confirmation. With history → a dialog offers "Archive instead" or "Delete permanently" (with its history). Both list where the item is used and which slots would be removed for being left empty |
| Archiving | Removes the item from the template and from overrides dated today or later; past overrides are left as they were |
| Restoring | Puts the item back at the end of the list; it isn't re-added to any slots |
| Notes | A blank note means no note; notes can be removed from the note dialog |

**History and stats**

| Topic | Decision |
|---|---|
| Completion rates | "Last 7/30 days" are the 7/30 closed days before today. Today isn't counted until rollover |
| Streaks and sessions | Include today's ticked tasks (same rule as the streak on Today); today's unticked tasks don't count until rollover |
| Overall completion rate | All-time done ÷ scheduled |
| "All items" view | Overall stats plus a per-item list (current streak, all-time rate); tapping an item filters to it and shows its full stats |
| Filter list | Active items, plus archived items that still have history |
| Marking a Missed entry Done | It has no completion time (shown as just "Done") |
| Today in the calendar | Not coloured; selecting it explains the day is still in progress |

**Reminders, settings, backup**

| Topic | Decision |
|---|---|
| Exact alarms | `USE_EXACT_ALARM` (granted on install, Android 13+; fine for a sideloaded app), plus `SCHEDULE_EXACT_ALARM` for Android 12. Falls back to an inexact alarm if exact alarms are ever unavailable |
| Scheduling | One alarm at a time, for the next slot start (looking up to 8 days ahead); when it fires, the notification is posted and the next start is scheduled. Slots that start at the same moment each get a notification |
| Notification permission | Reminders are on by default, so the permission is requested on first launch (Android 13+), and again when reminders are switched on. Settings shows "Notifications are blocked" with a shortcut if they're denied |
| Backup format | JSON written with Android's built-in `org.json` (no extra dependency). Ids are kept, so an import restores the database exactly; after import the normal catch-up runs |
| Import validation | Must be a Slot Tracker file of a known schema version, parse cleanly, and be internally consistent (no assignment pointing at a missing slot or item) |
| Samsung battery tip | Shown on Today only on Samsung phones, until dismissed or until the app's battery usage is Unrestricted. "Open App info" goes straight to the app's system page |
| Android 12+ backup | `dataExtractionRules` exclude everything from cloud backup and device transfer, matching `allowBackup="false"` |

## Visual design

Left to Claude, with the brief "sleek and aesthetic" ✅. Direction for the widget and app (phases 2–3):

- Calm, minimal surfaces: near-white in light mode, near-black in dark mode, solid (readable over any wallpaper, spec §5).
- The **item colours carry the colour**. App chrome is neutral with a single indigo accent taken from the icon. No wallpaper-based dynamic colour, so the look is the same everywhere and item colours never clash with it.
- Generous rounding (One UI style), soft tinted slot containers, slim colour bars for items, restrained typography (clear hierarchy, few weights).
- Done tasks fade and strike through rather than disappear.

## Data-model refinements (implementation detail)

- `OverrideDay(date)` marks that a date has an override. This lets an override legitimately contain **zero** slots ("nothing today") without falling back to the template.
- `OverrideSlot.sourceTemplateSlotId` (nullable) implements the task-identity rule above.
- `TaskState.slotKey` is `"t:<templateSlotId>"` for template slots and override slots copied from one, and `"o:<overrideSlotId>"` for slots created inside an override.
- Today's task list is always *derived* from the current template/override at read time and merged with `TaskState` rows. A `TaskState` row whose (slotKey, itemId) no longer exists is simply ignored, which drops its tick.
- The live day (the effective date currently shown and not yet logged to history) is stored in Room (`app_meta`) so that the history write and the live-day switch happen in one transaction.
- Times are stored as minutes after midnight; dates as ISO `yyyy-MM-dd` strings.
