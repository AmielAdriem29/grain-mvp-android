package com.grainmvp.android.session

/**
 * The technician and sample a capture belongs to. Collected on Start
 * Session and carried unchanged through Capture, Detect, Correction and
 * Submit, so screens pass one value instead of each field separately.
 */
data class SessionInfo(
    val technicianName: String,
    val sampleId: String
) {
    /** The same technician with the next sample ID, e.g. "S-0142" becomes "S-0143". */
    fun nextSample(): SessionInfo = copy(sampleId = incrementSampleId(sampleId))
}
