package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

class SampleNoForTest {

    private fun started(indexNumber: String, sampleNo: Int, season: String = "2026WS") = SessionInfo(
        technicianName = "Ana",
        season = season,
        indexNumber = indexNumber,
        selectionName = "NSIC Rc 222",
        sampleId = "S-0142",
        sampleNo = sampleNo
    )

    @Test
    fun `an entry not started this session is sample 1`() {
        assertEquals(1, sampleNoFor("2026WS", "NCT-118", emptyList()))
    }

    @Test
    fun `the same season and index after sample 1 is sample 2`() {
        assertEquals(2, sampleNoFor("2026WS", "NCT-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `an entry with both samples started has no sample number left`() {
        val entries = listOf(started("NCT-118", 1), started("NCT-118", 2))

        assertNull(sampleNoFor("2026WS", "NCT-118", entries))
    }

    @Test
    fun `a different index number is a new entry`() {
        assertEquals(1, sampleNoFor("2026WS", "NCT-119", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `the same index in a different season is a new entry`() {
        assertEquals(1, sampleNoFor("2027DS", "NCT-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `other entries in the session do not affect this one`() {
        val entries = listOf(started("NCT-119", 1), started("NCT-119", 2), started("NCT-118", 1))

        assertEquals(2, sampleNoFor("2026WS", "NCT-118", entries))
    }

    @Test
    fun `index numbers must match exactly, like the backend grouping`() {
        assertEquals(1, sampleNoFor("2026WS", "nct-118", listOf(started("NCT-118", 1))))
    }

    @Test
    fun `the selection name does not decide the sample number`() {
        val entries = listOf(started("NCT-118", 1).copy(selectionName = "Something else"))

        assertEquals(2, sampleNoFor("2026WS", "NCT-118", entries))
    }
}
