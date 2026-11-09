package com.grainmvp.android.submit

import org.junit.Assert.assertEquals
import org.junit.Test

class GrainCountTest {

    @Test
    fun `zero percentage falls back to the confirmed count`() {
        assertEquals(10, totalGrainCount(10, 0.0))
    }

    @Test
    fun `25 percent immature means four times the confirmed count`() {
        assertEquals(40, totalGrainCount(10, 25.0))
    }

    @Test
    fun `50 percent immature means twice the confirmed count`() {
        assertEquals(20, totalGrainCount(10, 50.0))
    }
}
