package com.booklog.inventory

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var scanBox: View
    private var cameraControl: CameraControl? = null

    companion object {
        private const val TAG = "ScannerActivity"
        private const val PERMISSIONS_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val EXTRA_RESULT_ISBN = "extra_result_isbn"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        previewView = findViewById(R.id.previewView)
        scanBox = findViewById(R.id.scan_box)
        findViewById<ImageButton>(R.id.close_btn).setOnClickListener { finish() }

        // Tap to focus
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cameraControl?.startFocusAndMetering(action)
            }
            true
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { isbn ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_RESULT_ISBN, isbn)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                cameraControl = camera.cameraControl
                
                // Enable auto-focus by default
                val factory = previewView.meteringPointFactory
                val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
                    .apply {
                        // Attempt to keep focus centered
                    }
                    .build()
                cameraControl?.startFocusAndMetering(action)
                
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private inner class BarcodeAnalyzer(private val onIsbnDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build()
        private val scanner = BarcodeScanning.getClient(options)
        private var isScanning = true

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null && isScanning) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue ?: continue
                            if (rawValue.length == 13 && (rawValue.startsWith("978") || rawValue.startsWith("979"))) {
                                
                                val boundingBox = barcode.boundingBox ?: continue
                                if (isInsideScanBox(boundingBox, imageProxy, image)) {
                                    isScanning = false
                                    onIsbnDetected(rawValue)
                                    break
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        private fun isInsideScanBox(
            barcodeBoundingBox: Rect,
            imageProxy: ImageProxy,
            inputImage: InputImage
        ): Boolean {
            // Get screen coordinates of the scan box
            val location = IntArray(2)
            scanBox.getLocationOnScreen(location)
            val boxLeft = location[0]
            val boxTop = location[1]
            val boxRight = boxLeft + scanBox.width
            val boxBottom = boxTop + scanBox.height

            // Tolerance to make scanning easier (allow center of barcode to be slightly outside)
            val tolerance = 40 // pixels

            // PreviewView size
            val previewWidth = previewView.width
            val previewHeight = previewView.height

            // Image dimensions (considering rotation)
            val rotationDegrees = inputImage.rotationDegrees
            val imageWidth: Int
            val imageHeight: Int
            
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                imageWidth = inputImage.height
                imageHeight = inputImage.width
            } else {
                imageWidth = inputImage.width
                imageHeight = inputImage.height
            }

            // Calculate scale factors between image and preview
            // CameraX FillScale logic: it might crop the image to fit the view
            val scaleX = previewWidth.toFloat() / imageWidth
            val scaleY = previewHeight.toFloat() / imageHeight
            
            // For FIT_CENTER or similar, we use the larger scale to avoid "dead" areas in detection
            val scale = maxOf(scaleX, scaleY)

            // Map barcode center to screen coordinates
            // Note: This is a simplification. For production, use CoordinateTransform from CameraX.
            val barcodeCenterX = barcodeBoundingBox.centerX() * scale
            val barcodeCenterY = barcodeBoundingBox.centerY() * scale

            // Offset by PreviewView location on screen
            val previewLocation = IntArray(2)
            previewView.getLocationOnScreen(previewLocation)
            val screenBarcodeCenterX = barcodeCenterX + previewLocation[0]
            val screenBarcodeCenterY = barcodeCenterY + previewLocation[1]

            // Check if barcode center is within the scan box (with tolerance)
            return screenBarcodeCenterX >= (boxLeft - tolerance) && 
                   screenBarcodeCenterX <= (boxRight + tolerance) &&
                   screenBarcodeCenterY >= (boxTop - tolerance) && 
                   screenBarcodeCenterY <= (boxBottom + tolerance)
        }
    }
}
