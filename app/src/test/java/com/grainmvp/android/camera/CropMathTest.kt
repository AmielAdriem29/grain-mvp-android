package com.grainmvp.android.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Expected values below are hand-calculated from the spec's formula,
 * not derived from the implementation — if these fail, the formula
 * itself is wrong, not just the test's assumptions.
 */
class CropMathTest {

    // Allow +/-1px slack since the implementation clamps/rounds with
    // coerceIn, which can shift results by a fraction of a pixel versus
    // hand-calculated floats.
    private val delta = 1.0

    @Test
    fun `portrait phone, typical 3-to-4 sensor, guide fills full width`() {
        // sensorAspect = 3072/4096 = 0.75, previewAspect = 1080/2400 = 0.45
        // sensorAspect > previewAspect -> branch 1
        // visibleSensorH = 4096, visibleSensorW = 4096 * 0.45 = 1843.2
        // sensorCropX = (3072 - 1843.2) / 2 = 614.4, sensorCropY = 0
        // scaleX = scaleY = 1843.2 / 1080 = 1.70666...
        // guideSize = min(1080, 2400) = 1080, guideLeft = 0
        // guideTop = (2400 - 1080) / 2 = 660
        // finalCropX = 614.4, finalCropY = 660 * 1.70666 = 1126.4
        // finalCropSize = 1080 * 1.70666 = 1843.2
        val result = computeCropRegion(
            sensorW = 3072,
            sensorH = 4096,
            previewW = 1080,
            previewH = 2400,
            guideLeft = 0f,
            guideTop = 660f,
            guideSize = 1080f
        )

        assertEquals(614.4, result.x.toDouble(), delta)
        assertEquals(1126.4, result.y.toDouble(), delta)
        assertEquals(1843.2, result.size.toDouble(), delta)
    }

    @Test
    fun `square sensor and square preview produce full image, no offset`() {
        // Degenerate case: sensor and preview are both already square and
        // equal aspect, so the guide should map to (0,0) at full size.
        val result = computeCropRegion(
            sensorW = 1024,
            sensorH = 1024,
            previewW = 1000,
            previewH = 1000,
            guideLeft = 0f,
            guideTop = 0f,
            guideSize = 1000f
        )

        assertEquals(0.0, result.x.toDouble(), delta)
        assertEquals(0.0, result.y.toDouble(), delta)
        assertEquals(1024.0, result.size.toDouble(), delta)
    }

    @Test
    fun `landscape preview, sensor wider than preview crops from left and right`() {
        // sensorAspect = 4096/3072 = 1.333, previewAspect = 2400/1080 = 2.222
        // sensorAspect < previewAspect -> branch 2 (else)
        // visibleSensorW = 4096, visibleSensorH = 4096 / 2.222 = 1843.2
        // sensorCropX = 0, sensorCropY = (3072 - 1843.2) / 2 = 614.4
        // scaleX = scaleY = 4096 / 2400 = 1.70666...
        // guideSize = min(2400, 1080) = 1080
        // guideLeft (landscape) = (2400 - 1080) / 2 = 660, guideTop = 0
        // finalCropX = 660 * 1.70666 = 1126.4, finalCropY = 614.4
        // finalCropSize = 1080 * 1.70666 = 1843.2
        val result = computeCropRegion(
            sensorW = 4096,
            sensorH = 3072,
            previewW = 2400,
            previewH = 1080,
            guideLeft = 660f,
            guideTop = 0f,
            guideSize = 1080f
        )

        assertEquals(1126.4, result.x.toDouble(), delta)
        assertEquals(614.4, result.y.toDouble(), delta)
        assertEquals(1843.2, result.size.toDouble(), delta)
    }

    @Test
    fun `result never exceeds sensor bounds even with edge-case rounding`() {
        val result = computeCropRegion(
            sensorW = 4000,
            sensorH = 3000,
            previewW = 1080,
            previewH = 2400,
            guideLeft = 0f,
            guideTop = 0f,
            guideSize = 1080f
        )

        assert(result.x >= 0)
        assert(result.y >= 0)
        assert(result.x + result.size <= 4000)
        assert(result.y + result.size <= 3000)
    }

    @Test
    fun `portrait image crops top and bottom to a centered square`() {
        // width=1000, height=2000 -> size=1000, x=0, y=(2000-1000)/2=500
        val result = computeCenterSquareCrop(width = 1000, height = 2000)

        assertEquals(0, result.x)
        assertEquals(500, result.y)
        assertEquals(1000, result.size)
    }

    @Test
    fun `landscape image crops left and right to a centered square`() {
        // width=2000, height=1000 -> size=1000, x=(2000-1000)/2=500, y=0
        val result = computeCenterSquareCrop(width = 2000, height = 1000)

        assertEquals(500, result.x)
        assertEquals(0, result.y)
        assertEquals(1000, result.size)
    }

    @Test
    fun `already-square image needs no offset`() {
        val result = computeCenterSquareCrop(width = 800, height = 800)

        assertEquals(0, result.x)
        assertEquals(0, result.y)
        assertEquals(800, result.size)
    }

    @Test
    fun `odd difference between width and height still stays in bounds`() {
        // width=999, height=1000 -> size=999, x=0, y=(1000-999)/2=0 (integer division)
        val result = computeCenterSquareCrop(width = 999, height = 1000)

        assert(result.x >= 0)
        assert(result.y >= 0)
        assert(result.x + result.size <= 999)
        assert(result.y + result.size <= 1000)
    }
}