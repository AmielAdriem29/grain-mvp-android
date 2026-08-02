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
- Actually verifying the checkpoint once Sitoy's server is reachable —
  run `NetworkTestScreen`, confirm a real parsed `PredictResponse` comes
  back, then delete the test screen and its debug toggle in `MainActivity`

---

## Phase 3 — Correction Screen

| Requirement | Implemented in | Status | Notes |
|---|---|---|---|
| Full-width 1024×1024 image display | `CorrectionScreen.kt` | ✅ | `displayScale = screenWidthPx / 1024`, per spec |
| Draw GrainBox rectangles, color by action | `CorrectionScreen.kt` → `Canvas` block | ✅ | blue = null/untouched, grey = removed, green = added |
| Tap-to-toggle existing box (null/kept ↔ removed) | `CorrectionScreen.kt` → `pointerInput`/`detectTapGestures` | ✅ | See deviation note below |
| Tap empty space to add a new 24×24 box | `CorrectionScreen.kt` → `pointerInput`/`detectTapGestures` | ✅ | Box placed with tap point as top-left corner, per spec's literal `x = imageX, y = imageY` — not centered on the tap. Worth revisiting with Fateful if it feels visually awkward in practice |
| Box list in observable state | `CorrectionScreen.kt` → `mutableStateListOf<GrainBox>()` | ✅ | Reuses `network.GrainBox` directly rather than a duplicate model |
| Weight input field | `CorrectionScreen.kt` → `OutlinedTextField` | ✅ | Numeric/decimal keyboard. Placed on this screen (not the Name/SampleType screen) — see note below |
| "Retake photo" button | `CorrectionScreen.kt` → `onRetakePhoto` | ✅ | Labeled "New Photo" to match Fateful's wireframe wording |

**Verified on-device:** full loop tested (Capture → Review → Classify →
Correction → Confirm). Tap-to-toggle confirmed reversible both ways
(blue → grey → blue), tap-empty-area-to-add confirmed (green box
appears at tap location). Instructional text was revised from "tap
empty space" to "tap directly on a missed grain" since the original
wording was misleading — you're marking an undetected grain, not
literal blank background.

**Deviation from spec:** re-tapping a box that started as `"added"` and
was then toggled to `"removed"` resets it to `null` rather than back to
`"added"`. The spec's toggle rule only describes `null/kept ↔ removed`
and doesn't cover re-toggling a user-added box specifically. This is a
minor, rare edge case (removing then un-removing your own added box) —
not a correctness issue for the main flow, but worth knowing about.

**Not in spec — decisions made to keep moving, flagged for Fateful:**
- **Weight field placement:** none of the wireframes show a weight
  input anywhere. We placed it on this screen (Correction), reasoning
  that weighing the identified immature grains chronologically happens
  right after correcting which grains count as immature, and right
  before submission. Easy to relocate if Fateful wants it elsewhere —
  it's a single `TextField`.
- **Fake development data:** since Sitoy hasn't started backend work,
  this screen is currently fed by `dev/FakeGrainsForDev.kt` — 5
  hardcoded placeholder boxes — instead of a real `/api/predict`
  response. This is **not a spec requirement**, purely a workaround so
  Android work isn't blocked. Wired in at `MainActivity`'s
  `Screen.Correction` branch. **Must be deleted** and swapped for a
  real `predict()` call once Phase 2 is verified against a running
  backend — tracked here so it doesn't get forgotten.
- **`technicianName`/`sampleId` timing:** resolved via discussion —
  `/api/predict` never persists these fields (prediction-only, no DB
  write per `02_BACKEND_SPEC.md`), so Android can send placeholder/empty
  values at that call and only require the real values later at
  `/api/replicate`'s screen, matching Fateful's wireframe order exactly.
  No screens need reordering.

## Phase 4 — Submit

| Requirement | Implemented in | Status | Notes |
|---|---|---|---|
| Wire Submit to real `POST /api/replicate` | `SubmitScreen.kt` → `submit()` | ✅ | Calls `RetrofitClient.apiService.submitReplicate()` directly — no fake dev stand-in this time, see reasoning below |
| Disable button + show spinner while in flight | `SubmitScreen.kt` → `SubmitState.Loading` | ✅ | `enabled = currentState !is SubmitState.Loading`, blocks double-tap |
| Success: show confirmation, return to capture | `SubmitScreen.kt` → `SuccessScreen()` | ✅ | Shows `percentage`/`grade` per spec's suggestion; "New Sample" returns to `Screen.Capture` |
| Failure: clear error, manual retry (same request) | `SubmitScreen.kt` → `SubmitState.Error` | ✅ | Broad `catch (e: Exception)` surfaces connection errors, timeouts, wrong API key, etc. as visible text. Retry just re-calls `submit()` with the same already-entered field values — no duplicate-prevention, per spec |
| Name + SampleType input screen | `SubmitScreen.kt` → `OutlinedTextField`s | ✅ | Not explicitly in `SPEC.md`'s Phase 4 section, but required by `/api/replicate`'s contract and matches Fateful's wireframe (screen 4, after Correction's Confirm) |

