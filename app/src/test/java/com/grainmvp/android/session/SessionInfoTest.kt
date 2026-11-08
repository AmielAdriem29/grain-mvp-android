package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionInfoTest {

    @Test
    fun `next sample increments the sample ID`() {
        val session = SessionInfo(technicianName = "Ana", sampleId = "S-0142")
        assertEquals("S-0143", session.nextSample().sampleId)
    }

    @Test
    fun `next sample keeps the same technician`() {
        val session = SessionInfo(technicianName = "Ana", sampleId = "S-0142")
        assertEquals("Ana", session.nextSample().technicianName)
    }

    @Test
    fun `next sample does not change the original session`() {
        val session = SessionInfo(technicianName = "Ana", sampleId = "S-0142")
        session.nextSample()
        assertEquals("S-0142", session.sampleId)
    }
}
