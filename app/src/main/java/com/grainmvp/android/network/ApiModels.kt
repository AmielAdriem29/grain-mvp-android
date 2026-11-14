package com.grainmvp.android.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the technician did to a grain box during correction.
 *
 * Serialized as the lowercase strings the backend and dashboard expect:
 * "kept", "removed", "added". A box nobody has touched yet has a null
 * action.
 */
@Serializable
enum class GrainAction {
    @SerialName("kept")
    KEPT,
    @SerialName("removed")
    REMOVED,
    @SerialName("added")
    ADDED
}

/**
 * Field names must match the backend/dashboard exactly:
 * x, y, width, height, confidence, action (lowercase, no underscores).
 * action is a [GrainAction] or null.
 *
 * This same shape is used in three places with different meanings:
 *  - as returned by /api/predict (action always null, fresh AI boxes)
 *  - as the "aiPredictedGrains" field sent to /api/replicate (unmodified
 *    copy of what /api/predict returned)
 *  - as the "confirmedGrains" field sent to /api/replicate (after the
 *    technician's corrections — action populated per box)
 */
@Serializable
data class GrainBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float? = null,
    val action: GrainAction? = null
)

/** Response shape from POST /api/predict. */
@Serializable
data class PredictResponse(
    val imageId: String,
    val grains: List<GrainBox>
)

/** Backend verdict on one photo: GOOD when AI confidence is high enough or it was manually overridden. */
@Serializable
enum class ReplicateQuality {
    @SerialName("good")
    GOOD,
    @SerialName("bad")
    BAD
}

/**
 * Response shape from POST /api/replicate. Every field after [grade] is
 * optional so the app still works against a backend that does not send
 * it yet. The replicate fields come from backend `dev`: the server numbers
 * photos of the same sample (-01, -02 ...) and auto-accepts the sample
 * once it has two GOOD photos.
 */
@Serializable
data class ReplicateResponse(
    val id: String,
    val percentage: Double,
    val grade: String,
    val meetsRecommendedValue: Boolean? = null,
    val replicateNo: Int = 0,
    /** Sample ID plus the replicate suffix, e.g. "S-0142-02". */
    val replicateLabel: String = "",
    /** Mean AI box confidence, 0 to 1. Null when the AI returned no boxes. */
    val confidence: Double? = null,
    val quality: ReplicateQuality? = null,
    /** "accepted" when this photo completed the sample, otherwise "review". */
    val reviewStatus: String? = null
) {
    val isAccepted: Boolean get() = reviewStatus == "accepted"
}
