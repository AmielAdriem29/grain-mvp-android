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

    /** The second sample of the same entry: same entry details, sample number 2 and its generated sample ID. */
    fun secondSample(): SessionInfo = copy(
        sampleId = sampleIdFor(season, indexNumber, 2),
        sampleNo = 2
    )
}

/**
 * Whether an entry (season + index number, exact match like the backend
 * grouping) already has a sample started this session. The Another sample
 * form blocks it: sample 2 is started from Result, never by retyping.
 */
fun isEntryInSession(season: String, indexNumber: String, started: List<SessionInfo>): Boolean =
    started.any { it.season == season && it.indexNumber == indexNumber }
