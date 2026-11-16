package com.grainmvp.android.submit

import com.grainmvp.android.network.ReplicateQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `good photo shows its confidence as a whole percent`() {
        assertEquals("GOOD · 86% confidence", qualityLabel(ReplicateQuality.GOOD, 0.86, false))
    }

    @Test
    fun `bad photo shows its confidence`() {
        assertEquals("BAD · 62% confidence", qualityLabel(ReplicateQuality.BAD, 0.62, false))
    }

    @Test
    fun `manual override wins over confidence`() {
        assertEquals("GOOD · manually verified", qualityLabel(ReplicateQuality.GOOD, 0.40, true))
    }

    @Test
    fun `missing confidence is said plainly`() {
        assertEquals("BAD · no AI confidence", qualityLabel(ReplicateQuality.BAD, null, false))
    }

    @Test
    fun `no quality from the backend hides the row`() {
        assertNull(qualityLabel(null, 0.9, false))
    }

    @Test
    fun `accepted banner shows the average when known`() {
        val average = SampleAverage(BigDecimal("0.98"), BigDecimal("3.3"), "G1")
        assertEquals("Sample accepted · avg 3.3% · G1", acceptedBannerText(average))
        assertEquals("Sample accepted", acceptedBannerText(null))
    }
}
