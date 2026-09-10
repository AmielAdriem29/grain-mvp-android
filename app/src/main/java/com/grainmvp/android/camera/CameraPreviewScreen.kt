package com.grainmvp.android.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Fixed 1024x1024 — the single coordinate space every GrainBox in this
 * whole system assumes. Do not change this without updating the
 * backend/dashboard, which both assume it too.
 */
private const val OUTPUT_SIZE = 1024

/**
 * Phase 1 (full): live camera preview + square guide + capture + crop +
 * downscale to 1024x1024, then a Review screen with Retake/Continue.
 *
 * State machine is intentionally simple: capturedImage == null means
 * "show the capture screen", non-null means "show the review screen".
 * No navigation library needed yet since there are only two states.
 */
@Composable
fun CameraPreviewScreen(onImageConfirmed: (Bitmap) -> Unit) {
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

    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }

    // Gallery picking needs no runtime permission, so its launcher lives
    // here rather than inside CaptureScreen -- that lets both the
    // permission-request screen and the capture screen offer it, even
    // when the technician hasn't granted camera access yet.
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = processPickedImage(context, uri)
            if (bitmap != null) {
                capturedImage = bitmap
            }
        }
    }
    val onPickFromGallery = {
        galleryLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    val currentImage = capturedImage
    if (currentImage != null) {
        ReviewScreen(
            image = currentImage,
            onRetake = { capturedImage = null },
            onContinue = { onImageConfirmed(currentImage) }
        )
        return
    }

    if (!hasCameraPermission) {
        PermissionRequestScreen(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onPickFromGallery = onPickFromGallery
        )
        return
    }

    CaptureScreen(
        onCaptured = { bitmap -> capturedImage = bitmap },
        onPickFromGallery = onPickFromGallery
    )
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit, onPickFromGallery: () -> Unit) {
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
        Text(
            "Or scan a photo you've already saved:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp)
        )
        Button(onClick = onPickFromGallery, modifier = Modifier.padding(top = 8.dp)) {
            Text("Choose from Gallery")
        }
    }
}

@Composable
private fun CaptureScreen(onCaptured: (Bitmap) -> Unit, onPickFromGallery: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val previewWidthPx = with(density) { maxWidth.toPx() }
                val previewHeightPx = with(density) { maxHeight.toPx() }
                val guideSize = min(previewWidthPx, previewHeightPx)
                val isPortrait = previewHeightPx >= previewWidthPx
                val guideLeft = if (isPortrait) 0f else (previewWidthPx - guideSize) / 2f
                val guideTop = if (isPortrait) (previewHeightPx - guideSize) / 2f else 0f

                CameraPreview(
                    onImageCaptureReady = { imageCapture = it }
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fullArea = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                    }
                    val guideArea = Path().apply {
                        addRect(
                            Rect(guideLeft, guideTop, guideLeft + guideSize, guideTop + guideSize)
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

                // Bottom-anchored capture + gallery buttons, matching the wireframe.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onPickFromGallery) {
                            Text("Choose from Gallery")
                        }

                        Button(onClick = {
                            val capture = imageCapture ?: return@Button
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val finalBitmap = processCapturedImage(
                                            image = image,
                                            previewWidthPx = previewWidthPx,
                                            previewHeightPx = previewHeightPx,
                                            guideLeft = guideLeft,
                                            guideTop = guideTop,
                                            guideSize = guideSize
                                        )
                                        image.close()
                                        onCaptured(finalBitmap)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        // Phase 5 polish adds a user-visible error
                                        // message here. For now, the technician
                                        // just sees the shutter didn't advance
                                        // to Review and can tap again.
                                    }
                                }
                            )
                        }) {
                            Text("Capture Photo")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Converts a raw camera capture into the final 1024x1024 image:
 * decode -> rotate to match sensor orientation -> crop to the guide's
 * region (via computeCropRegion) -> downscale to exactly 1024x1024.
 */
private fun processCapturedImage(
    image: ImageProxy,
    previewWidthPx: Float,
    previewHeightPx: Float,
    guideLeft: Float,
    guideTop: Float,
    guideSize: Float
): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val rotationDegrees = image.imageInfo.rotationDegrees
    val rotatedBitmap = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    } else {
        rawBitmap
    }

    val crop = computeCropRegion(
        sensorW = rotatedBitmap.width,
        sensorH = rotatedBitmap.height,
        previewW = previewWidthPx.toInt(),
        previewH = previewHeightPx.toInt(),
        guideLeft = guideLeft,
        guideTop = guideTop,
        guideSize = guideSize
    )

    val croppedBitmap = Bitmap.createBitmap(
        rotatedBitmap, crop.x, crop.y, crop.size, crop.size
    )

    return Bitmap.createScaledBitmap(croppedBitmap, OUTPUT_SIZE, OUTPUT_SIZE, true)
}

/**
 * Converts a gallery-picked image into the final 1024x1024 image:
 * bounds-only decode -> compute inSampleSize -> downsampled decode ->
 * rotate to match EXIF orientation -> center-crop to a square (no guide
 * overlay exists for a picked photo, unlike the camera path) -> downscale
 * to 1024x1024.
 *
 * Returns null if the URI can't be opened or decoded, if decoding throws
 * (a missing/revoked URI grant, corrupt image or EXIF data, or an
 * OutOfMemoryError on an extreme-aspect source the sample-size heuristic
 * below can't shrink), or if the source is too small to fill the output
 * size without upscaling past what the grading model should trust.
 */
private fun processPickedImage(context: Context, uri: Uri): Bitmap? {
    return try {
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, OUTPUT_SIZE)
        }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        val rawBitmap = decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null

        val exifStream = context.contentResolver.openInputStream(uri) ?: return null
        val orientation = exifStream.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            // ponytail: rotation only, not flip/mirror — add if real photos need it
            else -> 0
        }
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        val crop = computeCenterSquareCrop(width = rotatedBitmap.width, height = rotatedBitmap.height)
        // A source smaller than the output would get silently upscaled into a
        // well-formed-looking 1024x1024 image carrying far less real detail --
        // reject it instead of feeding the grading model false confidence.
        // Phase 5 polish adds a user-visible message here too (matching the
        // camera-capture error path above) -- for now the technician just
        // sees the picker close with no result and can try a different photo.
        if (crop.size < OUTPUT_SIZE) return null

        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, crop.x, crop.y, crop.size, crop.size)
        Bitmap.createScaledBitmap(croppedBitmap, OUTPUT_SIZE, OUTPUT_SIZE, true)
    } catch (e: Exception) {
        android.util.Log.w("CameraPreviewScreen", "Gallery image processing failed", e)
        null
    } catch (e: OutOfMemoryError) {
        android.util.Log.w("CameraPreviewScreen", "Gallery image processing ran out of memory", e)
        null
    }
}

@Composable
private fun ReviewScreen(image: Bitmap, onRetake: () -> Unit, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        com.grainmvp.android.ui.components.AppHeader("Review Photo")

        Box(modifier = Modifier.weight(1f)) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Captured rice sample",
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onRetake) {
                    Text("Retry Photo")
                }
                Button(onClick = onContinue) {
                    Text("Classify")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onImageCaptureReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // The Phase 1 crop math assumes FILL_CENTER — do not
                // change this without redoing that math.
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
                val imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                onImageCaptureReady(imageCapture)
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
