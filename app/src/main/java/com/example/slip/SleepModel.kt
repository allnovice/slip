package com.example.slip

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.util.Calendar

interface SleepClassifier {
    fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean
}

class HeuristicClassifier(private val settings: UserSettings) : SleepClassifier {
    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean {
        return settings.isRealSleep(startTimeMillis, durationSeconds)
    }
}

class MLClassifier(
    private val context: Context,
    private val durationMean: Float,
    private val durationStd: Float
) : SleepClassifier {
    private var interpreter: Interpreter? = null

    init {
        try {
            // This is the "heavy" part that we want to avoid for short sessions
            interpreter = Interpreter(loadModelFile("sleep_classifier_model.tflite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean {
        val model = interpreter ?: return false

        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startHour = calendar.get(Calendar.HOUR_OF_DAY).toFloat()
        
        calendar.timeInMillis = startTimeMillis + (durationSeconds * 1000)
        val endHour = calendar.get(Calendar.HOUR_OF_DAY).toFloat()

        val scaledDuration = (durationSeconds.toFloat() - durationMean) / durationStd

        val input = arrayOf(floatArrayOf(startHour, endHour, scaledDuration, targetHour.toFloat()))
        val output = Array(1) { FloatArray(1) }

        model.run(input, output)

        val probability = output[0][0]
        return probability > 0.5f
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}

class DynamicSleepClassifier(
    private val context: Context,
    private val settings: UserSettings,
    private val sessionCount: Int,
    private val durationStats: Pair<Float, Float>
) : SleepClassifier {
    
    private val heuristic = HeuristicClassifier(settings)
    
    // The ML model is only loaded if this property is accessed
    private val mlModel by lazy { 
        MLClassifier(context, durationStats.first, durationStats.second) 
    }

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): Boolean {
        // --- PERFORMANCE OPTIMIZATION ---
        // If the session is shorter than 1 hour, it's definitely not sleep.
        // By returning early, we never access 'mlModel', so the TFLite model is NEVER loaded.
        if (durationSeconds < 3600) {
            return false
        }

        return if (sessionCount >= 100) {
            mlModel.isRealSleep(startTimeMillis, durationSeconds, targetHour)
        } else {
            heuristic.isRealSleep(startTimeMillis, durationSeconds, targetHour)
        }
    }
}
