# Smart Ringer

Native Android ringer scheduling app built with Kotlin, Jetpack Compose, and Material 3. On Android 12 and newer, the UI automatically uses the device's Material You dynamic colors.

## Features

- Create, edit, delete, enable, and disable schedules.
- Weekday, weekend, every-day, or individual-day selection.
- Vibrate, Silent, and Do Not Disturb modes.
- Start and end times, including schedules that cross midnight.
- Rescheduling after reboot, app update, manual time changes, and timezone changes.
- Exact-alarm and ringer-policy permission guidance.
- Light and dark themes following the system setting.

## Build

Install JDK 17 and Android SDK 35, set `sdk.dir` in `local.properties`, then run:

```sh
./gradlew test lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Android access

Android requires **Alarms & reminders** access for exact execution on Android 12+ and **Do Not Disturb access** for Silent and Do Not Disturb schedules. Smart Ringer opens the corresponding system settings and never requests internet access.
