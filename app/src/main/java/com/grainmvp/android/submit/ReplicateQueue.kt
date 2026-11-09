package com.grainmvp.android.submit

import android.graphics.Bitmap
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import com.grainmvp.android.session.SessionInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant

/**
 * A submission that failed to send and is being held so the technician
 * can keep scanning and retry it later, rather than losing the
 * correction work already done on it -- see FailureScreen's "Keep on
 * device" and MainActivity's `replicateQueue`.
 *
 * [capturedAt] is when the photo was taken and [submittedAt] is when the
 * technician first tapped Submit. Both are fixed when the item is built,
 * so a retry (from the Failure screen or the queue) sends the original
 * values rather than the time of the retry.
 *
 * In-memory only for now: lost if the app is killed. A deliberate scope
 * decision for this first version, not an oversight -- a disk-backed
 * queue that survives an app restart is tracked as follow-up work (see
 * docs/IMPLEMENTATION.md).
 */
data class QueuedReplicate(
    val image: Bitmap,
    val session: SessionInfo,
    val aiPredictedGrains: List<GrainBox>,
    val confirmedGrains: List<GrainBox>,
    val weight: String,
    val capturedAt: Instant,
    val submittedAt: Instant
)

/** HTTP status the backend returns when the season, index number and sample number already exist. */
const val HTTP_CONFLICT = 409

/** Whether [error] is the backend's 409 for a duplicate sample (REQ-405). */
fun isDuplicateSampleError(error: Throwable): Boolean =
    error is HttpException && error.code() == HTTP_CONFLICT

/**
 * Calls POST /api/replicate for [item]. Shared by SubmitScreen's own
 * submit flow and MainActivity's "send queued" retry, so the multipart/
 * JSON-encoding incantation (see SPEC.md) exists in exactly one place
 * rather than being duplicated at both call sites.
 *
 * Timestamps use [Instant.toString], which is ISO-8601 in UTC.
 */
suspend fun submitQueuedReplicate(item: QueuedReplicate): ReplicateResponse {
    return RetrofitClient.apiService.submitReplicate(
        image = item.image.toImagePart(),
        technicianName = item.session.technicianName.toTextPart(),
        sampleId = item.session.sampleId.toTextPart(),
        aiPredictedGrains = Json.encodeToString(item.aiPredictedGrains).toTextPart(),
        confirmedGrains = Json.encodeToString(item.confirmedGrains).toTextPart(),
        weight = item.weight.toTextPart(),
        season = item.session.season.toTextPart(),
        indexNumber = item.session.indexNumber.toTextPart(),
        selectionName = item.session.selectionName.toTextPart(),
        sampleNo = item.session.sampleNo.toString().toTextPart(),
        capturedAt = item.capturedAt.toString().toTextPart(),
        submittedAt = item.submittedAt.toString().toTextPart()
    )
}
