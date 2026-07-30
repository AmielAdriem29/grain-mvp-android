package com.grainmvp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grainmvp.android.camera.CameraPreviewScreen

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
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CameraPreviewScreen()
        }
    }
}