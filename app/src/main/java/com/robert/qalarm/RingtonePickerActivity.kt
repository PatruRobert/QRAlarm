package com.robert.qalarm

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import java.io.File

@androidx.camera.core.ExperimentalGetImage
class RingtonePickerActivity : AppCompatActivity() {

    private var previewPlayer: MediaPlayer? = null
    private val selectedPaths = mutableSetOf<String>()
    private val checkboxes = mutableListOf<Pair<CheckBox, String>>()
    private lateinit var selectAllCheckbox: CheckBox
    private var programmaticChange = 0

    // Dropbox UI refs — populated in buildUI, updated in onResume
    private var hintText: TextView? = null
    private var dropboxStatusText: TextView? = null
    private var dropboxActionBtn: Button? = null
    private var dropboxSyncBtn: Button? = null
    private var listContainerRef: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent.getIntExtra("alarm_id", -1)

        if (alarmId != -1) {
            val alarm = AlarmStorage.getAlarms(this).firstOrNull { it.id == alarmId }
            alarm?.ringtonePaths?.let { selectedPaths.addAll(it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 200
            )
        } else {
            buildUI(alarmId)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDropboxUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                buildUI(alarmId)
            } else {
                Toast.makeText(this, "Storage permission denied — cannot list ringtones.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
            ).also { it.topMargin = dp(12); it.bottomMargin = dp(16) }
        }

        hintText = TextView(this).apply {
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, 0, 0, dp(12))
        }

