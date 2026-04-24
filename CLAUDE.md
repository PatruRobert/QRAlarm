# QRAlarm — Android Alarm App

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Clean build
./gradlew clean
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Project Overview
A native Android alarm app built in Kotlin. The core differentiator is QR code dismissal — the alarm cannot be stopped without scanning a specific QR code pre-configured per alarm and placed in another room, forcing the user to physically get out of bed.

## Package & Structure
- Package name: `com.robert.qalarm`
- Language: Kotlin
- Min SDK: 26, Target SDK: 36, compileSdk: 36
- Architecture: Single-module, activity-based — no Jetpack Compose, no MVVM, no Room
- All screens built programmatically (no XML layout files for activity content)
- Layout changes require editing Kotlin code, not XML

## Data Model (Alarm.kt)
```kotlin
data class Alarm(
    val id: Int,
    val label: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<String>, // "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    val isActive: Boolean = true,
    val ringtonePath: String? = null, // randomly picked path for current ring
    val qrCode: String? = null, // null = no QR required, plain dismiss button shown
    val ringtonePaths: Set<String> = emptySet() // full pool of ringtones for this alarm
)
```

## Data Flow

Activities → AlarmStorage (SharedPrefs/JSON) → AlarmScheduler (AlarmManager)
↓
AlarmReceiver (BroadcastReceiver)
↓
AlarmService (ForegroundService) + AlarmRingActivity

## Persistence
`AlarmStorage.kt` is a singleton `object` that serializes the alarm list as a JSON string in SharedPreferences under key `"alarms_list"` (prefs file: `"QRAlarmPrefs"`). Serialization is manual `JSONArray`/`JSONObject` — no Gson/Moshi.

## Alarm Lifecycle
1. `AlarmScheduler.schedule()` uses `AlarmManager.setAlarmClock()` with a PendingIntent pointing to `AlarmReceiver`. The alarm `id` is the PendingIntent request code. Extras passed: `alarm_id`, `ringtone_path`, `qr_code`.
2. `AlarmReceiver.onReceive()` fires at alarm time: starts `AlarmService` (foreground) and launches `AlarmRingActivity` (full-screen over lock screen).
3. On `ACTION_BOOT_COMPLETED`, `AlarmReceiver` reschedules all active alarms from `AlarmStorage`.
4. For repeating alarms, `AlarmReceiver` reads `repeatDays` from `AlarmStorage` and calls `AlarmScheduler.schedule()` for the next occurrence.

## AlarmService
Foreground service (`foregroundServiceType=mediaPlayback`). Key behaviors:
- Ramps volume from 50% to 100% over 6 seconds on start.
- `ContentObserver` prevents user from lowering volume while ringing (starts after ramp completes to prevent audio choking).
- Holds a `PARTIAL_WAKE_LOCK` (10 min).
- Responds to `PAUSE` / `RESUME` actions sent by `QRScanActivity` while camera is open.
- `onTaskRemoved()` reschedules a restart via `AlarmManager` if swiped away.
- Falls back to system default ringtone if no custom ringtone is configured.

## AlarmRingActivity
- `launchMode="singleTop"` — prevents stacking multiple instances.
- `onNewIntent()` handles being brought back to front and restarts service.
- WakeLock released in `onPause` not `onDestroy` to prevent under-lock crash.
- Shows SCAN QR button if `qrCode` is not null, otherwise shows plain DISMISS button.
- Back button shows confirmation dialog rather than immediately dismissing.

## QR Code Flow
- `QRSetupActivity` — captures QR during alarm editing via CameraX + ML Kit, stores string on `Alarm` object.
- `QRScanActivity` — launched from `AlarmRingActivity`. Has 30-second countdown; on correct QR match stops service and resets navigation to Home. On timeout restarts `AlarmService` and `AlarmRingActivity`.
- Multiple skips cause audio to stack intentionally — makes alarm progressively more annoying.

## Ringtones
Custom MP3 files stored at `Internal Storage/QRAlarm/`. `RingtonePickerActivity` scans that directory, allows multi-select with preview, saves `ringtonePaths` per alarm. One path chosen randomly at ring time.

## UI Theme
- Dark industrial: bg_primary `#0C0C0C`, accent `#00D4AA` (teal), danger `#FF4444`
- Drawables: `bg_card`, `bg_input`, `bg_button_accent`, `bg_button_danger`, `bg_button_ghost`
- Programmatic views use `resources.displayMetrics.density` for responsive dp conversion — no hardcoded pixel values

## Key Files

| File | Role |
|------|------|
| `Alarm.kt` | Data class — core model |
| `AlarmStorage.kt` | All SharedPreferences read/write |
| `AlarmScheduler.kt` | Wraps AlarmManager scheduling/cancellation |
| `AlarmReceiver.kt` | Triggered by AlarmManager and boot |
| `AlarmService.kt` | Foreground service — audio, volume ramp/enforcement |
| `MainActivity.kt` | Alarm list, FAB, per-alarm menu (duplicate/delete) |
| `AlarmEditActivity.kt` | Create/edit alarm — time picker, days, QR, ringtones |
| `AlarmRingActivity.kt` | Full-screen alarm UI over lock screen |
| `QRSetupActivity.kt` | Capture QR code during alarm edit |
| `QRScanActivity.kt` | Verify QR code to dismiss ringing alarm |
| `RingtonePickerActivity.kt` | Multi-select MP3 ringtones per alarm |

## Dependencies (key)
- ML Kit Barcode Scanning: `com.google.mlkit:barcode-scanning`
- CameraX: `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`
- Material Components: for FloatingActionButton