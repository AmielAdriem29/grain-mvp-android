package com.grainmvp.android.integration

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grainmvp.android.ImageSpec
import com.grainmvp.android.correction.prepareGrainsForSubmission
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.testsupport.RUN_ID
import com.grainmvp.android.testsupport.TestBackend
import com.grainmvp.android.testsupport.bodyBytes
import com.grainmvp.android.testsupport.evidence
import com.grainmvp.android.testsupport.sha256Hex
import com.grainmvp.android.testsupport.submitLikeTheApp
import com.grainmvp.android.testsupport.syntheticSampleImage
import com.grainmvp.android.testsupport.testReplicate
import com.grainmvp.android.testsupport.testSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TC-INT-04: Stored Image Integrity, the Android -> API leg.
 *
 * The STD row uploads with POST /api/replicate and downloads with
 * GET /api/images/{id}. The backend has no such endpoint (images go to
 * Cloudinary), and the rules forbid real Cloudinary, so the download half
 * can't run. What this test checks instead: the JPEG the app builds
 * (Bitmap.toImagePart) reaches the API byte for byte. The local test host's
 * image store records the SHA-256 of the bytes it received in the record's
 * imagePublicId ("local/<sha256>").
 */
@RunWith(AndroidJUnit4::class)
class StoredImageIntegrityIntegrationTest {

    @Before
    fun requireBackend() = TestBackend.assumeConfigured()

    @Test
    fun rowA_uploadedImageArrivesByteForByte() = runBlocking<Unit> {
        val bitmap = syntheticSampleImage(seed = 4)
        val part = bitmap.toImagePart()
        val bytes = part.bodyBytes()
        val sentHash = sha256Hex(bytes)

        // The app always sends a 1024 x 1024 JPEG.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(ImageSpec.SIZE_PX, bounds.outWidth)
        assertEquals(ImageSpec.SIZE_PX, bounds.outHeight)

        val ai = listOf(GrainBox(x = 10, y = 10, width = 24, height = 24, confidence = 0.8f))
        val session = testSession(sampleId = "INT04-$RUN_ID")
        val response = TestBackend.app.submitLikeTheApp(
            testReplicate(session, ai, prepareGrainsForSubmission(ai), weight = "2.0", image = bitmap),
            imagePart = part
        )

        val stored = TestBackend.storedReplicate(response.id)
        val storedPublicId = stored["imagePublicId"]!!.jsonPrimitive.content
        evidence("TC-INT-04-a", "id=${response.id} bytes=${bytes.size} sent=$sentHash stored=$storedPublicId")

        assertEquals("SHA-256 received by the API", "local/$sentHash", storedPublicId)
    }
}
