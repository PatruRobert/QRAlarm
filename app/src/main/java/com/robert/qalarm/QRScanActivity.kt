package com.robert.qalarm

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
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

        container.addView(previewView)
        container.addView(timerText)
        setContentView(container)

        // 30 second countdown — if it expires, close camera and resume alarm
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000
                timerText.text = "00:${String.format(java.util.Locale.getDefault(), "%02d", secs)}"
            }
            override fun onFinish() {
                if (!alarmDismissed) {
                    resumeAlarm()
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
            camera.cameraInfo.torchState.observe(this) { state ->
                if (state == TorchState.OFF) {
                    camera.cameraControl.enableTorch(true)
                }
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