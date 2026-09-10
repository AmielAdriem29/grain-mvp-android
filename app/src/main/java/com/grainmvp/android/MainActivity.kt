package com.grainmvp.android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grainmvp.android.camera.CameraPreviewScreen
import com.grainmvp.android.correction.CorrectionScreen
import com.grainmvp.android.dev.FAKE_GRAINS_FOR_DEV_ONLY
import com.grainmvp.android.home.StartSessionScreen
import com.grainmvp.android.home.WelcomeScreen
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.NetworkTestScreen
import com.grainmvp.android.submit.SubmitScreen
import com.grainmvp.android.ui.theme.GrainMvpTheme

/**
 * Entry point. Screens are swapped in per phase branch; as of the
 * GRANULAR field redesign (2026-09), the flow is:
 *  - Welcome (opening splash)
 *  - Start Session (technician + sample ID -- collected once here, not
 *    on Submit anymore)
 *  - Capture -> Correction -> Submit, all carrying technicianName/
 *    sampleId through as plain parameters
 *  - Submit's Result screen's "Next sample" skips straight back to
 *    Capture with the same technician and an incremented sample ID,
 *    bypassing Start Session
 *
 * Screen state is a simple sealed class rather than a real navigation
 * library, since there are still only a handful of screens.
 */
private sealed class Screen {
    data object Welcome : Screen()
    data object StartSession : Screen()
    data class Capture(val technicianName: String, val sampleId: String) : Screen()
    data class Correction(val image: Bitmap, val technicianName: String, val sampleId: String) : Screen()
    data class Submit(
        val image: Bitmap,
        val aiPredictedGrains: List<GrainBox>,
        val confirmedGrains: List<GrainBox>,
        val weight: String,
        val technicianName: String,
        val sampleId: String
    ) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrainMvpApp()
        }
    }
}

@Composable
fun GrainMvpApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }

    // Simple in-memory default (no persistence needed) so returning to
    // Start Session after ending a session pre-fills the last-used
    // technician name, per the redesign brief.
    var lastTechnicianName by remember { mutableStateOf("") }

    // TEMPORARY debug toggle to reach NetworkTestScreen (throwaway,
    // see network/NetworkTestScreen.kt). Only available from the
    // Capture screen. Remove this toggle once real navigation exists
    // and Phase 2 is verified -- this is not part of the real app flow.
    var showNetworkTest by remember { mutableStateOf(false) }

    GrainMvpTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val currentScreen = screen) {
                is Screen.Welcome -> {
                    WelcomeScreen(onStart = { screen = Screen.StartSession })
                }

                is Screen.StartSession -> {
                    StartSessionScreen(
                        defaultTechnicianName = lastTechnicianName,
                        onStartScan = { technicianName, sampleId ->
                            lastTechnicianName = technicianName
                            screen = Screen.Capture(technicianName, sampleId)
                        }
                    )
                }

                is Screen.Capture -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showNetworkTest) {
                            NetworkTestScreen()
                        } else {
                            CameraPreviewScreen(
                                technicianName = currentScreen.technicianName,
                                sampleId = currentScreen.sampleId,
                                onImageConfirmed = { bitmap ->
                                    screen = Screen.Correction(
                                        image = bitmap,
                                        technicianName = currentScreen.technicianName,
                                        sampleId = currentScreen.sampleId
                                    )
                                }
                            )
                        }

                        Button(
                            onClick = { showNetworkTest = !showNetworkTest },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(if (showNetworkTest) "Camera" else "Network Test")
                        }
                    }
                }

                is Screen.Correction -> {
                    CorrectionScreen(
                        image = currentScreen.image,
                        // TEMPORARY: real predicted grains come from Phase 2's
                        // predict() once verified against a real backend. See
                        // dev/FakeGrainsForDev.kt -- delete this fake data and
                        // call the real endpoint instead once Sitoy's server
                        // is reachable.
                        initialGrains = FAKE_GRAINS_FOR_DEV_ONLY,
                        onRetakePhoto = {
                            screen = Screen.Capture(currentScreen.technicianName, currentScreen.sampleId)
                        },
                        onSubmit = { confirmedGrains, weight ->
                            screen = Screen.Submit(
                                image = currentScreen.image,
                                // The "unmodified original AI list" the spec
                                // requires for aiPredictedGrains is exactly
                                // what Correction started from -- currently
                                // the same fake data, until Phase 2 is real.
                                aiPredictedGrains = FAKE_GRAINS_FOR_DEV_ONLY,
                                confirmedGrains = confirmedGrains,
                                weight = weight,
                                technicianName = currentScreen.technicianName,
                                sampleId = currentScreen.sampleId
                            )
                        }
                    )
                }

                is Screen.Submit -> {
                    SubmitScreen(
                        technicianName = currentScreen.technicianName,
                        sampleId = currentScreen.sampleId,
                        image = currentScreen.image,
                        aiPredictedGrains = currentScreen.aiPredictedGrains,
                        confirmedGrains = currentScreen.confirmedGrains,
                        weight = currentScreen.weight,
                        onEndSession = { screen = Screen.Welcome },
                        onNextSample = { technicianName, nextSampleId ->
                            lastTechnicianName = technicianName
                            screen = Screen.Capture(technicianName, nextSampleId)
                        }
                    )
                }
            }
        }
    }
}
