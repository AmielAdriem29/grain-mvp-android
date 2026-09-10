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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.AccentGreen
import com.grainmvp.android.ui.theme.DividerColor
import com.grainmvp.android.ui.theme.Neutral600
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.Neutral800
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.SurfaceBg
import com.grainmvp.android.ui.theme.TextPrimaryGranular
import kotlin.math.min

/**
 * Fixed 1024x1024 — the single coordinate space every GrainBox in this
 * whole system assumes. Do not change this without updating the
 * backend/dashboard, which both assume it too.
 */
private const val OUTPUT_SIZE = 1024

/**
 * The camera-screen "on-dark" accent used only for the capture chrome's
 * guide brackets and small icon glyphs (screen 03 of the redesign) --
 * lighter than the app's normal accent green so it reads against the
 * dark live preview. Not one of the named design-system tokens in
 * ui/theme/Color.kt because it's local to this one dark screen.
 */
private val CaptureAccentGreen = Color(0xFF8CC994)

/**
 * Screens 03 (Capture) and 04 (Review photo) of the GRANULAR field
 * redesign, layered on top of Phase 1's untouched camera/crop/downscale
 * logic -- see CropMath.kt (not touched) for the crop math itself. Only
 * the decorative overlay chrome changed here.
 *
 * State machine is intentionally simple: capturedImage == null means
 * "show the capture screen", non-null means "show the review screen".
 * No navigation library needed yet since there are only two states.
 */
@Composable
fun CameraPreviewScreen(
    technicianName: String,
    sampleId: String,
    onImageConfirmed: (Bitmap) -> Unit
) {
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
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Couldn't use that photo — try one that's at least 1024x1024.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
            sampleId = sampleId,
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
        sampleId = sampleId,
        onCaptured = { bitmap -> capturedImage = bitmap },
        onPickFromGallery = onPickFromGallery
    )
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit, onPickFromGallery: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Camera access is needed to photograph rice samples.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimaryGranular
        )
        PrimaryActionButton(
            text = "Grant camera permission",
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        Text(
            "Or scan a photo you've already saved:",
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral700,
            modifier = Modifier.padding(top = 24.dp)
        )
        SecondaryActionButton(
            text = "Choose from Gallery",
            onClick = onPickFromGallery,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

/**
 * Screen 03 (Capture). All CameraX/crop-math logic below is unchanged
 * from Phase 1 -- only the decorative overlay drawn on top changed:
 * green accent brackets instead of a plain white guide rectangle, a
 * session-context pill + torch toggle at the top, an instruction line,
 * and a three-cell bottom row (grid toggle / shutter / gallery).
 */
@Composable
private fun CaptureScreen(sampleId: String, onCaptured: (Bitmap) -> Unit, onPickFromGallery: () -> Unit) {
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    onBound = { boundCamera, capture ->
                        camera = boundCamera
                        imageCapture = capture
                    }
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
                    drawPath(dimArea, color = Color.Black.copy(alpha = 0.55f))

                    // Registration-frame corner brackets instead of a plain
                    // stroked rectangle, per the redesign.
                    val bracketLen = 26.dp.toPx()
                    val bracketStroke = 2.dp.toPx()
                    val corners = listOf(
                        Offset(guideLeft, guideTop) to Pair(1, 1),
                        Offset(guideLeft + guideSize, guideTop) to Pair(-1, 1),
                        Offset(guideLeft, guideTop + guideSize) to Pair(1, -1),
                        Offset(guideLeft + guideSize, guideTop + guideSize) to Pair(-1, -1)
                    )
                    corners.forEach { (corner, dir) ->
                        val (dx, dy) = dir
                        drawLine(
                            CaptureAccentGreen,
                            corner,
                            Offset(corner.x + bracketLen * dx, corner.y),
                            bracketStroke
                        )
                        drawLine(
                            CaptureAccentGreen,
                            corner,
                            Offset(corner.x, corner.y + bracketLen * dy),
                            bracketStroke
                        )
                    }

                    if (gridOn) {
                        val gridColor = Color.White.copy(alpha = 0.4f)
                        drawLine(
                            gridColor,
                            Offset(guideLeft + guideSize / 2f, guideTop),
                            Offset(guideLeft + guideSize / 2f, guideTop + guideSize),
                            1.dp.toPx()
                        )
                        drawLine(
                            gridColor,
                            Offset(guideLeft, guideTop + guideSize / 2f),
                            Offset(guideLeft + guideSize, guideTop + guideSize / 2f),
                            1.dp.toPx()
                        )
                    }
                }

                // Top bar: session-context pill + torch toggle.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "GRANULAR · $sampleId",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.35f))
                            .clickable(enabled = hasFlash) {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (torchOn) "LAMP ON" else "LAMP",
                            color = if (torchOn) CaptureAccentGreen else Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    "Fill the frame with the tray. Keep the phone flat.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 172.dp)
                        .fillMaxWidth()
                )

                // Bottom row: GRID / shutter / GALLERY, three equal-width cells.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptureChromeCell(label = "GRID") {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.35f))
                                .clickable { gridOn = !gridOn },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val lineColor = if (gridOn) CaptureAccentGreen else Color.White.copy(alpha = 0.65f)
                                drawLine(lineColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                                drawLine(lineColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(2.dp, Color.White)
                            .clickable {
                                val capture = imageCapture ?: return@clickable
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            val finalBitmap = processCapturedImage(
                                                image = imageProxy,
                                                previewWidthPx = previewWidthPx,
                                                previewHeightPx = previewHeightPx,
                                                guideLeft = guideLeft,
                                                guideTop = guideTop,
                                                guideSize = guideSize
                                            )
                                            imageProxy.close()
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(56.dp).background(CaptureAccentGreen))
                    }

                    CaptureChromeCell(label = "GALLERY") {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.25f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f))
                                .clickable { onPickFromGallery() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(26.dp, 22.dp).border(1.5.dp, Color.White))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureChromeCell(label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        icon()
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
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
        // reject it instead of feeding the grading model false confidence. The
        // caller (the gallery launcher's callback) shows a Toast when this
        // returns null, covering both this case and any other decode failure.
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

/**
 * Screen 04 (Review photo). A checklist of static reminders (dash-
 * bulleted, not interactive checkboxes -- they're just reminders,
 * per the redesign) replaces the old bare Retake/Classify pair.
 */
@Composable
private fun ReviewScreen(sampleId: String, image: Bitmap, onRetake: () -> Unit, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(SurfaceBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Review photo",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimaryGranular
            )
            Text("$sampleId · 1024 px", fontSize = 12.sp, color = Neutral600)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(width = 1.dp, color = DividerColor)
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Captured rice sample",
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "BEFORE CLASSIFYING",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = Accent700
            )
            ReminderLine("Grains in focus, no shadow across the tray")
            ReminderLine("Whole tray inside the frame")
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = DividerColor)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(text = "Retake", onClick = onRetake, modifier = Modifier.weight(1f))
            PrimaryActionButton(text = "Detect grains", onClick = onContinue, modifier = Modifier.weight(1.4f))
        }
    }
}

@Composable
private fun ReminderLine(text: String) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("—", color = AccentGreen, fontSize = 14.sp)
        Text(text, fontSize = 14.sp, color = Neutral800)
    }
}

@Composable
private fun CameraPreview(onBound: (Camera, ImageCapture) -> Unit) {
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
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                onBound(camera, imageCapture)
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
