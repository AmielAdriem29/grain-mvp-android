# Implementation Notes

This is a **living document** — update it every time a phase's code
changes, not just when a phase finishes. `SPEC.md` in this same folder
is the frozen source-of-truth requirement doc from the Team Lead; this file
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
| Review screen (Retake/Continue) | `CameraPreviewScreen.kt` → `ReviewScreen()` | ✅ | Buttons labeled "Retry Photo" / "Classify" to match the wireframe the Team Lead shared, not spec's literal "Retake/Continue" wording |
| Phase 1 checkpoint: capture/crop/downscale work offline | — | ✅ | Verified on both the Pixel 10 emulator (API 36.1) and a real physical device |

**Deviations from spec:**
- Spec says minSdk isn't specified; we chose **26** (Android 8.0+),
  more than covering CameraX's actual floor of API 21, chosen to
  comfortably support "at least Android 10" as a target floor (see
  README decisions log).
- `onError` in `ImageCapture.OnImageCapturedCallback` is currently a
  silent no-op. Spec doesn't require error handling here yet; Phase 5
  polish is the intended place for a visible error message.

**Open question for the Team Lead (unresolved as of Phase 2 start):**
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
| Tap empty space to add a new 24×24 box | `CorrectionScreen.kt` → `pointerInput`/`detectTapGestures` | ✅ | Box placed with tap point as top-left corner, per spec's literal `x = imageX, y = imageY` — not centered on the tap. Worth revisiting with the Team Lead if it feels visually awkward in practice |
| Box list in observable state | `CorrectionScreen.kt` → `mutableStateListOf<GrainBox>()` | ✅ | Reuses `network.GrainBox` directly rather than a duplicate model |
| Weight input field | `CorrectionScreen.kt` → `OutlinedTextField` | ✅ | Numeric/decimal keyboard. Placed on this screen (not the Name/SampleType screen) — see note below |
| "Retake photo" button | `CorrectionScreen.kt` → `onRetakePhoto` | ✅ | Labeled "New Photo" to match the Team Lead's wireframe wording |

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

**Not in spec — decisions made to keep moving, flagged for the Team Lead:**
- **Weight field placement:** none of the wireframes show a weight
  input anywhere. We placed it on this screen (Correction), reasoning
  that weighing the identified immature grains chronologically happens
  right after correcting which grains count as immature, and right
  before submission. Easy to relocate if the Team Lead wants it elsewhere —
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
  `/api/replicate`'s screen, matching the Team Lead's wireframe order exactly.
  No screens need reordering.

## Phase 4 — Submit

| Requirement | Implemented in | Status | Notes |
|---|---|---|---|
| Wire Submit to real `POST /api/replicate` | `SubmitScreen.kt` → `submit()` | ✅ | Calls `RetrofitClient.apiService.submitReplicate()` directly — no fake dev stand-in this time, see reasoning below |
| Disable button + show spinner while in flight | `SubmitScreen.kt` → `SubmitState.Loading` | ✅ | `enabled = currentState !is SubmitState.Loading`, blocks double-tap |
| Success: show confirmation, return to capture | `SubmitScreen.kt` → `SuccessScreen()` | ✅ | Shows `percentage`/`grade` per spec's suggestion; "New Sample" returns to `Screen.Capture` |
| Failure: clear error, manual retry (same request) | `SubmitScreen.kt` → `SubmitState.Error` | ✅ | Broad `catch (e: Exception)` surfaces connection errors, timeouts, wrong API key, etc. as visible text. Retry just re-calls `submit()` with the same already-entered field values — no duplicate-prevention, per spec |
| Name + SampleType input screen | `SubmitScreen.kt` → `OutlinedTextField`s | ✅ | Not explicitly in `SPEC.md`'s Phase 4 section, but required by `/api/replicate`'s contract and matches the Team Lead's wireframe (screen 4, after Correction's Confirm) |

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

