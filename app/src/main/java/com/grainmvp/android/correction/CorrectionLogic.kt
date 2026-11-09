package com.grainmvp.android.correction

import com.grainmvp.android.network.GrainAction
import com.grainmvp.android.network.GrainBox

/**
 * Fixed size for a newly added box, per SPEC.md.
 */
const val NEW_BOX_SIZE = 24

/** Largest immature-grain weight accepted, in grams (REQ-305). The backend enforces the same limit. */
const val MAX_WEIGHT_GRAMS = 30.0

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
 * Only meant for boxes the AI predicted. A user-added box is never
 * toggled: [applyGrainTap] deletes it outright instead, so "removed"
 * always means "the AI predicted this and the technician rejected it".
 */
fun toggleGrainBoxAction(box: GrainBox): GrainBox {
    return if (box.action == GrainAction.REMOVED) {
        box.copy(action = null)
    } else {
        box.copy(action = GrainAction.REMOVED)
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
        action = GrainAction.ADDED
    )
}

/**
 * Converts any untouched (action == null) boxes to action == "kept"
 * before submission, matching the backend's example confirmedGrains
 * payload, which shows untouched boxes as "kept", not null. Boxes with
 * any other action (removed, added) pass through unchanged.
 */
fun prepareGrainsForSubmission(grains: List<GrainBox>): List<GrainBox> {
    return grains.map { if (it.action == null) it.copy(action = GrainAction.KEPT) else it }
}

/**
 * Whether [value] is a complete, valid weight to submit: parses as a
 * number, is greater than zero and at most [MAX_WEIGHT_GRAMS]. A
 * negative, zero or over-limit weight, empty input, or anything
 * non-numeric (letters, multiple decimal points, stray whitespace) is
 * invalid.
 *
 * Deliberately does NOT block keystrokes while typing -- rejecting
 * characters silently as the user types (e.g. via onValueChange) reads
 * as a broken keyboard with no explanation. Instead, the TextField
 * accepts anything, and this function gates whether Confirm is enabled,
 * with a visible error message telling the user why if it's invalid.
 */
fun isValidWeightValue(value: String): Boolean {
    val parsed = value.toDoubleOrNull() ?: return false
    return parsed > 0 && parsed <= MAX_WEIGHT_GRAMS
}

/**
 * Screen 05 (Review & correct) of the GRANULAR field redesign replaces
 * the old always-toggle-or-add tap behavior with an explicit mode, so a
 * tap is never ambiguous about what it will do.
 */
enum class GrainCorrectionMode { REMOVE, ADD }

/**
 * Applies a tap at (imageX, imageY) to [grains] given the current
 * [mode]:
 *  - REMOVE mode: tapping an AI box toggles it kept/removed (via
 *    [toggleGrainBoxAction]); tapping a box the technician added deletes
 *    it from the list; tapping empty space does nothing.
 *  - ADD mode: tapping empty space adds a new 24x24 box (via
 *    [createAddedGrainBox]); tapping an existing box does nothing.
 *
 * Returns [grains] unchanged (same list reference) when the tap has no
 * effect, or a new list when something changed -- callers can compare
 * by reference to know whether to update their UI state.
 *
 * Pure and framework-free like the rest of this file's tap logic, so it
 * can be unit tested directly (see CorrectionLogicTest.kt).
 */
fun applyGrainTap(
    grains: List<GrainBox>,
    imageX: Float,
    imageY: Float,
    mode: GrainCorrectionMode
): List<GrainBox> {
    val tappedIndex = findTappedBoxIndex(grains, imageX, imageY)
    return when (mode) {
        GrainCorrectionMode.REMOVE -> {
            if (tappedIndex < 0) {
                grains
            } else {
                grains.toMutableList().also {
                    if (it[tappedIndex].action == GrainAction.ADDED) {
                        it.removeAt(tappedIndex)
                    } else {
                        it[tappedIndex] = toggleGrainBoxAction(it[tappedIndex])
                    }
                }
            }
        }

        GrainCorrectionMode.ADD -> {
            if (tappedIndex >= 0) {
                grains
            } else {
                grains + createAddedGrainBox(imageX, imageY)
            }
        }
    }
}

/**
 * Converts a tap position in the correction screen's outer, untransformed
 * box coordinate space (screen px, 0..boxSizePx) into 1024-space image
 * coordinates, accounting for the pinch-zoom/pan transform applied to the
 * inner image + grain-box-overlay content (see CorrectionScreen's
 * `graphicsLayer`). That transform scales the content around the box's
 * center and then translates it by the pan offset, so this inverts
 * exactly that: recover the position within the untransformed content
 * box, then divide by displayScale (screen px -> 1024 image px), same
 * conversion as before zoom/pan existed.
 *
 * Pure and framework-free (plain floats, no Offset/graphicsLayer types)
 * like the rest of this file's tap logic, so it's unit-testable directly.
 * At zoom == 1 and pan == 0 this reduces to the original
 * `imageX = screenX / displayScale` formula.
 */
fun screenTapToImageCoords(
    screenX: Float,
    screenY: Float,
    boxSizePx: Float,
    displayScale: Float,
    zoom: Float,
    panX: Float,
    panY: Float
): Pair<Float, Float> {
    val center = boxSizePx / 2f
    val contentX = center + (screenX - center - panX) / zoom
    val contentY = center + (screenY - center - panY) / zoom
    return (contentX / displayScale) to (contentY / displayScale)
}

/**
 * Clamps a pan offset (on one axis) so the zoomed content -- [zoom] x
 * [boxSizePx] on a side -- never pans far enough to reveal empty space
 * around the [boxSizePx] x [boxSizePx] viewport. Panning is only
 * possible up to how far the zoomed content overhangs the viewport on
 * that axis, and is fully locked at zoom == 1 (maxOffset is 0, so any
 * pan value collapses back to 0).
 */
fun clampPan(pan: Float, boxSizePx: Float, zoom: Float): Float {
    val maxOffset = (boxSizePx * (zoom - 1f) / 2f).coerceAtLeast(0f)
    return pan.coerceIn(-maxOffset, maxOffset)
}
