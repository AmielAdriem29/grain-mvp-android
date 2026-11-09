# Grain MVP — Android App

## Project Description

A rice grain grading app for field technicians. The technician photographs
a rice sample, an AI model detects the immature grains and draws a
bounding box over each one, and the technician corrects those boxes and
enters the sample weight. The record is then submitted to the backend,
which grades the sample and stores it for review on a separate dashboard.

This repo covers the **Android client only**. It talks to a separately-built
FastAPI backend over two endpoints. Full contract is in `docs/SPEC.md`.

## Docs

- [`docs/SPEC.md`](docs/SPEC.md) — the original build spec from the Team
  Lead, verbatim, frozen. Never edited to match reality; if a requirement
  is wrong or outdated, that's raised with the Team Lead, not silently
  patched here.
- [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md) — living doc mapping
  every spec requirement to the actual file/class that implements it,
  per-phase status, and any deviations from spec (with reasoning). This is
  where detailed per-phase notes live going forward — **update it every
  time a phase's code changes**, not just when a phase finishes.

## Tech Stack

- Kotlin + Jetpack Compose, Material3
- CameraX 1.3.4 (camera preview, capture)
- Retrofit with OkHttp (networking)
- kotlinx.serialization (JSON)
- Supabase Auth via supabase-kt (technician sign-in)
- Custom Material3 theme derived from the PhilRice/Dept. of Agriculture
  seal (deep green, gold, warm off-white) — see `ui/theme/`
- minSdk 26, targetSdk 34

## Setup and Installation

1. Clone the repository:
   ```
   git clone https://github.com/AmielAdriem29/grain-mvp-android.git
   ```
2. Open the project folder in Android Studio.
3. Set the Gradle JDK to 17 or 21 (Settings > Build, Execution,
   Deployment > Build Tools > Gradle).
4. Create `local.properties` in the project root with the keys listed
   under [Configuration](#configuration). Ask the team for the values.
5. Connect an Android device (Android 8.0 / API 26 or newer) with USB
   debugging enabled, then run the `app` configuration.

Unit tests run without `local.properties`:
```
./gradlew testDebugUnitTest
```

## Configuration

Real values are **never committed**. They live in the gitignored
`local.properties` file at the project root, under these keys:

| Key | Purpose |
|---|---|
| `backend.baseUrl` | Base URL of the backend API |
| `supabase.url` | Supabase project URL |
| `supabase.pubKey` | Supabase publishable key |

At build time (see `app/build.gradle.kts`) these are read into
`BuildConfig.BACKEND_BASE_URL`, `BuildConfig.SUPABASE_URL` and
`BuildConfig.SUPABASE_PUB_KEY`. If a key is missing, the build falls back to
an obvious placeholder, so the project still compiles for anyone who hasn't
created `local.properties` yet. The `backend.baseUrl` fallback is
`http://10.0.2.2:8000`, the Android emulator's alias for the host
machine's `localhost`.

## Deployed Backend URL

https://immaturebackend.onrender.com

## Git Workflow

- **`master`** — stable milestone snapshots. Fast-forwarded/merged from
  `develop` at completed milestones (e.g. "MVP feature-complete").
- **`develop`** — integration branch. All phase branches merge back here.
- **`phase-N-<name>`** — one branch per phase, branched off `develop`,
  merged back into `develop` when its checkpoint passes.

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
| 1 — Camera, crop, downscale to 1024×1024 | `phase-1-camera-crop` | No | ✅ done, verified on real device |
| 2 — Networking (Retrofit, auth interceptor, 2 endpoints) | `phase-2-networking` | Yes (BASE_URL) | ✅ code done — checkpoint (real call verification) blocked on backend being reachable |
| 3 — Correction screen (tap to toggle/add boxes) | `phase-3-correction-screen` | No | ✅ done, verified on-device |
| 4 — Submit flow | `phase-4-submit` | Yes | ✅ code done — same backend blocker as Phase 2 |
| 5 — Polish (tests, theme, Home screen, weight validation) | `phase-5-polish` | No | ✅ done |

All phases merged into `develop` and `master`. **MVP is feature-complete
as of this milestone** — see `docs/IMPLEMENTATION.md` for exactly what's
verified vs. what's still blocked on the backend existing.

## Things To Sort With the Team

Tracked in detail in `docs/IMPLEMENTATION.md`, summarized here:

- **Backend needs to exist and be reachable** for Phase 2/4's real
  checkpoints to be verified (predict/replicate calls currently fail
  with a connection error, which is expected — code is ready and waiting).
- **Weight field placement** — currently on the Correction screen, no
  wireframe shows it; the Team Lead hasn't confirmed a preferred location yet.
- **SampleType: free text vs. dropdown** — no list of valid rice
  varieties has been provided yet, so it stays free text for now.

## Decisions / Notes Log

Historical log from early setup. Detailed per-phase decisions from
Phase 2 onward live in `docs/IMPLEMENTATION.md` instead of being
duplicated here.

### 2026-07-30 — Phase 1 complete (merged phase-1-camera-crop -> develop)
- Full flow works: live preview -> square guide overlay -> capture ->
  crop (matches guide exactly, via a pure/testable `computeCropRegion`
  function) -> downscale to a fixed 1024x1024 -> Review screen with
  Retry Photo / Classify buttons.
- Crop math has unit tests (`CropMathTest.kt`, 4 cases: portrait,
  landscape, degenerate square/square, bounds-safety) verified against
  hand-calculated expected values, not derived from the implementation.
- Confirmed working on both the emulator (Pixel 10, API 36.1) and a
  real physical device — crop looked correct on both.
- Emulator system image (API 36.1 "Baklava" / Android 16) is
  independent from `minSdk 26` in the manifest — the emulator just
  needs to be a recent stable image for daily dev; it doesn't limit
  which real devices the built app can install on.

### 2026-07-30 — Repo bootstrap
- Package name: `com.grainmvp.android`. minSdk 26 (Android 8.0+ —
  more than covers CameraX's actual floor of API 21, and comfortably
  covers "at least Android 10" as the target device floor), targetSdk
  34.
- Single coordinate space end-to-end: everything is 1024×1024. No scaling
  between "model space" and "original photo space" anywhere in this app.
- GrainBox field names are fixed and must match exactly across Android,
  backend, and dashboard: `x, y, width, height, confidence, action`
  (lowercase, no underscores). `action` is `"kept" | "removed" | "added" | null`.