**Not in spec — decisions made to keep moving, flagged for the Team Lead:**
- **SampleType input is free text, not a dropdown.** `sampleId`
  represents a rice variety (per the Team Lead: "sampleID(type of rice)"),
  which is almost certainly a small, fixed, known set in practice —
  free text risks inconsistent data (typos, casing, trailing spaces
  all creating "different" varieties in the database). Left as a plain
  `OutlinedTextField` for now because **no actual list of valid
  varieties has been provided anywhere** — not in any spec, not in the
  wireframes. The dashboard spec's example (`"RC-Dinorado-004"`) only
  shows a naming *format*, not a real list. Needs an answer from
  the Team Lead before converting this to a dropdown/spinner.

---

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
- Weight field placement (Correction vs. a separate screen) — the Team Lead hasn't answered yet
- SampleType as free text vs. dropdown — no list of valid rice varieties provided yet
- Phase 2/4's real backend verification — still blocked on Sitoy

## 2026-09 — GRANULAR field redesign (UX pass, not a spec change)

A Claude Design session produced HTML mockups of 8 redesigned screens
(`project/GRANULAR Field Redesign.dc.html` in the design repo) and this
phase recreated them as native Compose code. Same two endpoints, same
1024×1024 coordinate space, same `GrainBox` field names as SPEC.md --
this is a visual/UX pass, not a backend or contract change. Substantive
behavior changes:

1. **Technician name + sample ID moved earlier.** Collected on the new
   `home/StartSessionScreen.kt` (screen 02), right after the opening
   splash, instead of on Submit. By the time the technician reaches
   `submit/SubmitScreen.kt`, those two fields are a read-only summary,
   not a form. `MainActivity.kt`'s `Screen` sealed class now threads
   `technicianName`/`sampleId` through `Capture` → `Correction` →
   `Submit`.
2. **Correction screen (`correction/CorrectionScreen.kt`, screen 05)**
   gets a live running count (AI detected / you removed / you added,
   computed reactively), an explicit Remove/Add mode
   (`GrainCorrectionMode` in `correction/CorrectionLogic.kt`) so a tap
   is never ambiguous, and box states distinguished by line style, not
   just color, for colorblind accessibility: solid blue = detected,
   white dashed = removed, thick dark with a white halo = added. New
   pure logic (`applyGrainTap`) is unit tested in
   `CorrectionLogicTest.kt` alongside the existing tap-logic tests.
3. **Failure screen (`submit/FailureScreen.kt`, screen 07)** replaces
   the raw `"${exceptionClassName}: ${message}"` string with a
   plain-language sentence, keeping the raw exception class name +
   endpoint in small print underneath for support purposes.
4. **App renamed GRANULAR** -- `res/values/strings.xml`'s `app_name`,
   plus the wordmark on every redesigned screen. Logo asset copied to
   `res/drawable/granular_logo.png`.
5. **Camera screen (`camera/CameraPreviewScreen.kt`, screen 03)** keeps
   all CameraX/crop-math logic (`camera/CropMath.kt`, untouched) and
   only changes the decorative overlay: green accent corner brackets
   instead of a plain guide rectangle, a session-context pill + a real
   torch toggle (wired to `Camera.cameraControl.enableTorch`, guarded
   by `cameraInfo.hasFlashUnit()`), a decorative grid toggle, and the
   gallery button (previously present but buried) surfaced into the
   bottom control row alongside the shutter.

New reusable components: `ui/components/BlueprintFrame.kt` (the
bordered, corner-ticked "blueprint" card used on Start Session, Submit,
Failure, and Result) and `ui/components/AppButtons.kt`
(`PrimaryActionButton`/`SecondaryActionButton`, square-cornered,
matching the design system). New design tokens added to
`ui/theme/Color.kt` (accent scale, neutral scale, divider, detection
colors, failure/result card colors) rather than hardcoding hex per
screen. `ui/theme/Theme.kt` now overrides every Material3 shape slot to
a 0dp-radius `RoundedCornerShape` (Material3's `Shapes` class requires
`CornerBasedShape`, which the plain `RectangleShape` object does not
implement) so stock Material components default to square corners too.

