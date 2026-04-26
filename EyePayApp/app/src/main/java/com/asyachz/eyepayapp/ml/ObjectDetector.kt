package com.asyachz.eyepayapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer

class ObjectDetector(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = listOf("Banknote", "Card")

    init {
        try {
            val model: MappedByteBuffer = FileUtil.loadMappedFile(context, "eyepay_model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors())
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error loading model: ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap): String? {
        val interp = interpreter ?: return null
        
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()
        val processedImage = imageProcessor.process(tensorImage)

        val outputTensor = interp.getOutputTensor(0)
        val outputShape = outputTensor.shape() // [1, 6, 8400] or [1, 25200, 7]
        
        var maxConf = 0f
        var detectedClassIdx = -1

        if (outputShape[1] < outputShape[2]) {
            // YOLOv8 format: [1, 4 + classes, boxes]
            val output = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            interp.run(processedImage.buffer, output)
            
            for (boxIdx in 0 until outputShape[2]) {
                for (classIdx in 0 until (outputShape[1] - 4)) {
                    val conf = output[0][classIdx + 4][boxIdx]
                    if (conf > maxConf) {
                        maxConf = conf
                        detectedClassIdx = classIdx
                    }
                }
            }
        } else {
            // YOLOv5 format: [1, boxes, 5 + classes]
            val output = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            interp.run(processedImage.buffer, output)
            
            for (boxIdx in 0 until outputShape[1]) {
                val objConf = output[0][boxIdx][4]
                for (classIdx in 0 until (outputShape[2] - 5)) {
                    val conf = output[0][boxIdx][classIdx + 5] * objConf
                    if (conf > maxConf) {
                        maxConf = conf
                        detectedClassIdx = classIdx
                    }
                }
            }
        }

        return if (maxConf > 0.45f && detectedClassIdx in labels.indices) {
            labels[detectedClassIdx]
        } else {
            null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
