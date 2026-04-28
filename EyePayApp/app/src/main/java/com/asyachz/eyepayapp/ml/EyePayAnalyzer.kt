package com.asyachz.eyepayapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class EyePayAnalyzer(
    context: Context,
    private val onResult: (String?) -> Unit
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter
    private var lastAnalyzedTimestamp = 0L

    private val classNames = mapOf(
        0 to "1000 рублей", 1 to "100 рублей", 2 to "10 рублей",
        3 to "2000 рублей", 4 to "200 рублей", 5 to "5000 рублей",
        6 to "500 рублей", 7 to "50 рублей", 8 to "5 рублей",
        9 to "Карта"
    )

    init {
        val modelBuffer = loadModelFile(context, "eyepay_model.tflite")
        val options = Interpreter.Options().apply { numThreads = 4 }
        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < 500) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val bitmap = image.toBitmap()
        val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees.toFloat())
        val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 640, 640, false)

        val inputBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // Выходной тензор [1, 300, 6]
        val outputBuffer = Array(1) { Array(300) { FloatArray(6) } }

        try {
            interpreter.run(inputBuffer, outputBuffer)
            processOutput(outputBuffer[0])
        } catch (e: Exception) {
            Log.e("EyePayAnalyzer", "Ошибка инференса: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun processOutput(boxes: Array<FloatArray>) {
        var maxConfidence = 0f
        var bestClassIndex = -1

        for (box in boxes) {
            val confidence = box[4]
            if (confidence > maxConfidence) {
                maxConfidence = confidence
                bestClassIndex = box[5].toInt()
            }
        }

        if (maxConfidence >= 0.5f && classNames.containsKey(bestClassIndex)) {
            val result = classNames[bestClassIndex]!!
            Log.d("EyePayAnalyzer", "Детекция: $result (Confidence: $maxConfidence)")
            onResult(result)
        } else {
            onResult(null)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(640 * 640)
        bitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)

        for (pixelValue in intValues) {
            byteBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
        }
        return byteBuffer
    }
}