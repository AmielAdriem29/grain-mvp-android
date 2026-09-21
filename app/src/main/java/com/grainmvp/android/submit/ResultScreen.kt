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
import com.grainmvp.android.session.incrementSampleId
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.ResultEyebrow
import com.grainmvp.android.ui.theme.ResultPlateBg
import com.grainmvp.android.ui.theme.SurfaceBg
import kotlin.math.roundToInt

/**
 * Screen 08 (Result) of the GRANULAR field redesign. The grade is the
 * reason the technician is here, so it gets the whole plate (the green
 * [BlueprintFrame] below). "Next sample" keeps the same technician and
 * increments the sample ID (via [incrementSampleId]), going straight
 * back to Capture and skipping Start Session -- see MainActivity.
 */
@Composable
fun ResultScreen(
    response: ReplicateResponse,
    technicianName: String,
    sampleId: String,
    weight: String,
    confirmedGrainCount: Int,
    onNextSample: () -> Unit,
    onEndSession: () -> Unit
) {
    // totalGrains is NOT returned by the backend -- it's derived exactly
    // (not a guess) from percentage = immature/total * 100, i.e.
    // total = immature / (percentage / 100). Worth a one-line comment
    // since it's derived, not backend-supplied (see docs/IMPLEMENTATION.md).
    val totalGrains = if (response.percentage > 0.0) {
        (confirmedGrainCount / (response.percentage / 100.0)).roundToInt()
    } else {
        confirmedGrainCount
    }
    val nextSampleId = incrementSampleId(sampleId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        Text(
            "Recorded · $sampleId",
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
                fontSize = 76.sp,
                lineHeight = 76.sp,
                color = Color.White
            )
            Text(
                "${response.percentage}% immature",
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
            SummaryRow("Weight", "$weight g")
            RowDivider()
            SummaryRow("Technician", technicianName)
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(
            text = "Next sample · $nextSampleId",
            onClick = onNextSample,
            modifier = Modifier.fillMaxWidth()
        )
        SecondaryActionButton(
            text = "End session",
            onClick = onEndSession,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}
