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
    @SerialName("kept") KEPT,
    @SerialName("removed") REMOVED,
    @SerialName("added") ADDED
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

/** Response shape from POST /api/replicate. */
@Serializable
data class ReplicateResponse(
    val id: String,
    val percentage: Double,
    val grade: String
)