# Gallery Image Scan — Design

Date: 2026-09-10

## Problem

Today the only way to get a rice sample into the grading pipeline is to
photograph it live with the camera (`CameraPreviewScreen`). The technician
should also be able to pick an already-saved photo from their device and
run it through the same pipeline.

## Design

### Entry point & flow

Add a "Choose from Gallery" button next to the existing "Capture Photo"
button on the Capture screen (`CaptureScreen` composable in
`camera/CameraPreviewScreen.kt`).

Whichever source produces the bitmap — camera capture or gallery pick —
feeds the same `capturedImage` state already owned by
`CameraPreviewScreen`. That state already drives the existing Review
screen (Retake / Classify) and, from there, `onImageConfirmed` into
Correction and Submit. No changes to `MainActivity.kt`,
`CorrectionScreen`, or `SubmitScreen` are needed — they're agnostic to
where the bitmap came from.

### Picking & processing the image

- Use `ActivityResultContracts.PickVisualMedia` (the Android system
  Photo Picker). It's already available via the `activity-compose`
  dependency already in the project — no new library.
- No new runtime permission: the Photo Picker grants scoped, one-time
  access to just the selected file, so `READ_MEDIA_IMAGES` is not
  needed.
- On a picked URI:
  1. Decode it to a `Bitmap` (`ContentResolver.openInputStream` +
     `BitmapFactory.decodeStream`, consistent with how the camera path
     decodes bytes via `BitmapFactory`).
  2. Center-crop to a square: side = `min(width, height)`, centered on
     the longer axis — the same "guide overlay" framing the camera path
     applies, just without a live guide to look at since this is a
     photo the technician already took.
  3. Downscale to exactly `1024x1024`, the single fixed coordinate
     space every `GrainBox` in this app (and the backend/dashboard)
     assumes.
- The center-crop math is a new pure function, `computeCenterSquareCrop
  (width: Int, height: Int): CropRegion`, added to `camera/CropMath.kt`
  next to the existing `computeCropRegion`, reusing the same
  `CropRegion` data class. It takes no Android framework types, so it's
  unit-testable the same way `CropMathTest` already covers
  `computeCropRegion` (portrait, landscape, and square-input cases).
- If the technician backs out of the picker without choosing anything,
  the callback receives no URI and nothing changes — they stay on the
  Capture screen.

### Out of scope

- No manual crop/reposition UI. Auto center-crop only, matching the
  camera path's lack of manual adjustment.
- No multi-image / batch picking. One image in, one scan, same as
  camera capture.

## Testing

- Unit tests for `computeCenterSquareCrop` covering: portrait input,
  landscape input, already-square input, and bounds-safety — mirroring
  the existing `CropMathTest` pattern.
- Manual verification on a real device / emulator: pick a photo from
  the gallery, confirm it reaches the Review screen correctly cropped
  and downscaled, then flows through Correction and Submit exactly like
  a camera-captured image.
