package com.grainmvp.android.session

/**
 * Increments the trailing digit run of a sample ID, preserving its
 * zero-padded width -- e.g. "S-0142" -> "S-0143". Backing the redesign's
 * screen 08 "Next sample · {incrementedId}" button, which keeps the same
 * technician and moves straight to Capture with a new sample ID instead
 * of sending the technician back through Start Session.
 *
 * If the increment needs an extra digit (e.g. "S-0999" -> "S-1000"), the
 * result naturally grows by one digit -- there's no fixed width to
 * preserve past what padStart guarantees as a minimum.
 *
 * If [id] has no trailing digit run at all, "-2" is appended as a
 * reasonable fallback (e.g. "SAMPLE" -> "SAMPLE-2").
 *
 * Pure and framework-free so it's covered by a fast JUnit test, same
 * pattern as CropMath.kt and CorrectionLogic.kt.
 */
fun incrementSampleId(id: String): String {
    val match = Regex("(\\d+)$").find(id) ?: return "$id-2"
    val digits = match.value
    val prefix = id.substring(0, match.range.first)
    val incremented = (digits.toLong() + 1).toString().padStart(digits.length, '0')
    return prefix + incremented
}
