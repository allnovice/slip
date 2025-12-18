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
    fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, stats: Pair<Float, Float>? = null): Boolean
}

class HeuristicClassifier(private val settings: UserSettings) : SleepClassifier {
    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, stats: Pair<Float, Float>?): Boolean {
        return settings.isRealSleep(startTimeMillis, durationSeconds)
    }
}

class MLClassifier(private val context: Context, private val settings: UserSettings) : SleepClassifier {
    private var interpreter: Interpreter? = null

    // --- UNIVERSAL DEFAULTS ---
    // These are your training values. We use them as a "starting point" 
    // before the app has enough of the user's own data to calculate their personal stats.
    private val DEFAULT_MEAN = 1290.4127f 
    private val DEFAULT_SCALE = 3688.5928f 

    init {
        try {
            interpreter = Interpreter(loadModelFile("sleep_classifier_model.tflite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, stats: Pair<Float, Float>?): Boolean {
        val model = interpreter ?: return false 

        // 1. Convert Start/End to decimal hours (e.g. 10:30 PM = 22.5)
        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startMins = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0f
        
        val endCalendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis + (durationSeconds * 1000) }
        val endMins = endCalendar.get(Calendar.HOUR_OF_DAY) + endCalendar.get(Calendar.MINUTE) / 60.0f

        val isWeekend = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val targetBedtimeHour = (if (isWeekend) settings.weekendSleepStart else settings.weekdaySleepStart).hour.toFloat()
        
        // 2. Calculate offsets relative to Target Bedtime [-12, 12]
        // This handles the "Universal" time logic from your Colab perfectly
        var startOffset = (startMins - targetBedtimeHour) % 24
        if (startOffset < 0) startOffset += 24
        if (startOffset > 12) startOffset -= 24

        var endOffset = (endMins - targetBedtimeHour) % 24
        if (endOffset < 0) endOffset += 24
        if (endOffset > 12) endOffset -= 24

        // 3. DYNAMIC SCALING
        // This is the "Universal" duration logic. We use the stats calculated 
        // in the Repository from the user's local database.
        val mean = stats?.first ?: DEFAULT_MEAN
        val stdDev = stats?.second ?: DEFAULT_SCALE
        val scaledDuration = (durationSeconds.toFloat() - mean) / stdDev

        // 4. Run Inference [start_offset, end_offset, scaled_duration]
        val input = arrayOf(floatArrayOf(startOffset, endOffset, scaledDuration))
        val output = Array(1) { FloatArray(1) }

        model.run(input, output)
        
        return output[0][0] > 0.5f
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
    private val stats: Pair<Float, Float>?
) : SleepClassifier {
    
    private val heuristic = HeuristicClassifier(settings)
    private val mlModel = MLClassifier(context, settings)

    override fun isRealSleep(startTimeMillis: Long, durationSeconds: Long, statsArg: Pair<Float, Float>?): Boolean {
        // Use ML only after we have a good baseline of data
        return if (sessionCount >= 100) {
            mlModel.isRealSleep(startTimeMillis, durationSeconds, stats)
        } else {
            heuristic.isRealSleep(startTimeMillis, durationSeconds)
        }
    }
}
