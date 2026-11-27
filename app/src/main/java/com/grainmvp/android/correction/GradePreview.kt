package com.grainmvp.android.correction

import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.submit.gradeFor
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.math.RoundingMode

/** A grade to show before submitting, and whether it came from the backend or the phone. */
data class GradePreview(val grade: String, val percentage: Double, val fromServer: Boolean)

/** Grade calculated on the phone, the same steps as backend CalculationService. Pure, so it is unit-tested. */
fun localGradePreview(weight: String): GradePreview {
    val percentage = BigDecimal(weight).multiply(BigDecimal(100))
        .divide(BigDecimal(30), 1, RoundingMode.HALF_UP)
    return GradePreview(gradeFor(percentage), percentage.toDouble(), fromServer = false)
}

/** Asks GET /api/grade; on any failure (offline, older backend without the endpoint) uses [localGradePreview]. */
suspend fun gradePreview(weight: String): GradePreview = try {
    RetrofitClient.apiService.grade(weight).let { GradePreview(it.grade, it.percentage, fromServer = true) }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    localGradePreview(weight)
}
