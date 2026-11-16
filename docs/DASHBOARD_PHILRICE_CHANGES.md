# Dashboard Changes for PhilRice Replicates

Who this is for: whoever maintains `immature-dashboard` (React + Vite).
Backend to target: `ImmatureBackend` branch **`dev`** (`https://immaturebackend-dev.onrender.com`).
Related: the Android side is in `docs/superpowers/specs/2026-11-14-philrice-replicates-design.md`.

## Background

PhilRice asked for:

1. Multiple photos per sample (Android).
2. Photos of the same sample numbered `-01`, `-02`, `-03` (backend, done).
3. **The dashboard showing one record per sample**, grouping all its photos with their grade and confidence (**this guide**).
4. Each photo marked GOOD (confidence ≥ 0.80, or manually overridden) or BAD (backend, done).
5. A sample with 2 GOOD photos accepted automatically, with the average calculated (backend, done).

The backend `dev` branch already does the grouping, the quality checks, the auto-accept and the average. The dashboard only needs to **read the new endpoint and show it**, plus (optionally) a toggle to override a photo.

Today, on both `main` and `dev`, the dashboard calls only `GET /api/replicates` and `PATCH /api/replicates/{id}/status`, and shows one card or row per photo.

## New and changed backend endpoints (`dev`)

All of them need the existing Supabase bearer token (`authHeaders()` in `src/api.ts`) and the dashboard role.

### `GET /api/samples` (new): one record per sample

A sample is season + index number + sample no. The list is ordered by newest submission first.

```jsonc
[
  {
    "season": "2026WS",
    "indexNumber": "NCT-118",
    "selectionName": "PSB Rc222",
    "sampleNo": 1,
    "sampleId": "S-0142",
    "replicateCount": 3,
    "goodCount": 2,
    "status": "accepted",            // "accepted" once 2 photos are accepted, otherwise "review"
    "averageWeight": 0.98,           // null until accepted
    "averagePercentage": 3.3,        // null until accepted
    "averageGrade": "G1",            // null until accepted
    "meetsRecommendedValue": false,  // null until accepted
    "latestSubmittedAt": "2026-11-14T03:12:45Z",
    "replicates": [ /* ReplicateListItem[], ordered by replicateNo */ ]
  }
]
```

### `GET /api/replicates` (changed): each item has new fields

The existing fields are unchanged. The new ones:

```jsonc
{
  "replicateNo": 2,
  "replicateLabel": "S-0142-02",    // show this instead of sampleId for a photo
  "confidence": 0.86,               // mean AI box confidence, 0-1, may be null
  "quality": "good",                // "good" | "bad"
  "manuallyOverridden": false,
  "season": "2026WS", "indexNumber": "NCT-118", "selectionName": "PSB Rc222", "sampleNo": 1,
  "meetsRecommendedValue": false,
  "capturedAt": "...", "submittedAt": "...", "processingSeconds": 41.3
}
```

`season`, `indexNumber`, `selectionName`, `sampleNo`, `meetsRecommendedValue`, `capturedAt`, `submittedAt` and `processingSeconds` already existed on the backend but aren't in `src/types.ts` yet.

### `PATCH /api/replicates/{id}/override` (new): mark a photo GOOD by hand

```jsonc
// request
{ "overridden": true }     // true = force GOOD, false = clear the override
// 200 response
{ "id": "…", "manuallyOverridden": true, "quality": "good", "reviewStatus": "accepted" }
```

After an override, the backend re-runs auto-accept for that sample. Refetch `/api/samples` afterwards, because a *different* photo of the same sample may also have become `accepted`.

`PATCH /api/replicates/{id}/status` still exists. Changing a status also re-runs auto-accept now.

## Changes to make

### 1. `src/types.ts`

