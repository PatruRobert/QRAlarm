package com.robert.qalarm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

@androidx.camera.core.ExperimentalGetImage
class AlarmEditActivity : AppCompatActivity() {

    private var editingAlarmId: Int = -1
    private var currentQRCode: String? = null
    private val selectedRingtonePaths = mutableSetOf<String>()

    private val qrSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedValue = result.data?.getStringExtra("qr_value")
            if (scannedValue != null) {
                currentQRCode = scannedValue
                updateQRButton()
                Toast.makeText(this, "QR code saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        val npHour = findViewById<android.widget.NumberPicker>(R.id.npHour).apply {
            minValue = 0
            maxValue = 23
            setFormatter { i -> String.format(java.util.Locale.getDefault(), "%02d", i) }
        }
        val npMinute = findViewById<android.widget.NumberPicker>(R.id.npMinute).apply {
            minValue = 0
            maxValue = 59
            setFormatter { i -> String.format(java.util.Locale.getDefault(), "%02d", i) }
        }
        val etLabel = findViewById<EditText>(R.id.etLabel)
        val cbMon = findViewById<CheckBox>(R.id.cbMon)
        val cbTue = findViewById<CheckBox>(R.id.cbTue)
        val cbWed = findViewById<CheckBox>(R.id.cbWed)
        val cbThu = findViewById<CheckBox>(R.id.cbThu)
        val cbFri = findViewById<CheckBox>(R.id.cbFri)
        val cbSat = findViewById<CheckBox>(R.id.cbSat)
        val cbSun = findViewById<CheckBox>(R.id.cbSun)
        val cbWeekly = findViewById<CheckBox>(R.id.cbWeekly)
        val tvTitle = findViewById<TextView>(R.id.tvEditTitle)
        val btnSave = findViewById<Button>(R.id.btnSaveAlarm)
        val btnSetQR = findViewById<Button>(R.id.btnSetQR)
        val btnRingtones = findViewById<Button>(R.id.btnRingtones)

        cbWeekly.setOnCheckedChangeListener { _, checked ->
            cbMon.isChecked = checked
            cbTue.isChecked = checked
            cbWed.isChecked = checked
            cbThu.isChecked = checked
            cbFri.isChecked = checked
            cbSat.isChecked = checked
            cbSun.isChecked = checked
        }

        btnSetQR.setOnClickListener {
            qrSetupLauncher.launch(Intent(this, QRSetupActivity::class.java))
        }

        btnRingtones.setOnClickListener {
            val intent = Intent(this, RingtonePickerActivity::class.java)
            intent.putExtra("alarm_id", editingAlarmId)
            startActivity(intent)
        }

        editingAlarmId = intent.getIntExtra("alarm_id", -1)
        if (editingAlarmId != -1) {
            tvTitle.text = "EDIT ALARM"
            btnSave.text = "UPDATE ALARM"
            val alarm = AlarmStorage.getAlarms(this).firstOrNull { it.id == editingAlarmId }
            alarm?.let {
                npHour.value = it.hour
                npMinute.value = it.minute
                etLabel.setText(it.label)
                cbMon.isChecked = it.repeatDays.contains("Mon")
                cbTue.isChecked = it.repeatDays.contains("Tue")
                cbWed.isChecked = it.repeatDays.contains("Wed")
                cbThu.isChecked = it.repeatDays.contains("Thu")
                cbFri.isChecked = it.repeatDays.contains("Fri")
                cbSat.isChecked = it.repeatDays.contains("Sat")
                cbSun.isChecked = it.repeatDays.contains("Sun")
                currentQRCode = it.qrCode
                selectedRingtonePaths.addAll(it.ringtonePaths)
            }
        }

        updateQRButton()
        updateRingtoneButton()

        btnSave.setOnClickListener {
            val label = etLabel.text.toString().ifBlank { "Alarm" }
            val hour = npHour.value
            val minute = npMinute.value

            val repeatDays = mutableSetOf<String>()
            if (cbMon.isChecked) repeatDays.add("Mon")
            if (cbTue.isChecked) repeatDays.add("Tue")
            if (cbWed.isChecked) repeatDays.add("Wed")
            if (cbThu.isChecked) repeatDays.add("Thu")
            if (cbFri.isChecked) repeatDays.add("Fri")
            if (cbSat.isChecked) repeatDays.add("Sat")
            if (cbSun.isChecked) repeatDays.add("Sun")

            val ringtonePath = if (selectedRingtonePaths.isNotEmpty())
                selectedRingtonePaths.random() else null

            if (editingAlarmId == -1) {
                val alarm = Alarm(
                    id = AlarmStorage.generateId(this),
                    label = label,
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDays,
                    ringtonePath = ringtonePath,
                    qrCode = currentQRCode,
                    ringtonePaths = selectedRingtonePaths.toSet()
                )
                AlarmStorage.addAlarm(this, alarm)
                AlarmScheduler.schedule(this, alarm)
                Toast.makeText(this, "Alarm set for ${
                    String.format(java.util.Locale.getDefault(), "%02d", hour)
                }:${
                    String.format(java.util.Locale.getDefault(), "%02d", minute)
                }", Toast.LENGTH_SHORT).show()
            } else {
                AlarmScheduler.cancel(this, editingAlarmId)
                val alarm = Alarm(
                    id = editingAlarmId,
                    label = label,
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDays,
                    ringtonePath = ringtonePath,
                    qrCode = currentQRCode,
                    ringtonePaths = selectedRingtonePaths.toSet()
                )
                AlarmStorage.updateAlarm(this, alarm)
                AlarmScheduler.schedule(this, alarm)
                Toast.makeText(this, "Alarm updated", Toast.LENGTH_SHORT).show()
            }

            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (editingAlarmId != -1) {
            val alarm = AlarmStorage.getAlarms(this).firstOrNull { it.id == editingAlarmId }
            alarm?.let {
                selectedRingtonePaths.clear()
                selectedRingtonePaths.addAll(it.ringtonePaths)
                updateRingtoneButton()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateQRButton() {
        val btn = findViewById<Button>(R.id.btnSetQR)
        btn.text = if (currentQRCode != null) "QR SET ✓" else "SET QR CODE"
    }

    @SuppressLint("SetTextI18n")
    private fun updateRingtoneButton() {
        val btn = findViewById<Button>(R.id.btnRingtones)
        btn.text = if (selectedRingtonePaths.isNotEmpty())
            "${selectedRingtonePaths.size} RINGTONE(S)" else "ADD RINGTONES"
    }
}