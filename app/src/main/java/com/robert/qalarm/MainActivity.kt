package com.robert.qalarm

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "QRAlarmPrefs"
        const val KEY_QR_CODE = "qr_code_value"
        const val KEY_REPEAT_DAYS = "repeat_days"
        const val KEY_ALARM_HOUR = "alarm_hour"
        const val KEY_ALARM_MINUTE = "alarm_minute"
        const val KEY_RINGTONES = "ringtone_list"
    }

    private val editAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshAlarmList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        refreshAlarmList()

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabAddAlarm
        ).setOnClickListener {
            editAlarmLauncher.launch(Intent(this, AlarmEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAlarmList()
    }

    private fun refreshAlarmList() {
        val container = findViewById<LinearLayout>(R.id.alarmListContainer)
        container.removeAllViews()

        val alarms = AlarmStorage.getAlarms(this)
            .sortedWith(compareBy({ it.hour }, { it.minute }))

        if (alarms.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No alarms set yet"
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                textSize = 16f
                setPadding(0, 64, 0, 64)
            }
            container.addView(empty)
            return
        }

        for (alarm in alarms) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 20, 20, 20)
                background = resources.getDrawable(R.drawable.bg_card, theme)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 10
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val intent = Intent(this@MainActivity, AlarmEditActivity::class.java)
                    intent.putExtra("alarm_id", alarm.id)
                    editAlarmLauncher.launch(intent)
                }
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val timeText = TextView(this).apply {
                text = "${
                    String.format(java.util.Locale.getDefault(), "%02d", alarm.hour)
                }:${
                    String.format(java.util.Locale.getDefault(), "%02d", alarm.minute)
                }"
                textSize = 28f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(resources.getColor(R.color.text_primary, theme))
            }

            val labelText = TextView(this).apply {
                text = alarm.label
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_secondary, theme))
            }

            val daysText = TextView(this).apply {
                text = if (alarm.repeatDays.isEmpty()) "Once"
                else alarm.repeatDays.joinToString(" · ")
                textSize = 11f
                setTextColor(resources.getColor(R.color.accent, theme))
            }

            val qrIndicator = TextView(this).apply {
                text = if (alarm.qrCode != null) "QR ✓" else "No QR"
                textSize = 11f
                setTextColor(
                    if (alarm.qrCode != null)
                        resources.getColor(R.color.accent, theme)
                    else
                        resources.getColor(R.color.text_secondary, theme)
                )
            }

            val menuBtn = Button(this).apply {
                text = "⋮"
                textSize = 20f
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                background = null
                setOnClickListener {
                    val popup = android.widget.PopupMenu(this@MainActivity, this)
                    popup.menu.add(0, 1, 0, "Duplicate")
                    popup.menu.add(0, 2, 1, "Delete")
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                // Duplicate
                                val newAlarm = alarm.copy(
                                    id = AlarmStorage.generateId(this@MainActivity)
                                )
                                AlarmStorage.addAlarm(this@MainActivity, newAlarm)
                                AlarmScheduler.schedule(this@MainActivity, newAlarm)
                                refreshAlarmList()
                                Toast.makeText(this@MainActivity, "Alarm duplicated", Toast.LENGTH_SHORT).show()
                                true
                            }
                            2 -> {
                                // Delete
                                AlarmScheduler.cancel(this@MainActivity, alarm.id)
                                AlarmStorage.removeAlarm(this@MainActivity, alarm.id)
                                refreshAlarmList()
                                Toast.makeText(this@MainActivity, "Alarm removed", Toast.LENGTH_SHORT).show()
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }

            infoLayout.addView(timeText)
            infoLayout.addView(labelText)
            infoLayout.addView(daysText)
            infoLayout.addView(qrIndicator)
            card.addView(infoLayout)
            card.addView(menuBtn)
            container.addView(card)
        }
    }
}