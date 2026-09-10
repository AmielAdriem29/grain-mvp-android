package com.grainmvp.android.correction

import com.grainmvp.android.network.GrainBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionLogicTest {

    // ---- findTappedBoxIndex ----

    @Test
    fun `tap inside a box returns that box's index`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null),
            GrainBox(x = 200, y = 200, width = 24, height = 24, confidence = 0.7f, action = null)
        )

        // Well inside the second box's bounds (200-224, 200-224)
        val result = findTappedBoxIndex(grains, imageX = 210f, imageY = 210f)

        assertEquals(1, result)
    }

    @Test
    fun `tap outside every box returns -1`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = findTappedBoxIndex(grains, imageX = 500f, imageY = 500f)

        assertEquals(-1, result)
    }

    @Test
    fun `tap exactly on a box's top-left corner counts as inside`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = findTappedBoxIndex(grains, imageX = 100f, imageY = 100f)

        assertEquals(0, result)
    }

    @Test
    fun `tap exactly on a box's far edge (x+width) counts as outside`() {
        // Half-open interval per the implementation: [x, x+width)
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = findTappedBoxIndex(grains, imageX = 124f, imageY = 110f)

        assertEquals(-1, result)
    }

    @Test
    fun `empty grains list always returns -1`() {
        val result = findTappedBoxIndex(emptyList(), imageX = 50f, imageY = 50f)

        assertEquals(-1, result)
    }

    // ---- toggleGrainBoxAction ----

    @Test
    fun `toggling an untouched (null action) box marks it removed`() {
        val box = GrainBox(x = 0, y = 0, width = 24, height = 24, confidence = 0.9f, action = null)

        val result = toggleGrainBoxAction(box)

        assertEquals("removed", result.action)
    }

    @Test
    fun `toggling a removed box resets its action to null`() {
        val box = GrainBox(x = 0, y = 0, width = 24, height = 24, confidence = 0.9f, action = "removed")

        val result = toggleGrainBoxAction(box)

        assertNull(result.action)
    }

    @Test
    fun `toggling preserves every other field unchanged`() {
        val box = GrainBox(x = 42, y = 99, width = 24, height = 24, confidence = 0.55f, action = null)

        val result = toggleGrainBoxAction(box)

        assertEquals(42, result.x)
        assertEquals(99, result.y)
        assertEquals(24, result.width)
        assertEquals(24, result.height)
        assertEquals(0.55f, result.confidence)
    }

    // ---- createAddedGrainBox ----

    @Test
    fun `created box has fixed 24x24 size and action added`() {
        val result = createAddedGrainBox(imageX = 300.7f, imageY = 150.2f)

        assertEquals(24, result.width)
        assertEquals(24, result.height)
        assertEquals("added", result.action)
        assertNull(result.confidence)
    }

    @Test
    fun `created box coordinates truncate toward zero, not rounded`() {
        // Spec: x = imageX, y = imageY -- straightforward truncation via
        // Float.toInt(), not Math.round().
        val result = createAddedGrainBox(imageX = 300.9f, imageY = 150.9f)

        assertEquals(300, result.x)
        assertEquals(150, result.y)
    }

    // ---- prepareGrainsForSubmission ----

    @Test
    fun `untouched boxes convert to kept, others pass through unchanged`() {
        val grains = listOf(
            GrainBox(x = 0, y = 0, width = 24, height = 24, confidence = 0.9f, action = null),
            GrainBox(x = 10, y = 10, width = 24, height = 24, confidence = 0.5f, action = "removed"),
            GrainBox(x = 20, y = 20, width = 24, height = 24, confidence = null, action = "added")
        )

        val result = prepareGrainsForSubmission(grains)

        assertEquals("kept", result[0].action)
        assertEquals("removed", result[1].action)
        assertEquals("added", result[2].action)
    }

    @Test
    fun `empty list produces empty list`() {
        val result = prepareGrainsForSubmission(emptyList())

        assertEquals(0, result.size)
    }

    // ---- isValidWeightValue ----

    @Test
    fun `empty string is invalid`() {
        assertEquals(false, isValidWeightValue(""))
    }

    @Test
    fun `a positive whole number is valid`() {
        assertEquals(true, isValidWeightValue("12"))
    }

    @Test
    fun `a positive decimal is valid`() {
        assertEquals(true, isValidWeightValue("12.45"))
    }

    @Test
    fun `zero is invalid`() {
        assertEquals(false, isValidWeightValue("0"))
    }

    @Test
    fun `a negative number is invalid`() {
        assertEquals(false, isValidWeightValue("-5"))
    }

    @Test
    fun `letters are invalid`() {
        assertEquals(false, isValidWeightValue("abc"))
        assertEquals(false, isValidWeightValue("12a"))
    }

    @Test
    fun `multiple decimal points is invalid`() {
        assertEquals(false, isValidWeightValue("12.34.5"))
    }

    @Test
    fun `whitespace-only is invalid`() {
        assertEquals(false, isValidWeightValue("   "))
    }

    // ---- applyGrainTap (screen 05's explicit Remove/Add mode) ----

    @Test
    fun `remove mode tapping an untouched box marks it removed`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.REMOVE)

        assertEquals("removed", result[0].action)
    }

    @Test
    fun `remove mode tapping an already-removed box un-removes it`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = "removed")
        )

        val result = applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.REMOVE)

        assertNull(result[0].action)
    }

    @Test
    fun `remove mode tapping empty space does nothing`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = applyGrainTap(grains, imageX = 500f, imageY = 500f, mode = GrainCorrectionMode.REMOVE)

        assertEquals(grains, result)
    }

    @Test
    fun `add mode tapping empty space adds a new box`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = applyGrainTap(grains, imageX = 500f, imageY = 500f, mode = GrainCorrectionMode.ADD)

        assertEquals(2, result.size)
        assertEquals("added", result[1].action)
        assertEquals(500, result[1].x)
        assertEquals(500, result[1].y)
    }

    @Test
    fun `add mode tapping an existing box does nothing`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result = applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.ADD)

        assertEquals(grains, result)
        assertEquals(1, result.size)
    }

    // ---- screenTapToImageCoords (screen 05's pinch-zoom + pan) ----

    @Test
    fun `screenTapToImageCoords at zoom 1x with no pan matches plain displayScale division`() {
        // Same formula as every findTappedBoxIndex test above pre-dates
        // zoom: imageX = screenX / displayScale.
        val (x, y) = screenTapToImageCoords(
            screenX = 210f, screenY = 210f,
            boxSizePx = 1000f, displayScale = 1000f / 1024f,
            zoom = 1f, panX = 0f, panY = 0f
        )

        assertEquals(215.04f, x, 0.01f)
        assertEquals(215.04f, y, 0.01f)
    }

    @Test
    fun `screenTapToImageCoords at the box center is unaffected by zoom`() {
        // Zoom scales around the box center, so a tap exactly there maps
        // to the same content point regardless of zoom level.
        val (x, y) = screenTapToImageCoords(
            screenX = 500f, screenY = 500f,
            boxSizePx = 1000f, displayScale = 1000f / 1024f,
            zoom = 3f, panX = 0f, panY = 0f
        )

        assertEquals(512f, x, 0.01f)
        assertEquals(512f, y, 0.01f)
    }

    @Test
    fun `screenTapToImageCoords accounts for pan offset`() {
        // Zoomed 2x and panned 100px right/down: content_x = 500 +
        // (500 - 500 - 100) / 2 = 450 -> imageX = 450 / (1000/1024).
        val (x, y) = screenTapToImageCoords(
            screenX = 500f, screenY = 500f,
            boxSizePx = 1000f, displayScale = 1000f / 1024f,
            zoom = 2f, panX = 100f, panY = 100f
        )

        assertEquals(460.8f, x, 0.01f)
        assertEquals(460.8f, y, 0.01f)
    }

    // ---- clampPan (screen 05's pinch-zoom + pan) ----

    @Test
    fun `clampPan allows no pan at all at zoom 1x`() {
        assertEquals(0f, clampPan(pan = 250f, boxSizePx = 1000f, zoom = 1f), 0.01f)
        assertEquals(0f, clampPan(pan = -250f, boxSizePx = 1000f, zoom = 1f), 0.01f)
    }

    @Test
    fun `clampPan caps pan at the zoomed content's overhang at 2x zoom`() {
        // Content is 2x the box size (2000px for a 1000px box), so it
        // overhangs the viewport by 1000px total -- 500px on each side.
        assertEquals(500f, clampPan(pan = 9999f, boxSizePx = 1000f, zoom = 2f), 0.01f)
        assertEquals(-500f, clampPan(pan = -9999f, boxSizePx = 1000f, zoom = 2f), 0.01f)
    }

    @Test
    fun `clampPan leaves an in-range pan value untouched`() {
        assertEquals(100f, clampPan(pan = 100f, boxSizePx = 1000f, zoom = 2f), 0.01f)
    }
}