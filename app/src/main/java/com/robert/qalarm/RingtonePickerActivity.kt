package com.robert.qalarm

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.io.File
import android.content.Intent

@androidx.camera.core.ExperimentalGetImage
class RingtonePickerActivity : AppCompatActivity() {

    private var previewPlayer: MediaPlayer? = null
    private val selectedPaths = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load previously saved ringtones
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val preselected = intent.getStringArrayListExtra("preselected_paths") ?: arrayListOf()
        selectedPaths.addAll(preselected)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                200
            )
        } else {
            buildUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            buildUI()
        } else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_primary, theme))
            setPadding(24, 48, 24, 24)
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
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = 12; it.bottomMargin = 32 }
        }

        val hint = TextView(this).apply {
            text = "Place MP3 files in Internal Storage → QRAlarm"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, 0, 0, 24)
        }

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            ).also { it.topMargin = 16 }
            setOnClickListener {
                saveBundle()
            }
        }

        root.addView(title)
        root.addView(divider)
        root.addView(hint)
        root.addView(scrollView)
        root.addView(saveBtn)

        setContentView(root)
        populateList(listContainer)
    }

    private fun populateList(container: LinearLayout) {
        val folder = File(
            Environment.getExternalStorageDirectory(),
            "QRAlarm"
        )

        if (!folder.exists() || !folder.isDirectory) {
            val empty = TextView(this).apply {
                text = "No QRAlarm folder found.\nCreate it and add MP3 files."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, 48, 0, 0)
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
                setPadding(0, 48, 0, 0)
            }
            container.addView(empty)
            return
        }

        for (file in files.sortedBy { it.name }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(R.drawable.bg_card, theme)
                setPadding(16, 16, 16, 16)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 10
                layoutParams = params
            }

            val checkbox = CheckBox(this).apply {
                isChecked = selectedPaths.contains(file.absolutePath)
                setButtonTintList(android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.accent, theme)
                ))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPaths.add(file.absolutePath)
                    else selectedPaths.remove(file.absolutePath)
                }
            }

            val nameText = TextView(this).apply {
                text = file.nameWithoutExtension
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(12, 0, 0, 0)
            }

            val previewBtn = Button(this).apply {
                text = "▶"
                textSize = 14f
                setTextColor(resources.getColor(R.color.accent, theme))
                background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
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
    }

    private fun saveBundle() {
        val resultIntent = Intent()
        resultIntent.putStringArrayListExtra("selected_paths", ArrayList(selectedPaths))
        setResult(RESULT_OK, resultIntent)
        Toast.makeText(this, "${selectedPaths.size} ringtone(s) saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.stop()
        previewPlayer?.release()
    }
}