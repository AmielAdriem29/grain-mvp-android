package com.grainmvp.android.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.grainmvp.android.ImageSpec

private const val TAG = "ImageProcessing"

/**
 * Converts a raw camera capture into the final standardized image:
 * decode -> rotate to match sensor orientation -> crop to the guide's
 * region (via computeCropRegion) -> downscale to [ImageSpec.SIZE_PX].
 */
internal fun processCapturedImage(
    image: ImageProxy,
    previewWidthPx: Float,
    previewHeightPx: Float,
    guideLeft: Float,
    guideTop: Float,
    guideSize: Float
): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val rotatedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        .rotated(image.imageInfo.rotationDegrees)

    val crop = computeCropRegion(
        sensorW = rotatedBitmap.width,
        sensorH = rotatedBitmap.height,
        previewW = previewWidthPx.toInt(),
        previewH = previewHeightPx.toInt(),
        guideLeft = guideLeft,
        guideTop = guideTop,
        guideSize = guideSize
    )
    return rotatedBitmap.cropToStandardSize(crop)
}

/**
 * Converts a gallery-picked image into the final standardized image:
 * bounds-only decode -> compute inSampleSize -> downsampled decode ->
 * rotate to match EXIF orientation -> center-crop to a square (no guide
 * overlay exists for a picked photo, unlike the camera path) -> downscale
 * to [ImageSpec.SIZE_PX].
 *
 * Returns null if the URI can't be opened or decoded, if decoding throws
 * (a missing/revoked URI grant, corrupt image or EXIF data, or an
 * OutOfMemoryError on an extreme-aspect source the sample-size heuristic
 * can't shrink), or if the source is too small to fill the output size
 * without upscaling past what the grading model should trust.
 */
internal fun processPickedImage(context: Context, uri: Uri): Bitmap? {
    return try {
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, ImageSpec.SIZE_PX)
        }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        val rawBitmap = decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null

        val exifStream = context.contentResolver.openInputStream(uri) ?: return null
        val orientation = exifStream.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotatedBitmap = rawBitmap.rotated(exifRotationDegrees(orientation))

        val crop = computeCenterSquareCrop(width = rotatedBitmap.width, height = rotatedBitmap.height)
        // A source smaller than the output would get silently upscaled into a
        // well-formed-looking standard image carrying far less real detail --
        // reject it instead of feeding the grading model false confidence. The
        // caller (the gallery launcher's callback) shows a Toast when this
        // returns null, covering both this case and any other decode failure.
        if (crop.size < ImageSpec.SIZE_PX) return null

        rotatedBitmap.cropToStandardSize(crop)
    } catch (e: Exception) {
        Log.w(TAG, "Gallery image processing failed", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Gallery image processing ran out of memory", e)
        null
    }
}

/**
 * Maps an EXIF orientation tag to the clockwise rotation, in degrees,
 * that makes the image upright. Only rotations are handled; flipped or
 * mirrored orientations return 0.
 */
internal fun exifRotationDegrees(orientation: Int): Int = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

/** Returns this bitmap rotated clockwise by [degrees], or itself when [degrees] is 0. */
internal fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/** Crops this bitmap to the square [crop] region and scales it to [ImageSpec.SIZE_PX]. */
private fun Bitmap.cropToStandardSize(crop: CropRegion): Bitmap {
    val cropped = Bitmap.createBitmap(this, crop.x, crop.y, crop.size, crop.size)
    return Bitmap.createScaledBitmap(cropped, ImageSpec.SIZE_PX, ImageSpec.SIZE_PX, true)
}
