package com.grainmvp.android.correction

import com.grainmvp.android.network.GrainAction
import com.grainmvp.android.network.GrainBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

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

        assertEquals(GrainAction.REMOVED, result.action)
    }

    @Test
    fun `toggling a removed box resets its action to null`() {
        val box = GrainBox(
            x = 0,
            y = 0,
            width = 24,
            height = 24,
            confidence = 0.9f,
            action = GrainAction.REMOVED
        )

        val result = toggleGrainBoxAction(box)

        assertNull(result.action)
    }

    @Test
    fun `toggling preserves every other field unchanged`() {
        val box =
            GrainBox(x = 42, y = 99, width = 24, height = 24, confidence = 0.55f, action = null)

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
        assertEquals(GrainAction.ADDED, result.action)
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
            GrainBox(
                x = 10,
                y = 10,
                width = 24,
                height = 24,
                confidence = 0.5f,
                action = GrainAction.REMOVED
            ),
            GrainBox(
                x = 20,
                y = 20,
                width = 24,
                height = 24,
                confidence = null,
                action = GrainAction.ADDED
            )
        )

        val result = prepareGrainsForSubmission(grains)

        assertEquals(GrainAction.KEPT, result[0].action)
        assertEquals(GrainAction.REMOVED, result[1].action)
        assertEquals(GrainAction.ADDED, result[2].action)
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

    @Test
    fun `weight just above zero is valid`() {
        assertEquals(true, isValidWeightValue("0.01"))
    }

    @Test
    fun `weight of exactly 30 g is valid`() {
        assertEquals(true, isValidWeightValue("30"))
        assertEquals(true, isValidWeightValue("30.0"))
    }

    @Test
    fun `weight just above 30 g is invalid`() {
        assertEquals(false, isValidWeightValue("30.01"))
    }

    @Test
    fun `a very large weight is invalid`() {
        assertEquals(false, isValidWeightValue("1000"))
        assertEquals(false, isValidWeightValue("Infinity"))
    }

    @Test
    fun `NaN is invalid`() {
        assertEquals(false, isValidWeightValue("NaN"))
    }

    // ---- applyGrainTap (screen 05's explicit Remove/Add mode) ----

    @Test
    fun `remove mode tapping an untouched box marks it removed`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result =
            applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.REMOVE)

        assertEquals(GrainAction.REMOVED, result[0].action)
    }

    @Test
    fun `remove mode tapping an already-removed box un-removes it`() {
        val grains = listOf(
            GrainBox(
                x = 100,
                y = 100,
                width = 24,
                height = 24,
                confidence = 0.8f,
                action = GrainAction.REMOVED
            )
        )

        val result =
            applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.REMOVE)

        assertNull(result[0].action)
    }

    @Test
    fun `remove mode tapping a user-added box deletes it from the list`() {
        val grains = listOf(
            GrainBox(x = 10, y = 10, width = 24, height = 24, confidence = 0.9f, action = null),
            GrainBox(
                x = 100,
                y = 100,
                width = 24,
                height = 24,
                confidence = null,
                action = GrainAction.ADDED
            )
        )

        val result = applyGrainTap(grains, 105f, 105f, GrainCorrectionMode.REMOVE)

        assertEquals(1, result.size)
        assertEquals(10, result[0].x)
    }

    @Test
    fun `add then remove then add again leaves exactly one added box`() {
        val ai = listOf(
            GrainBox(
                x = 10,
                y = 10,
                width = 24,
                height = 24,
                confidence = 0.9f,
                action = null
            )
        )

        val added = applyGrainTap(ai, 300f, 300f, GrainCorrectionMode.ADD)
        val removed = applyGrainTap(added, 305f, 305f, GrainCorrectionMode.REMOVE)
        val addedAgain = applyGrainTap(removed, 300f, 300f, GrainCorrectionMode.ADD)

        assertEquals(1, removed.size)
        assertEquals(2, addedAgain.size)
        assertEquals(1, addedAgain.count { it.action == GrainAction.ADDED })
        assertEquals(0, addedAgain.count { it.action == GrainAction.REMOVED })
    }

    @Test
    fun `remove mode tapping empty space does nothing`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result =
            applyGrainTap(grains, imageX = 500f, imageY = 500f, mode = GrainCorrectionMode.REMOVE)

        assertEquals(grains, result)
    }

    @Test
    fun `add mode tapping empty space adds a new box`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result =
            applyGrainTap(grains, imageX = 500f, imageY = 500f, mode = GrainCorrectionMode.ADD)

        assertEquals(2, result.size)
        assertEquals(GrainAction.ADDED, result[1].action)
        assertEquals(500, result[1].x)
        assertEquals(500, result[1].y)
    }

    @Test
    fun `add mode tapping an existing box does nothing`() {
        val grains = listOf(
            GrainBox(x = 100, y = 100, width = 24, height = 24, confidence = 0.8f, action = null)
        )

        val result =
            applyGrainTap(grains, imageX = 110f, imageY = 110f, mode = GrainCorrectionMode.ADD)

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

    // ---- keptGrainCount ----

    private fun box(action: GrainAction?, confidence: Float? = 0.8f) =
        GrainBox(x = 0, y = 0, width = 24, height = 24, confidence = confidence, action = action)

    @Test
    fun `keptGrainCount counts untouched, kept and added boxes`() {
        val grains = listOf(box(null), box(GrainAction.KEPT), box(GrainAction.ADDED, confidence = null))

        assertEquals(3, keptGrainCount(grains))
    }

    @Test
    fun `keptGrainCount leaves out removed boxes`() {
        val grains = listOf(box(null), box(GrainAction.REMOVED), box(GrainAction.REMOVED))

        assertEquals(1, keptGrainCount(grains))
    }

    @Test
    fun `keptGrainCount of no boxes is 0`() {
        assertEquals(0, keptGrainCount(emptyList()))
    }

    // ---- estimatedWeight ----

    @Test
    fun `estimatedWeight is 0_01 g per box with two decimals`() {
        assertEquals("0.00", estimatedWeight(0))
        assertEquals("0.03", estimatedWeight(3))
        assertEquals("0.47", estimatedWeight(47))
        assertEquals("1.00", estimatedWeight(100))
    }

    @Test
    fun `estimatedWeight of the largest box count is the 30 g limit`() {
        assertEquals("30.00", estimatedWeight(MAX_ESTIMATED_BOXES))
    }

    @Test
    fun `estimatedWeight always uses a dot, whatever the phone's locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("0.47", estimatedWeight(47))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ---- normalizedWeight ----

    @Test
    fun `normalizedWeight drops trailing zeros`() {
        assertEquals("12.45", normalizedWeight("12.450"))
        assertEquals("0.5", normalizedWeight("0.50"))
    }

    @Test
    fun `normalizedWeight keeps whole numbers whole`() {
        assertEquals("30", normalizedWeight("30"))
        assertEquals("10", normalizedWeight("10.00"))
    }

    @Test
    fun `normalizedWeight writes out exponent notation`() {
        assertEquals("10", normalizedWeight("1e1"))
        assertEquals("0.25", normalizedWeight("2.5E-1"))
    }

    @Test
    fun `normalizedWeight drops leading zeros and adds one before a bare decimal point`() {
        assertEquals("7.5", normalizedWeight("007.5"))
        assertEquals("0.5", normalizedWeight(".5"))
    }

    // ---- resolveWeight ----

    @Test
    fun `a valid typed weight is measured and wins over the boxes`() {
        assertEquals(ResolvedWeight("12.45", WeightSource.MEASURED), resolveWeight("12.45", keptBoxes = 47))
    }

    @Test
    fun `a valid typed weight works with no boxes`() {
        assertEquals(ResolvedWeight("3", WeightSource.MEASURED), resolveWeight("3", keptBoxes = 0))
    }

    @Test
    fun `a typed weight is sent normalized`() {
        assertEquals(ResolvedWeight("12.45", WeightSource.MEASURED), resolveWeight("12.450", keptBoxes = 0))
        assertEquals(ResolvedWeight("10", WeightSource.MEASURED), resolveWeight("1e1", keptBoxes = 0))
    }

    @Test
    fun `a blank field with boxes gives an estimated weight`() {
        assertEquals(ResolvedWeight("0.47", WeightSource.ESTIMATED), resolveWeight("", keptBoxes = 47))
    }

    @Test
    fun `a field of only spaces counts as blank`() {
        assertEquals(ResolvedWeight("0.05", WeightSource.ESTIMATED), resolveWeight("   ", keptBoxes = 5))
    }

    @Test
    fun `a blank field with one box and with the largest box count both estimate`() {
        assertEquals(ResolvedWeight("0.01", WeightSource.ESTIMATED), resolveWeight("", keptBoxes = 1))
        assertEquals(ResolvedWeight("30.00", WeightSource.ESTIMATED), resolveWeight("", keptBoxes = MAX_ESTIMATED_BOXES))
    }

    @Test
    fun `a blank field with no boxes gives no weight`() {
        assertNull(resolveWeight("", keptBoxes = 0))
    }

    @Test
    fun `a blank field with too many boxes to estimate gives no weight`() {
        assertNull(resolveWeight("", keptBoxes = MAX_ESTIMATED_BOXES + 1))
    }

    @Test
    fun `a typed comma decimal gives no weight, even with boxes`() {
        assertNull(resolveWeight("0,5", keptBoxes = 47))
    }

    @Test
    fun `an invalid typed weight gives no weight instead of falling back to the estimate`() {
        assertNull(resolveWeight("abc", keptBoxes = 47))
        assertNull(resolveWeight("0", keptBoxes = 47))
        assertNull(resolveWeight("30.01", keptBoxes = 47))
        assertNull(resolveWeight("-1", keptBoxes = 47))
    }

    // ---- averageConfidence ----

    @Test
    fun `averageConfidence is the mean of the boxes that have one`() {
        val grains = listOf(box(null, 0.8f), box(null, 0.9f), box(GrainAction.ADDED, confidence = null))

        assertEquals(0.85, averageConfidence(grains)!!, 1e-6)
    }

    @Test
    fun `averageConfidence still counts AI boxes the technician removed, like the backend`() {
        val grains = listOf(box(null, 0.9f), box(GrainAction.REMOVED, 0.5f))

        assertEquals(0.7, averageConfidence(grains)!!, 1e-6)
    }

    @Test
    fun `averageConfidence is null when no box has a confidence`() {
        assertNull(averageConfidence(emptyList()))
        assertNull(averageConfidence(listOf(box(GrainAction.ADDED, confidence = null))))
    }
}
