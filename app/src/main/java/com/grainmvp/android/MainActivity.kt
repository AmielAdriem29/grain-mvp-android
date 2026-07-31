package com.grainmvp.android

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
import com.grainmvp.android.network.NetworkTestScreen

/**
 * Entry point. Screens are swapped in per phase branch:
 *  - Phase 1: live camera preview (this commit)
 *  - Phase 3: correction screen
 *  - Phase 4: submit flow
 * Actual navigation between screens (capture -> correction -> submit)
 * is introduced once there's more than one screen to navigate between.
 */
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
    // TEMPORARY debug toggle to reach NetworkTestScreen (throwaway,
    // see network/NetworkTestScreen.kt). Remove this toggle once real
    // navigation exists and Phase 2 is verified -- this is not part of
    // the real app flow.
    var showNetworkTest by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (showNetworkTest) {
                    NetworkTestScreen()
                } else {
                    CameraPreviewScreen()
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
    }
}