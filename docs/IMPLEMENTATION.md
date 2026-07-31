# Implementation Notes

This is a **living document** — update it every time a phase's code
changes, not just when a phase finishes. `SPEC.md` in this same folder
is the frozen source-of-truth requirement doc from Fateful; this file
is where reality gets recorded: what actually got built, exactly where
it lives, and anywhere the implementation deviates from the spec (with
the reasoning, so it's a decision, not drift).

How to read the tables below: **Requirement** quotes/paraphrases
`SPEC.md`. **Implemented in** is the actual file/class/function.
**Status** is ✅ done, 🚧 in progress, or ⬜ not started. **Notes**
covers anything worth knowing — a deviation, a gotcha, an open question.

---

## Phase 0 — Bare-bones skeleton (not in SPEC.md, added by us)

Not a spec requirement — this was our own addition for a clean git
starting point. Covered in the README's Git Workflow section instead
of here, since it's process, not a spec requirement.

Status: ✅ done (`master` branch).

---

## Phase 1 — Camera, Crop, Downscale

| Requirement | Implemented in | Status | Notes |
|---|---|---|---|
| Jetpack Compose project + CameraX deps | `app/build.gradle.kts` | ✅ | CameraX 1.3.4, not the latest 1.4/1.5 betas, to avoid forcing compileSdk 34+ |
| Live preview via `PreviewView`, `FILL_CENTER` | `CameraPreviewScreen.kt` → `CameraPreview()` | ✅ | Scale type is load-bearing — the crop math assumes it |
| Square guide overlay, portrait/landscape formula | `CameraPreviewScreen.kt` → `CaptureScreen()`, drawn via `Canvas` | ✅ | Matches spec's exact `guideLeft`/`guideTop`/`guideSize` formula |
| Crop math (sensor → guide-matched region) | `CropMath.kt` → `computeCropRegion()` | ✅ | Deliberately framework-free (no Bitmap/Android imports) so it's a fast JVM unit test, not an instrumented one |
| Crop math unit tests | `CropMathTest.kt` | ✅ | 4 cases: portrait, landscape, degenerate square/square, bounds-safety. Expected values hand-calculated from the spec's formula, not derived from the implementation |
| Downscale to 1024×1024 | `CameraPreviewScreen.kt` → `processCapturedImage()` | ✅ | `Bitmap.createScaledBitmap(cropped, 1024, 1024, true)`, exactly per spec |
| Review screen (Retake/Continue) | `CameraPreviewScreen.kt` → `ReviewScreen()` | ✅ | Buttons labeled "Retry Photo" / "Classify" to match the wireframe Fateful shared, not spec's literal "Retake/Continue" wording |
| Phase 1 checkpoint: capture/crop/downscale work offline | — | ✅ | Verified on both the Pixel 10 emulator (API 36.1) and a real physical device |

**Deviations from spec:**
- Spec says minSdk isn't specified; we chose **26** (Android 8.0+),
  more than covering CameraX's actual floor of API 21, chosen to
  comfortably support "at least Android 10" as a target floor (see
  README decisions log).
- `onError` in `ImageCapture.OnImageCapturedCallback` is currently a
  silent no-op. Spec doesn't require error handling here yet; Phase 5
  polish is the intended place for a visible error message.

**Open question for Fateful (unresolved as of Phase 2 start):**
None of the wireframes shared so far show a **weight input field**,
but `weight` is required by `/api/replicate` per `02_BACKEND_SPEC.md`.
Phase 3/4 screens shouldn't be finalized until this is answered.

---

## Phase 2 — Networking

| Requirement | Implemented in | Status | Notes |
|---|---|---|---|
| Retrofit + kotlinx.serialization deps | `app/build.gradle.kts` | ✅ | Chose kotlinx.serialization over Moshi/Gson — matches JSON shapes directly, no reflection |
| `X-API-Key` auth interceptor | `network/ApiKeyInterceptor.kt` | ✅ | Applied once at the OkHttpClient level, not per-call |
| `BackendConfig` (BASE_URL / API_KEY) | `app/build.gradle.kts` (`buildConfigField`) + `local.properties` | ✅ (deviation) | See below |
| `POST /api/predict` as suspend fn | `network/ApiService.kt` → `predict()` | ✅ | Multipart, matches spec's 3 form fields |
| `POST /api/replicate` as suspend fn | `network/ApiService.kt` → `submitReplicate()` | ✅ | Multipart, matches spec's 6 form fields exactly |
| 30-second HTTP timeout | `network/RetrofitClient.kt` | ✅ | connect/read/write all set to 30s |
| GrainBox/PredictResponse/ReplicateResponse models | `network/ApiModels.kt` | ✅ | Field names match spec exactly (lowercase, no underscores) |
| Throwaway test screen calling both endpoints | `network/NetworkTestScreen.kt` | ✅ | Reachable via a temporary "Network Test" debug button in `MainActivity` (top-right corner) — not real navigation, delete both once Phase 2 is verified |
| Phase 2 checkpoint: call both endpoints, get parsed responses | — | ⬜ | Code is ready; blocked on Sitoy's real BASE_URL/API_KEY being reachable and running |

**Deviation from spec:** spec says *"Every network call in this app
reads from this object [`BackendConfig`]. Never write the URL or key
anywhere else."* We implemented this via **`BuildConfig` fields
generated from `local.properties`** instead of a literal Kotlin
`object BackendConfig { const val ... }`. Reasoning: `local.properties`
is already gitignored by Android convention, so real secrets never
touch source control at all — closer to the spirit of "never write the
real values anywhere committed" than a Kotlin object would be, since a
plain object's placeholder values are trivially easy to accidentally
commit with real values filled in. Functionally identical from every
other file's point of view: `BuildConfig.BACKEND_BASE_URL` /
`BuildConfig.BACKEND_API_KEY` are read the same way a `BackendConfig`
object's fields would be.

**Still to do this phase:**
- Actually verifying the checkpoint once Sitoy's server is reachable -
  run `NetworkTestScreen`, confirm a real parsed `PredictResponse` comes
  back, then delete the test screen and its debug toggle in `MainActivity`

---

## Phase 3 — Correction Screen

Not started. Requirements per `SPEC.md`: full-width 1024×1024 image
display, `displayScale` tap-to-image-coordinate conversion, box
add/toggle logic, weight input, retake button.

## Phase 4 — Submit

Not started. Requirements per `SPEC.md`: wire Submit to
`submitReplicate()`, disable-while-in-flight, success/failure handling,
manual retry with no duplicate-prevention (per spec, this is accepted).

---

## Cross-cutting notes (apply to every phase)

- **Single coordinate space.** Every `GrainBox` everywhere in this app
  is in 1024×1024 space. No file in this codebase should ever scale a
  coordinate against anything other than a display/screen size for
  rendering purposes (e.g. Phase 3's `displayScale`). If you find
  yourself converting between "sensor space" and "model space," stop —
  per spec, there's only one space.
- **Field naming.** `x, y, width, height, confidence, action` — always
  exactly these names, lowercase, no underscores, everywhere a
  `GrainBox` is serialized. This is what keeps Android, the backend,
  and the dashboard compatible with each other without translation.