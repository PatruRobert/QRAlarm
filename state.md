# QAlarm — App State (as of 2026-05-05)

## What Works

- **Alarm scheduling** — `AlarmManager.setAlarmClock()` fires reliably at the set time. Both one-time and repeat-day alarms schedule correctly.
- **Boot rescheduling** — `AlarmReceiver` listens for `BOOT_COMPLETED` and reschedules all active alarms from storage.
- **Foreground service** — `AlarmService` starts as a media-playback foreground service; shows a persistent notification with full-screen intent.
- **Volume ramp** — Alarm volume ramps from 50 % to 100 % over 30 seconds on service start.
- **Volume enforcement** — After the ramp, a `ContentObserver` prevents the user from lowering alarm volume.
- **Wake lock + show-over-lock-screen** — `AlarmRingActivity` wakes the device, dismisses the keyguard, and holds a partial wake lock.
- **QR code setup** — `QRSetupActivity` captures a QR code via CameraX + ML Kit and stores it on the alarm.
- **QR code dismiss flow** — `QRScanActivity` provides a 30-second window; correct QR stops the service and closes the ring screen. Wrong / timeout restarts the service.
- **Plain dismiss** — Alarms without a QR code show a plain DISMISS button.
- **Back-button guard** — Back press shows a confirmation dialog rather than immediately dismissing.
- **Ringtone pool** — `RingtonePickerActivity` lists MP3s from `Internal Storage/QRAlarm/`, allows multi-select, saves a bundle per alarm. One path is picked randomly at ring time.
- **Service self-restart** — `AlarmService.onTaskRemoved()` schedules a 1-second restart via `AlarmManager` if the app is swiped away.
- **Alarm list UI** — `MainActivity` shows alarms sorted by time with enable/disable toggle, duplicate, delete, and inline QR/ringtone indicators.
- **Alarm edit UI** — `AlarmEditActivity` supports time picker, label, repeat days (including "select all weekdays"), QR setup, and ringtone picker.
- **Pause/resume during QR scan** — `QRScanActivity` sends PAUSE to `AlarmService` on open and the service resumes when the activity finishes.
- **Audio stacking on repeat skips** — intentional: multiple skips make the alarm progressively louder/more annoying.

---

## What Does NOT Work (Bugs)

### Critical

| # | File | Bug |
|---|------|-----|
| C1 | `AlarmScheduler.kt` | No `canScheduleExactAlarms()` check before `setAlarmClock()` — throws `SecurityException` on API 31–32 if permission not granted; silent failure on API 33 if `USE_EXACT_ALARM` isn't auto-granted. |
| C2 | `AlarmService.kt` | `MediaPlayer.prepare()` called on the **main thread** inside `onStartCommand()` — blocks the main thread; ANR risk on slow storage (SD card on TCL tablet). Both the primary player and `buildDefaultPlayer()` share this bug. |
| C3 | `AlarmRingActivity.kt` | `wakeLock` released in `onPause()` but **never re-acquired** in `onResume()`. If the user opens the notification drawer and closes it, the screen can go to sleep while the alarm is still ringing. |
| C4 | `AlarmService.kt` | `ContentObserver` is created at service start but only *registered* after the 30-second ramp. `onDestroy()` unconditionally calls `unregisterContentObserver()` — if the service is destroyed before the ramp completes the observer was never registered, which can cause a crash. |
| C5 | `AlarmService.kt` | `onTaskRemoved()` uses `PendingIntent.FLAG_ONE_SHOT` — the restart `PendingIntent` becomes invalid after the first use; subsequent task-removed events do not restart the service. |
| C6 | `AlarmReceiver.kt` | One-time (non-repeat) alarms are never marked inactive after firing. On device reboot the boot receiver reschedules all `isActive` alarms, so a one-time alarm that already fired will re-trigger. |
| C7 | `RingtonePickerActivity.kt` | `READ_MEDIA_AUDIO` is declared in the manifest but never **requested at runtime**. On Android 13 the file list will be empty and no error is shown; users simply see no ringtones. |

### High

| # | File | Bug |
|---|------|-----|
| H1 | `AlarmRingActivity.kt` | `wakeLock` not released in `onDestroy()` — only released in `onPause()`. If the activity is killed without going through `onPause` the wake lock leaks, preventing the device from sleeping. |
| H2 | `QRScanActivity.kt` | `executor.shutdown()` called in `onDestroy()` instead of `shutdownNow()`. On configuration change (device rotation) threads can linger; over multiple rotations threads accumulate. |
| H3 | `QRSetupActivity.kt` | Same executor shutdown issue as H2. |

---

## What Needs Improvement (Medium / Low)

| # | File | Issue |
|---|------|-------|
| M1 | `AlarmEditActivity.kt` | No visual feedback when the user picks a time in the past for a one-time alarm. The alarm will fire tomorrow but nothing tells the user that. |
| M2 | `AlarmReceiver.kt` | `scheduleNextRepeat()` companion function is dead code — never called anywhere. |
| M3 | `AlarmService.kt` | In the RESUME action, `mediaPlayer?.start()` is called directly; if `prepareAsync()` hasn't completed yet (after fixing C2) this throws `IllegalStateException`. Needs a `prepared` flag. |
| M4 | `AlarmService.kt` | Volume observer is registered only after the full 30-second ramp. During the ramp, the user can freely lower alarm volume (the enforcement doesn't kick in until ramp completes). Registering earlier is blocked by the ramp itself changing volume. Best fix: register immediately but make the observer tolerant of ramp-driven changes. |
| M5 | Multiple | Hardcoded UI strings (`"DISMISS ALARM"`, `"SCAN QR CODE"`, `"Time to wake up!"`, etc.) are not in `strings.xml` — app is not localizable. |
| M6 | Multiple | No dark-mode color variants (`res/values-night/`). On a tablet with system dark mode, the industrial theme may still look fine since it already is dark, but hasn't been tested. |
| M7 | `RingtonePickerActivity.kt` | `MediaPlayer.prepare()` for preview is also on the main thread — same ANR risk as C2 (smaller window, but still worth fixing). |
| M8 | `AlarmScheduler.kt` | No user-facing error if alarm scheduling fails (e.g., permission denied at runtime). |
| L1 | `AlarmEditActivity.kt` | `NumberPicker` time selector is small on a large tablet screen — no minimum size set. |
| L2 | `RingtonePickerActivity.kt` | `Environment.getExternalStorageDirectory()` is deprecated since API 29. Works on API 33 but may break on future versions. MediaStore API is the recommended replacement. |
