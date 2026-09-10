package com.grainmvp.android.camera

import kotlin.math.min

/**
 * A square region to crop out of the raw sensor image, in sensor pixel
 * space. x/y are the top-left corner, size is both width and height
 * (always square, matching the guide overlay).
 */
data class CropRegion(val x: Int, val y: Int, val size: Int)

/**
 * Converts the on-screen square guide overlay into the matching crop
 * region on the actual captured sensor image.
 *
 * This exists as a plain function with no Android framework dependency
 * (no Bitmap, no PreviewView) specifically so it can be covered by a
 * fast, deterministic JUnit test — this is the single most error-prone
 * piece of Phase 1's math, per the spec.
 *
 * Assumes PreviewView.ScaleType.FILL_CENTER (see CameraPreviewScreen).
 * Changing that scale type invalidates this formula and these tests.
 *
 * @param sensorW actual captured photo width, in pixels
 * @param sensorH actual captured photo height, in pixels
 * @param previewW on-screen camera preview width, in pixels
 * @param previewH on-screen camera preview height, in pixels
 * @param guideLeft guide overlay's left edge, in preview pixel space
 * @param guideTop guide overlay's top edge, in preview pixel space
 * @param guideSize guide overlay's side length, in preview pixel space
 */
fun computeCropRegion(
    sensorW: Int,
    sensorH: Int,
    previewW: Int,
    previewH: Int,
    guideLeft: Float,
    guideTop: Float,
    guideSize: Float
): CropRegion {
    val sensorAspect = sensorW.toFloat() / sensorH.toFloat()
    val previewAspect = previewW.toFloat() / previewH.toFloat()

    val visibleSensorW: Float
    val visibleSensorH: Float
    val sensorCropX: Float
    val sensorCropY: Float

    if (sensorAspect > previewAspect) {
        visibleSensorH = sensorH.toFloat()
        visibleSensorW = sensorH * previewAspect
        sensorCropX = (sensorW - visibleSensorW) / 2f
        sensorCropY = 0f
    } else {
        visibleSensorW = sensorW.toFloat()
        visibleSensorH = sensorW / previewAspect
        sensorCropX = 0f
        sensorCropY = (sensorH - visibleSensorH) / 2f
    }

    val scaleX = visibleSensorW / previewW
    val scaleY = visibleSensorH / previewH

    val finalCropX = sensorCropX + (guideLeft * scaleX)
    val finalCropY = sensorCropY + (guideTop * scaleY)
    val finalCropSize = guideSize * scaleX

    // Defensive clamp: rounding at the edges could otherwise push the
    // crop rectangle a pixel or two outside the actual sensor bounds,
    // which would crash Bitmap creation. The spec's formula doesn't
    // mention this, but it costs nothing and prevents a rare crash.
    val clampedX = finalCropX.coerceIn(0f, (sensorW - finalCropSize).coerceAtLeast(0f))
    val clampedY = finalCropY.coerceIn(0f, (sensorH - finalCropSize).coerceAtLeast(0f))
    val clampedSize = finalCropSize.coerceAtMost(minOf(sensorW.toFloat(), sensorH.toFloat()))

    return CropRegion(
        x = clampedX.toInt(),
        y = clampedY.toInt(),
        size = clampedSize.toInt()
    )
}

/**
 * Center-crops an arbitrary width/height image down to the largest
 * centered square that fits inside it. Used for gallery-picked images,
 * which — unlike a camera capture — have no guide overlay to match, so
 * a plain centered square is the closest equivalent.
 */
fun computeCenterSquareCrop(width: Int, height: Int): CropRegion {
    val size = min(width, height)
    val x = (width - size) / 2
    val y = (height - size) / 2
    return CropRegion(x = x, y = y, size = size)
}

/**
 * Largest power-of-two sample size that keeps a decoded image's shorter
 * side at or above [reqSize] — the eventual crop/scale target, so a
 * gallery pick never decodes (and holds in memory) more resolution than
 * the final output needs. No Android framework dependency, so it's
 * covered by a fast JUnit test rather than needing a real decode.
 */
fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
    var inSampleSize = 1
    while (min(width, height) / (inSampleSize * 2) >= reqSize) {
        inSampleSize *= 2
    }
    return inSampleSize
}
