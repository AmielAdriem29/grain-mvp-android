package com.grainmvp.android.submit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

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

class QualityLabelTest {

    @Test
    fun `confidence is shown as a whole percent`() {
        assertEquals("86% confidence", qualityLabel(0.86))
    }

    @Test
    fun `confidence rounds to the nearest whole percent`() {
        assertEquals("86% confidence", qualityLabel(0.855))
        assertEquals("62% confidence", qualityLabel(0.6249))
    }

    @Test
    fun `GOOD or BAD is never shown`() {
        val label = qualityLabel(0.40)
        assertEquals("40% confidence", label)
        assertFalse(label.contains("GOOD") || label.contains("BAD"))
    }

    @Test
    fun `missing confidence is said plainly`() {
        assertEquals("No AI confidence", qualityLabel(null))
    }

    @Test
    fun `accepted banner shows the average when known`() {
        val average = SampleAverage(BigDecimal("0.98"), BigDecimal("3.3"), "G1")
        assertEquals("Sample accepted · avg 3.3% · G1", acceptedBannerText(average))
        assertEquals("Sample accepted", acceptedBannerText(null))
    }
}
