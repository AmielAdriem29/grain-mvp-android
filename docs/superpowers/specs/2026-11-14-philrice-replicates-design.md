# PhilRice Replicates (Android) — Design

Date: 2026-11-14
Branch: `feature/philrice-replicates` (based on `upstream/develop`)
Backend: `ImmatureBackend` branch `dev` (`https://immaturebackend-dev.onrender.com`)

## Problem

PhilRice asked for five changes before the presentation:

1. The app lets the technician take multiple photos of one sample.
2. Photos of the same sample get a replicate number: `-01`, `-02`, `-03`.
3. The dashboard shows one record per sample, grouping its photos with their grade and confidence.
4. Each photo is GOOD (confident enough, or manually overridden by a user) or BAD.
5. When a sample has 2 GOOD photos, it is accepted automatically and the average result is calculated.

This spec covers **only the Android app**. Backend `dev` already implements 2, 4 and 5 on the server. Item 3 belongs to the dashboard (see `docs/DASHBOARD_PHILRICE_CHANGES.md`).

## What the backend (`dev`) already does

`POST /api/replicate` is still called once per photo. On `dev`:

- **Numbering.** Photos with the same season + index number + sample no are numbered 1, 2, 3 … by the server. The response returns `replicateNo` and `replicateLabel` (`"<sampleId>-02"`). The app never assigns numbers.
- **Confidence.** The server computes the mean `confidence` of the AI-predicted boxes, rounded to 2 decimals. It is `null` when there are no AI boxes.
- **Quality.** `"good"` when `manuallyOverridden` is true or confidence ≥ 0.80; otherwise `"bad"`.
- **Auto-accept.** After each save, if the sample has fewer than 2 accepted photos and enough GOOD photos under review, the earliest GOOD photos (by `replicateNo`) are set to `accepted`. The response's `reviewStatus` is `"accepted"` when this photo was one of them.
- **New request field.** `manuallyOverridden` (optional boolean, default false).
- **409 changed meaning.** It now means "a replicate number could not be assigned because another photo of the same sample was submitted at the same moment. Retry." It no longer means "this sample already exists".
- **Average.** Only available from `GET /api/samples`, which requires the dashboard role. The app cannot call it.

## Design

The backend decides numbering, quality and acceptance. The app sends the photo and the override flag, shows what comes back, and computes the average locally.

### 1. Flow: more photos of the same sample

- On Result, add a button **"Take another photo of this sample"**. It goes back to Capture with the **same** `SessionInfo`, so the sample ID and sample no are unchanged. The server assigns the next replicate number.
- The existing buttons are unchanged: "Next sample", "Add second sample" (only when `sampleNo == 1`) and "End session".
- Each photo still goes through Detect → Correction (with its own weight) → Submit.

### 2. Network

`network/ApiModels.kt`, `ReplicateResponse` gets new fields. All have defaults, so the app still decodes responses from a `master` backend that doesn't send them:

```kotlin
val replicateNo: Int = 0,
val replicateLabel: String = "",
val confidence: Double? = null,
val quality: ReplicateQuality? = null,   // @SerialName("good") GOOD, @SerialName("bad") BAD
val reviewStatus: String? = null         // "accepted" when auto-accepted, otherwise "review"
```

- `network/ApiService.kt`: `submitReplicate` gets `@Part("manuallyOverridden") manuallyOverridden: RequestBody`, sent as `"true"` or `"false"`.
- `submit/ReplicateQueue.kt`: `QueuedReplicate` gets `manuallyOverridden: Boolean`, and `submitQueuedReplicate` sends it. The value is fixed when the item is built, so a retry from the Failure screen or the queue sends the original value.

### 3. Submit: manual override toggle

- `SubmitFormScreen` gets a checkbox labelled **"I verified this count manually"**. It is off by default.
- Its value goes into `buildQueuedItem()` as `manuallyOverridden`.
- A short helper line under it: "Marks this photo GOOD regardless of AI confidence."

### 4. Result: label, quality, acceptance

- **Header.** "Recorded · {replicateLabel}". Falls back to `sampleId` when the label is blank (old backend).
- **New Quality row** in the summary:
  - `GOOD · manually verified` when the item was sent with the override;
  - otherwise `GOOD · 86% confidence` or `BAD · 62% confidence`;
  - `BAD · no AI confidence` when `confidence` is null and there was no override.
  - The row is hidden when `quality` is null (old backend).
