package com.grainmvp.android.testsupport

import kotlin.math.ceil

/**
 * Timings for one endpoint. Percentiles use the nearest-rank method on
 * successful requests only; failed requests count toward [errorPercent].
 */
data class LatencyStats(
    val label: String,
    val users: Int,
    val requests: Int,
    val successMs: List<Long>,
    val errors: Int
) {
    private val sorted = successMs.sorted()

    val p50: Long? get() = percentile(0.50)
    val p95: Long? get() = percentile(0.95)
    val max: Long? get() = sorted.lastOrNull()
    val errorPercent: Double get() = if (requests == 0) 0.0 else errors * 100.0 / requests

    fun percentile(p: Double): Long? {
        if (sorted.isEmpty()) return null
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** One row of the results table, in the format the test report asks for. */
    fun tableRow(targetP95Ms: Long): String {
        val verdict = if (p95 != null && p95!! <= targetP95Ms && errors == 0) "P" else "F"
        return "| $label | $users | $requests | ${p50 ?: "-"} | ${p95 ?: "-"} | ${max ?: "-"} | " +
            "${"%.1f".format(errorPercent)}% | <= $targetP95Ms | $verdict |"
    }

    companion object {
        const val TABLE_HEADER =
            "| Endpoint | Users | Requests | p50 ms | p95 ms | max ms | Error % | Target p95 ms | P/F |"
        const val TABLE_DIVIDER = "|---|---|---|---|---|---|---|---|---|"
    }
}
