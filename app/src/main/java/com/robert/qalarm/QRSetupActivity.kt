package com.robert.qalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
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
class QRSetupActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var captured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        } else {
            finish()
        }
    }

    private fun startCamera() {
        val density = resources.displayMetrics.density
        val btnSize = (56 * density).toInt()
        val margin = (16 * density).toInt()

        val container = FrameLayout(this)

        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
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
        container.addView(torchBtn)
        setContentView(container)

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
                if (mediaImage != null && !captured) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    val scanner = BarcodeScanning.getClient()
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val qr = barcodes.firstOrNull {
                                it.format == Barcode.FORMAT_QR_CODE
                            }
                            if (qr != null && qr.rawValue != null) {
                                captured = true
                                val result = Intent()
                                result.putExtra("qr_value", qr.rawValue)
                                setResult(RESULT_OK, result)
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

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
