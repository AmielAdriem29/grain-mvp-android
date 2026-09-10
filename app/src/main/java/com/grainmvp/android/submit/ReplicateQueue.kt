package com.grainmvp.android.submit

import android.graphics.Bitmap
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A submission that failed to send and is being held so the technician
 * can keep scanning and retry it later, rather than losing the
 * correction work already done on it -- see FailureScreen's "Keep on
 * device" and MainActivity's `replicateQueue`.
 *
 * In-memory only for now: lost if the app is killed. A deliberate scope
 * decision for this first version, not an oversight -- a disk-backed
 * queue that survives an app restart is tracked as follow-up work (see
 * docs/IMPLEMENTATION.md).
 */
data class QueuedReplicate(
    val image: Bitmap,
    val technicianName: String,
    val sampleId: String,
    val aiPredictedGrains: List<GrainBox>,
    val confirmedGrains: List<GrainBox>,
    val weight: String
)

/**
 * Calls POST /api/replicate for [item]. Shared by SubmitScreen's own
 * submit flow and MainActivity's "send queued" retry, so the multipart/
 * JSON-encoding incantation (see SPEC.md) exists in exactly one place
 * rather than being duplicated at both call sites.
 */
suspend fun submitQueuedReplicate(item: QueuedReplicate): ReplicateResponse {
    return RetrofitClient.apiService.submitReplicate(
        image = item.image.toImagePart(),
        technicianName = item.technicianName.toTextPart(),
        sampleId = item.sampleId.toTextPart(),
        aiPredictedGrains = Json.encodeToString(item.aiPredictedGrains).toTextPart(),
        confirmedGrains = Json.encodeToString(item.confirmedGrains).toTextPart(),
        weight = item.weight.toTextPart()
    )
}
