package com.grainmvp.android.network

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * THROWAWAY — this screen exists only to satisfy the spec's own Phase 2
 * checkpoint: "you can call both endpoints from a throwaway test screen
 * and get back parsed responses." It is not part of the real app flow
 * and is not wired into MainActivity's normal navigation — see the
 * temporary debug toggle in MainActivity.kt.
 *
 * Uses a tiny solid-color placeholder bitmap rather than a real capture,
 * since the point here is proving the network plumbing works (auth
 * header, multipart encoding, response parsing), not testing real
 * capture data — that's what the actual capture screen already covers.
 *
 * Delete this file once Phase 2 is verified against a real running
 * backend and Phase 3/4 are wired to the real calls instead.
 */
@Composable
fun NetworkTestScreen() {
    var resultText by remember { mutableStateOf("Not tested yet.") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Phase 2 Network Test (throwaway)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            resultText,
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            enabled = !isLoading,
            onClick = {
                isLoading = true
                resultText = "Calling POST /api/predict..."
                scope.launch {
                    try {
                        val fakeBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
                            .apply { eraseColor(Color.RED) }

                        val response = RetrofitClient.apiService.predict(
                            image = fakeBitmap.toImagePart(),
                            technicianName = "network-test".toTextPart(),
                            sampleId = "network-test-sample".toTextPart()
                        )

                        resultText = "SUCCESS\n" +
                                "imageId = ${response.imageId}\n" +
                                "grains returned = ${response.grains.size}"
                    } catch (e: Exception) {
                        // Deliberately broad catch here -- this screen's whole
                        // job is to surface ANY failure (wrong URL, wrong key,
                        // server down, bad JSON shape) as visible text rather
                        // than crash, since that's the point of a smoke test.
                        resultText = "FAILED\n${e.javaClass.simpleName}: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        ) {
            Text(if (isLoading) "Calling..." else "Call POST /api/predict")
        }
    }
}