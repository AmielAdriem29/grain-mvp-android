# Grain MVP — Android App

Rice grain grading MVP. Technician photographs a rice sample, an AI model
proposes bounding boxes over individual grains, the technician corrects
those boxes and enters a weight, and the record is submitted to the
backend for later review on a separate dashboard.

This repo covers the **Android client only**. It talks to a FastAPI
backend (built separately) over two endpoints. Full contract is in
`docs/backend-contract.md` (added once Phase 2 networking starts).

## Stack

- Kotlin + Jetpack Compose
- CameraX (added in Phase 1 branch)
- Retrofit / kotlinx.serialization (added in Phase 2 branch)
- minSdk 26, targetSdk 34

## Git Workflow

- **`master`** — bare-bones, always-buildable skeleton. Only fast-forwarded
  from `develop` at stable checkpoints. Never committed to directly once
  `develop` exists.
- **`develop`** — integration branch. All phase branches merge back here.
- **`phase-N-<name>`** — one branch per phase from the build order below,
  branched off `develop`, merged back into `develop` when its checkpoint
  passes.

Commit convention (Conventional Commits):
- `feat:` new functionality
- `fix:` bug fix
- `chore:` tooling/config/setup, no behavior change
- `docs:` documentation only
- `refactor:` code change with no behavior change
- `test:` tests only

One logical change per commit.

## Phase Plan (per build spec)

| Phase | Branch | Needs backend? | Status |
|---|---|---|---|
| 0 — bare-bones skeleton | `master` | No | ✅ done |
| 1 — Camera, crop, downscale to 1024×1024 | `phase-1-camera-crop` | No | ✅ done, merged into `develop` |
| 2 — Networking (Retrofit, auth interceptor, 2 endpoints) | `phase-2-networking` | Yes (BASE_URL + API_KEY) | not started |
| 3 — Correction screen (tap to toggle/add boxes) | `phase-3-correction-screen` | No | not started |
| 4 — Submit flow | `phase-4-submit` | Yes | not started |

## Connection Details (placeholders — fill in once backend hands these off)

```kotlin
object BackendConfig {
    const val BASE_URL = "http://<TO BE PROVIDED>:8000"
    const val API_KEY = "<TO BE PROVIDED>"
}
```

These are **never** committed with real values. Real values go in a
local, gitignored config (exact mechanism decided in Phase 2 — likely
`local.properties` + `BuildConfig` fields, or a gitignored Kotlin object).

## Decisions / Notes Log

Running log of choices made and why, mainly so the backend developer has
context without re-reading the whole spec. Newest entries on top.

### 2026-07-30 — Phase 1 complete (merged phase-1-camera-crop -> develop)
- Full flow works: live preview -> square guide overlay -> capture ->
  crop (matches guide exactly, via a pure/testable `computeCropRegion`
  function) -> downscale to a fixed 1024x1024 -> Review screen with
  Retry Photo / Classify buttons (Classify is currently a no-op
  placeholder; Phase 2 wires it to `POST /api/predict`).
- Crop math has unit tests (`CropMathTest.kt`, 4 cases: portrait,
  landscape, degenerate square/square, bounds-safety) verified against
  hand-calculated expected values, not derived from the implementation.
- Confirmed working on both the emulator (Pixel 10, API 36.1) and a
  real physical device — crop looked correct on both.
- Emulator system image (API 36.1 "Baklava" / Android 16) is
  independent from `minSdk 26` in the manifest — the emulator just
  needs to be a recent stable image for daily dev; it doesn't limit
  which real devices the built app can install on.
- Still open / not yet decided: the wireframes shared so far (Figma,
  screenshotted) don't show a **weight input field** anywhere, but
  `weight` is a required field for `POST /api/replicate` per the
  backend spec (used to calculate `percentage`). Needs an answer from
  Fateful before Phase 3/4 screens are finalized: does weight go on
  the Name/SampleType form screen, or somewhere in Review and Correct?

### 2026-07-30 — Repo bootstrap
- Package name: `com.grainmvp.android`. minSdk 26 (Android 8.0+ —
  more than covers CameraX's actual floor of API 21, and comfortably
  covers "at least Android 10" as the target device floor), targetSdk
  34.
- Bare-bones skeleton intentionally excludes CAMERA permission and all
  networking/CameraX dependencies — those land in their phase branches so
  `master` never carries dependencies it doesn't use yet.
- Single coordinate space end-to-end: everything is 1024×1024. No scaling
  between "model space" and "original photo space" anywhere in this app.
- GrainBox field names are fixed and must match exactly across Android,
  backend, and dashboard: `x, y, width, height, confidence, action`
  (lowercase, no underscores). `action` is `"kept" | "removed" | "added" | null`.