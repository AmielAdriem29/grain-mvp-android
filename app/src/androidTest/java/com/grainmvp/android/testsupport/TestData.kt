package com.grainmvp.android.testsupport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.grainmvp.android.ImageSpec
import com.grainmvp.android.network.ApiService
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.PredictResponse
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.submit.QueuedReplicate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okio.Buffer
import java.security.MessageDigest
import java.time.Instant
import kotlin.random.Random

/** Unique per test run, so records from different runs never collide. */
val RUN_ID: String = java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase()

/**
 * A 1024 x 1024 picture of rice-like grains, always drawn the same way, so
 * a run is repeatable without a camera.
 */
fun syntheticSampleImage(seed: Int = 42): Bitmap {
    val size = ImageSpec.SIZE_PX
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(32, 34, 40))
    val random = Random(seed)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(60) {
        val cx = random.nextInt(60, size - 60).toFloat()
        val cy = random.nextInt(60, size - 60).toFloat()
        val green = random.nextInt(150, 230)
        paint.color = Color.rgb(230, green, 150)
        canvas.save()
        canvas.rotate(random.nextInt(0, 180).toFloat(), cx, cy)
        canvas.drawOval(RectF(cx - 30, cy - 11, cx + 30, cy + 11), paint)
        canvas.restore()
    }
    return bitmap
}

/**
 * The backend groups photos into a sample by season + index number +
 * sample no, and auto-accepts a sample after two GOOD photos. Each test
 * submission gets its own index number so tests never share a sample.
 */
fun testSession(sampleId: String, sampleNo: Int = 1) = SessionInfo(
    technicianName = "Android Test",
    season = "2026WS",
    indexNumber = "IT-$sampleId",
    selectionName = "Integration Test",
    sampleId = sampleId,
    sampleNo = sampleNo
)

fun testReplicate(
    session: SessionInfo,
    aiPredictedGrains: List<GrainBox>,
    confirmedGrains: List<GrainBox>,
    weight: String,
    image: Bitmap = syntheticSampleImage()
): QueuedReplicate {
    val capturedAt = Instant.now().minusSeconds(30)
    return QueuedReplicate(
        image = image,
        session = session,
        aiPredictedGrains = aiPredictedGrains,
        confirmedGrains = confirmedGrains,
        weight = weight,
        manuallyOverridden = false,
        capturedAt = capturedAt,
        submittedAt = Instant.now()
    )
}

suspend fun ApiService.predictLikeTheApp(session: SessionInfo, image: Bitmap): PredictResponse =
    predict(
        image = image.toImagePart(),
        technicianName = session.technicianName.toTextPart(),
        sampleId = session.sampleId.toTextPart()
    )

/**
 * Sends [item] exactly the way submitQueuedReplicate (ReplicateQueue.kt)
 * does: same parts, same JSON encoding, same timestamp format. Kept field
 * for field in step with that function; TC-INT-05 checks the field list.
 * [imagePart] can be passed in when the test needs the exact bytes sent.
 */
suspend fun ApiService.submitLikeTheApp(
    item: QueuedReplicate,
    imagePart: MultipartBody.Part = item.image.toImagePart()
): ReplicateResponse = submitReplicate(
    image = imagePart,
    technicianName = item.session.technicianName.toTextPart(),
    sampleId = item.session.sampleId.toTextPart(),
    aiPredictedGrains = Json.encodeToString(item.aiPredictedGrains).toTextPart(),
    confirmedGrains = Json.encodeToString(item.confirmedGrains).toTextPart(),
    weight = item.weight.toTextPart(),
    season = item.session.season.toTextPart(),
    indexNumber = item.session.indexNumber.toTextPart(),
    selectionName = item.session.selectionName.toTextPart(),
    sampleNo = item.session.sampleNo.toString().toTextPart(),
    manuallyOverridden = item.manuallyOverridden.toString().toTextPart(),
    capturedAt = item.capturedAt.toString().toTextPart(),
    submittedAt = item.submittedAt.toString().toTextPart()
)

/** The exact bytes inside a multipart file part. */
fun MultipartBody.Part.bodyBytes(): ByteArray = Buffer().also { body.writeTo(it) }.readByteArray()

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
