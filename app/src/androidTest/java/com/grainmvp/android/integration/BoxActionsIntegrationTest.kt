package com.grainmvp.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grainmvp.android.correction.GrainCorrectionMode
import com.grainmvp.android.correction.applyGrainTap
import com.grainmvp.android.correction.prepareGrainsForSubmission
import com.grainmvp.android.network.GrainAction
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.testsupport.RUN_ID
import com.grainmvp.android.testsupport.TestBackend
import com.grainmvp.android.testsupport.evidence
import com.grainmvp.android.testsupport.submitLikeTheApp
import com.grainmvp.android.testsupport.testReplicate
import com.grainmvp.android.testsupport.testSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * TC-INT-01: Box Actions From App to Dashboard (Android -> API -> database).
 *
 * Row a: 6 proposed boxes at known coordinates; in the app keep 3, remove 2,
 * add 1, submit. The backend must store 3 kept, 2 removed, 1 added, with
 * every box's coordinates unchanged.
 *
 * Row b (opening the record on the dashboard) belongs to the dashboard
 * test suite and is not run here.
 *
 * The corrections go through the app's real correction code (applyGrainTap
 * and prepareGrainsForSubmission), and the request goes through the app's
 * real ApiService to the real API and database. What was stored is read
 * back with GET /api/replicates.
 */
@RunWith(AndroidJUnit4::class)
class BoxActionsIntegrationTest {

    private val proposed = listOf(
        GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.91f),
        GrainBox(x = 300, y = 100, width = 24, height = 24, confidence = 0.82f),
        GrainBox(x = 500, y = 100, width = 24, height = 24, confidence = 0.77f),
        GrainBox(x = 100, y = 300, width = 24, height = 24, confidence = 0.66f),
        GrainBox(x = 300, y = 300, width = 24, height = 24, confidence = 0.58f),
        GrainBox(x = 500, y = 300, width = 24, height = 24, confidence = 0.95f)
    )

    @Before
    fun requireBackend() = TestBackend.assumeConfigured()

    @Test
    fun rowA_keep3_remove2_add1_arrivesUnchanged() = runBlocking<Unit> {
        // In the app: remove boxes 4 and 5, add one at (800, 800), keep the rest.
        var grains: List<GrainBox> = proposed
        grains = applyGrainTap(grains, 112f, 312f, GrainCorrectionMode.REMOVE)
        grains = applyGrainTap(grains, 312f, 312f, GrainCorrectionMode.REMOVE)
        grains = applyGrainTap(grains, 800f, 800f, GrainCorrectionMode.ADD)
        val confirmed = prepareGrainsForSubmission(grains)

        assertEquals("app side: kept", 3, confirmed.count { it.action == GrainAction.KEPT })
        assertEquals("app side: removed", 2, confirmed.count { it.action == GrainAction.REMOVED })
        assertEquals("app side: added", 1, confirmed.count { it.action == GrainAction.ADDED })

        val session = testSession(sampleId = "INT01-$RUN_ID")
        val response = TestBackend.app.submitLikeTheApp(
            testReplicate(session, aiPredictedGrains = proposed, confirmedGrains = confirmed, weight = "1.5")
        )
        evidence("TC-INT-01-a", "submitted id=${response.id} label=${response.replicateLabel}")

        val stored = TestBackend.storedReplicate(response.id)
        val storedConfirmed = stored.boxes("confirmedGrains")
        val storedAi = stored.boxes("aiPredictedGrains")
        evidence("TC-INT-01-a", "stored confirmedGrains=$storedConfirmed")
        evidence("TC-INT-01-a", "stored aiPredictedGrains=$storedAi")

        assertEquals("stored kept", 3, storedConfirmed.count { it.action() == "kept" })
        assertEquals("stored removed", 2, storedConfirmed.count { it.action() == "removed" })
        assertEquals("stored added", 1, storedConfirmed.count { it.action() == "added" })

        assertBoxesUnchanged("confirmedGrains", confirmed, storedConfirmed)
        assertBoxesUnchanged("aiPredictedGrains", proposed, storedAi)
        evidence("TC-INT-01-a", "PASS: 3 kept, 2 removed, 1 added; all 12 boxes unchanged")
    }

    private fun JsonObject.boxes(field: String): List<JsonObject> =
        (this[field] as? JsonArray ?: error("Stored record has no $field array: $this"))
            .map { it.jsonObject }

    private fun JsonObject.action(): String? =
        this["action"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun assertBoxesUnchanged(field: String, sent: List<GrainBox>, stored: List<JsonObject>) {
        assertEquals("$field: number of boxes", sent.size, stored.size)
        sent.zip(stored).forEachIndexed { i, (s, st) ->
            val where = "$field[$i]"
            assertEquals("$where.x", s.x, st["x"]!!.jsonPrimitive.int)
            assertEquals("$where.y", s.y, st["y"]!!.jsonPrimitive.int)
            assertEquals("$where.width", s.width, st["width"]!!.jsonPrimitive.int)
            assertEquals("$where.height", s.height, st["height"]!!.jsonPrimitive.int)
            val storedConfidence = st["confidence"]?.jsonPrimitive?.doubleOrNull
            if (s.confidence == null) {
                assertEquals("$where.confidence", null, storedConfidence)
            } else {
                assertTrue(
                    "$where.confidence sent ${s.confidence} stored $storedConfidence",
                    storedConfidence != null && abs(storedConfidence - s.confidence!!.toDouble()) < 1e-4
                )
            }
            val sentAction = s.action?.name?.lowercase()
            assertEquals("$where.action", sentAction, st.action())
        }
    }
}
