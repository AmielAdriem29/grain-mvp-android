package com.grainmvp.android.submit

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.ReplicateResponse
import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import com.grainmvp.android.ui.components.AppHeader
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase 4 — Submit, per SPEC.md:
 *  - collects technicianName + sampleId (Fateful's wireframe puts these
 *    on their own screen, after Review and Correct's Confirm button)
 *  - wires Submit to the real POST /api/replicate call
 *  - disables the button + shows a spinner while in flight, no double-tap
 *  - on success: show confirmation (percentage/grade), return to capture
 *  - on failure: clear error message, manual retry re-sends the SAME
 *    request, no special duplicate-prevention (per spec, this is
 *    explicitly accepted)
 *
 * Unlike Phase 3, this calls the REAL submitReplicate() directly rather
 * than a fake dev stand-in -- see IMPLEMENTATION.md for why. Testing
 * this right now will genuinely fail (no reachable backend yet), which
 * is fine: that failure exercises the real error/retry code path this
 * screen needs anyway. Only the success path stays unverified until
 * Sitoy's server exists.
 */
private sealed class SubmitState {
    data object Form : SubmitState()
    data object Loading : SubmitState()
    data class Success(val response: ReplicateResponse) : SubmitState()
    data class Error(val message: String) : SubmitState()
}

@Composable
fun SubmitScreen(
    image: Bitmap,
    aiPredictedGrains: List<GrainBox>,
    confirmedGrains: List<GrainBox>,
    weight: String,
    onDone: () -> Unit
) {
    var technicianName by remember { mutableStateOf("") }
    var sampleId by remember { mutableStateOf("") }
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
                // than crash the app.
                state = SubmitState.Error("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    val currentState = state
    if (currentState is SubmitState.Success) {
        SuccessScreen(response = currentState.response, onDone = onDone)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader("Submit Sample")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("Name:", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = technicianName,
                onValueChange = { technicianName = it },
                enabled = currentState !is SubmitState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            Text("SampleType", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = sampleId,
                onValueChange = { sampleId = it },
                enabled = currentState !is SubmitState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            if (currentState is SubmitState.Error) {
                Text(
                    "Submission failed: ${currentState.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = { submit() },
                // Spec: disable while in flight, no second tap allowed.
                enabled = currentState !is SubmitState.Loading &&
                        technicianName.isNotBlank() &&
                        sampleId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentState is SubmitState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Submitting...")
                } else if (currentState is SubmitState.Error) {
                    // Spec: retry simply calls the same endpoint again with
                    // the same data -- no special duplicate-prevention logic.
                    Text("Retry Submit")
                } else {
                    Text("Submit")
                }
            }
        }
    }
}

@Composable
private fun SuccessScreen(response: ReplicateResponse, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Submitted successfully!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            "Grade: ${response.grade}",
            modifier = Modifier.padding(top = 16.dp)
        )
        Text("Percentage: ${response.percentage}%")
        Button(onClick = onDone, modifier = Modifier.padding(top = 24.dp)) {
            Text("New Sample")
        }
    }
}