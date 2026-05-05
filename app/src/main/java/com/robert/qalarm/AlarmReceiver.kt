package com.robert.qalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

@androidx.camera.core.ExperimentalGetImage
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val alarms = AlarmStorage.getAlarms(context)
            for (alarm in alarms) {
                if (alarm.isActive) {
                    AlarmScheduler.schedule(context, alarm)
                }
            }
            return
        }

        val alarmId = intent.getIntExtra("alarm_id", -1)
        val qrCode = intent.getStringExtra("qr_code")

        val alarms = AlarmStorage.getAlarms(context)
        val alarm = alarms.firstOrNull { it.id == alarmId }

        // Pick a fresh random ringtone from the full pool on every ring.
        val ringtonePath = alarm?.ringtonePaths
            ?.takeIf { it.isNotEmpty() }
            ?.random()
            ?: intent.getStringExtra("ringtone_path")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ringtone_path", ringtonePath)
            putExtra("qr_code", qrCode)
        }
        context.startForegroundService(serviceIntent)

        val activityIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("qr_code", qrCode)
        }
        context.startActivity(activityIntent)

        if (alarm != null && alarm.isActive) {
            if (alarm.repeatDays.isNotEmpty()) {
                // Repeating alarm — schedule the next occurrence.
                AlarmScheduler.schedule(context, alarm)
            } else {
                // One-time alarm — mark inactive so the boot receiver doesn't re-fire it.
                AlarmStorage.updateAlarm(context, alarm.copy(isActive = false))
            }
        }
    }
}
