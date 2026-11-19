package com.grainmvp.android.submit

import java.util.Locale
import kotlin.math.roundToInt

/** The immature percentage shown to the technician: one decimal place, always with a dot (REQ-307). */
fun formatPercentage(percentage: Double): String = String.format(Locale.US, "%.1f", percentage)

/**
 * The Quality row on Result: AI confidence only, e.g. "86% confidence".
 * GOOD/BAD still drives auto-accept on the server but is not shown.
 */
fun qualityLabel(confidence: Double?): String =
    if (confidence == null) "No AI confidence" else "${(confidence * 100).roundToInt()}% confidence"

/** Banner text once the backend auto-accepts the sample; without numbers when this phone can't compute them. */
fun acceptedBannerText(average: SampleAverage?): String =
    if (average == null) "Sample accepted"
    else "Sample accepted · avg ${average.percentage.toPlainString()}% · ${average.grade}"
