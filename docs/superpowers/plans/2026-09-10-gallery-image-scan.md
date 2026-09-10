# Gallery Image Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the technician pick an existing photo from their device gallery, in addition to live camera capture, and run it through the same grain-detection pipeline.

**Architecture:** Add a "Choose from Gallery" button next to "Capture Photo" on the existing Capture screen. A picked image is decoded, center-cropped to a square, and downscaled to 1024x1024 — then fed into the exact same `capturedImage` state that already drives the Review → Correction → Submit flow, so nothing downstream changes.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.activity` Photo Picker (`ActivityResultContracts.PickVisualMedia`) — already available via the `activity-compose:1.9.0` dependency already in `app/build.gradle.kts`. No new dependency, no new runtime permission.

## Global Constraints

- Every `GrainBox`/image coordinate space in this app is a fixed `1024x1024` square — the picked image must end up exactly that size, same as the camera path (`docs/01_ANDROID_SPEC.md`).
- No manual crop/reposition UI — auto center-crop only (per design spec).
- No multi-image / batch picking — one image in, one scan (per design spec).
- Follow this file's existing convention of fully-qualified `androidx.activity...` references inline rather than adding new top-level imports (matches the existing permission-launcher code in `CameraPreviewScreen.kt`).
- Package root: `com.grainmvp.android`.

---

### Task 1: Center-square crop math + unit tests

**Files:**
- Modify: `app/src/main/java/com/grainmvp/android/camera/CropMath.kt`
- Test: `app/src/test/java/com/grainmvp/android/camera/CropMathTest.kt`

**Interfaces:**
- Consumes: existing `CropRegion(val x: Int, val y: Int, val size: Int)` data class already in `CropMath.kt`.
- Produces: `fun computeCenterSquareCrop(width: Int, height: Int): CropRegion` — used by Task 2's `processPickedImage`.

- [ ] **Step 1: Write the failing tests**

Add to the bottom of `app/src/test/java/com/grainmvp/android/camera/CropMathTest.kt`, inside the existing `CropMathTest` class (before the final closing `}`):

```kotlin
    @Test
    fun `portrait image crops top and bottom to a centered square`() {
        // width=1000, height=2000 -> size=1000, x=0, y=(2000-1000)/2=500
        val result = computeCenterSquareCrop(width = 1000, height = 2000)

        assertEquals(0, result.x)
        assertEquals(500, result.y)
        assertEquals(1000, result.size)
    }

    @Test
    fun `landscape image crops left and right to a centered square`() {
        // width=2000, height=1000 -> size=1000, x=(2000-1000)/2=500, y=0
        val result = computeCenterSquareCrop(width = 2000, height = 1000)

        assertEquals(500, result.x)
        assertEquals(0, result.y)
        assertEquals(1000, result.size)
    }

    @Test
    fun `already-square image needs no offset`() {
        val result = computeCenterSquareCrop(width = 800, height = 800)

        assertEquals(0, result.x)
        assertEquals(0, result.y)
        assertEquals(800, result.size)
    }

    @Test
    fun `odd difference between width and height still stays in bounds`() {
        // width=999, height=1000 -> size=999, x=0, y=(1000-999)/2=0 (integer division)
        val result = computeCenterSquareCrop(width = 999, height = 1000)

        assert(result.x >= 0)
        assert(result.y >= 0)
        assert(result.x + result.size <= 999)
        assert(result.y + result.size <= 1000)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.grainmvp.android.camera.CropMathTest"`
Expected: FAIL — `computeCenterSquareCrop` is not defined (compile error).

- [ ] **Step 3: Implement `computeCenterSquareCrop`**

Edit `app/src/main/java/com/grainmvp/android/camera/CropMath.kt`. Add the `kotlin.math.min` import at the top (after the `package` line) and the new function at the end of the file:

```kotlin
package com.grainmvp.android.camera

import kotlin.math.min

/**
 * ... existing CropRegion doc ...
 */
data class CropRegion(val x: Int, val y: Int, val size: Int)
```

(Only the `import kotlin.math.min` line is new above — leave the rest of the file's existing content, including `computeCropRegion`, untouched.)

Then append at the end of the file:

```kotlin

/**
 * Center-crops an arbitrary width/height image down to the largest
 * centered square that fits inside it. Used for gallery-picked images,
 * which — unlike a camera capture — have no guide overlay to match, so
 * a plain centered square is the closest equivalent.
 */
fun computeCenterSquareCrop(width: Int, height: Int): CropRegion {
    val size = min(width, height)
    val x = (width - size) / 2
    val y = (height - size) / 2
    return CropRegion(x = x, y = y, size = size)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.grainmvp.android.camera.CropMathTest"`
Expected: PASS — all 8 tests (4 existing + 4 new) green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/grainmvp/android/camera/CropMath.kt app/src/test/java/com/grainmvp/android/camera/CropMathTest.kt
git commit -m "feat: add center-square crop math for gallery images"
```

---

### Task 2: Gallery picker button + wiring into the Capture screen

**Files:**
- Modify: `app/src/main/java/com/grainmvp/android/camera/CameraPreviewScreen.kt`

**Interfaces:**
- Consumes: `computeCenterSquareCrop(width: Int, height: Int): CropRegion` from Task 1; existing `CaptureScreen(onCaptured: (Bitmap) -> Unit)` composable and its existing `onCaptured` callback (already wired to `capturedImage` state in `CameraPreviewScreen`, which already drives the existing `ReviewScreen`).
- Produces: nothing new consumed elsewhere — this is the final wiring, reusing the existing `capturedImage` → `ReviewScreen` → `onImageConfirmed` chain unchanged.

- [ ] **Step 1: Add the image-decoding helper function**

In `app/src/main/java/com/grainmvp/android/camera/CameraPreviewScreen.kt`, add this new private function directly below the existing `processCapturedImage` function (after its closing `}` around line 244):

```kotlin
/**
 * Converts a gallery-picked image into the final 1024x1024 image:
 * decode -> center-crop to a square (no guide overlay exists for a
 * picked photo, unlike the camera path) -> downscale to 1024x1024.
 * Returns null if the URI can't be opened or decoded.
 */
private fun processPickedImage(context: android.content.Context, uri: android.net.Uri): Bitmap? {
    val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: return null

    val crop = computeCenterSquareCrop(width = rawBitmap.width, height = rawBitmap.height)
    val croppedBitmap = Bitmap.createBitmap(rawBitmap, crop.x, crop.y, crop.size, crop.size)

    return Bitmap.createScaledBitmap(croppedBitmap, 1024, 1024, true)
}
```

- [ ] **Step 2: Add the gallery launcher inside `CaptureScreen`**

In the same file, find the `CaptureScreen` composable (starts around line 116):

```kotlin
@Composable
private fun CaptureScreen(onCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
```

Immediately after the `imageCapture` line, add the gallery launcher:

```kotlin
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val bitmap = processPickedImage(context, uri)
            if (bitmap != null) {
                onCaptured(bitmap)
            }
        }
    }
