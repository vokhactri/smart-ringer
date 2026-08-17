# Smart Ringer

Native Android ringer scheduling app built with Kotlin, Jetpack Compose, and Material 3. On Android 12 and newer, the UI automatically uses the device's Material You dynamic colors.

## Features

- Create, edit, delete, enable, and disable schedules.
- Start a one-time ringer timer from 1 minute to 24 hours.
- Weekday, weekend, every-day, or individual-day selection.
- Vibrate, Silent, and Do Not Disturb modes.
- Start and end times, including schedules that cross midnight.
- Exact alarms that survive UI closure and normal process death.
- A 15-minute WorkManager recovery check plus rescheduling after reboot, app update, time changes, timezone changes, and exact alarm access being granted.
- Active, finished, and permission-error notifications, plus a Quick Settings tile for the master automation switch.
- Startup permission onboarding for notifications, exact alarms, and ringer policy access.
- Settings rows for battery optimization and unused-app hibernation, the two system switches that silently kill schedulers.
- A manual ringer change during a run takes over from the schedule.
- 12-hour or 24-hour time display setting.
- Gesture navigation, edge-to-edge layout, and predictive back animation.
- Light and dark themes following the system setting.

## Build

Install JDK 17 and Android SDK 35, set `sdk.dir` in `local.properties`, then run:

```sh
./gradlew test lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## CI and releases

Every push or pull request to `main` runs tests, Android Lint, and a debug build. To publish a signed APK on GitHub Releases, create and push a version tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release workflow derives both `versionName` and `versionCode` from the tag (`v1.1.6` becomes `1.1.6` and `10106`), builds the minified signed APK, and attaches both the APK and its SHA-256 checksum to a generated GitHub Release.

`versionCode` is what Android compares when installing over an existing build, and it refuses anything that is not higher than what is installed. Deriving it from the tag keeps it rising with the version, so releases must stay tagged as `vMAJOR.MINOR.PATCH` with each component under 100.

## Android access

Android requires **Alarms & reminders** access for exact execution on Android 12+ and **Do Not Disturb access** for Silent and Do Not Disturb schedules. Smart Ringer opens the corresponding system settings and never requests internet access.

A plain Vibrate schedule never needs ringer policy access, so it is only asked for once a schedule actually uses Silent or Do Not Disturb. When it is missing, that schedule cannot be applied — or its ringer restored at the end of the run — and Smart Ringer posts a notification saying so rather than failing silently.

Settings also shows a **Battery optimization** row that raises the system's one-tap "run in background" dialog (via `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which keeps the app off the Play Store but fine on GitHub Releases). Stock Android does not need the exemption — exact alarms fire through Doze — but aggressive OEM battery management is the usual reason schedules stop firing, and this flag is what those OEMs key off.

On Android 11+ there is a **Manage unused apps** row as well. If that system toggle stays on, Android hibernates an app that goes unopened for a few months — and hibernation force-stops it, suspending every registered alarm until the app is opened again. For a scheduler that is meant to be set and forgotten, turning the toggle off is the difference between schedules that keep firing and schedules that quietly die; the row links to the App Info screen where it lives.

Closing the activity, swiping it from Recents, backgrounding it, or normal Android process cleanup does not cancel registered alarms. Android deliberately suspends every app's alarms, jobs, and receivers after the user presses **Force stop** in App Info; reopening Smart Ringer reconciles and registers them again.

## Manual ringer changes

Changing the ringer by hand while a schedule or timer is running is treated as taking over, not as a state to correct. Smart Ringer stops rewriting the ringer for the rest of that run, dismisses the active notification, and skips restoring the previous mode when the run ends.

This is scoped to the single run. The same schedule applies again at its next occurrence, so silencing your phone during today's meeting block does not disable tomorrow's.
