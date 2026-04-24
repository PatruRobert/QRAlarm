package com.robert.qalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

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
        val ringtonePath = intent.getStringExtra("ringtone_path")
        val qrCode = intent.getStringExtra("qr_code")

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

        // Reschedule using per-alarm storage
        if (alarmId != -1) {
            val alarms = AlarmStorage.getAlarms(context)
            val alarm = alarms.firstOrNull { it.id == alarmId }
            if (alarm != null && alarm.repeatDays.isNotEmpty() && alarm.isActive) {
                AlarmScheduler.schedule(context, alarm)
            }
        }
    }

    companion object {
        fun scheduleNextRepeat(
            context: Context,
            hour: Int,
            minute: Int,
            repeatDays: Set<String>
        ) {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val dayMap = mapOf(
                "Mon" to Calendar.MONDAY,
                "Tue" to Calendar.TUESDAY,
                "Wed" to Calendar.WEDNESDAY,
                "Thu" to Calendar.THURSDAY,
                "Fri" to Calendar.FRIDAY,
                "Sat" to Calendar.SATURDAY,
                "Sun" to Calendar.SUNDAY
            )

            val selectedCalDays = repeatDays.mapNotNull { dayMap[it] }.sorted()
            if (selectedCalDays.isEmpty()) return

            // Find the next day from today
            val nextDay = selectedCalDays.firstOrNull { it > today }
                ?: selectedCalDays.first()

            val daysUntil = if (nextDay > today) nextDay - today
            else 7 - today + nextDay

            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysUntil)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                pendingIntent
            )
        }
    }
}