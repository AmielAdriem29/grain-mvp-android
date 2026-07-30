package com.grainmvp.android.camera

import android.Manifest
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Phase 1, slice 2: square guide overlay on top of the live preview.
 *
 * Purely visual in this commit — no capture button, no crop math wired
 * up yet (that's slice 3). The guide's position/size here is exactly
 * what the crop math in slice 3 will read from, per the spec:
 *   guideSize = min(screenWidth, screenHeight)
 *   portrait:  guideLeft = 0, guideTop = (screenHeight - guideSize) / 2
 *   landscape: guideLeft = (screenWidth - guideSize) / 2, guideTop = 0
 */
@Composable
fun CameraPreviewScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    if (hasCameraPermission) {
        CameraPreviewWithGuide()
    } else {
        PermissionRequestScreen(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        })
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Camera access is needed to photograph rice samples.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant camera permission")
        }
    }
}

@Composable
private fun CameraPreviewWithGuide() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CameraPreview()

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val guideSize = min(screenWidthPx, screenHeightPx)
        val isPortrait = screenHeightPx >= screenWidthPx
        val guideLeft = if (isPortrait) 0f else (screenWidthPx - guideSize) / 2f
        val guideTop = if (isPortrait) (screenHeightPx - guideSize) / 2f else 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dim everything outside the guide square, leaving the square
            // itself clear, then draw a border around it.
            val fullArea = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            val guideArea = Path().apply {
                addRect(
                    Rect(
                        guideLeft,
                        guideTop,
                        guideLeft + guideSize,
                        guideTop + guideSize
                    )
                )
            }
            val dimArea = Path().apply {
                op(fullArea, guideArea, PathOperation.Difference)
            }
            drawPath(dimArea, color = Color.Black.copy(alpha = 0.5f))
            drawRect(
                color = Color.White,
                topLeft = Offset(guideLeft, guideTop),
                size = Size(guideSize, guideSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // The Phase 1 crop math in the spec assumes FILL_CENTER —
                // do not change this without redoing that math.
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}