- **Accepted banner**, shown when `reviewStatus == "accepted"`:
  - with an average: "Sample accepted · avg {percentage}% · {grade}" (see §5);
  - without one: "Sample accepted".

### 5. Average on the phone

New file `submit/SampleAverage.kt`. Pure Kotlin, no Android types.

```kotlin
data class SampleAverage(val weight: BigDecimal, val percentage: BigDecimal, val grade: String)

/** Mirrors backend CalculationService: mean weight (2 dp, half-up), % = w/30*100 (1 dp, half-up), grade bands. */
fun sampleAverage(weights: List<BigDecimal>): SampleAverage

/** Earliest 2 GOOD results by replicateNo -- the same pick as backend AutoAcceptSampleAsync. Null if fewer than 2. */
fun acceptedWeights(results: List<SubmittedPhoto>): List<BigDecimal>?
```

- Grade bands: `< 2.0` → `Pr`, `≤ 5.0` → `G1`, `≤ 10.0` → `G2`, `≤ 15.0` → `G3`, otherwise `Not classified`.
- It uses `BigDecimal` with `RoundingMode.HALF_UP`, which matches .NET `decimal` with `MidpointRounding.AwayFromZero` for positive values.
- A `ponytail:` comment notes that the bands and rounding are copied from backend `CalculationService` and must change with it.
- Known limit: the average appears only when both accepted photos were submitted from this phone in this app session. Otherwise the banner shows "Sample accepted" without numbers. This is acceptable; the dashboard shows the authoritative average.

### 6. Session results (MainActivity)

```kotlin
data class SubmittedPhoto(val session: SessionInfo, val weight: String, val manuallyOverridden: Boolean, val response: ReplicateResponse)
```

- `MainActivity` holds `val submittedPhotos = remember { mutableStateListOf<SubmittedPhoto>() }`.
- The sample key is `(season, indexNumber, sampleNo)`, the same grouping the backend uses.
- Both successful paths append to it: `SubmitScreen`'s success, and each item sent from the queue.
- `ResultScreen` receives the computed `SampleAverage?` for the current sample (null when not accepted or not computable).
- The list is cleared on "End session". It is in memory only, like `replicateQueue`.

### 7. 409 fix (required)

On backend `dev`, 409 is a retryable numbering collision. Today the app treats 409 as "already saved":

- `sendQueuedReplicates` (MainActivity) removes 409 items from the queue. **This would silently lose photos.** Remove that branch, so a 409 leaves the item queued like any other failure.
- `FailureScreen`'s 409 copy changes to "Another photo of this sample was sent at the same moment. Retry." Retry stays the primary action. Drop the "skip sample" framing for 409.
- Rename `isDuplicateSampleError` to `isReplicateConflictError`, or delete it if nothing uses it after the change.

## Error handling

- Network and HTTP failures go through the existing Failure screen, Retry and "Keep on device" queue. No new failure paths.
- An old backend (missing fields) degrades gracefully: no label, no quality row, no banner.

## Testing (JUnit, existing style)

- `SampleAverageTest`
  - mean and rounding, e.g. 0.95 + 1.00 → 0.98 g → 3.3% → G1;
  - every grade boundary (1.9/2.0, 5.0/5.1, 10.0/10.1, 15.0/15.1).
- `acceptedWeights`
  - two GOOD photos;
  - GOOD, BAD, GOOD (uses replicates 1 and 3);
  - only one GOOD returns null;
  - unordered input is sorted by `replicateNo`.
- `ReplicateResponse` decoding: the `dev` payload with every new field (including `"quality":"good"`), and the `master` payload without them.

## Out of scope

- **Dashboard** (feature 3: per-sample records, GOOD/BAD display, override toggle). See `docs/DASHBOARD_PHILRICE_CHANGES.md`.
- **Backend.** No changes needed; `dev` already provides everything above. `dev` must be the backend the demo build points at.
- Computing confidence or quality on the device.
- Persisting session results or the queue across app restarts.
- Edge cases of auto-accept (e.g. a third GOOD photo after acceptance, conflicting grades). These are deferred, as the team agreed.