**Deviations from the design mockups (documented, not silent):**

1. **Typography.** The design specifies "Barlow Condensed" (headings)
   and "Barlow" (body) via Google Fonts. There is no offline font file
   bundled in this app and no `ui-text-google-fonts` (downloadable
   fonts) dependency in `app/build.gradle.kts`. Rather than add a new
   font dependency I could not verify compiles in this environment (no
   Android SDK available -- see below), every redesigned screen
   approximates the look with `FontFamily.SansSerif` plus bold/semibold
   weights and generous `letterSpacing`, especially on the uppercase
   "eyebrow" labels, which carries most of the "condensed industrial"
   feel even without the exact typeface.
2. **"Backend reachable" / "N queued" on Start Session
   (`home/StartSessionScreen.kt`) are static display text**, not wired
   to a real connectivity check or an offline submission queue. Neither
   exists anywhere in this app, and SPEC.md explicitly says
   duplicate-submission/queueing logic is not required for the MVP.
   Building either would be new functionality out of scope for a UI
   redesign.
3. **"Keep on device" on the Failure screen
   (`submit/FailureScreen.kt`) does not actually persist the sample for
   a later automatic retry.** This app has no local persistence layer.
   The button currently just ends the current attempt and returns to
   Welcome -- functionally identical to today's lack of a retry queue,
   despite the copy ("...or keep scanning and send it later") implying
   more. This is a real UX gap, not just a technical footnote -- it's
   worth a follow-up conversation with whoever specced that copy before
   shipping it, since a technician reading "send it later" would
   reasonably expect the app to actually do that.
4. **Result screen's total grain count is derived, not
   backend-supplied.** `POST /api/replicate`'s response
   (`ReplicateResponse` in `network/ApiModels.kt`) has no total-grain
   field. `submit/ResultScreen.kt` computes
   `totalGrains = round(confirmedGrainCount / (percentage / 100.0))` --
   exact algebra from `percentage`'s own definition
   (`immature/total × 100`), not a guess, but flagged here since it's
   derived client-side rather than sent by the server.

**Not a deviation, but worth noting:** `ui/components/AppHeader.kt`
(the old branded header bar) has no remaining callers -- none of the
redesigned screens use it, since each screen in the mockups has its own
bespoke header. Left in place rather than deleted, per the same
"unverified compile, don't take a destructive action I can't check"
reasoning as the font dependency above; a future cleanup pass can
remove it once someone can actually build the app.

**Compilation was not verified.** There is no Android SDK in this
environment, so `./gradlew build` could not be run. Every file touched
in this phase was re-read after writing for import correctness and
Compose API usage against the exact APIs already in use elsewhere in
this codebase, but this phase should be built once before merging.

---

## 2026-09 — GRANULAR redesign follow-up: UI fixes + correction-screen zoom/pan

Real-device screenshots of the previous phase surfaced four visual bugs,
fixed in two small commits, plus a genuine new feature requested on top
of the redesign (not part of the original mockups, and a deliberate
departure from SPEC.md's "you do not implement zoom" line -- the person
who owns the app asked for it directly).

**Bug fixes:**