```

- [ ] **Step 3: Add the "Choose from Gallery" button next to "Capture Photo"**

Still in `CaptureScreen`, find the bottom-anchored button block (around line 157-194):

```kotlin
                // Bottom-anchored capture button, matching the wireframe.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
```

Replace the inner `Button` with a `Row` containing both buttons:

```kotlin
                // Bottom-anchored capture + gallery buttons, matching the wireframe.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = {
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }) {
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
```

- [ ] **Step 4: Build the app**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` — no compile errors. (This step has no automated test: decoding a real `content://` URI needs an actual device/emulator gallery, and this project has no instrumented test setup, consistent with the rest of `CameraPreviewScreen.kt` which is also only manually verified — see `README.md`'s Phase 1 notes.)

- [ ] **Step 5: Manual verification on device/emulator**

1. Install the debug build on a device or emulator that has at least one photo saved (emulator: use Android Studio's device file explorer or the Extended Controls camera tab to push a sample JPEG into Photos first).
2. Launch the app, go from Home into Capture.
3. Tap "Choose from Gallery", pick a non-square photo.
4. Confirm the Review screen shows a square, centered crop of that photo (top/bottom or left/right trimmed depending on the photo's orientation).
5. Tap "Classify" and confirm it proceeds into the Correction screen exactly as the camera path does.
6. Tap "Choose from Gallery" again and back out of the picker without selecting anything — confirm the app stays on the Capture screen with no crash.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/grainmvp/android/camera/CameraPreviewScreen.kt
git commit -m "feat: add gallery image picker as an alternative to camera capture"
```
