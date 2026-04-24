package com.robert.qalarm

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.camera.core.ExperimentalGetImage
class QRScanActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var alarmDismissed = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pause alarm sound while camera is open
        val pauseIntent = Intent(this, AlarmService::class.java)
        pauseIntent.action = "PAUSE"
        startService(pauseIntent)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    private fun startCamera() {
        val density = resources.displayMetrics.density
        val btnSize = (56 * density).toInt()
        val margin = (16 * density).toInt()

        val container = FrameLayout(this)
        container.setBackgroundColor(resources.getColor(R.color.bg_primary, theme))

        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val timerText = TextView(this).apply {
            textSize = 20f
            setTextColor(resources.getColor(R.color.danger, theme))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 64, 32, 0)
        }

        val torchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#666666"))
            background = null
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, margin, margin)
            }
        }

        container.addView(previewView)
        container.addView(timerText)
        container.addView(torchBtn)
        setContentView(container)

        // 30 second countdown — if it expires, close camera and resume alarm
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000
                timerText.text = getString(R.string.timer_format, secs)
            }
            override fun onFinish() {
                if (!alarmDismissed) {
                    // Restart service directly — don't rely on AlarmRingActivity being alive
                    val serviceIntent = Intent(applicationContext, AlarmService::class.java).apply {
                        putExtra("qr_code", intent.getStringExtra("qr_code"))
                        putExtra("ringtone_path", intent.getStringExtra("ringtone_path"))
                    }
                    applicationContext.startForegroundService(serviceIntent)

                    // Relaunch ring screen
                    val ringIntent = Intent(applicationContext, AlarmRingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("qr_code", intent.getStringExtra("qr_code"))
                        putExtra("ringtone_path", intent.getStringExtra("ringtone_path"))
                    }
                    applicationContext.startActivity(ringIntent)

                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }.start()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !alarmDismissed) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    val scanner = BarcodeScanning.getClient()
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val expectedQR = intent.getStringExtra("qr_code")
                            val scanned = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                            if (scanned != null && scanned.rawValue == expectedQR) {
                                alarmDismissed = true
                                countDownTimer?.cancel()
                                stopService(Intent(this, AlarmService::class.java))
                                setResult(RESULT_OK)
                                finish()
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }

            val camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )

            var torchOn = false
            torchBtn.setOnClickListener {
                torchOn = !torchOn
                camera.cameraControl.enableTorch(torchOn)
                torchBtn.imageTintList = ColorStateList.valueOf(
                    Color.parseColor(if (torchOn) "#00D4AA" else "#666666")
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun resumeAlarm() {
        val resumeIntent = Intent(this, AlarmService::class.java)
        resumeIntent.action = "RESUME"
        startService(resumeIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        executor.shutdown()
    }
}
