package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `second sample gets the generated sample 2 ID`() {
        assertEquals("2026WS-1234-S2", session().secondSample().sampleId)
    }
}

class IsEntryInSessionTest {

    private fun started(indexNumber: String, sampleNo: Int, season: String = "2026WS") = SessionInfo(
        technicianName = "Ana",
        season = season,
        indexNumber = indexNumber,
        selectionName = "NSIC Rc 222",
        sampleId = "S-0142",
        sampleNo = sampleNo
    )

    @Test
    fun `nothing is in an empty session`() {
        assertFalse(isEntryInSession("2026WS", "NCT-118", emptyList()))
    }

    @Test
    fun `the same season and index after sample 1 is in the session`() {
        assertTrue(isEntryInSession("2026WS", "NCT-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `an entry with both samples is in the session`() {
        assertTrue(isEntryInSession("2026WS", "NCT-118", listOf(started("NCT-118", 1), started("NCT-118", 2))))
    }

    @Test
    fun `a different index number is a new entry`() {
        assertFalse(isEntryInSession("2026WS", "NCT-119", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `the same index in a different season is a new entry`() {
        assertFalse(isEntryInSession("2027DS", "NCT-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `index numbers must match exactly, like the backend grouping`() {
        assertFalse(isEntryInSession("2026WS", "nct-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `the selection name does not decide the entry`() {
        val entries = listOf(started("NCT-118", 1).copy(selectionName = "Something else"))

        assertTrue(isEntryInSession("2026WS", "NCT-118", entries))
    }
}
