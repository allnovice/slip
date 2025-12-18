package com.example.slip

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.util.Calendar

interface SleepClassifier {
    fun isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean
}

class HeuristicClassifier(private val settings: UserSettings) : SleepClassifier {
    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
        return settings.isRealSleep(startTimeMillis, durationSeconds)
    }
}

class MLClassifier(private val context: Context) : SleepClassifier {
    private var interpreter: Interpreter? = null

    // Replace these with the values from your Colab: print(scaler.mean_, scaler.scale_)
    private val DURATION_MEAN = 15000f  // Example value
    private val DURATION_SCALE = 5000f  // Example value

    init {
        try {
            interpreter = Interpreter(loadModelFile("sleep_model.tflite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
        val model = interpreter ?: return false // Fallback if model failed to load

        // 1. Extract features exactly like your Colab
        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startHour = calendar.get(Calendar.HOUR_OF_DAY).toFloat()
        
        calendar.timeInMillis = startTimeMillis + (durationSeconds * 1000)
        val endHour = calendar.get(Calendar.HOUR_OF_DAY).toFloat()

        // 2. Scale duration using your StandardScaler params
        val scaledDuration = (durationSeconds.toFloat() - DURATION_MEAN) / DURATION_SCALE

        // 3. Prepare Input (3 features: start_hour, end_hour, duration_seconds)
        val input = arrayOf(floatArrayOf(startHour, endHour, scaledDuration))
        
        // 4. Prepare Output (1 float for sigmoid probability)
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
    private val sessionCount: Int
) : SleepClassifier {
    
    private val heuristic = HeuristicClassifier(settings)
    private val mlModel = MLClassifier(context)

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
        // Use ML only if we have enough data (100 rows)
        return if (sessionCount >= 100) {
            mlModel.isRealSleep(startTimeMillis, durationSeconds)
        } else {
            heuristic.isRealSleep(startTimeMillis, durationSeconds)
        }
    }
}
