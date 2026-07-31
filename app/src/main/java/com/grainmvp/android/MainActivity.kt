package com.grainmvp.android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.grainmvp.android.network.NetworkTestScreen

/**
 * Entry point. Screens are swapped in per phase branch:
 *  - Phase 1: live camera preview (done)
 *  - Phase 3: correction screen (this commit)
 *  - Phase 4: submit flow (not yet wired -- see onSubmit below)
 *
 * Screen state is a simple sealed class rather than a real navigation
 * library, since there are still only a handful of screens. Revisit
 * this once Phase 4 adds more states (loading, success, error).
 */
private sealed class Screen {
    data object Capture : Screen()
    data class Correction(val image: Bitmap) : Screen()
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
    var screen by remember { mutableStateOf<Screen>(Screen.Capture) }

    // TEMPORARY debug toggle to reach NetworkTestScreen (throwaway,
    // see network/NetworkTestScreen.kt). Only available from the
    // Capture screen. Remove this toggle once real navigation exists
    // and Phase 2 is verified -- this is not part of the real app flow.
    var showNetworkTest by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val currentScreen = screen) {
                is Screen.Capture -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showNetworkTest) {
                            NetworkTestScreen()
                        } else {
                            CameraPreviewScreen(
                                onImageConfirmed = { bitmap ->
                                    screen = Screen.Correction(bitmap)
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
                        onRetakePhoto = { screen = Screen.Capture },
                        onSubmit = { _, _ ->
                            // Phase 4 wires this to the real submitReplicate()
                            // call. For now, just return to Capture so the
                            // Correction screen's own flow can be verified
                            // end to end.
                            screen = Screen.Capture
                        }
                    )
                }
            }
        }
    }
}