package com.grainmvp.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grainmvp.android.correction.prepareGrainsForSubmission
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.submit.formatPercentage
import com.grainmvp.android.testsupport.RUN_ID
import com.grainmvp.android.testsupport.TestBackend
import com.grainmvp.android.testsupport.evidence
import com.grainmvp.android.testsupport.submitLikeTheApp
import com.grainmvp.android.testsupport.testReplicate
import com.grainmvp.android.testsupport.testSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TC-INT-02: Displayed Values Match Stored Values (Android + API + database).
 *
 * Input from the STD: immature 1.5 g, total 29.0 g. Expected: app, database
 * and dashboard all show 5.2% and G2.
 *
 * The app has no total-weight field; the backend divides by a fixed 30 g.
 * So the STD predicts this case fails until TC-203 passes. The two tests
 * below keep the two questions apart:
 *  - [rowA_appShowsWhatDatabaseStored]: do the app and the database agree
 *    with each other? (the integration question)
 *  - [rowA_valuesMatchStdExpectation]: are those values 5.2% and G2?
 *
 * The dashboard reading is part of the dashboard suite and is not run here.
 */
@RunWith(AndroidJUnit4::class)
class DisplayedValuesIntegrationTest {

    private val ai = listOf(GrainBox(x = 200, y = 200, width = 24, height = 24, confidence = 0.9f))

    @Before
    fun requireBackend() = TestBackend.assumeConfigured()

    private suspend fun submit(tag: String): ReplicateResponse {
        val session = testSession(sampleId = "INT02$tag-$RUN_ID")
        return TestBackend.app.submitLikeTheApp(
            testReplicate(session, ai, prepareGrainsForSubmission(ai), weight = "1.5")
        )
    }

    @Test
    fun rowA_appShowsWhatDatabaseStored() = runBlocking<Unit> {
        val response = submit("a")
        val stored = TestBackend.storedReplicate(response.id)

        // What ResultScreen shows: formatPercentage(response.percentage) and response.grade.
        val appPercent = formatPercentage(response.percentage)
        val dbPercent = formatPercentage(stored["percentage"]!!.jsonPrimitive.double)
        val dbGrade = stored["grade"]!!.jsonPrimitive.content
        evidence("TC-INT-02-a", "id=${response.id} app=$appPercent% ${response.grade} | db=$dbPercent% $dbGrade")

        assertEquals("percentage: app vs database", dbPercent, appPercent)
        assertEquals("grade: app vs database", dbGrade, response.grade)
    }

    @Test
    fun rowA_valuesMatchStdExpectation() = runBlocking<Unit> {
        val response = submit("b")
        val appPercent = formatPercentage(response.percentage)
        evidence("TC-INT-02-a", "STD expects 5.2% G2; app shows $appPercent% ${response.grade}")

        assertEquals("percentage shown in the app (STD: 5.2)", "5.2", appPercent)
        assertEquals("grade shown in the app (STD: G2)", "G2", response.grade)
    }
}
