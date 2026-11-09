package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionInfoTest {

    private fun session(sampleNo: Int = 1) = SessionInfo(
        technicianName = "Ana",
        season = "2026WS",
        indexNumber = "1234",
        selectionName = "NSIC Rc 222",
        sampleId = "S-0142",
        sampleNo = sampleNo
    )

    @Test
    fun `sample number defaults to 1`() {
        val s = SessionInfo("Ana", "2026WS", "1234", "NSIC Rc 222", "S-0142")
        assertEquals(1, s.sampleNo)
    }

    @Test
    fun `next sample increments the sample ID`() {
        assertEquals("S-0143", session().nextSample().sampleId)
    }

    @Test
    fun `next sample keeps the technician and season`() {
        val next = session().nextSample()
        assertEquals("Ana", next.technicianName)
        assertEquals("2026WS", next.season)
    }

    @Test
    fun `next sample clears the entry details and resets the sample number`() {
        val next = session(sampleNo = 2).nextSample()
        assertEquals("", next.indexNumber)
        assertEquals("", next.selectionName)
        assertEquals(1, next.sampleNo)
    }

    @Test
    fun `next sample does not change the original session`() {
        val original = session()
        original.nextSample()
        assertEquals("S-0142", original.sampleId)
    }

    @Test
    fun `second sample keeps the entry and moves to sample 2`() {
        val second = session().secondSample()
        assertEquals("1234", second.indexNumber)
        assertEquals("NSIC Rc 222", second.selectionName)
        assertEquals("2026WS", second.season)
        assertEquals(2, second.sampleNo)
    }

    @Test
    fun `second sample increments the sample ID`() {
        assertEquals("S-0143", session().secondSample().sampleId)
    }
}
