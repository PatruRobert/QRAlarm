# QAlarm — Change Log (2026-05-05)

Full bug-fix pass based on audit of the codebase targeting a TCL Android 13 tablet.

---

## AlarmScheduler.kt

### [C1] Added `canScheduleExactAlarms()` guard before `setAlarmClock()`
- **Problem:** `AlarmManager.setAlarmClock()` throws `SecurityException` on API 31–32 when the user has not granted `SCHEDULE_EXACT_ALARM` in Settings → Special app access. On API 33 `USE_EXACT_ALARM` is auto-granted for alarm apps, but the call was always made unconditionally.
- **Fix:** Added a `Build.VERSION.SDK_INT >= S && !alarmManager.canScheduleExactAlarms()` check. If the permission is absent, the function logs a warning and returns early instead of crashing.
- **Also:** Removed the spurious `@ExperimentalGetImage` annotation (this class does not use Camera APIs). Added `set(Calendar.MILLISECOND, 0)` so the calendar is fully zeroed before comparison.

---

## MainActivity.kt

### [C1 companion] Prompt user to grant exact-alarm permission on first launch
- **Problem:** If `canScheduleExactAlarms()` returns false the scheduler silently does nothing; the user has no idea why alarms aren't firing.
- **Fix:** In `onCreate()`, if `Build.VERSION.SDK_INT >= S` and `canScheduleExactAlarms()` is false, show a `Toast` and navigate to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` so the user can grant the permission immediately.
- **Also:** Removed the redundant `@ExperimentalGetImage` annotation from `MainActivity` (it does not use Camera APIs). Updated `android.os.Build.VERSION_CODES.TIRAMISU` reference to use the imported `Build` class for consistency.

---

## AlarmService.kt

### [C2] Switched `MediaPlayer.prepare()` to `prepareAsync()` — eliminates ANR risk
- **Problem:** `prepare()` performs disk I/O (reading the MP3 header) on whichever thread calls it. `onStartCommand()` runs on the main thread, so a slow SD card or a large file could easily exceed the 5-second ANR threshold. Both the custom-ringtone path and the `buildDefaultPlayer()` fallback shared this bug.
- **Fix:** Replaced the inline `MediaPlayer().apply { ... prepare(); start() }` blocks with a new private `startMediaPlayer(path: String?)` helper that:
  - Calls `prepareAsync()` instead of `prepare()`.
  - Uses `setOnPreparedListener { mp -> mp.start() }` to start playback once the decoder is ready (off the main thread, called back on the main thread by the framework).
  - Uses `setOnErrorListener` to fall through to the system default ringtone on any media error.
  - Tracks readiness via `private var mediaPlayerReady = false` so the RESUME action doesn't call `start()` on an unprepared player.

### [C4] Fixed `ContentObserver` unregister crash in `onDestroy()`
- **Problem:** The volume `ContentObserver` is created in `onStartCommand()` but only *registered* inside the volume-ramp `Runnable` after the 30-second ramp completes. If the service was destroyed before the ramp finished, `onDestroy()` called `unregisterContentObserver()` on an observer that was never registered, which can crash on some Android versions.
- **Fix:** Added `private var volumeObserverRegistered = false`. The flag is set to `true` only when `registerContentObserver()` is called. `onDestroy()` now only unregisters if `volumeObserverRegistered` is true.

### [C5] Fixed `onTaskRemoved()` using `FLAG_ONE_SHOT`
- **Problem:** `PendingIntent.FLAG_ONE_SHOT` makes the `PendingIntent` invalid after its first use. The service could only self-restart once after being swiped away; subsequent task-removed events silently failed to schedule the restart.
- **Fix:** Changed `FLAG_ONE_SHOT` to `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` so the `PendingIntent` remains reusable across multiple task-removed events.

### [M3] RESUME action guarded against unprepared player
- **Problem:** The RESUME action called `mediaPlayer?.start()` directly. After switching to `prepareAsync()` this would throw `IllegalStateException` if the player hadn't finished preparing yet.
- **Fix:** The RESUME handler now checks `mediaPlayerReady` before calling `start()`. If the player is still preparing, `setOnPreparedListener` will call `start()` automatically when ready.

### General cleanup
- Extracted duplicated `MediaPlayer` construction into `startMediaPlayer(path)`.
- Guarded `mediaPlayer?.stop()` in `onDestroy()` with a try-catch (stop on a non-started player throws `IllegalStateException`).
- Guarded `wakeLock?.release()` with `isHeld` check in `onDestroy()`.

---

## AlarmRingActivity.kt

### [C3] Re-acquire `wakeLock` in `onResume()`
- **Problem:** The wake lock was acquired in `onCreate()` and released in `onPause()`. If the user opened the notification drawer and closed it (triggering `onPause` → `onResume`), the wake lock was never re-acquired. The screen could then go to sleep while the alarm was still ringing.
- **Fix:** Added a `wakeLock?.isHeld != true` check in `onResume()` that re-acquires the lock (10-minute timeout) when it's not held.

### [H1] Release `wakeLock` in `onDestroy()`
- **Problem:** `onDestroy()` was an empty override. If the activity was killed without going through `onPause()` (e.g. process death, force-close) the wake lock would leak, preventing the device from entering deep sleep and draining the battery.
- **Fix:** `onDestroy()` now releases the wake lock if it's still held.

---

## AlarmReceiver.kt

### [C6] One-time alarms deactivated after firing
- **Problem:** After a one-time (non-repeat) alarm fired, it was left with `isActive = true` in `AlarmStorage`. On the next device reboot, `ACTION_BOOT_COMPLETED` reschedules all active alarms, causing a one-time alarm to fire again.
- **Fix:** After an alarm fires, the receiver now checks `alarm.repeatDays.isEmpty()`. For one-time alarms, it calls `AlarmStorage.updateAlarm(context, alarm.copy(isActive = false))` to mark it inactive, so the boot receiver skips it.

### [M2] Removed dead `scheduleNextRepeat()` companion function
- **Problem:** The `scheduleNextRepeat()` method in the companion object was never called anywhere in the codebase. It was dead code with a locale-sensitive day-of-week calculation that could produce wrong results on non-US locales.
- **Fix:** Removed the function entirely.

---

## RingtonePickerActivity.kt

### [C7] Added `READ_MEDIA_AUDIO` runtime permission request
- **Problem:** `READ_MEDIA_AUDIO` was declared in the manifest but never requested at runtime. On Android 13, this is a dangerous permission that requires explicit user consent. Without it, `File.listFiles()` on the QRAlarm folder returns null, and the ringtone list is silently empty.
- **Fix:** In `onCreate()`, before calling `buildUI()`, the code now checks for `READ_MEDIA_AUDIO` on API 33+. If not granted, it calls `ActivityCompat.requestPermissions()`. `onRequestPermissionsResult()` then calls `buildUI()` on grant or shows a message and finishes on denial.

### [M7] Preview `MediaPlayer.prepare()` switched to `prepareAsync()`
- **Problem:** Tapping the preview button called `prepare()` on the main thread — same ANR risk as in `AlarmService`.
- **Fix:** Changed to `prepareAsync()` + `setOnPreparedListener { mp -> mp.start() }`. Also added `setOnCompletionListener { it.release(); previewPlayer = null }` to clean up the reference after playback finishes.

---

## QRScanActivity.kt

### [H2] Switched executor to `shutdownNow()`
- **Problem:** `executor.shutdown()` in `onDestroy()` requests a graceful shutdown but does not interrupt running tasks. On a configuration change (device rotation), the old executor's image-analysis thread could linger.
- **Fix:** Changed to `executor.shutdownNow()` which interrupts the worker thread immediately.

### Timer `onFinish()` guarded against running after `finish()`
- **Problem:** If the QR code was scanned at exactly the last second, both the `addOnSuccessListener` callback and `CountDownTimer.onFinish()` could execute in quick succession. `onFinish()` would then try to start a foreground service and a new activity from an already-finishing activity context.
- **Fix:** Added `if (alarmDismissed || isFinishing) return` at the top of `onFinish()`. Cleaned up the redundant comment block while there.

---

## QRSetupActivity.kt

### [H3] Switched executor to `shutdownNow()`
- **Problem:** Same as `QRScanActivity` — `executor.shutdown()` doesn't interrupt the camera analysis thread on activity destruction.
- **Fix:** Changed to `executor.shutdownNow()`.

---

## AlarmEditActivity.kt

### [M1] Past-time toast for one-time alarms
- **Problem:** If the user picked a time that had already passed for a one-time alarm, `AlarmScheduler` correctly scheduled it for the next day, but nothing told the user. They could believe the alarm was set for today and miss it.
- **Fix:** After computing `hour`/`minute` in the save handler, if `repeatDays` is empty and the chosen time is at or before the current time, a `Toast.LENGTH_SHORT` toast is shown: *"Time is in the past — alarm will ring tomorrow."*

---

## Summary table

| ID | Severity | File | Fix |
|----|----------|------|-----|
| C1 | Critical | AlarmScheduler.kt + MainActivity.kt | `canScheduleExactAlarms()` guard + Settings redirect |
| C2 | Critical | AlarmService.kt | `prepareAsync()` + `setOnPreparedListener` |
| C3 | Critical | AlarmRingActivity.kt | Re-acquire `wakeLock` in `onResume()` |
| C4 | Critical | AlarmService.kt | `volumeObserverRegistered` flag guards unregister |
| C5 | Critical | AlarmService.kt | `FLAG_ONE_SHOT` → `FLAG_UPDATE_CURRENT` |
| C6 | Critical | AlarmReceiver.kt | Mark one-time alarms inactive after firing |
| C7 | Critical | RingtonePickerActivity.kt | `READ_MEDIA_AUDIO` runtime permission |
| H1 | High | AlarmRingActivity.kt | Release `wakeLock` in `onDestroy()` |
| H2 | High | QRScanActivity.kt | `shutdownNow()` + `isFinishing` guard |
| H3 | High | QRSetupActivity.kt | `shutdownNow()` |
| M1 | Medium | AlarmEditActivity.kt | Past-time alarm toast |
| M2 | Medium | AlarmReceiver.kt | Removed dead `scheduleNextRepeat()` |
| M3 | Medium | AlarmService.kt | `mediaPlayerReady` flag guards RESUME start |
| M7 | Medium | RingtonePickerActivity.kt | Preview `prepareAsync()` |
