# Slot Tracker — Android App Build Spec

Oct 26, 2026 · @Vishwesh

> Transcribed from `Slot Tracker — Android App Build Spec.pdf`. This file is the source of truth.
> Decisions made with the user where the spec was silent or ambiguous are recorded in
> [`docs/DECISIONS.md`](docs/DECISIONS.md).

## 1. Overview and how to use this doc

Build a native Android app, **Slot Tracker**, whose main surface is a resizable home-screen widget showing today's scheduled tasks per time slot, with checkboxes that can be ticked directly on the widget. Target device: Samsung Galaxy S24 (Android 14+, One UI). Single user, offline, no accounts.

**Instructions for Claude Code:**

- Treat this doc as the source of truth. Save it in the repo as `SPEC.md`.
- Do not invent features beyond this spec. If something is ambiguous, ask the user before deciding.
- Build in the phases listed in section 9; stop at the end of each phase, summarize, and wait for the user's go-ahead.
- Everything the user configures (slots, items, times, assignments) must be editable in-app. Nothing about the schedule is hard-coded except the first-launch seed data in section 4.

**User's goal:** two free slots a day (by default) for personal projects. The app removes daily decision-making: open the phone, see what today's slots are for, do them, tick them off, and build streaks.

## 2. Tech stack, repo and build

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI (app) | Jetpack Compose, Material 3 |
| Widget | Jetpack Glance (`GlanceAppWidget`) with interactive checkboxes via `actionRunCallback` |
| Storage | Room (SQLite) for all schedule + history data; DataStore (Preferences) for settings |
| Background work | WorkManager for widget day-rollover refresh; `AlarmManager` exact alarms for slot reminders |
| DI | Hilt (or manual DI if simpler; Claude Code's call, keep it lightweight) |
| Min / target SDK | minSdk 31 (Android 12, needed for native widget checkboxes), targetSdk = latest stable |
| Distribution | Sideloaded APK, no Play Store |

**Repo and CI:**

- Create a GitHub repo (user will provide or authorize the name). Standard Gradle (Kotlin DSL) Android project.
- Add a GitHub Actions workflow that, on every push to `main` and on tags `v*`, builds a release APK and uploads it as a workflow artifact; on tags, also attaches it to a GitHub Release.
- Sign the release APK with a keystore stored as GitHub Actions secrets (base64 keystore + passwords). Write a short `README.md` section explaining how to generate the keystore and add the secrets, since the same signing key is required for in-place updates on the phone.
- Version code increments automatically from the CI run number.
- User installs by downloading the APK from the Release page on the phone and allowing "install unknown apps" for the browser once.

Keep dependencies minimal. No analytics, no network permission.

## 3. Core concepts and data model

The schedule is a **weekly template** (Mon–Sun) that repeats every week, plus optional **date overrides** for one-off changes. Each day in the template has its own list of slots, so days can have different numbers of slots and different timings.

**Concepts:**

- **Item**: a thing you work on (e.g. "Diffusion Models"). Has a name, a user-picked colour, and optional details text.
- **Slot**: a time window on a given weekday (e.g. Mon 10:00–13:00, label "Morning"). A day can have 0..n slots.
- **Assignment**: an item placed in a slot. A slot can hold 1..n items; each item is its own tickable task.
- **Task (occurrence)**: an assignment on a specific calendar date. This is what appears on the widget and gets ticked.
- **Override**: a replacement schedule for one specific date. If a date has an override, that date uses only the override's slots and assignments, not the template.
- **History entry**: a permanent record of each task occurrence's outcome (Done or Missed).

**Room entities (suggested; Claude Code may refine names):**

| Entity | Fields |
|---|---|
| `Item` | id, name, colorArgb, details (nullable text), sortOrder, archived (bool) |
| `TemplateSlot` | id, dayOfWeek (1=Mon..7=Sun), label, startTime, endTime, sortOrder |
| `TemplateAssignment` | id, templateSlotId, itemId, sortOrder |
| `OverrideSlot` | id, date, label, startTime, endTime, sortOrder |
| `OverrideAssignment` | id, overrideSlotId, itemId, sortOrder |
| `TaskState` | id, date, slotKey, itemId, done (bool), completedAt (nullable), note (nullable) — live state for the current day |
| `HistoryEntry` | id, date, itemId, itemNameSnapshot, itemColorSnapshot, slotLabelSnapshot, slotStart, slotEnd, status (DONE / MISSED), completedAt (nullable), note (nullable) |

**Rules:**

- History stores snapshots so that later edits to items or the template never rewrite the past.
- Deleting an item that has history **archives** it instead (hidden from pickers, kept in history and stats). Offer a separate "delete permanently including history" option with a confirmation.
- Editing the template affects today (if the day's tasks are untouched) and all future days; it never changes existing history.

## 4. Default seed configuration (first launch only)

On first launch, seed the database with the following. All of it is editable afterwards; nothing here is hard-coded into logic.

**Items (default colours; user can change):**

| Item | Default colour |
|---|---|
| Diffusion Models | Indigo `#5C6BC0` |
| Game Development | Green `#43A047` |
| Leetcode / DSA | Amber `#FFB300` |
| Math Puzzle | Teal `#00897B` |
| Roman Art | Terracotta `#D84315` |
| Language Study | Purple `#8E24AA` |

**Slots:** every day has two slots: **Morning 10:00–13:00** and **Evening 20:00–22:00**.

**Weekly template:**

| Day | Morning (10:00–13:00) | Evening (20:00–22:00) |
|---|---|---|
| Mon | Diffusion Models | Leetcode / DSA |
| Tue | Game Development | Roman Art, Language Study |
| Wed | Diffusion Models | Leetcode / DSA, Math Puzzle |
| Thu | Game Development | Roman Art, Language Study |
| Fri | Diffusion Models | Leetcode / DSA, Math Puzzle |
| Sat | Game Development | Leetcode / DSA |
| Sun | Diffusion Models | Leetcode / DSA |

Week starts on **Monday** everywhere in the UI (template editor, history, stats).

## 5. Widget

The widget shows **all of today's slots and their tasks**, and tasks are ticked directly on the widget without opening the app.

**Layout (top to bottom):**

1. Header row: day + date (e.g. "Mon, 26 Oct") and a small progress count (e.g. "1/3 done"). Tapping the header opens the app's Today screen.
2. One group per slot, in time order. Each group has a slot header showing label and time range ("Morning · 10:00–13:00").
3. Under each slot header, one row per task: checkbox, item name, and a colour indicator in the item's colour.

**Grouping vs item colour:** the user wants each item in its own colour and also a visual cue that tasks share a slot. Do both: tasks in the same slot sit inside one rounded container (subtle background tint) under a shared slot header; each task row carries a coloured left bar or dot in its item colour. The current or next upcoming slot gets a slightly stronger highlight.

**Behaviour:**

- Tapping a checkbox toggles Done immediately and updates the widget, app, and progress count. Unticking on the same day is allowed.
- Done tasks stay visible with a ticked box and strikethrough or reduced opacity (not hidden), so the day's progress is visible.
- Tapping a task's name opens the app on Today, scrolled to that task, where a note can be added.
- A day with no slots shows a short "Nothing scheduled today" message.
- The widget's "day" switches at the **day rollover time** (setting, default 06:00), not at midnight. Before rollover, the widget still shows the previous day.

**Size:** resizable horizontally and vertically, using Glance `SizeMode.Responsive` with at least these breakpoints:

- Small (about 2×2): header + compact list of task names with checkboxes, slot headers reduced to a thin coloured divider with the time.
- Medium (about 4×2): full layout; scroll inside the widget if content overflows (`LazyColumn`).
- Large (4×3 and above): full layout with item details text shown as a one-line subtitle under each task.

Match One UI widget conventions: rounded corners, respects light/dark system theme, readable over any wallpaper (solid background following the system theme).

## 6. App screens

Bottom navigation with four tabs: **Today**, **Schedule**, **History**, **Settings**. Items are managed from within Schedule.

### 6.1 Today

- Same content as the widget, larger: slot groups, task rows with checkbox, item colour, item details (if any).
- Each task row has an "add note" action. Notes are optional, short free text (e.g. "finished DDPM section 3"), editable until the day rolls over and later from History.
- Shows the per-item current streak next to each task (e.g. a small "5" with a flame or similar).
- Arrows or swipe to peek at other days (read-only preview of upcoming days; past days link to History).
- An "Edit this day only" action that opens the override editor for today's date.

### 6.2 Schedule (two sub-tabs: Week and Items)

*Week (template editor):*

- Shows Mon–Sun. Each day expands to its list of slots; each slot shows its label, time range and assigned items as coloured chips.
- Add, edit, delete and reorder slots per day. Slot editor: label (free text), start time, end time (time pickers).
- Add or remove items in a slot via a multi-select item picker; reorder items within a slot.
- "Copy day to…" action: copy one day's slots and assignments to other selected days (saves repetitive editing when changing timings).
- Warn (but allow) if two slots on the same day overlap.

*Items:*

- List of active items with colour swatch. Add, rename, reorder, archive, permanently delete (with confirmation).
- Item editor: name, colour (palette of ~16 preset colours plus a custom colour picker), details (optional multi-line text, e.g. "Italian — Duolingo unit 4").
- Archived items shown in a collapsed section; can be restored.

*Overrides (date-specific edits):*

- Reachable from Today ("Edit this day only") and from a date picker in Schedule ("Edit a specific date").
- Opens a slot/item editor pre-filled with that date's template; changes apply to that date only.
- Dates with overrides are marked in the Schedule view and Today screen; an override can be reset back to the template.
- If an override is created for today after some tasks were ticked, keep the ticks for tasks that still exist.

### 6.3 History

- Default view: a month calendar (week starts Monday). Each day cell is coloured by completion: all done / partial / none done / nothing scheduled. Tapping a day shows its tasks with status, completion time and note.
- Past entries can be edited: flip Done/Missed, edit note (in case of forgetting to tick).
- Filter by item.
- **Stats per item:** current streak, best streak, total sessions done, completion rate (done ÷ scheduled) for last 7 days, 30 days, and all time.
- **Overall stats:** current and best overall streak, overall completion rate.

### 6.4 Settings — see section 8.

## 7. Logic: days, ticking, history, streaks

**Effective day.** The app's "today" = the calendar date, shifted by the rollover time. If the current time is before the rollover time (default 06:00), "today" is still the previous calendar date. Example: at 01:30 on Tuesday with rollover 06:00, the widget still shows Monday.

**Building a day's tasks.** For an effective date: if an override exists, use the override's slots and assignments; else use the template for that weekday. Each (slot, item) pair is one task with initial state not done.

**Ticking.** Ticking marks the task Done with `completedAt` = now. Unticking clears it. Both are allowed until rollover.

**Rollover (at the rollover time each day):**

1. For every task of the day that just ended: write a `HistoryEntry` with status DONE (if ticked) or MISSED (if not), including the note and snapshots.
2. Clear live state; build the new day's tasks; refresh the widget.
3. Missed tasks simply disappear from the widget (no carry-over). The item appears again on its next scheduled day as normal.

Rollover must be robust: if the phone was off or the app process was killed, on next app open, widget update or boot, detect any un-rolled days and process them in order (writing MISSED for days that were never opened). Use WorkManager for the scheduled run plus a catch-up check on app start, widget update and `BOOT_COMPLETED`.

**Streaks.**

- **Per-item streak** = number of consecutive *scheduled occurrences* of that item that were Done, counting back from the most recent. Days the item isn't scheduled don't break it. Example: Roman Art is scheduled Tue/Thu; done on both for 3 weeks = streak 6.
- **Best streak** per item = longest such run ever.
- **Overall streak** = consecutive effective days on which every scheduled task was Done. Days with nothing scheduled are skipped (don't break or extend it).
- Today's not-yet-done tasks don't break any streak until rollover.
- Streaks are recomputed from history whenever a past entry is edited.
- If an item appears in two slots on the same day, each occurrence counts separately.

## 8. Settings (exact list — do not add others)

The user wants a small settings page containing only these. Anything else (theme, week start, colours) follows the defaults in this spec.

| Setting | Type | Default | Notes |
|---|---|---|---|
| Day rollover time | Time picker | 06:00 | When the widget and app switch to the next day and the previous day is logged to history |
| Slot reminders | On/off toggle | On | Notification at each slot's start time listing that slot's items |
| Backup: export | Action button | — | Export all data to a JSON file |
| Backup: import | Action button | — | Restore from a JSON export (replaces current data after confirmation) |

Everything about the schedule itself (slots, times, items, colours, details, overrides) is edited in the Schedule screen, not in Settings.

App theme follows the system light/dark mode.

## 9. Notifications, backup, edge cases, acceptance and build phases

**Notifications (when "Slot reminders" is on):**

- At each slot's start time, post one notification: title = slot label + time ("Morning · 10:00–13:00"), body = the slot's item names.
- Tapping it opens Today. **No action buttons**; keep it a plain reminder.
- Use exact alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` as appropriate) and request `POST_NOTIFICATIONS` on first enable. Reschedule all alarms after boot, time change, timezone change, and any schedule/override edit.
- Turning the setting off cancels all scheduled reminders.
- On Samsung, guide the user once (a dismissible tip) to set the app's battery usage to "Unrestricted" so alarms and rollover are reliable.

**Backup:**

- Export writes a single versioned JSON file (schema version field included) containing items, template, overrides, live state, history and settings, via the system file picker (Storage Access Framework). No storage permission.
- Import validates the file, shows a summary (counts of items, history entries), asks for confirmation, then replaces all data and refreshes widget and alarms.

**Edge cases to handle:**

- Slot crossing midnight (e.g. 23:00–01:00) is allowed; it belongs to the day it starts on.
- Editing today's template after some tasks were ticked: keep ticks for (slot, item) pairs that still exist; drop the rest.
- Item archived while it has future assignments: remove it from the template and overrides, after a confirmation listing where it was used.
- Multiple widget instances on the home screen all stay in sync.
- Time zone change: effective day uses the device's current local time.

**Acceptance criteria:**

- [ ] First launch shows the seeded schedule; Monday's widget shows Diffusion Models (morning) and Leetcode / DSA (evening).
- [ ] Ticking on the widget updates the widget instantly and the app's Today screen, and survives app restart.
- [ ] At 06:00 (or the configured time), yesterday's tasks appear in History as Done/Missed and the widget shows the new day.
- [ ] A day with three or more slots and a slot with three items render correctly on small, medium and large widget sizes.
- [ ] Override for a single date changes only that date.
- [ ] Per-item and overall streaks match the rules in section 7, including after editing a past history entry.
- [ ] Reminders fire at slot start when enabled and never when disabled.
- [ ] Export, then wipe app data, then import restores everything exactly.
- [ ] GitHub Actions produces a signed APK that installs and updates in place on a Galaxy S24.

**Build phases (stop after each and wait for the user):**

1. Project setup, GitHub repo, CI that builds a signed APK, data layer with seed data, and unit tests for day/rollover/streak logic.
2. Widget with live ticking and day rollover.
3. App: Today and Schedule (template editor, items, overrides).
4. History, stats and streaks.
5. Notifications, settings, backup export/import, Samsung battery tip.
6. Polish pass against the acceptance criteria, then tag `v1.0.0`.
