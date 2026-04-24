package com.robert.qalarm

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

@androidx.camera.core.ExperimentalGetImage
class RingtonePickerActivity : AppCompatActivity() {

    private var previewPlayer: MediaPlayer? = null
    private val selectedPaths = mutableSetOf<String>()
    private val checkboxes = mutableListOf<Pair<CheckBox, String>>()
    private lateinit var selectAllCheckbox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent.getIntExtra("alarm_id", -1)

        if (alarmId != -1) {
            val alarm = AlarmStorage.getAlarms(this).firstOrNull { it.id == alarmId }
            alarm?.ringtonePaths?.let { selectedPaths.addAll(it) }
        }

        buildUI(alarmId)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    private fun buildUI(alarmId: Int) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_primary, theme))
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "RINGTONES"
            textSize = 12f
            letterSpacing = 0.3f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resources.getColor(R.color.accent, theme))
        }

        val divider = android.view.View(this).apply {
            setBackgroundColor(resources.getColor(R.color.border, theme))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(12); it.bottomMargin = dp(24) }
        }

        val hint = TextView(this).apply {
            text = "Place MP3 files in Internal Storage → QRAlarm"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, 0, 0, dp(16))
        }

        // Select All row
        val selectAllRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_elevated, theme))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(8)
            layoutParams = params
        }

        selectAllCheckbox = CheckBox(this).apply {
            isChecked = false
            buttonTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.accent, theme)
            )
            setOnCheckedChangeListener { _, checked ->
                checkboxes.forEach { (cb, path) ->
                    if (checked) selectedPaths.add(path) else selectedPaths.remove(path)
                    cb.isChecked = checked
                }
            }
        }

        val selectAllLabel = TextView(this).apply {
            text = "Select All"
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setPadding(dp(12), 0, 0, 0)
        }

        selectAllRow.addView(selectAllCheckbox)
        selectAllRow.addView(selectAllLabel)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(listContainer)

        val saveBtn = Button(this).apply {
            text = "SAVE BUNDLE"
            textSize = 13f
            letterSpacing = 0.15f
            setTextColor(resources.getColor(R.color.bg_primary, theme))
            background = resources.getDrawable(R.drawable.bg_button_accent, theme)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(16)
            params.bottomMargin = dp(24)
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = params
            setOnClickListener { saveBundle(alarmId) }
        }

        root.addView(title)
        root.addView(divider)
        root.addView(hint)
        root.addView(selectAllRow)
        root.addView(scrollView)
        root.addView(saveBtn)

        setContentView(root)
        populateList(listContainer)
    }

    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    private fun populateList(container: LinearLayout) {
        val folder = File(Environment.getExternalStorageDirectory(), "QRAlarm")

        if (!folder.exists() || !folder.isDirectory) {
            val empty = TextView(this).apply {
                text = "No QRAlarm folder found.\nCreate it and add MP3 files."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, dp(48), 0, 0)
            }
            container.addView(empty)
            return
        }

        val files = folder.listFiles { f -> f.extension.lowercase() == "mp3" }

        if (files.isNullOrEmpty()) {
            val empty = TextView(this).apply {
                text = "No MP3 files found in QRAlarm folder"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, dp(48), 0, 0)
            }
            container.addView(empty)
            return
        }

        for (file in files.sortedBy { it.name }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(R.drawable.bg_card, theme)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = dp(8)
                layoutParams = params
            }

            val checkbox = CheckBox(this).apply {
                isChecked = selectedPaths.contains(file.absolutePath)
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.accent, theme)
                )
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPaths.add(file.absolutePath)
                    else {
                        selectedPaths.remove(file.absolutePath)
                        // Uncheck select all if any item unchecked
                        selectAllCheckbox.setOnCheckedChangeListener(null)
                        selectAllCheckbox.isChecked = false
                        selectAllCheckbox.setOnCheckedChangeListener { _, all ->
                            checkboxes.forEach { (cb, path) ->
                                cb.isChecked = all
                                if (all) selectedPaths.add(path)
                                else selectedPaths.remove(path)
                            }
                        }
                    }
                }
            }

            checkboxes.add(Pair(checkbox, file.absolutePath))

            val nameText = TextView(this).apply {
                text = file.nameWithoutExtension
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                setPadding(dp(12), 0, 0, 0)
            }

            val previewBtn = Button(this).apply {
                text = "▶"
                textSize = 14f
                setTextColor(resources.getColor(R.color.accent, theme))
                background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
                setOnClickListener {
                    previewPlayer?.stop()
                    previewPlayer?.release()
                    previewPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener { release() }
                    }
                }
            }

            row.addView(checkbox)
            row.addView(nameText)
            row.addView(previewBtn)
            container.addView(row)
        }

        // After populating, check if all are selected to sync select all state
        if (files.isNotEmpty() && selectedPaths.containsAll(files.map { it.absolutePath })) {
            selectAllCheckbox.isChecked = true
        }
    }

    private fun saveBundle(alarmId: Int) {
        Log.d("RingtonePicker", "saveBundle: selectedPaths.size=${selectedPaths.size} paths=$selectedPaths")
        if (alarmId != -1) {
            val alarms = AlarmStorage.getAlarms(this)
            val alarm = alarms.firstOrNull { it.id == alarmId }
            if (alarm != null) {
                val ringtonePath = if (selectedPaths.isNotEmpty())
                    selectedPaths.random() else null
                AlarmStorage.updateAlarm(
                    this,
                    alarm.copy(
                        ringtonePaths = selectedPaths.toSet(),
                        ringtonePath = ringtonePath
                    )
                )
            }
        }
        Toast.makeText(this, "${selectedPaths.size} ringtone(s) saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.stop()
        previewPlayer?.release()
    }
}