**Deliberate deviation from the Phase 2/3 pattern — no fake data this
time:** Phase 3 needed `FakeGrainsForDev.kt` because without it, there
was no way to reach or test the Correction screen at all. Phase 4 is
different: wiring the **real** `submitReplicate()` call directly still
lets us fully test the loading/disable/error/retry states, since a
connection failure (expected right now, no backend running) exercises
exactly the same code path a real failure would. Only the **success**
confirmation screen stays genuinely unverified until Sitoy's backend
exists — same "code ready, checkpoint blocked" situation as Phase 2.

**Still tied to Phase 3's fake data:** `aiPredictedGrains` sent here is
still `FAKE_GRAINS_FOR_DEV_ONLY` (passed through from `MainActivity`'s
`Screen.Correction` → `Screen.Submit` transition), not a real AI
response. This resolves automatically once Phase 2/3's fake-data
wiring is swapped for the real `predict()` call — no separate fix
needed in `SubmitScreen.kt` itself.

**Phase 4 checkpoint:** loading/disable/error/retry states are
verifiable now, offline. Full success-path verification requires
Sitoy's backend, same blocker as Phase 2.

**Deviation — cleartext HTTP allowed:** `AndroidManifest.xml` sets
`android:usesCleartextTraffic="true"`. Android blocks plain HTTP by
default since API 28; without this, every request to a plain
`http://` backend (like the dev alias `10.0.2.2:8000`) fails at the OS
level with `CLEARTEXT communication ... not permitted`, before it even
leaves the phone. Acceptable for this MVP since Sitoy's backend almost
certainly won't have HTTPS set up — **flag for whoever eventually
deploys this for real**: a production app should use HTTPS and remove
this flag.

**Not in spec — decisions made to keep moving, flagged for Fateful:**
- **SampleType input is free text, not a dropdown.** `sampleId`
  represents a rice variety (per Fateful: "sampleID(type of rice)"),
  which is almost certainly a small, fixed, known set in practice —
  free text risks inconsistent data (typos, casing, trailing spaces
  all creating "different" varieties in the database). Left as a plain
  `OutlinedTextField` for now because **no actual list of valid
  varieties has been provided anywhere** — not in any spec, not in the
  wireframes. The dashboard spec's example (`"RC-Dinorado-004"`) only
  shows a naming *format*, not a real list. Needs an answer from
  Fateful before converting this to a dropdown/spinner.

## Phase 5 — Polish (not in SPEC.md's build order, added by us)

Not a numbered phase in `SPEC.md` — the spec's "Things You Do NOT Need
To Do" section implies polish is optional for the MVP. Tracked here
since real work happened and future contributors should know about it.

| What | Implemented in | Notes |
|---|---|---|
| Unit tests for Correction's tap logic | `CorrectionLogic.kt` + `CorrectionLogicTest.kt` | Extracted from inline `pointerInput` code, same pattern as Phase 1's `CropMath.kt`. 12 tests |
| Weight input validation | `CorrectionLogic.kt` → `isValidWeightValue()` | Gates the Confirm button + shows a visible error message; deliberately does NOT block keystrokes (silently rejecting typed characters reads as a broken keyboard). 8 tests |
| PhilRice-derived visual theme | `ui/theme/Color.kt`, `ui/theme/Theme.kt` | Deep green + gold + warm off-white, applied app-wide via `GrainMvpTheme` |
| Branded header component | `ui/components/AppHeader.kt` | Applied to Review, Correction, Submit screens. Capture screen deliberately left full-bleed/header-free, matching the original wireframe |
| Home/Welcome screen | `home/HomeScreen.kt` | Shown first on launch, before Capture. Camera permission is no longer requested the instant the app opens — only once the user taps "Start Scanning." Not a spec requirement, a UX improvement |

**Still open from earlier phases, unresolved:**
- Weight field placement (Correction vs. a separate screen) — Fateful hasn't answered yet
- SampleType as free text vs. dropdown — no list of valid rice varieties provided yet
- Phase 2/4's real backend verification — still blocked on Sitoy

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