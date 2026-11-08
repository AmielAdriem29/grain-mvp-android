package com.grainmvp.android.camera

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessingTest {

    @Test
    fun `normal orientation needs no rotation`() {
        assertEquals(0, exifRotationDegrees(ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun `rotate 90 orientation maps to 90 degrees`() {
        assertEquals(90, exifRotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
    }

    @Test
    fun `rotate 180 orientation maps to 180 degrees`() {
        assertEquals(180, exifRotationDegrees(ExifInterface.ORIENTATION_ROTATE_180))
    }

    @Test
    fun `rotate 270 orientation maps to 270 degrees`() {
        assertEquals(270, exifRotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun `flipped orientations are not rotated`() {
        assertEquals(0, exifRotationDegrees(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(0, exifRotationDegrees(ExifInterface.ORIENTATION_TRANSPOSE))
    }

    @Test
    fun `undefined orientation is not rotated`() {
        assertEquals(0, exifRotationDegrees(ExifInterface.ORIENTATION_UNDEFINED))
    }
}
