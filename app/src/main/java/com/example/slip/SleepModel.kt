package com.example.slip

import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.Calendar

interface SleepClassifier {
    fun classify(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String?
}

class HeuristicClassifier : SleepClassifier {
    override fun classify(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String {
        val hours = durationSeconds / 3600.0

        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val startMinsTotal = calendar.get(Calendar.HOUR_OF_DAY) * 60.0f + calendar.get(Calendar.MINUTE)

        var startOffset = (startMinsTotal / 60.0f - targetHour.toFloat()) % 24
        if (startOffset < -12) startOffset += 24
        if (startOffset > 12) startOffset -= 24

        val result = when {
            hours >= 4.0 && startOffset >= -2.0 && startOffset <= 5.0 -> SleepSession.CATEGORY_SLEEP
            startOffset >= -11.0 && startOffset <= -5.0 -> SleepSession.CATEGORY_NAP
            else -> SleepSession.CATEGORY_IDLE
        }

        return result
    }
}

class UserCustomClassifier(
    private val modelPath: String,
    private val means: List<Float>?,
    private val stds: List<Float>?
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

    fun isModelStructureValid(): Boolean {
        return interpreter != null && inputFeatureCount >= 2 && outputClassCount == 3
    }

    fun isValid(): Boolean {
        if (!isModelStructureValid()) return false
        val statsProvided = means != null && stds != null && means.size >= inputFeatureCount && stds.size >= inputFeatureCount
        return statsProvided
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

        val rawFeatures = mutableListOf<Float>()
        rawFeatures.add(startOffset)
        rawFeatures.add(endOffset)
        rawFeatures.add(durationSeconds.toFloat())
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        rawFeatures.add(if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 1.0f else 0.0f)
        rawFeatures.add(startMinsTotal / 1440.0f)
        rawFeatures.add(endMinsTotal / 1440.0f)

        val features = mutableListOf<Float>()
        for (i in 0 until inputFeatureCount) {
            val raw = rawFeatures.getOrElse(i) { 0.0f }
            val mean = means?.getOrNull(i) ?: 0.0f
            val std = stds?.getOrNull(i) ?: 1.0f
            features.add((raw - mean) / std)
        }

        val input = arrayOf(features.toFloatArray())
        val output = Array(1) { FloatArray(outputClassCount) }
        model.run(input, output)

        val maxIdx = output[0].indices.maxByOrNull { output[0][it] } ?: 2
        return when (maxIdx) {
            0 -> SleepSession.CATEGORY_SLEEP
            1 -> SleepSession.CATEGORY_NAP
            else -> SleepSession.CATEGORY_IDLE
        }
    }
}

class ModelLabEngine(
    private val customPath: String?,
    private val customMeans: List<Float>?,
    private val customStds: List<Float>?
) {
    private val customClassifier: UserCustomClassifier? by lazy {
        if (customPath != null) UserCustomClassifier(customPath, customMeans, customStds) else null
    }

    fun runAll(startTimeMillis: Long, durationSeconds: Long, targetHour: Int): String {
        val heuristic = HeuristicClassifier().classify(startTimeMillis, durationSeconds, targetHour)

        val mlResult = customClassifier?.classify(startTimeMillis, durationSeconds, targetHour)
        if (mlResult != null) return mlResult

        return heuristic
    }
}
