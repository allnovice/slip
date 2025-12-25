package com.example.slip

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.Calendar

interface SleepClassifier {
    fun classify(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String?
}

class HeuristicClassifier(private val settings: UserSettings) : SleepClassifier {
    override fun classify(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String {
        val hours = durationSeconds / 3600.0
        
        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startMinsTotal = calendar.get(Calendar.HOUR_OF_DAY) * 60.0f + calendar.get(Calendar.MINUTE)
        
        // Calculate offset from target bedtime (-12 to 12)
        var startOffset = (startMinsTotal / 60.0f - targetHour.toFloat()) % 24
        if (startOffset < -12) startOffset += 24
        if (startOffset > 12) startOffset -= 24

        return when {
            // 1. SLEEP: Long duration and starts in window (-2h to +4h)
            // Example: Bedtime 10PM -> Window 8PM to 2AM
            hours >= 4.0 && startOffset >= -2.0 && startOffset <= 4.0 -> {
                SleepSession.CATEGORY_SLEEP
            }

            // 2. NAP: Starts in afternoon window (5 to 10 hours BEFORE bedtime)
            // Example: Bedtime 10PM -> Window 12PM to 5PM
            // Removed: "hours < 4.0" check to avoid contradiction with IDLE
            startOffset >= -10.0 && startOffset <= -5.0 -> {
                SleepSession.CATEGORY_NAP
            }

            // 3. IDLE: Anything else (including short night sessions or morning junk)
            else -> SleepSession.CATEGORY_IDLE
        }
    }
}

class UserCustomClassifier(
    private val modelPath: String,
    private val durationMean: Float?,
    private val durationStd: Float?
) : SleepClassifier {
    private var interpreter: Interpreter? = null
    var inputFeatureCount: Int = 0
    var outputClassCount: Int = 0

    init {
        try {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                interpreter = Interpreter(modelFile)
                interpreter?.let {
                    inputFeatureCount = it.getInputTensor(0).shape()[1]
                    outputClassCount = it.getOutputTensor(0).shape()[1]
                }
            }
        } catch (_: Exception) { }
    }

    fun isValid(): Boolean {
        val interp = interpreter ?: return false
        val needsStats = inputFeatureCount >= 3
        val statsProvided = durationMean != null && durationStd != null
        return inputFeatureCount >= 2 && (!needsStats || statsProvided) && outputClassCount == 3
    }

    override fun classify(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String? {
        val model = interpreter ?: return null
        if (!isValid()) return null

        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startMinsTotal = calendar.get(Calendar.HOUR_OF_DAY) * 60.0f + calendar.get(Calendar.MINUTE)
        val endCalendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis + (durationSeconds * 1000) }
        val endMinsTotal = endCalendar.get(Calendar.HOUR_OF_DAY) * 60.0f + endCalendar.get(Calendar.MINUTE)

        var startOffset = (startMinsTotal / 60.0f - targetHour.toFloat()) % 24
        if (startOffset < -12) startOffset += 24
        if (startOffset > 12) startOffset -= 24

        var endOffset = (endMinsTotal / 60.0f - targetHour.toFloat()) % 24
        if (endOffset < -12) endOffset += 24
        if (endOffset > 12) endOffset -= 24

        val features = mutableListOf<Float>()
        features.add(startOffset) 
        features.add(endOffset)   
        
        if (inputFeatureCount >= 3) {
            val scaled = (durationSeconds.toFloat() - durationMean!!) / durationStd!!
            features.add(scaled)
        }
        if (inputFeatureCount >= 4) {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            features.add(if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 1.0f else 0.0f)
        }
        if (inputFeatureCount >= 5) {
            features.add(startMinsTotal / 1440.0f)
        }
        if (inputFeatureCount >= 6) {
            features.add(endMinsTotal / 1440.0f)
        }
        
        while (features.size < inputFeatureCount) {
            features.add(0.0f)
        }

        val input = arrayOf(features.toFloatArray())
        val output = Array(1) { FloatArray(3) }
        model.run(input, output)

        val maxIdx = output[0].indices.maxByOrNull { output[0][it] } ?: 2
        return when(maxIdx) {
            0 -> SleepSession.CATEGORY_SLEEP
            1 -> SleepSession.CATEGORY_NAP
            else -> SleepSession.CATEGORY_IDLE
        }
    }
}

class ModelLabEngine(
    private val settings: UserSettings,
    private val customPath: String?,
    private val customMean: Float?,
    private val customStd: Float?
) {
    fun runAll(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String {
        val heuristic = HeuristicClassifier(settings).classify(startTimeMillis, durationSeconds, targetHour)
        if (customPath != null) {
            val custom = UserCustomClassifier(customPath, customMean, customStd)
            val mlResult = custom.classify(startTimeMillis, durationSeconds, targetHour)
            if (mlResult != null) return mlResult
        }
        return heuristic
    }
}
