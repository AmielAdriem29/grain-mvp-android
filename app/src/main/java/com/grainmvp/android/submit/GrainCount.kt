package com.grainmvp.android.submit

import kotlin.math.roundToInt

/** Total grains derived from the confirmed count and the immature percentage. */
fun totalGrainCount(confirmedGrainCount: Int, percentage: Double): Int =
    if (percentage > 0.0) {
        (confirmedGrainCount / (percentage / 100.0)).roundToInt()
    } else {
        confirmedGrainCount
    }
