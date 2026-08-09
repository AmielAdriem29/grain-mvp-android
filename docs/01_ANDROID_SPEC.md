# Android App — Complete Build Specification

This file contains everything needed to build the Android app in isolation. You do not need to read any other document. Two values below are placeholders (BACKEND_BASE_URL and API_KEY) — get these from whoever is coordinating the project before you start Phase 2, but you can build Phase 1 (camera/crop) with zero network code first.

---

## Connection Details (fill in these two values, then stop guessing)

```
BACKEND_BASE_URL = "http://<TO BE PROVIDED>:8000"
API_KEY = "<TO BE PROVIDED>"
```

Put both in one Kotlin object, e.g.:

```kotlin
object BackendConfig {
    const val BASE_URL = "http://<TO BE PROVIDED>:8000"
    const val API_KEY = "<TO BE PROVIDED>"
}
```

Every network call in this app reads from this object. Never write the URL or key anywhere else.

---

## The Two Endpoints You Call

You call exactly two endpoints, in this order, once per rice sample. Nothing else. You do not call the database directly. You do not need to know anything about how the backend or database work internally.

### Endpoint 1 — Get AI Predictions

```
POST {BASE_URL}/api/predict
Content-Type: multipart/form-data
Header: X-API-Key: {API_KEY}

Form fields:
  image           -> binary JPEG file (your cropped, 1024x1024 downscaled photo)
  technicianName  -> string
  sampleId        -> string
```

**Response you receive (JSON):**
```json
{
  "imageId": "some-string-id",
  "grains": [
    { "x": 112, "y": 340, "width": 24, "height": 24, "confidence": 0.81, "action": null },
    { "x": 560, "y": 210, "width": 24, "height": 24, "confidence": 0.63, "action": null }
  ]
}
```

- `grains` may be an empty list `[]`. This is valid. Show the image with no boxes if so.
- Every x/y/width/height value is in the same coordinate space as your 1024x1024 image. No conversion needed. A box at x=112 means 112 pixels from the left edge of the exact 1024x1024 image you sent.
- Hold onto `imageId`, the full `grains` list, your `technicianName`, and `sampleId` in memory. You need all of them for the second call.

### Endpoint 2 — Submit Final Replicate

Call this once the technician has corrected the boxes and entered a weight.

```
POST {BASE_URL}/api/replicate
Content-Type: multipart/form-data
Header: X-API-Key: {API_KEY}

Form fields:
  image               -> binary JPEG file (the SAME 1024x1024 image sent in Endpoint 1)
  technicianName      -> string (same value as before)
  sampleId            -> string (same value as before)
  aiPredictedGrains   -> string containing JSON (the exact "grains" list you got back from Endpoint 1, unmodified, re-serialized to a JSON string)
  confirmedGrains     -> string containing JSON (your corrected list, after the technician tapped boxes)
  weight              -> string or number (grams, e.g. "12.45")
```

Example of what the `confirmedGrains` field's JSON string content looks like:
```json
[
  { "x": 112, "y": 340, "width": 24, "height": 24, "confidence": 0.81, "action": "kept" },
  { "x": 890, "y": 430, "width": 24, "height": 24, "confidence": null, "action": "added" }
]
```

**Response you receive (JSON):**
```json
{
  "id": "the-new-database-row-id",
  "percentage": 41.5,
  "grade": "G3"
}
```

You can show `percentage` and `grade` to the technician as a confirmation, or just show a generic "Submitted successfully" message. Either is fine.

**Failure handling:** if this request fails or times out, show a clear failure message. Let the technician tap retry, which simply calls this same endpoint again with the same data. Do not build any special duplicate-prevention logic. This is expected and accepted.

---

## GrainBox Shape (memorize this, it's used everywhere)

```kotlin
data class GrainBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float? = null,
    val action: String? = null   // "kept", "removed", "added", or null
)
```

Field names must be spelled exactly like this in your JSON serialization. `x`, `y`, `width`, `height`, `confidence`, `action`. Lowercase, no underscores.

---

## Build Order

### Phase 1 — Camera, Crop, Downscale (no network needed, build and test this fully offline first)

