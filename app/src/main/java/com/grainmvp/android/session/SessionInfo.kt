package com.grainmvp.android.session

/**
 * The NCT entry and sample a capture belongs to. Collected on Start
 * Session and carried unchanged through Capture, Detect, Correction and
 * Submit, so screens pass one value instead of each field separately.
 *
 * An entry (season + index number) has up to two samples; [sampleNo] is
 * 1 or 2.
 */
data class SessionInfo(
    val technicianName: String,
    val season: String,
    val indexNumber: String,
    val selectionName: String,
    val sampleId: String,
    val sampleNo: Int = 1
) {
    /**
     * Starting point for the next entry: same technician and season, the
     * next sample ID (e.g. "S-0142" becomes "S-0143"), sample number 1.
     * The index number and selection name differ per entry, so they are
     * cleared and the technician enters them again on Start Session.
     */
    fun nextSample(): SessionInfo = copy(
        sampleId = incrementSampleId(sampleId),
        indexNumber = "",
        selectionName = "",
        sampleNo = 1
    )

    /** The second sample of the same entry: same entry details, next sample ID, sample number 2. */
    fun secondSample(): SessionInfo = copy(
        sampleId = incrementSampleId(sampleId),
        sampleNo = 2
    )
}
