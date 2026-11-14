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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.ImageSpec
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.session.SessionInfo
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
import retrofit2.HttpException
import java.time.Instant

/**
 * Screen 06 (Submit) of the GRANULAR field redesign, plus the state
 * machine behind screens 07 (Failure & retry) and 08 (Result), which
 * this composable delegates to based on how the same POST /api/replicate
 * call resolved.
 *
 * The substantive change from the old SubmitScreen: this screen no
 * longer collects technicianName/sampleId -- those are now collected
 * earlier, on home/StartSessionScreen.kt (screen 02), and carried
 * through Capture/Correction as a SessionInfo (see MainActivity's
 * Screen sealed class). By the time the technician reaches this screen,
 * it's a read-only confirmation of what will be sent, not a form.
 */
private sealed class SubmitState {
    data object Form : SubmitState()
    data object Loading : SubmitState()
    data class Success(val response: ReplicateResponse) : SubmitState()
    /** [httpCode] is set when the server answered with an error status (for example 409), null for network failures. */
    data class Error(val exceptionClassName: String, val httpCode: Int?) : SubmitState()
}

@Composable
fun SubmitScreen(
    session: SessionInfo,
    image: Bitmap,
    aiPredictedGrains: List<GrainBox>,
    confirmedGrains: List<GrainBox>,
    weight: String,
    capturedAt: Instant,
    // Photos already sent this session; Result averages the sample's GOOD ones once accepted.
    submittedPhotos: List<SubmittedPhoto>,
    onSubmitted: (SubmittedPhoto) -> Unit,
    onEndSession: () -> Unit,
    onAnotherPhoto: () -> Unit,
    onAddSecondSample: (secondSession: SessionInfo) -> Unit,
    onNextSample: (nextSession: SessionInfo) -> Unit,
    // Called with everything needed to retry this submission later, once
    // the technician taps "Keep on device" on the Failure screen -- see
    // MainActivity's replicateQueue and ReplicateQueue.kt.
    onKeepOnDevice: (QueuedReplicate) -> Unit
) {
    var state by remember { mutableStateOf<SubmitState>(SubmitState.Form) }
    val scope = rememberCoroutineScope()
    // Set once, on the first tap of Submit. A retry (Retry now, or from
    // the queue) re-sends this original value instead of a new time.
    var submittedAt by remember { mutableStateOf<Instant?>(null) }
    var manuallyOverridden by remember { mutableStateOf(false) }

    fun buildQueuedItem(): QueuedReplicate {
        val at = submittedAt ?: Instant.now().also { submittedAt = it }
        return QueuedReplicate(
            image = image,
            session = session,
            aiPredictedGrains = aiPredictedGrains,
            confirmedGrains = confirmedGrains,
            weight = weight,
            manuallyOverridden = manuallyOverridden,
            capturedAt = capturedAt,
            submittedAt = at
        )
    }

    fun submit() {
        state = SubmitState.Loading
        val item = buildQueuedItem()
        scope.launch {
            try {
                val response = submitQueuedReplicate(item)
                onSubmitted(SubmittedPhoto(session, weight, response))
                state = SubmitState.Success(response)
            } catch (e: Exception) {
                // Broad catch is deliberate: connection refused, timeout,
                // wrong API key (401), malformed response -- all of these
                // should land here as a visible, retryable error rather
                // than crash the app. The exception's class name is what
                // FailureScreen (07) maps to plain language.
                state = SubmitState.Error(e.javaClass.simpleName, (e as? HttpException)?.code())
            }
        }
    }

    when (val currentState = state) {
        is SubmitState.Success -> {
            ResultScreen(
                response = currentState.response,
                session = session,
                weight = weight,
                confirmedGrainCount = confirmedGrains.size,
                manuallyOverridden = manuallyOverridden,
                average = if (currentState.response.isAccepted) sampleAverageFor(session, submittedPhotos) else null,
                onAnotherPhoto = onAnotherPhoto,
                onAddSecondSample = { onAddSecondSample(session.secondSample()) },
                onNextSample = { onNextSample(session.nextSample()) },
                onEndSession = onEndSession
            )
        }
        is SubmitState.Error -> {
            FailureScreen(
                exceptionClassName = currentState.exceptionClassName,
                httpCode = currentState.httpCode,
                sampleId = session.sampleId,
                confirmedGrainCount = confirmedGrains.size,
                onRetry = { submit() },
                onKeepOnDevice = { onKeepOnDevice(buildQueuedItem()) }
            )
        }
        else -> {
            SubmitFormScreen(
                session = session,
                weight = weight,
                image = image,
                aiCount = aiPredictedGrains.size,
                confirmedCount = confirmedGrains.size,
                isLoading = currentState is SubmitState.Loading,
                manuallyOverridden = manuallyOverridden,
                onManuallyOverriddenChange = { manuallyOverridden = it },
                onSubmit = { submit() }
            )
        }
    }
}

@Composable
private fun SubmitFormScreen(
    session: SessionInfo,
    weight: String,
    image: Bitmap,
    aiCount: Int,
    confirmedCount: Int,
    isLoading: Boolean,
    manuallyOverridden: Boolean,
    onManuallyOverriddenChange: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        // Scrolls so the longer summary (entry details) still fits on small phones.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
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
                SummaryRow("Technician", session.technicianName)
                RowDivider()
                SummaryRow("Entry", "${session.season} · ${session.indexNumber}")
                RowDivider()
                SummaryRow("Selection", session.selectionName)
                RowDivider()
                SummaryRow("Sample ID", session.sampleId, suffix = "(${session.sampleNo} of 2)")
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
        }

        ManualOverrideToggle(
            checked = manuallyOverridden,
            enabled = !isLoading,
            onCheckedChange = onManuallyOverriddenChange
        )

        PrimaryActionButton(
            text = if (isLoading) "Submitting…" else "Submit replicate",
            loading = isLoading,
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
        Text(
            "Sends the ${ImageSpec.SIZE_PX} px image, both grain lists, the weight and the entry details.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            fontSize = 12.sp,
            color = Neutral600,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * "I verified this count manually": sent as manuallyOverridden, which makes
 * the backend mark the photo GOOD regardless of AI confidence. The whole row
 * is the touch target, not just the box.
 */
@Composable
private fun ManualOverrideToggle(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Accent700)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text("I verified this count manually", fontSize = 15.sp, color = TextPrimaryGranular)
            Text(
                "Marks this photo GOOD regardless of AI confidence.",
                fontSize = 12.sp,
                color = Neutral600
            )
        }
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
