package com.grainmvp.android.performance

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grainmvp.android.correction.prepareGrainsForSubmission
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.testsupport.LatencyStats
import com.grainmvp.android.testsupport.RUN_ID
import com.grainmvp.android.testsupport.TestBackend
import com.grainmvp.android.testsupport.predictLikeTheApp
import com.grainmvp.android.testsupport.submitLikeTheApp
import com.grainmvp.android.testsupport.syntheticSampleImage
import com.grainmvp.android.testsupport.testReplicate
import com.grainmvp.android.testsupport.testSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * TC-SYS-03 rows a and b, measured from the phone: the time the technician
 * waits, through the app's own network code (image encoding, multipart,
 * JSON, OkHttp) to the real API and database on the local test host.
 *
 *  - Row a: 20 POST /api/predict requests, target p95 <= 5 s.
 *  - Row b: 20 POST /api/replicate requests, target p95 <= 3 s.
 *  - Cold start: the first request after the host starts, reported apart
 *    and left out of the percentiles. The runner starts a fresh host for
 *    this suite so the first request really is cold.
 *
 * One phone sends one request at a time (users = 1), as the app does.
 * Results go to logcat tag "GrainPerf"; the runner saves them to
 * testing/build/results/. Server-side load with many users is the k6
 * part of TC-SYS-03 and is not covered here.
 *
 * Methods run in name order: a (cold start) before b and c.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ApiLatencyPerformanceTest {

    private companion object {
        const val TAG = "GrainPerf"
        const val REQUESTS = 20
        const val PREDICT_TARGET_P95_MS = 5_000L
        const val SUBMIT_TARGET_P95_MS = 3_000L
    }

    private val image = syntheticSampleImage(seed = 7)
    private val ai = listOf(
        GrainBox(x = 120, y = 140, width = 24, height = 24, confidence = 0.88f),
        GrainBox(x = 420, y = 610, width = 24, height = 24, confidence = 0.81f)
    )

    @Before
    fun requireBackend() = TestBackend.assumeConfigured()

    @Test
    fun a_coldStart() = runBlocking<Unit> {
        val predictMs = timed { TestBackend.app.predictLikeTheApp(testSession("PERFCOLDP-$RUN_ID"), image) }
        val submitMs = timed { submitOnce("PERFCOLDS-$RUN_ID") }
        Log.i(TAG, "TC-SYS-03 | cold start | predict first request ${predictMs} ms | replicate first request ${submitMs} ms")
    }

    @Test
    fun b_rowA_predictLatency() = runBlocking<Unit> {
        val stats = measure("POST /api/predict") { i ->
            TestBackend.app.predictLikeTheApp(testSession("PERFP$i-$RUN_ID"), image)
        }
        report("a", stats, PREDICT_TARGET_P95_MS)
    }

    @Test
    fun c_rowB_replicateLatency() = runBlocking<Unit> {
        val stats = measure("POST /api/replicate") { i -> submitOnce("PERFS$i-$RUN_ID") }
        report("b", stats, SUBMIT_TARGET_P95_MS)
    }

    private suspend fun submitOnce(sampleId: String) {
        TestBackend.app.submitLikeTheApp(
            testReplicate(testSession(sampleId), ai, prepareGrainsForSubmission(ai), weight = "1.5", image = image)
        )
    }

    private suspend fun measure(label: String, call: suspend (Int) -> Unit): LatencyStats {
        val times = mutableListOf<Long>()
        var errors = 0
        repeat(REQUESTS) { i ->
            val start = System.nanoTime()
            try {
                call(i)
                times += (System.nanoTime() - start) / 1_000_000
            } catch (e: Exception) {
                errors++
                Log.w(TAG, "TC-SYS-03 | $label request ${i + 1} failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return LatencyStats(label, users = 1, requests = REQUESTS, successMs = times, errors = errors)
    }

    private fun report(row: String, stats: LatencyStats, targetP95Ms: Long) {
        Log.i(TAG, "TC-SYS-03 | row $row | ${LatencyStats.TABLE_HEADER}")
        Log.i(TAG, "TC-SYS-03 | row $row | ${LatencyStats.TABLE_DIVIDER}")
        Log.i(TAG, "TC-SYS-03 | row $row | ${stats.tableRow(targetP95Ms)}")
        Log.i(TAG, "TC-SYS-03 | row $row | all ms: ${stats.successMs}")

        assertTrue("row $row: ${stats.errors} of ${stats.requests} requests failed", stats.errors == 0)
        assertTrue(
            "row $row: p95 ${stats.p95} ms is over the ${targetP95Ms} ms target",
            stats.p95 != null && stats.p95!! <= targetP95Ms
        )
    }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
