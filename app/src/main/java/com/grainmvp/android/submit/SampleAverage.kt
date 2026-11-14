package com.grainmvp.android.submit

import com.grainmvp.android.network.ReplicateQuality
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.session.SessionInfo
import java.math.BigDecimal
import java.math.RoundingMode

/** One photo submitted during this app session, kept so Result can show the sample's average once accepted. */
data class SubmittedPhoto(
    val session: SessionInfo,
    val weight: String,
    val response: ReplicateResponse
)

/** The averaged result of a sample's accepted photos. */
data class SampleAverage(val weight: BigDecimal, val percentage: BigDecimal, val grade: String)

// ponytail: bands, sample weight and rounding copied from backend CalculationService
// (dev branch). Change both together; the dashboard's /api/samples value is authoritative.
private val SAMPLE_WEIGHT_GRAMS = BigDecimal("30")
private val HUNDRED = BigDecimal("100")
private const val ACCEPTED_PHOTOS_PER_SAMPLE = 2

/** Grade for an immature percentage: Pr < 2.0, G1 <= 5, G2 <= 10, G3 <= 15, otherwise Not classified. */
fun gradeFor(percentage: BigDecimal): String = when {
    percentage < BigDecimal("2.0") -> "Pr"
    percentage <= BigDecimal("5.0") -> "G1"
    percentage <= BigDecimal("10.0") -> "G2"
    percentage <= BigDecimal("15.0") -> "G3"
    else -> "Not classified"
}

/**
 * Mean weight rounded half-up to 2 decimals, then percentage = weight / 30 * 100
 * rounded half-up to 1 decimal -- the same steps as the backend. HALF_UP matches
 * .NET MidpointRounding.AwayFromZero for these positive values.
 */
fun sampleAverage(weights: List<BigDecimal>): SampleAverage {
    require(weights.isNotEmpty()) { "weights must not be empty" }
    val weight = weights.reduce(BigDecimal::add)
        .divide(BigDecimal(weights.size), 2, RoundingMode.HALF_UP)
    val percentage = weight.multiply(HUNDRED).divide(SAMPLE_WEIGHT_GRAMS, 1, RoundingMode.HALF_UP)
    return SampleAverage(weight, percentage, gradeFor(percentage))
}

/**
 * Average of [session]'s sample from the photos this phone submitted: the
 * earliest two GOOD photos by replicate number, the same pick the backend
 * makes when it auto-accepts. Null when this phone has fewer than two GOOD
 * photos of that sample (e.g. one was sent from another device).
 */
fun sampleAverageFor(session: SessionInfo, photos: List<SubmittedPhoto>): SampleAverage? {
    val weights = photos
        .filter { it.session.isSameSampleAs(session) && it.response.quality == ReplicateQuality.GOOD }
        .sortedBy { it.response.replicateNo }
        .take(ACCEPTED_PHOTOS_PER_SAMPLE)
        .mapNotNull { it.weight.toBigDecimalOrNull() }
    return if (weights.size == ACCEPTED_PHOTOS_PER_SAMPLE) sampleAverage(weights) else null
}

/** Same grouping as the backend: season + index number + sample no. */
private fun SessionInfo.isSameSampleAs(other: SessionInfo): Boolean =
    season == other.season && indexNumber == other.indexNumber && sampleNo == other.sampleNo
