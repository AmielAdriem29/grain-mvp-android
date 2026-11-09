package com.grainmvp.android.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.ResultEyebrow
import com.grainmvp.android.ui.theme.ResultPlateBg
import com.grainmvp.android.ui.theme.SurfaceBg

/**
 * Screen 08 (Result) of the GRANULAR field redesign. The grade is the
 * reason the technician is here, so it gets the whole plate (the green
 * [BlueprintFrame] below). "Add second sample" (only after sample 1)
 * goes straight back to Capture for sample 2 of the same entry.
 * "Next sample" returns to Start Session with the technician, season and
 * incremented sample ID (via [incrementSampleId]) prefilled -- see
 * MainActivity.
 */
@Composable
fun ResultScreen(
    response: ReplicateResponse,
    session: SessionInfo,
    weight: String,
    confirmedGrainCount: Int,
    onAddSecondSample: () -> Unit,
    onNextSample: () -> Unit,
    onEndSession: () -> Unit
) {
    // totalGrains is NOT returned by the backend -- it's derived exactly
    // (not a guess) from percentage = immature/total * 100, i.e.
    // total = immature / (percentage / 100). Worth a one-line comment
    // since it's derived, not backend-supplied (see docs/IMPLEMENTATION.md).
    val totalGrains = totalGrainCount(confirmedGrainCount, response.percentage)
    val nextSampleId = session.nextSample().sampleId
    // "Not classified" is far wider than "G1", so it gets a smaller size.
    val gradeFontSize = if (response.grade.length <= 3) 76.sp else 40.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        Text(
            "Recorded · ${session.sampleId}",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            color = Accent700
        )

        BlueprintFrame(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            backgroundColor = ResultPlateBg,
            borderColor = ResultPlateBg,
            tickColor = Color.White.copy(alpha = 0.6f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Text(
                "GRADE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = ResultEyebrow
            )
            Text(
                response.grade,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = gradeFontSize,
                lineHeight = gradeFontSize,
                color = Color.White
            )
            Text(
                "${formatPercentage(response.percentage)}% immature",
                modifier = Modifier.padding(top = 8.dp),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = Color.White
            )
        }

        Column(modifier = Modifier.padding(top = 20.dp)) {
            SummaryRow("Grains", "$confirmedGrainCount of $totalGrains")
            RowDivider()
            SummaryRow("Sample", "${session.sampleNo} of 2")
            RowDivider()
            response.meetsRecommendedValue?.let { meets ->
                SummaryRow(
                    "Recommended value",
                    if (meets) "Meets (below 2.0%)" else "Does not meet"
                )
                RowDivider()
            }
            SummaryRow("Weight", "$weight g")
            RowDivider()
            SummaryRow("Technician", session.technicianName)
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(
            text = "Next sample · $nextSampleId",
            onClick = onNextSample,
            modifier = Modifier.fillMaxWidth()
        )
        if (session.sampleNo == 1) {
            SecondaryActionButton(
                text = "Add second sample",
                onClick = onAddSecondSample,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        SecondaryActionButton(
            text = "End session",
            onClick = onEndSession,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}
