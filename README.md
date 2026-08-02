# Grain MVP — Android App

Rice grain grading MVP. Technician photographs a rice sample, an AI model
proposes bounding boxes over individual grains, the technician corrects
those boxes and enters a weight, and the record is submitted to the
backend for later review on a separate dashboard.

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

## Stack

- Kotlin + Jetpack Compose, Material3
- CameraX 1.3.4 (camera preview, capture)
- Retrofit + OkHttp + kotlinx.serialization (networking)
- Custom Material3 theme derived from the PhilRice/Dept. of Agriculture
  seal (deep green, gold, warm off-white) — see `ui/theme/`
- minSdk 26, targetSdk 34

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
| 2 — Networking (Retrofit, auth interceptor, 2 endpoints) | `phase-2-networking` | Yes (BASE_URL + API_KEY) | ✅ code done — checkpoint (real call verification) blocked on backend being reachable |
| 3 — Correction screen (tap to toggle/add boxes) | `phase-3-correction-screen` | No | ✅ done, verified on-device |
| 4 — Submit flow | `phase-4-submit` | Yes | ✅ code done — same backend blocker as Phase 2 |
| 5 — Polish (tests, theme, Home screen, weight validation) | `phase-5-polish` | No | ✅ done |

All phases merged into `develop` and `master`. **MVP is feature-complete
as of this milestone** — see `docs/IMPLEMENTATION.md` for exactly what's
verified vs. what's still blocked on the backend existing.

## Connection Details

Real values are **never committed**. They live in a gitignored
`local.properties` file at the project root:

```properties
backend.baseUrl=http://10.0.2.2:8000
backend.apiKey=<the real key, once the backend owner provides one>
```

These get read into `BuildConfig.BACKEND_BASE_URL` / `BuildConfig.BACKEND_API_KEY`
at build time (see `app/build.gradle.kts`). If `local.properties` doesn't
set these, the build falls back to the emulator's host-loopback alias
(`10.0.2.2`) and an obviously-fake placeholder key, so the project still
compiles for anyone who hasn't set up their own `local.properties` yet.

`10.0.2.2` is the Android emulator's special alias for your host
machine's `localhost` — if the backend is running locally on someone
else's machine (not yours), you'll need their actual reachable address
instead.

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