package com.grainmvp.android.submit

import java.util.Locale

/** The immature percentage shown to the technician: one decimal place, always with a dot (REQ-307). */
fun formatPercentage(percentage: Double): String = String.format(Locale.US, "%.1f", percentage)
