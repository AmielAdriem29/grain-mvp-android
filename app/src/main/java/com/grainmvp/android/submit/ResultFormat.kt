package com.grainmvp.android.submit

import com.grainmvp.android.network.ReplicateQuality
import java.util.Locale
import kotlin.math.roundToInt

/** The immature percentage shown to the technician: one decimal place, always with a dot (REQ-307). */
fun formatPercentage(percentage: Double): String = String.format(Locale.US, "%.1f", percentage)

/**
 * The Quality row on Result, e.g. "GOOD · 86% confidence". Null when the
 * backend sent no quality (an older backend), so the row is hidden.
 */
fun qualityLabel(quality: ReplicateQuality?, confidence: Double?, manuallyOverridden: Boolean): String? {
    val verdict = when (quality) {
        ReplicateQuality.GOOD -> "GOOD"
        ReplicateQuality.BAD -> "BAD"
        null -> return null
    }
    val detail = when {
        manuallyOverridden -> "manually verified"
        confidence == null -> "no AI confidence"
        else -> "${(confidence * 100).roundToInt()}% confidence"
    }
    return "$verdict · $detail"
}

/** Banner text once the backend auto-accepts the sample; without numbers when this phone can't compute them. */
fun acceptedBannerText(average: SampleAverage?): String =
    if (average == null) "Sample accepted"
    else "Sample accepted · avg ${average.percentage.toPlainString()}% · ${average.grade}"
