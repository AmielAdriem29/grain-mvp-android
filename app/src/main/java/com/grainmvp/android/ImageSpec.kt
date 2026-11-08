package com.grainmvp.android

/**
 * The fixed image format every part of GRANULAR shares.
 *
 * Every captured or picked photo is standardized to [SIZE_PX] x [SIZE_PX],
 * and every GrainBox coordinate is in that space. The backend and the
 * dashboard assume the same size, so change it in all three together.
 */
object ImageSpec {
    const val SIZE_PX = 1024
}
