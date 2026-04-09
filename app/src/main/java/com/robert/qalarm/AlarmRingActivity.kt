package com.robert.qalarm

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

@androidx.camera.core.ExperimentalGetImage
class AlarmRingActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager.requestDismissKeyguard(this, null)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QRAlarm::AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)

        setContentView(R.layout.activity_alarm_ring)

        val qrCode = intent.getStringExtra("qr_code")
        val btnScanQR = findViewById<Button>(R.id.btnScanQR)

        if (qrCode.isNullOrEmpty()) {
            btnScanQR.text = "DISMISS ALARM"
            btnScanQR.setOnClickListener {
                stopService(Intent(this, AlarmService::class.java))
                finish()
            }
        } else {
            btnScanQR.text = "SCAN QR CODE"
            btnScanQR.setOnClickListener {
                val scanIntent = Intent(this, QRScanActivity::class.java).apply {
                    putExtra("qr_code", qrCode)
                }
                qrScanLauncher.launch(scanIntent)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            @SuppressLint("SetTextI18n")
            override fun handleOnBackPressed() {
                android.app.AlertDialog.Builder(this@AlarmRingActivity)
                    .setTitle("Skip alarm?")
                    .setMessage("You haven't dismissed the alarm yet.")
                    .setPositiveButton("Stay awake") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("Dismiss anyway") { _, _ ->
                        stopService(Intent(this@AlarmRingActivity, AlarmService::class.java))
                        finish()
                    }
                    .show()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        wakeLock?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}