1. `Detect grains` (Review photo) and `Confirm` (Review & correct) were
   56.dp tall (`PrimaryActionButton`'s default) next to their neighbor
   at 52.dp (`SecondaryActionButton`'s default) -- the two screens where
   they sit in the same row. Read as an oversized primary button with
   disproportionately small corner ticks. Both now explicitly pass
   `height = 52.dp` to match, per the mockup (both buttons are
   height:52px on these two screens specifically).
2. Review photo's image now uses `ContentScale.Crop` instead of the
   default `Fit`, and the padding above "BEFORE CLASSIFYING" was
   tightened, to remove a visible gap reported between the photo and
   the checklist text. (The underlying bitmap is always exactly
   1024×1024 by construction, so this is a defensive/robustness fix
   rather than a confirmed root-cause fix -- see the "not independently
   verified" note below.)
3. Submit screen's thumbnail changed from `aspectRatio(1.9f)` to a true
   `aspectRatio(1f)` (with `ContentScale.Crop`), so the always-square
   source photo fills more of the space that was previously a blank
   `Spacer` before the Submit button.

**Zoom/pan feature (`correction/CorrectionScreen.kt`,
`correction/CorrectionLogic.kt`):**

Pinch-to-zoom (1x–4x) and pan (single-finger drag once zoomed) on the
Review & correct image, so a technician can zoom into a dense cluster
of grains before tapping. Implementation notes:

- **One unified gesture detector, not two stacked ones.** The old code
  used `detectTapGestures` alone. Naively adding a second, independent
  `detectTransformGestures` alongside it is a known source of
  double-firing/conflicts, since both would read the same raw pointer
  event stream. Instead, a single `awaitEachGesture` loop decides tap
  vs. transform itself: below a touch-slop threshold and with only one
  pointer down, a release commits as a tap; past that threshold, or
  with a second pointer down, it commits as zoom/pan. Without this
  slop check, *any* tap with even a pixel of finger tremor during
  touch-and-lift -- which is most real taps -- would misfire as a pan
  and silently fail to add/remove a grain, which would have made the
  screen's core interaction nearly unusable.
- **Tap coordinates account for zoom/pan.** `screenTapToImageCoords`
  (new, in `CorrectionLogic.kt`, unit-tested) inverts the
  `graphicsLayer` scale+translation applied to the image content to
  recover the correct 1024-space point under a tap at any zoom/pan
  state. `clampPan` (also new, unit-tested) keeps panning from ever
  revealing empty space around the image, and fully locks pan at 1x
  zoom.
- **The floating +/- buttons over the image were repurposed.** They
  previously duplicated the Remove/Add segmented control below the
  image (functionally a second mode toggle, visually indistinguishable
  from zoom controls). Per the app owner's explicit choice, they're now
  real zoom in/out steps (pinch is still the primary way to zoom), and
  are smaller (32.dp, down from 44.dp) since a discrete zoom nicety is
  lower-emphasis than the mode switch it used to be.

**Not independently verified.** Same limitation as the phase above:
there is no Android SDK in this environment, so none of this was built
or run. The gesture-conflict reasoning above follows Compose's own
`detectTransformGestures` implementation pattern (mirrored deliberately
rather than invented), and the coordinate-transform math is unit
tested, but real multi-touch gesture behavior -- particularly whether
the touch-slop threshold feels right, and whether tap-to-add/remove
still feels reliable once this ships -- needs on-device confirmation
before this is considered done.

**Update, same week, after real on-device testing:** two things above
were wrong, caught by the app owner building and running this on an
actual device (not something this environment could catch):

- `import androidx.compose.ui.draw.graphicsLayer` doesn't exist --
  `graphicsLayer` lives in `androidx.compose.ui.graphics`, not
  `androidx.compose.ui.draw` (unlike `alpha`, `clip`, and
  `clipToBounds`, which genuinely are in `.draw`). This should have
  failed to compile from the moment this phase landed.
- The zoom/pan clip was on the wrong element. `graphicsLayer(clip =
  true)` clips a layer's *content* to *that layer's own* local,
  pre-transform bounds -- since the image already exactly fills that
  local space, there was nothing for it to clip, and the zoomed image
  visibly overflowed into the legend and mode-control rows below it.
  The fix is `Modifier.clipToBounds()` on the *outer*, fixed-size
  gesture Box instead, which clips the transformed/scaled inner content
  to the outer container's bounds -- clipping has to wrap the
  transform, not be applied by the transformed element to itself.

Worth remembering for anywhere else this codebase reaches for
`graphicsLayer` with a scale/translation and expects it to stay
contained: the clip belongs on the parent, not the scaled node.

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