1. Set up Jetpack Compose project. Add CameraX dependencies (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose`).
2. Show a live camera preview using `PreviewView`. Set `scaleType = FILL_CENTER` explicitly. Do not change this setting — the crop math below assumes it.
3. Draw a square guide overlay on top of the preview. Square side length = `min(screenWidth, screenHeight)`. Center it on the longer axis (like a QR scanner).
   - If portrait (screen taller than wide): `guideLeft = 0`, `guideTop = (screenHeight - guideSize) / 2`
   - If landscape: `guideLeft = (screenWidth - guideSize) / 2`, `guideTop = 0`
4. On photo capture, crop the actual sensor image to match exactly what the guide showed. Use this formula:

```
Given:
  sensorW, sensorH = actual captured photo resolution
  previewW, previewH = size of the on-screen camera preview view

sensorAspect = sensorW / sensorH
previewAspect = previewW / previewH

if sensorAspect > previewAspect:
    visibleSensorH = sensorH
    visibleSensorW = sensorH * previewAspect
    sensorCropX = (sensorW - visibleSensorW) / 2
    sensorCropY = 0
else:
    visibleSensorW = sensorW
    visibleSensorH = sensorW / previewAspect
    sensorCropX = 0
    sensorCropY = (sensorH - visibleSensorH) / 2

scaleX = visibleSensorW / previewW
scaleY = visibleSensorH / previewH

finalCropX = sensorCropX + (guideLeft * scaleX)
finalCropY = sensorCropY + (guideTop * scaleY)
finalCropSize = guideSize * scaleX
```

   Crop the captured bitmap using `finalCropX`, `finalCropY`, `finalCropSize` as a square region.

5. Downscale the cropped square using:
```kotlin
Bitmap.createScaledBitmap(croppedBitmap, 1024, 1024, true)
```
6. Show this final 1024x1024 image back to the technician with Retake / Continue buttons. **Checkpoint: you can capture, crop, and downscale correctly, entirely offline, before writing any network code.**

### Phase 2 — Networking

1. Add Retrofit (or Ktor) + `kotlinx.serialization` or Moshi.
2. Build an OkHttp interceptor that adds the `X-API-Key` header to every outgoing request automatically.
3. Build the two endpoint calls exactly as specified above, as Kotlin suspend functions.
4. Set a 30-second timeout on the HTTP client.
5. **Checkpoint: you can call both endpoints from a throwaway test screen and get back parsed responses, once BACKEND_BASE_URL is real and the backend is running.**

### Phase 3 — Correction Screen

1. Display the 1024x1024 image at full screen width. Since it's square, height = width. No letterboxing needed on a typical portrait phone.
2. Display scale factor: `displayScale = screenWidthPx / 1024`.
3. Draw each GrainBox as a rectangle at `(x * displayScale, y * displayScale)` with size `(width * displayScale, height * displayScale)`. Color: blue if `action == null`, grey if `action == "removed"`, green if `action == "added"`.
4. On tap, convert the tap position back to image coordinates: `imageX = tappedX / displayScale`, `imageY = tappedY / displayScale`.
5. Check if `(imageX, imageY)` falls inside any existing box. If yes, toggle its action (null/kept ↔ removed). If no, create a new box at that point: `x = imageX, y = imageY, width = 24, height = 24, action = "added"`.
6. Hold the box list in `mutableStateListOf<GrainBox>()` so the UI redraws automatically.
7. Add a numeric weight input field.
8. Add a "retake photo" button that returns to Phase 1's capture screen.

### Phase 4 — Submit

1. Wire the "Submit" button to call Endpoint 2, packaging everything as specified above.
2. Disable the submit button and show a loading spinner while the request is in flight. Do not allow a second tap during this time.
3. On success, show confirmation and return to the capture screen for the next sample.
4. On failure, show a clear error and allow manual retry (same request, no special handling).

---

## Things You Do NOT Need To Do

- You do not talk to the database directly, ever.
- You do not implement zoom. It was deliberately cut.
- You do not implement drag-to-resize on boxes. Fixed 24x24 size only.
- You do not implement duplicate-submission prevention. This is accepted as a known limitation.
- You do not scale coordinates between "model space" and "original photo space." There is only one space: the 1024x1024 image you send. The backend does not transform it.
