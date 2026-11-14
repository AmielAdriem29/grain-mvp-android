package com.grainmvp.android.submit

import com.grainmvp.android.network.ReplicateQuality
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.session.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SampleAverageTest {

    private val session = SessionInfo("Ana", "2026WS", "NCT-1", "PSB Rc222", "S-0142", sampleNo = 1)

    private fun photo(
        replicateNo: Int,
        weight: String,
        quality: ReplicateQuality?,
        session: SessionInfo = this.session
    ) = SubmittedPhoto(
        session = session,
        weight = weight,
        response = ReplicateResponse(
            id = "id-$replicateNo",
            percentage = 0.0,
            grade = "Pr",
            replicateNo = replicateNo,
            quality = quality
        )
    )

    private fun average(vararg weights: String) = sampleAverage(weights.map(::BigDecimal))

    @Test
    fun `mean weight rounds half up to 2 decimals before the percentage`() {
        // 0.975 -> 0.98 g -> 3.2666 -> 3.3 %, matching backend CalculationService
        val result = average("0.95", "1.00")
        assertEquals(BigDecimal("0.98"), result.weight)
        assertEquals(BigDecimal("3.3"), result.percentage)
        assertEquals("G1", result.grade)
    }

    @Test
    fun `grade boundaries match the backend bands`() {
        assertEquals("Pr", gradeFor(BigDecimal("1.9")))
        assertEquals("G1", gradeFor(BigDecimal("2.0")))
        assertEquals("G1", gradeFor(BigDecimal("5.0")))
        assertEquals("G2", gradeFor(BigDecimal("5.1")))
        assertEquals("G2", gradeFor(BigDecimal("10.0")))
        assertEquals("G3", gradeFor(BigDecimal("10.1")))
        assertEquals("G3", gradeFor(BigDecimal("15.0")))
        assertEquals("Not classified", gradeFor(BigDecimal("15.1")))
    }

    @Test
    fun `average uses the first two good photos by replicate number`() {
        val photos = listOf(
            photo(3, "0.60", ReplicateQuality.GOOD),
            photo(2, "9.00", ReplicateQuality.BAD),
            photo(1, "0.30", ReplicateQuality.GOOD),
            photo(4, "6.00", ReplicateQuality.GOOD)
        )
        // replicates 1 and 3: (0.30 + 0.60) / 2 = 0.45 g -> 1.5 %
        val result = sampleAverageFor(session, photos)!!
        assertEquals(BigDecimal("0.45"), result.weight)
        assertEquals(BigDecimal("1.5"), result.percentage)
        assertEquals("Pr", result.grade)
    }

    @Test
    fun `fewer than two good photos gives no average`() {
        val photos = listOf(
            photo(1, "0.30", ReplicateQuality.GOOD),
            photo(2, "0.60", ReplicateQuality.BAD),
            photo(3, "0.60", null)
        )
        assertNull(sampleAverageFor(session, photos))
    }

    @Test
    fun `photos of other samples are ignored`() {
        val otherSample = session.copy(sampleNo = 2)
        val otherEntry = session.copy(indexNumber = "NCT-2")
        val photos = listOf(
            photo(1, "0.30", ReplicateQuality.GOOD),
            photo(1, "0.60", ReplicateQuality.GOOD, otherSample),
            photo(1, "0.90", ReplicateQuality.GOOD, otherEntry)
        )
        assertNull(sampleAverageFor(session, photos))
    }
}
