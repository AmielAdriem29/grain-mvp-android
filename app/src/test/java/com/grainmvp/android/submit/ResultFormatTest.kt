package com.grainmvp.android.submit

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultFormatTest {

    @Test
    fun `percentage is shown with one decimal`() {
        assertEquals("1.9", formatPercentage(1.9))
        assertEquals("12.5", formatPercentage(12.5))
    }

    @Test
    fun `whole percentages get a trailing decimal`() {
        assertEquals("2.0", formatPercentage(2.0))
        assertEquals("0.0", formatPercentage(0.0))
    }

    @Test
    fun `an old two-decimal value is rounded to one`() {
        assertEquals("3.3", formatPercentage(3.33))
    }
}
