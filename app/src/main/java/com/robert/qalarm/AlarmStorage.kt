package com.robert.qalarm

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

@androidx.camera.core.ExperimentalGetImage
object AlarmStorage {

    private const val KEY_ALARMS = "alarms_list"

    fun getAlarms(context: Context): MutableList<Alarm> {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ALARMS, null) ?: return mutableListOf()
        val array = JSONArray(json)
        val list = mutableListOf<Alarm>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val days = mutableSetOf<String>()
            val daysArray = obj.getJSONArray("repeatDays")
            for (j in 0 until daysArray.length()) days.add(daysArray.getString(j))
            val ringtonePaths = mutableSetOf<String>()
            if (obj.has("ringtonePaths")) {
                val rArray = obj.getJSONArray("ringtonePaths")
                for (k in 0 until rArray.length()) ringtonePaths.add(rArray.getString(k))
            }
            list.add(
                Alarm(
                    id = obj.getInt("id"),
                    label = obj.getString("label"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    repeatDays = days,
                    isActive = obj.getBoolean("isActive"),
                    ringtonePath = if (obj.has("ringtonePath")) obj.getString("ringtonePath") else null,
                    qrCode = if (obj.has("qrCode")) obj.getString("qrCode").ifEmpty { null } else null,
                    ringtonePaths = ringtonePaths
                )
            )
        }
        return list
    }

    fun saveAlarms(context: Context, alarms: List<Alarm>) {
        val array = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject()
            obj.put("id", alarm.id)
            obj.put("label", alarm.label)
            obj.put("hour", alarm.hour)
            obj.put("minute", alarm.minute)
            obj.put("isActive", alarm.isActive)
            obj.put("ringtonePath", alarm.ringtonePath ?: "")
            obj.put("qrCode", alarm.qrCode ?: "")
            val daysArray = JSONArray()
            alarm.repeatDays.forEach { daysArray.put(it) }
            obj.put("repeatDays", daysArray)
            val rArray = JSONArray()
            alarm.ringtonePaths.forEach { rArray.put(it) }
            obj.put("ringtonePaths", rArray)
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_ALARMS, array.toString()) }
    }

    fun addAlarm(context: Context, alarm: Alarm) {
        val alarms = getAlarms(context)
        alarms.add(alarm)
        saveAlarms(context, alarms)
    }

    fun removeAlarm(context: Context, alarmId: Int) {
        val alarms = getAlarms(context).filter { it.id != alarmId }
        saveAlarms(context, alarms)
    }

    fun updateAlarm(context: Context, alarm: Alarm) {
        val alarms = getAlarms(context)
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarm
            saveAlarms(context, alarms)
        }
    }

    fun generateId(context: Context): Int {
        val alarms = getAlarms(context)
        return if (alarms.isEmpty()) 1 else alarms.maxOf { it.id } + 1
    }
}