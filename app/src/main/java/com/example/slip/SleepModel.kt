package com.example.slip

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Calendar

interface SleepClassifier {
    fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean
}

/**
 * Model 1: The "Dumb" Model (Heuristic Rules)
 */
class HeuristicClassifier(private val settings: UserSettings) : SleepClassifier {
    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean {
        return settings.isRealSleep(startTimeMillis, durationSeconds)
    }
}

/**
 * Base class for TFLite models to avoid code duplication
 */
open class BaseMLClassifier(
    private val context: Context,
    private val modelFileName: String?,
    private val customPath: String?,
    private val durationMean: Float,
    private val durationStd: Float
) : SleepClassifier {
    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = when {
                customPath != null -> Interpreter(File(customPath))
                modelFileName != null -> Interpreter(loadModelFile(modelFileName))
                else -> null
            }
        } catch (_: Exception) {
            // Error logged silently to avoid console noise
        }
    }

    fun isValid(): Boolean {
        return try {
            val interp = interpreter ?: return false
            val inputShape = interp.getInputTensor(0).shape()
            val outputShape = interp.getOutputTensor(0).shape()
            inputShape[1] == 3 && outputShape[1] == 1
        } catch (_: Exception) {
            false
        }
    }

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean {
        val model = interpreter ?: return false
        if (durationSeconds < 3600) return false 

        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startMins = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0f
        
        val endCalendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis + (durationSeconds * 1000) }
        val endMins = endCalendar.get(Calendar.HOUR_OF_DAY) + endCalendar.get(Calendar.MINUTE) / 60.0f

        var startOffset = (startMins - targetHour.toFloat()) % 24
        if (startOffset < 0) startOffset += 24
        if (startOffset > 12) startOffset -= 24

        var endOffset = (endMins - targetHour.toFloat()) % 24
        if (endOffset < 0) endOffset += 24
        if (endOffset > 12) endOffset -= 24

        val scaledDuration = (durationSeconds.toFloat() - durationMean) / durationStd
        val input = arrayOf(floatArrayOf(startOffset, endOffset, scaledDuration))
        val output = Array(1) { FloatArray(1) }

        model.run(input, output)
        return output[0][0] > 0.5f
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}

/**
 * Model 2: The Default System ML Model
 */
class DefaultMLClassifier(context: Context, mean: Float, std: Float) : 
    BaseMLClassifier(context, "sleep_classifier_model.tflite", null, mean, std)

/**
 * Model 3: The Custom User ML Model
 */
class CustomMLClassifier(context: Context, path: String, mean: Float, std: Float) : 
    BaseMLClassifier(context, null, path, mean, std)

/**
 * The Brain that runs all models for the "Model Lab" comparison
 */
data class LabResult(val dumb: Boolean, val defaultMl: Boolean, val customMl: Boolean)

class ModelLabEngine(
    private val context: Context,
    private val settings: UserSettings,
    private val systemStats: Pair<Float, Float>,
    private val customPath: String?,
    private val customStats: Pair<Float, Float>
) {
    fun runAll(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): LabResult {
        val dumb = HeuristicClassifier(settings).isRealSleep(startTimeMillis, durationSeconds, targetHour)
        
        val defaultMl = DefaultMLClassifier(context, systemStats.first, systemStats.second)
            .isRealSleep(startTimeMillis, durationSeconds, targetHour)
            
        val customMl = if (customPath != null) {
            CustomMLClassifier(context, customPath, customStats.first, customStats.second)
                .isRealSleep(startTimeMillis, durationSeconds, targetHour)
        } else false

        return LabResult(dumb, defaultMl, customMl)
    }
}
