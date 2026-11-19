package com.grainmvp.android.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.correction.ResolvedWeight
import com.grainmvp.android.correction.WeightSource
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.Accent100
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.ResultEyebrow
import com.grainmvp.android.ui.theme.ResultPlateBg
import com.grainmvp.android.ui.theme.SurfaceBg

/**
 * Screen 08 (Result) of the GRANULAR field redesign. The grade is the
 * reason the technician is here, so it gets the whole plate (the green
 * [BlueprintFrame] below). After sample 1, "Take photo of sample 2 of 2"
 * is the main action: it goes to Capture for sample 2 of the same entry.
 * "Retake photo of sample N" goes back to Capture with the same sample;
 * the backend numbers it as the next replicate (-02, -03 ...) and
 * auto-accepts the sample once two photos are GOOD. After sample 2 the
 * retake is the main action. "Take photo of another sample" opens
 * the Another sample form (Start Session without the technician) -- see
 * MainActivity. [weight] is shown only when it was measured; an estimated
 * weight is hidden so the estimation rule is not revealed.
 * [average] is the sample's average when this photo completed it and this
 * phone holds both accepted photos (see SampleAverage.kt).
 */
@Composable
fun ResultScreen(
    response: ReplicateResponse,
    session: SessionInfo,
    weight: ResolvedWeight,
    confirmedGrainCount: Int,
    average: SampleAverage?,
    onAnotherPhoto: () -> Unit,
    onSecondSample: () -> Unit,
    onAnotherSample: () -> Unit,
    onEndSession: () -> Unit
) {
    // "Not Classified" is far wider than "G1", so it gets a smaller size.
    val gradeFontSize = if (response.grade.length <= 3) 76.sp else 40.sp
    // Older backends send no label; fall back to the plain sample ID.
    val recordedLabel = response.replicateLabel.ifBlank { session.sampleId }
    val quality = qualityLabel(response.confidence)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        // Scrolls so the extra replicate rows and buttons still fit on small phones.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Recorded · $recordedLabel",
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

            if (response.isAccepted) {
                BlueprintFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    backgroundColor = Accent100,
                    borderColor = Accent700,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        acceptedBannerText(average),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Accent700
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 20.dp)) {
                SummaryRow("Quality", quality)
                RowDivider()
                SummaryRow("Grains", confirmedGrainCount.toString())
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
                if (weight.source == WeightSource.MEASURED) {
                    SummaryRow("Weight", "${weight.grams} g", suffix = "(measured)")
                    RowDivider()
                }
                SummaryRow("Technician", session.technicianName)
            }
        }

        val retakeText = "Retake photo of sample ${session.sampleNo}"
        if (session.sampleNo == 1) {
            PrimaryActionButton(
                text = "Take photo of sample 2 of 2",
                onClick = onSecondSample,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            SecondaryActionButton(
                text = retakeText,
                onClick = onAnotherPhoto,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        } else {
            PrimaryActionButton(
                text = retakeText,
                onClick = onAnotherPhoto,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        SecondaryActionButton(
            text = "Take photo of another sample",
            onClick = onAnotherSample,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        SecondaryActionButton(
            text = "End session",
            onClick = onEndSession,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}