        // ── Dropbox control row ──────────────────────────────────────────────
        val dropboxRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_elevated, theme))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }

        dropboxStatusText = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        dropboxSyncBtn = Button(this).apply {
            text = "SYNC"
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(resources.getColor(R.color.accent, theme))
            background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(8) }
            setOnClickListener { syncDropbox() }
        }

        val testBtn = Button(this).apply {
            text = "TEST"
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(8) }
            setOnClickListener {
                val handler = Handler(Looper.getMainLooper())
                Thread {
                    val result = DropboxManager.testToken(this@RingtonePickerActivity)
                    handler.post {
                        Toast.makeText(this@RingtonePickerActivity, result, Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }

        dropboxActionBtn = Button(this).apply {
            textSize = 11f
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                if (DropboxManager.isConnected(this@RingtonePickerActivity)) {
                    DropboxManager.disconnect(this@RingtonePickerActivity)
                    refreshDropboxUI()
                    refreshList()
                } else {
                    showTokenDialog()
                }
            }
        }

        dropboxRow.addView(dropboxStatusText)
        dropboxRow.addView(dropboxSyncBtn)
        dropboxRow.addView(testBtn)
        dropboxRow.addView(dropboxActionBtn)

        // ── Select All row ───────────────────────────────────────────────────
        val selectAllRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_elevated, theme))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
        }

        selectAllCheckbox = CheckBox(this).apply {
            isChecked = false
            buttonTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.accent, theme)
            )
            setOnCheckedChangeListener { _, checked ->
                programmaticChange++
                checkboxes.forEach { (cb, path) ->
                    cb.isChecked = checked
                    if (checked) selectedPaths.add(path) else selectedPaths.remove(path)
                }
                programmaticChange--
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

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listContainerRef = listContainer
        scrollView.addView(listContainer)

        val saveBtn = Button(this).apply {
            text = "SAVE BUNDLE"
            textSize = 13f
            letterSpacing = 0.15f
            setTextColor(resources.getColor(R.color.bg_primary, theme))
            background = resources.getDrawable(R.drawable.bg_button_accent, theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(16); it.bottomMargin = dp(24) }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { saveBundle(alarmId) }
        }

        root.addView(title)
        root.addView(divider)
        root.addView(hintText)
        root.addView(dropboxRow)
        root.addView(selectAllRow)
        root.addView(scrollView)
        root.addView(saveBtn)

        setContentView(root)
        refreshDropboxUI()
        populateList(listContainer)
    }

    private fun refreshDropboxUI() {
        val connected = DropboxManager.isConnected(this)
        hintText?.text = if (connected)
            "Tap SYNC to download your Dropbox /Alarms folder"
        else
            "Connect Dropbox below, or place MP3s in Dropbox/Alarms on this device"

        if (connected) {
            dropboxStatusText?.text = "Dropbox: connected"
            dropboxStatusText?.setTextColor(resources.getColor(R.color.accent, theme))
            dropboxSyncBtn?.visibility = android.view.View.VISIBLE
            dropboxActionBtn?.text = "DISCONNECT"
            dropboxActionBtn?.setTextColor(resources.getColor(R.color.danger, theme))
            dropboxActionBtn?.background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
        } else {
            dropboxStatusText?.text = "Dropbox: not connected"
            dropboxStatusText?.setTextColor(resources.getColor(R.color.text_secondary, theme))
            dropboxSyncBtn?.visibility = android.view.View.GONE
            dropboxActionBtn?.text = "CONNECT"
            dropboxActionBtn?.setTextColor(resources.getColor(R.color.bg_primary, theme))
            dropboxActionBtn?.background = resources.getDrawable(R.drawable.bg_button_accent, theme)
        }
    }

    private fun syncDropbox() {
        val syncBtn = dropboxSyncBtn ?: return
        syncBtn.isEnabled = false
        syncBtn.text = "…"
        checkboxes.clear()
        listContainerRef?.removeAllViews()

        val handler = Handler(Looper.getMainLooper())
        Thread {
            val (_, error) = DropboxManager.syncFiles(this)
            handler.post {
                syncBtn.isEnabled = true
                syncBtn.text = "SYNC"
                if (error == "TOKEN_EXPIRED") {
                    refreshDropboxUI()
                    showTokenDialog()
                } else if (error != null) {
                    Toast.makeText(this, "Sync failed: $error", Toast.LENGTH_LONG).show()
                }
                listContainerRef?.let { populateList(it) }
            }
        }.start()
    }

    private fun refreshList() {
        checkboxes.clear()
        listContainerRef?.removeAllViews()
        listContainerRef?.let { populateList(it) }
    }

    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    private fun populateList(container: LinearLayout) {
        val folder = if (DropboxManager.isConnected(this)) {
            DropboxManager.getCacheDir(this)
        } else {
            File(Environment.getExternalStorageDirectory(), "Dropbox/Alarms")
        }

        if (!folder.exists() || !folder.isDirectory) {
            container.addView(TextView(this).apply {
                text = if (DropboxManager.isConnected(this@RingtonePickerActivity))
                    "No files synced yet — tap SYNC to download from Dropbox"
                else
                    "Dropbox/Alarms folder not found on device.\nConnect Dropbox or create the folder manually."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, dp(48), 0, 0)
            })
            return
        }

        val files = folder.listFiles { f -> f.extension.lowercase() == "mp3" }

        if (files.isNullOrEmpty()) {
            container.addView(TextView(this).apply {
                text = if (DropboxManager.isConnected(this@RingtonePickerActivity))
                    "No MP3s in local cache — tap SYNC"
                else
                    "No MP3 files found in Dropbox/Alarms"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, dp(48), 0, 0)
            })
            return
        }

        for (file in files.sortedBy { it.name }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(R.drawable.bg_card, theme)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(8) }
            }

            val checkbox = CheckBox(this).apply {
                isChecked = selectedPaths.contains(file.absolutePath)
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.accent, theme)
                )
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPaths.add(file.absolutePath)
                    else selectedPaths.remove(file.absolutePath)
                    if (programmaticChange == 0) {
                        programmaticChange++
                        selectAllCheckbox.isChecked = checkboxes.all { (cb, _) -> cb.isChecked }
                        programmaticChange--
                    }
                }
            }

            checkboxes.add(Pair(checkbox, file.absolutePath))

            val nameText = TextView(this).apply {
                text = file.nameWithoutExtension
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }

            val previewBtn = Button(this).apply {
                text = "▶"
                textSize = 14f
                setTextColor(resources.getColor(R.color.accent, theme))
                background = resources.getDrawable(R.drawable.bg_button_ghost, theme)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    previewPlayer?.stop()
                    previewPlayer?.release()
                    previewPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnPreparedListener { mp -> mp.start() }
                        setOnCompletionListener { it.release(); previewPlayer = null }
                        prepareAsync()
                    }
                }
            }

            row.addView(checkbox)
            row.addView(nameText)
            row.addView(previewBtn)
            container.addView(row)
        }

        if (files.isNotEmpty() && selectedPaths.containsAll(files.map { it.absolutePath })) {
            programmaticChange++
            selectAllCheckbox.isChecked = true
            programmaticChange--
        }
    }

    private fun showTokenDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Paste access token here"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setHintTextColor(resources.getColor(R.color.text_secondary, theme))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Connect Dropbox")
            .setMessage(
                "Go to dropbox.com/developers/apps → your app → " +
                "scroll to \"Generated access token\" → click Generate → paste it below."
            )
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isNotEmpty()) {
                    DropboxManager.setToken(this, token)
                    refreshDropboxUI()
                    refreshList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveBundle(alarmId: Int) {
        Log.d("RingtonePicker", "saveBundle: ${selectedPaths.size} paths")
        if (alarmId != -1) {
            val alarms = AlarmStorage.getAlarms(this)
            val alarm = alarms.firstOrNull { it.id == alarmId }
            if (alarm != null) {
                val ringtonePath = if (selectedPaths.isNotEmpty()) selectedPaths.random() else null
                AlarmStorage.updateAlarm(
                    this,
                    alarm.copy(ringtonePaths = selectedPaths.toSet(), ringtonePath = ringtonePath)
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
