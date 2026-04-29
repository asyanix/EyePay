package com.asyachz.eyepayapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

class EyePayAnalyzer(
    context: Context,
    private val onDetectionResult: (String?) -> Unit,
    private val onOcrResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter
    private var lastAnalyzedTimestamp = 0L
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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

        val outputBuffer = Array(1) { Array(300) { FloatArray(6) } }

        try {
            interpreter.run(inputBuffer, outputBuffer)
            processOutput(outputBuffer[0], rotatedBitmap)
        } catch (e: Exception) {
            Log.e("EyePayAnalyzer", "Ошибка инференса: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun processOutput(boxes: Array<FloatArray>, rotatedBitmap: Bitmap) {
        var maxConfidence = 0f
        var bestBox: FloatArray? = null

        for (box in boxes) {
            val confidence = box[4]
            if (confidence > maxConfidence) {
                maxConfidence = confidence
                bestBox = box
            }
        }

        if (bestBox != null && maxConfidence >= 0.5f) {
            val classId = bestBox[5].toInt()
            val className = classNames[classId]

            if (className != null) {
                onDetectionResult(className)

                if (classId == 9 && maxConfidence > 0.7f) {
                    cropAndRecognizeText(bestBox, rotatedBitmap)
                }
            } else {
                onDetectionResult(null)
            }
        } else {
            onDetectionResult(null)
        }
    }

    private fun cropAndRecognizeText(box: FloatArray, originalBitmap: Bitmap) {
        Log.d("OCR_DEBUG", "Raw box from YOLO: ${box.joinToString()}")

        val cx = box[0]
        val cy = box[1]
        val w = box[2]
        val h = box[3]

        val bitmapWidth = originalBitmap.width
        val bitmapHeight = originalBitmap.height
        val isNormalized = cx <= 1f && cy <= 1f

        val left: Int
        val top: Int
        val cropWidth: Int
        val cropHeight: Int

        if (isNormalized) {
            left = ((cx - w / 2) * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
            top = ((cy - h / 2) * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
            cropWidth = (w * bitmapWidth).toInt().coerceIn(32, bitmapWidth - left)
            cropHeight = (h * bitmapHeight).toInt().coerceIn(32, bitmapHeight - top)
        } else {
            val scaleX = bitmapWidth / 640f
            val scaleY = bitmapHeight / 640f

            left = ((cx - w / 2) * scaleX).toInt().coerceIn(0, bitmapWidth - 1)
            top = ((cy - h / 2) * scaleY).toInt().coerceIn(0, bitmapHeight - 1)
            cropWidth = (w * scaleX).toInt().coerceIn(32, bitmapWidth - left)
            cropHeight = (h * scaleY).toInt().coerceIn(32, bitmapHeight - top)
        }

        Log.d("OCR_DEBUG", "Final crop: L=$left, T=$top, W=$cropWidth, H=$cropHeight on Bitmap ${bitmapWidth}x${bitmapHeight}")

        try {
            if (cropWidth < 32 || cropHeight < 32) {
                Log.e("OCR_DEBUG", "Crop too small, skipping OCR")
                return
            }

            val croppedBitmap = Bitmap.createBitmap(originalBitmap, left, top, cropWidth, cropHeight)
            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text
                    if (recognizedText.isNotBlank()) {
                        Log.d("OCR_DEBUG", "Found text:\n$recognizedText")
                        onOcrResult(recognizedText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OCR_DEBUG", "OCR Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("EyePayAnalyzer", "Bitmap creation failed: ${e.message}")
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