```ts
export type Quality = "good" | "bad";

export interface Replicate {
  // ...existing fields...
  season: string | null;
  indexNumber: string | null;
  selectionName: string | null;
  sampleNo: number | null;
  replicateNo: number;
  replicateLabel: string;
  confidence: number | null;
  quality: Quality;
  manuallyOverridden: boolean;
  meetsRecommendedValue: boolean;
  capturedAt: string | null;
  submittedAt: string | null;
  processingSeconds: number;
}

export interface SampleRecord {
  season: string | null;
  indexNumber: string | null;
  selectionName: string | null;
  sampleNo: number | null;
  sampleId: string;
  replicateCount: number;
  goodCount: number;
  status: "review" | "accepted";
  averageWeight: number | null;
  averagePercentage: number | null;
  averageGrade: string | null;
  meetsRecommendedValue: boolean | null;
  latestSubmittedAt: string | null;
  replicates: Replicate[];
}
```

### 2. `src/api.ts`

Follow the same pattern as `fetchReplicates` and `updateStatus`:

```ts
export async function fetchSamples(): Promise<SampleRecord[]> {
    const response = await fetch(`${BASE_URL}/api/samples`, {headers: await authHeaders()});
    if (!response.ok) throw await responseError(response, "Failed to fetch samples");
    return (await response.json()) as SampleRecord[];
}

export async function updateOverride(replicateId: string, overridden: boolean) {
    const response = await fetch(`${BASE_URL}/api/replicates/${encodeURIComponent(replicateId)}/override`, {
        method: "PATCH",
        headers: await authHeaders({"Content-Type": "application/json"}),
        body: JSON.stringify({overridden}),
    });
    if (!response.ok) throw await responseError(response, "Failed to update override");
    return (await response.json()) as Pick<Replicate, "id" | "manuallyOverridden" | "quality" | "reviewStatus">;
}
```

### 3. `src/App.tsx`: per-sample view (feature 3)

- Switch the main query from `fetchReplicates` to `fetchSamples`, keeping React Query as it is used now.
- **One row or card per sample** shows:
  - sample ID, season · index number, selection name, sample no;
  - photo count and good count, e.g. "3 photos · 2 good";
  - status (Accepted / Review);
  - when accepted: average % and average grade, and whether it meets the recommended value.
- **Expanding a sample** (or the existing `DetailOverlay`) lists its photos. Each photo shows:
  - `replicateLabel` (`S-0142-02`);
  - grade and percentage;
  - confidence as a percentage (`86%`, or `—` when null);
  - a **GOOD / BAD** pill, with "manually overridden" shown when `manuallyOverridden` is true;
  - its own review status and image (the existing `ImageTiers` / `GrainCanvas` work per photo, unchanged).
- **Search** should also match `replicateLabel`, `indexNumber` and `selectionName`.
- **Status filter and Kanban.** They currently work per photo. The simplest option is to filter samples by `status` and keep Kanban per photo. Decide with the team whether Kanban should move to samples. It isn't required by PhilRice.
- **After any status or override change,** invalidate both the samples and the replicates queries.

### 4. Override toggle (optional but recommended)

- In the photo detail, add a "Mark as GOOD (manual override)" toggle that calls `updateOverride` and then refetches.
- This is how a reviewer rescues a BAD photo, so that 2 GOOD photos can auto-accept the sample.

## Things to watch

- **Average values come from the server.** Don't recompute them in the dashboard.
- Rows saved **before** the `dev` migration all have `replicateNo = 1` and `manuallyOverridden = false` (the migration's column defaults). Very old rows may also have a null season or index number, so the backend groups them together. Show what the API returns; don't regroup on the client.
- **Grade strings:** `"Pr" | "G1" | "G2" | "G3" | "Not classified"`.
- **Status values:** `review`, `accepted`, `rejected`, `retraining`. Sample `status` is only ever `review` or `accepted`.
- **Point the dashboard at the dev backend while testing.** Set `VITE_BACKEND_URL` to the dev URL; in local dev, the Vite proxy target.

## Done when

- [ ] The dashboard lists one record per sample from `GET /api/samples`.
- [ ] Each sample shows its photos with label (`-01`, `-02` …), grade, confidence and GOOD/BAD.
- [ ] Accepted samples show average %, grade and recommended-value status.
- [ ] (Optional) The override toggle works, and the sample updates to accepted when it reaches 2 GOOD photos.
- [ ] Status changes and overrides refresh the sample list.
