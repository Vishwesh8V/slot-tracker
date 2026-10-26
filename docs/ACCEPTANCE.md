# Acceptance check (spec §9)

How each acceptance criterion was verified before `v1.0.0`, on an Android 16 emulator sized like a Galaxy S24 (1080×2340, 420 dpi), plus automated tests.

| # | Criterion | Status | How it was checked |
|---|---|---|---|
| 1 | First launch shows the seeded schedule; Monday's widget shows Diffusion Models (morning) and Leetcode / DSA (evening) | ✅ | Fresh install on a Monday: app and widget showed exactly those two tasks. `RepositoryTest.firstLaunchShowsSeededMonday`, `DayBuilderTest` (whole seeded week and colours) |
| 2 | Ticking on the widget updates the widget instantly and the app's Today screen, and survives app restart | ✅ | Ticked on the widget: box, strikethrough and "2/2 done" updated at once; Today matched; still ticked after force-stop and relaunch. `RepositoryTest.tickPersistsAndUntickClears` |
| 3 | At 06:00 (or the configured time), yesterday's tasks appear in History as Done/Missed and the widget shows the new day | ✅ | Moved the device clock to Tue 06:00: the widget switched to Tuesday by itself, and History showed Monday's entries as Done (with time) or Missed. `RepositoryTest.rolloverWritesHistoryAndShowsTheNewDay`, `RolloverPlannerTest` (catch-up across missed days) |
| 4 | A day with three or more slots and a slot with three items render correctly on small, medium and large widget sizes | ✅ | A four-slot override (one slot with three items, one crossing midnight) checked at 2×2 (compact, time dividers), 4×2 (full, scrolls) and 4×3 (details shown) |
| 5 | Override for a single date changes only that date | ✅ | `RepositoryTest.overrideForTodayKeepsTicksAndOnlyChangesThatDate` (next Monday unchanged; reset restores the template; ticks kept) |
| 6 | Per-item and overall streaks match §7, including after editing a past history entry | ✅ | `StreaksTest` (spec's Roman Art example, unscheduled days, today's tasks, two slots on one day, edited entry), `StatsTest` |
| 7 | Reminders fire at slot start when enabled and never when disabled | ✅ | With reminders on, an exact alarm was set for the next slot (23:00) and the notification "Night · 23:00–01:00 / Game Development" was posted at 23:00; the next alarm was then set for 10:00. Turning reminders off cancels the alarm, and the receiver also checks the setting before posting |
| 8 | Export, then wipe app data, then import restores everything exactly | ✅ | `BackupTest.exportWipeImportRestoresEverything` (every table and both settings identical after import into an empty database); invalid and inconsistent files are rejected |
| 9 | GitHub Actions produces a signed APK that installs and updates in place on a Galaxy S24 | ✅ CI, ⏳ phone | CI builds and verifies the signature (`apksigner verify`) with the repo's keystore, and every build has a higher version code. Installing on the S24 itself is for the owner to confirm |
