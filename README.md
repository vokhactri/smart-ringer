# Smart Ringer

Native Android ringer scheduling app built with Kotlin, Jetpack Compose, and Material 3. On Android 12 and newer, the UI automatically uses the device's Material You dynamic colors.

## Features

- Create, edit, delete, enable, and disable schedules.
- Start a one-time ringer timer from 1 minute to 24 hours.
- Weekday, weekend, every-day, or individual-day selection.
- Vibrate, Silent, and Do Not Disturb modes.
- Start and end times, including schedules that cross midnight.
- Exact alarms that survive UI closure and normal process death.
- A 15-minute WorkManager recovery check plus rescheduling after reboot, app update, time changes, and timezone changes.
- Active/finished notifications and a Quick Settings tile for the master automation switch.
- Startup permission onboarding for notifications, exact alarms, and ringer policy access.
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

The release workflow derives `versionName` from the tag, builds the minified signed APK, and attaches both the APK and its SHA-256 checksum to a generated GitHub Release.

## Android access

Android requires **Alarms & reminders** access for exact execution on Android 12+ and **Do Not Disturb access** for Silent and Do Not Disturb schedules. Smart Ringer opens the corresponding system settings and never requests internet access.

Closing the activity, swiping it from Recents, backgrounding it, or normal Android process cleanup does not cancel registered alarms. Android deliberately suspends every app's alarms, jobs, and receivers after the user presses **Force stop** in App Info; reopening Smart Ringer reconciles and registers them again.
