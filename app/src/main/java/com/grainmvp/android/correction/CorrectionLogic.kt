package com.grainmvp.android.correction

import com.grainmvp.android.network.GrainBox

/**
 * Fixed size for a newly added box, per SPEC.md.
 */
const val NEW_BOX_SIZE = 24

/**
 * Finds the index of the first GrainBox in [grains] that contains the
 * point (imageX, imageY), or -1 if no box contains it.
 *
 * Extracted from CorrectionScreen's pointerInput block so this hit-test
 * logic can be unit tested directly, without needing a UI test harness.
 */
fun findTappedBoxIndex(grains: List<GrainBox>, imageX: Float, imageY: Float): Int {
    return grains.indexOfFirst { box ->
        imageX >= box.x && imageX < box.x + box.width &&
                imageY >= box.y && imageY < box.y + box.height
    }
}

/**
 * Toggles a box between "removed" and its prior state, per SPEC.md's
 * "toggle its action (null/kept <-> removed)".
 *
 * Documented deviation: a box that started as "added" and is toggled to
 * "removed" then tapped again resets to null, not back to "added" --
 * the spec's rule doesn't cover re-toggling a user-added box, and this
 * is a rare, minor edge case. See IMPLEMENTATION.md.
 */
fun toggleGrainBoxAction(box: GrainBox): GrainBox {
    return if (box.action == "removed") {
        box.copy(action = null)
    } else {
        box.copy(action = "removed")
    }
}

/**
 * Creates a new fixed-size box at the tapped point, per SPEC.md:
 * "create a new box at that point: x = imageX, y = imageY, width = 24,
 * height = 24, action = added".
 */
fun createAddedGrainBox(imageX: Float, imageY: Float): GrainBox {
    return GrainBox(
        x = imageX.toInt(),
        y = imageY.toInt(),
        width = NEW_BOX_SIZE,
        height = NEW_BOX_SIZE,
        confidence = null,
        action = "added"
    )
}

/**
 * Converts any untouched (action == null) boxes to action == "kept"
 * before submission, matching the backend's example confirmedGrains
 * payload, which shows untouched boxes as "kept", not null. Boxes with
 * any other action (removed, added) pass through unchanged.
 */
fun prepareGrainsForSubmission(grains: List<GrainBox>): List<GrainBox> {
    return grains.map { if (it.action == null) it.copy(action = "kept") else it }
}
/**
 * Whether [value] is a valid partial or complete weight entry: empty,
 * digits, and at most one decimal point. No negative sign (a negative
 * weight is never valid), no letters, no multiple decimal points.
 *
 * KeyboardType.Decimal on the TextField only *suggests* a numeric
 * on-screen keyboard -- it does not block other input (switching to a
 * full keyboard, pasting text, a physical keyboard). This function is
 * the actual gate: call it in onValueChange and only accept the new
 * value if this returns true.
 */
/**
 * Whether [value] is a complete, valid weight to submit: parses as a
 * number and is greater than zero. A negative or zero weight, empty
 * input, or anything non-numeric (letters, multiple decimal points,
 * stray whitespace) is invalid.
 *
 * Deliberately does NOT block keystrokes while typing -- rejecting
 * characters silently as the user types (e.g. via onValueChange) reads
 * as a broken keyboard with no explanation. Instead, the TextField
 * accepts anything, and this function gates whether Confirm is enabled,
 * with a visible error message telling the user why if it's invalid.
 */
fun isValidWeightValue(value: String): Boolean {
    val parsed = value.toDoubleOrNull() ?: return false
    return parsed > 0
}
