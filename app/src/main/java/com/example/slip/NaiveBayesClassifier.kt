package com.example.slip

import com.google.gson.Gson
import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class NaiveBayesModel(
    val classPriors: Map<String, Double>,
    val featureParams: Map<String, List<Pair<Double, Double>>>, // Class -> List of (mean, stddev)
    val durationMean: Double,
    val durationStd: Double
)

object NaiveBayesClassifier {

    private const val EPSILON = 1e-9 // To prevent division by zero or log(0)

    fun train(sessions: List<SleepSession>): NaiveBayesModel {
        if (sessions.isEmpty()) {
            return NaiveBayesModel(emptyMap(), emptyMap(), 0.0, 1.0)
        }
        
        // 1. Calculate global duration stats for Z-score normalization
        val allDurations = sessions.map { it.durationSeconds.toDouble() }
        val durationMean = allDurations.average()
        val durationStd = sqrt(allDurations.sumOf { (it - durationMean).pow(2) } / allDurations.size).takeIf { it > 0.0 } ?: 1.0

        // 2. Create feature vectors for all sessions
        val sessionsWithFeatures = sessions.map { session ->
            session to getFeatureVector(
                session.startTimeMillis,
                session.durationSeconds,
                session.targetBedtimeHour,
                durationMean,
                durationStd
            )
        }

        val sessionsByCategory = sessionsWithFeatures.groupBy { it.first.category }
        val totalSessions = sessions.size.toDouble()

        // 3. Calculate prior probabilities for each class
        val classPriors = sessionsByCategory.mapValues { (_, sessionList) ->
            (sessionList.size.toDouble() / totalSessions).takeIf { it > 0 } ?: EPSILON
        }

        // 4. Calculate mean and stddev for each feature in each class
        val featureParams = sessionsByCategory.mapValues { (_, sessionList) ->
            if (sessionList.isEmpty()) {
                List(6) { Pair(0.0, 1.0) }
            } else {
                val featuresList = sessionList.map { it.second } // Get the feature vectors
                val featuresTransposed = List(6) { featureIndex ->
                    featuresList.map { features -> features[featureIndex] }
                }

                featuresTransposed.map { featureValues ->
                    val mean = featureValues.average()
                    val stddev = sqrt(featureValues.sumOf { (it - mean).pow(2) } / featureValues.size).takeIf { it > 0 } ?: EPSILON
                    Pair(mean, stddev)
                }
            }
        }
        
        val allCategories = listOf(SleepSession.CATEGORY_SLEEP, SleepSession.CATEGORY_NAP, SleepSession.CATEGORY_IDLE)
        val finalFeatureParams = allCategories.associateWith { category ->
            featureParams[category] ?: List(6) { Pair(0.0, 1.0) }
        }
        val finalClassPriors = allCategories.associateWith { category ->
            classPriors[category] ?: EPSILON
        }

        return NaiveBayesModel(finalClassPriors, finalFeatureParams, durationMean, durationStd)
    }

    fun predict(model: NaiveBayesModel, session: SleepSession): String {
        val features = getFeatureVector(
            session.startTimeMillis,
            session.durationSeconds,
            session.targetBedtimeHour,
            model.durationMean,
            model.durationStd
        )

        var bestClass = SleepSession.CATEGORY_IDLE
        var maxLogProb = Double.NEGATIVE_INFINITY

        model.classPriors.forEach { (category, prior) ->
            if (prior > 0) {
                val logPrior = ln(prior)
                var logLikelihood = 0.0

                val params = model.featureParams[category] ?: return@forEach

                features.forEachIndexed { i, featureValue ->
                    val (mean, stddev) = params[i]
                    logLikelihood += gaussianLogProbability(featureValue, mean, stddev)
                }

                val totalLogProb = logPrior + logLikelihood
                if (totalLogProb > maxLogProb) {
                    maxLogProb = totalLogProb
                    bestClass = category
                }
            }
        }
        return bestClass
    }
    
    private fun getFeatureVector(
        startTimeMillis: Long,
        durationSeconds: Long,
        targetBedtimeHour: Int,
        durationMean: Double,
        durationStd: Double
    ): DoubleArray {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        val startTimeMinutes = hour * 60 + minute
        val targetBedtimeMinutes = targetBedtimeHour * 60

        val startOffset = (startTimeMinutes - targetBedtimeMinutes).toDouble()
        val durationZ = (durationSeconds.toDouble() - durationMean) / (durationStd.takeIf { it > 0 } ?: 1.0)
        
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekend = (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY).let { if (it) 1.0 else 0.0 }
        val isFriday = (dayOfWeek == java.util.Calendar.FRIDAY).let { if (it) 1.0 else 0.0 }
        val isSunday = (dayOfWeek == java.util.Calendar.SUNDAY).let { if (it) 1.0 else 0.0 }
        val startHour = hour.toDouble()

        return doubleArrayOf(startOffset, durationZ, isWeekend, isFriday, isSunday, startHour)
    }

    private fun gaussianLogProbability(x: Double, mean: Double, stddev: Double): Double {
        if (stddev < EPSILON) return 0.0
        val variance = stddev.pow(2)
        val exponent = -((x - mean).pow(2)) / (2 * variance)
        val logProb = exponent - ln(sqrt(2 * kotlin.math.PI) * stddev)
        return if (logProb.isFinite()) logProb else 0.0
    }

    fun toJson(model: NaiveBayesModel): String {
        return Gson().toJson(model)
    }

    fun fromJson(json: String): NaiveBayesModel? {
        return try {
            Gson().fromJson(json, NaiveBayesModel::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
