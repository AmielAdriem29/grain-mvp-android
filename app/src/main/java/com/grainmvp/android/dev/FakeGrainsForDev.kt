package com.grainmvp.android.dev

import com.grainmvp.android.network.GrainBox

/**
 * TEMPORARY -- NOT PART OF THE SPEC.
 *
 * This is a development-only stand-in for what POST /api/predict would
 * actually return. It exists purely so Phase 3 (the Correction Screen)
 * can be built and tested without a working backend, since Sitoy hasn't
 * started backend work yet.
 *
 * This is NOT a spec requirement -- it's a workaround so Android
 * development isn't blocked waiting on the backend. See
 * docs/IMPLEMENTATION.md for the full reasoning.
 *
 * DELETE THIS FILE once Phase 2's predict() call is verified against a
 * real running backend, and swap its one call site (MainActivity) over
 * to a real predict() call instead.
 */
val FAKE_GRAINS_FOR_DEV_ONLY = listOf(
    GrainBox(x = 150, y = 200, width = 24, height = 24, confidence = 0.91f, action = null),
    GrainBox(x = 300, y = 250, width = 24, height = 24, confidence = 0.87f, action = null),
    GrainBox(x = 450, y = 400, width = 24, height = 24, confidence = 0.62f, action = null),
    GrainBox(x = 600, y = 550, width = 24, height = 24, confidence = 0.78f, action = null),
    GrainBox(x = 700, y = 300, width = 24, height = 24, confidence = 0.55f, action = null),
)