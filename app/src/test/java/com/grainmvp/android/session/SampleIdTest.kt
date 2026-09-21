package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleIdTest {

    @Test
    fun `increments a zero-padded trailing number, preserving width`() {
        assertEquals("S-0143", incrementSampleId("S-0142"))
    }

    @Test
    fun `rollover that needs an extra digit grows the width`() {
        assertEquals("S-1000", incrementSampleId("S-0999"))
    }

    @Test
    fun `single non-padded digit increments normally`() {
        assertEquals("A-10", incrementSampleId("A-9"))
    }

    @Test
    fun `id that is entirely digits increments as a whole`() {
        assertEquals("043", incrementSampleId("042"))
    }

    @Test
    fun `no trailing digits appends a -2 fallback`() {
        assertEquals("SAMPLE-2", incrementSampleId("SAMPLE"))
    }

    @Test
    fun `empty string appends a -2 fallback`() {
        assertEquals("-2", incrementSampleId(""))
    }

    @Test
    fun `digits in the middle but not at the end are not touched`() {
        // Only a *trailing* digit run counts -- "S1-A" has no trailing digits.
        assertEquals("S1-A-2", incrementSampleId("S1-A"))
    }
}
