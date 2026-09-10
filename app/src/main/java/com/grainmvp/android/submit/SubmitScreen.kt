package com.grainmvp.android.submit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import com.grainmvp.android.session.incrementSampleId
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.blueprintCorners
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.DividerColor
import com.grainmvp.android.ui.theme.Neutral600
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.SurfaceBg
import com.grainmvp.android.ui.theme.TextPrimaryGranular
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Screen 06 (Submit) of the GRANULAR field redesign, plus the state
 * machine behind screens 07 (Failure & retry) and 08 (Result), which
 * this composable delegates to based on how the same POST /api/replicate
 * call resolved.
 *
 * The substantive change from the old SubmitScreen: this screen no
 * longer collects technicianName/sampleId -- those are now collected
 * earlier, on home/StartSessionScreen.kt (screen 02), and carried
 * through Capture/Correction as plain parameters (see MainActivity's
 * Screen sealed class). By the time the technician reaches this screen,
 * it's a read-only confirmation of what will be sent, not a form.
 */
private sealed class SubmitState {
    data object Form : SubmitState()
    data object Loading : SubmitState()
    data class Success(val response: ReplicateResponse) : SubmitState()
    data class Error(val exceptionClassName: String) : SubmitState()
}

@Composable
fun SubmitScreen(
    technicianName: String,
    sampleId: String,
    image: Bitmap,
    aiPredictedGrains: List<GrainBox>,
    confirmedGrains: List<GrainBox>,
    weight: String,
    onEndSession: () -> Unit,
    onNextSample: (technicianName: String, nextSampleId: String) -> Unit
) {
    var state by remember { mutableStateOf<SubmitState>(SubmitState.Form) }
    val scope = rememberCoroutineScope()

    fun submit() {
        state = SubmitState.Loading
        scope.launch {
            try {
                val response = RetrofitClient.apiService.submitReplicate(
                    image = image.toImagePart(),
                    technicianName = technicianName.toTextPart(),
                    sampleId = sampleId.toTextPart(),
                    // Spec: aiPredictedGrains/confirmedGrains are JSON
                    // STRINGS inside multipart text fields, not real
                    // JSON bodies, since the whole request is
                    // multipart/form-data.
                    aiPredictedGrains = Json.encodeToString(aiPredictedGrains).toTextPart(),
                    confirmedGrains = Json.encodeToString(confirmedGrains).toTextPart(),
                    weight = weight.toTextPart()
                )
                state = SubmitState.Success(response)
            } catch (e: Exception) {
                // Broad catch is deliberate: connection refused, timeout,
                // wrong API key (401), malformed response -- all of these
                // should land here as a visible, retryable error rather
                // than crash the app. The exception's class name is what
                // FailureScreen (07) maps to plain language.
                state = SubmitState.Error(e.javaClass.simpleName)
            }
        }
    }

    when (val currentState = state) {
        is SubmitState.Success -> {
            ResultScreen(
                response = currentState.response,
                technicianName = technicianName,
                sampleId = sampleId,
                weight = weight,
                confirmedGrainCount = confirmedGrains.size,
                onNextSample = { onNextSample(technicianName, incrementSampleId(sampleId)) },
                onEndSession = onEndSession
            )
        }
        is SubmitState.Error -> {
            FailureScreen(
                exceptionClassName = currentState.exceptionClassName,
                sampleId = sampleId,
                confirmedGrainCount = confirmedGrains.size,
                onRetry = { submit() },
                // Honesty note (see docs/IMPLEMENTATION.md): this app has
                // no persistent local queue, so "Keep on device" cannot
                // literally hold the sample for a later automatic send.
                // It ends this attempt and returns to Welcome, same as
                // "End session" elsewhere.
                onKeepOnDevice = onEndSession
            )
        }
        else -> {
            SubmitFormScreen(
                technicianName = technicianName,
                sampleId = sampleId,
                weight = weight,
                image = image,
                aiCount = aiPredictedGrains.size,
                confirmedCount = confirmedGrains.size,
                isLoading = currentState is SubmitState.Loading,
                onSubmit = { submit() }
            )
        }
    }
}

@Composable
private fun SubmitFormScreen(
    technicianName: String,
    sampleId: String,
    weight: String,
    image: Bitmap,
    aiCount: Int,
    confirmedCount: Int,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        Text(
            "Submit replicate",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = TextPrimaryGranular
        )

        BlueprintFrame(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
        ) {
            SummaryRow("Technician", technicianName)
            RowDivider()
            SummaryRow("Sample ID", sampleId)
            RowDivider()
            SummaryRow("Weight", "$weight g")
            RowDivider()
            SummaryRow("Confirmed grains", confirmedCount.toString(), suffix = "(AI $aiCount)")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .aspectRatio(1f)
                .border(1.5.dp, DividerColor)
                // Corner ticks matching the "blueprint" frame used
                // elsewhere on this screen (the summary above), so the
                // thumbnail reads as a clearly contained element.
                .blueprintCorners(Accent700)
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Sample thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(
            text = if (isLoading) "Submitting…" else "Submit replicate",
            loading = isLoading,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Sends the 1024 px image, both grain lists and the weight.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            fontSize = 12.sp,
            color = Neutral600,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * A label/value row used inside a [BlueprintFrame] across the Submit
 * (06), Failure (07) and Result (08) screens. Not `private` so the
 * sibling files in this package (FailureScreen.kt, ResultScreen.kt) can
 * reuse it.
 */
@Composable
internal fun SummaryRow(label: String, value: String, suffix: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label.uppercase(), fontSize = 12.sp, letterSpacing = 0.8.sp, color = Neutral700)
        Row {
            Text(value, fontSize = 16.sp, color = TextPrimaryGranular)
            if (suffix != null) {
                Text(" $suffix", fontSize = 13.sp, color = Neutral600)
            }
        }
    }
}

/** Hairline row separator inside a [BlueprintFrame], between [SummaryRow]s. */
@Composable
internal fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}
