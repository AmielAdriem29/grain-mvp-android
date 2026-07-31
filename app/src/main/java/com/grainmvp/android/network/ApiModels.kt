package com.grainmvp.android.network

import kotlinx.serialization.Serializable

/**
 * Field names must match the backend/dashboard exactly:
 * x, y, width, height, confidence, action (lowercase, no underscores).
 * action is one of "kept" | "removed" | "added" | null.
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
    val action: String? = null
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