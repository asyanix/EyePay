package com.asyachz.eyepayapp.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class DetectionAnalyzer(
    private val detector: ObjectDetector,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTime = 0L
    private var lastDetectionTime = 0L
    private val frameInterval = 800L
    private val cooldownInterval = 4000L

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAnalysisTime < frameInterval) {
            image.close()
            return
        }
        
        lastAnalysisTime = currentTime

        // Use built-in toBitmap() from CameraX
        val bitmap = image.toBitmap()
        
        // Rotate bitmap based on image rotation
        val rotatedBitmap = if (image.imageInfo.rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val result = detector.detect(rotatedBitmap)
        
        if (result != null && currentTime - lastDetectionTime > cooldownInterval) {
            lastDetectionTime = currentTime
            Log.d("DetectionAnalyzer", "Detected: $result")
            onResult(result)
        }

        image.close()
    }
}
