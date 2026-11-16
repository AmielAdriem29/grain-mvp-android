# Android integration and performance test results

Repo: grain-mvp-android, branch `test/android-integration-performance` (app code unchanged from `develop` @ 646dd16).
How the tests are set up and run: [testing/README.md](README.md).

| TC-ID | Title | Type | Status |
|---|---|---|---|
| TC-INT-01 | Box Actions From App to Dashboard | Integration | Written, **not run yet** |
| TC-INT-02 | Displayed Values Match Stored Values | Integration | Written, **not run yet** |
| TC-INT-04 | Stored Image Integrity | Integration | Written, **not run yet** (STD row only partly runnable) |
| TC-INT-05 | Field Contract Across Components | Integration (static review) | **Run: Fail** |
| TC-SYS-03 | Performance Targets (rows a, b from the phone) | Performance | Written, **not run yet** |

---

## TC-INT-05: Field Contract Across Components

Type: Integration (static review) | Env: source inspection, no runtime
Build: android@646dd16 (develop), backend@505e4e9 (master; the STD names `dev`), dashboard@1134498
Run command: none (inspection). Files: `network/ApiModels.kt` + `network/ApiService.kt`, `ReplicateRequest.cs` /
`ReplicateResponse.cs` / `GrainBox.cs` / `Grade.cs`, `src/types.ts`, `docs/02_BACKEND_SPEC.md`.

### POST /api/replicate request

| Field | Android | Backend | Dashboard (`Replicate`) | 02_BACKEND_SPEC | P/F |
|---|---|---|---|---|---|
| image | JPEG 1024², part `image` | `IFormFile`, 1024² JPEG/PNG, ≤10 MB | `imagePublicId`, `imageSecureUrl` | binary; served by `GET /api/images/{id}` | F (spec) |
| technicianName | String | string ≤100 | string | string | P |
| sampleId | String | string | string | string | P |
| aiPredictedGrains | JSON string of GrainBox[] | string (JSON) | GrainBox[] | JSON string | P |
| confirmedGrains | JSON string of GrainBox[] | string (JSON) | GrainBox[] | JSON string | P |
| weight | String, 0 < w ≤ 30 | `decimal?`, 0 < w ≤ 30 | read back as `immatureWeight: number` | "string or float", no limit | F (spec) |
| season | String | string, `^\d{4}(DS\|WS)$` | string \| null | missing | F (spec) |
| indexNumber | String | string ≤50 | string \| null | missing | F (spec) |
| selectionName | String | string ≤100 | string \| null | missing | F (spec) |
| sampleNo | Int as text | `int?`, 1 or 2 | number \| null | missing | F (spec) |
| manuallyOverridden | "true"/"false" | bool | boolean | missing | F (spec) |
| capturedAt | ISO-8601 UTC (`Instant`) | `DateTimeOffset?`, required | string \| null | missing | F (spec) |
| submittedAt | ISO-8601 UTC (`Instant`) | `DateTimeOffset?`, ≥ capturedAt | string \| null | missing | F (spec) |
| auth | `Authorization: Bearer <Supabase JWT>` | JWT, policy "Capture" | n/a | `X-API-Key` header | F (spec) |

### GrainBox

| Field | Android | Backend | Dashboard | 02_BACKEND_SPEC | P/F |
|---|---|---|---|---|---|
| x, y, width, height | Int | int | number | int | P |
| confidence | `Float?` | `double?` | number \| null | float \| None | P (see note 3) |
| action | enum `kept`/`removed`/`added` or null | `string?`, documented "reserved, always null", values not checked | `"kept"\|"removed"\|"added"\|null` | str \| None | **F** |

### POST /api/replicate response

| Field | Android | Backend | Dashboard | 02_BACKEND_SPEC | P/F |
|---|---|---|---|---|---|
| id | String | string | string | string | P |
| percentage | Double | decimal, 1 dp | number | float; column NUMERIC(5,2) | F (spec) |
| grade | String; local average uses "Not classified" | enum, sends **"Not Classified"**; XML docs say "Not classified" | string; maps only "Below Standard" | "Below Standard" | **F** |
| meetsRecommendedValue | `Boolean?` | bool | boolean | missing | F (spec) |
| replicateNo / replicateLabel | Int / String | int / string | number / string | missing | F (spec) |
| confidence | `Double?` | `decimal?` | number \| null | missing | F (spec) |
| quality | enum good/bad | enum good/bad | "good" \| "bad" | missing | F (spec) |
| reviewStatus | `String?` ("accepted"/"review") | review/accepted/rejected/retraining | same 4 | "unreviewed" | F (spec) |

**Result: Fail.** The app, backend and dashboard agree on every field name and type. They disagree on two
allowed-values points, and the backend spec is out of date.

### Bugs found (TC-INT-05)

1. **Grade label casing.** The backend sends and stores `"Not Classified"` (`Grade.cs`, `GradeConverter.cs`). Its
   own XML docs, the STD (TC-INT-03 row b) and the app's local average (`SampleAverage.kt`) use `"Not classified"`.
   So the Result screen can show "Not Classified" for one photo and "Not classified" in the sample-accepted
   banner. The dashboard only rewrites the old "Below Standard" label.
2. **GrainBox.action contract.** The backend documents `action` as "reserved for future use; currently always
   null" and does not check its values. The app sends, and the dashboard expects, `kept`/`removed`/`added`.
   TC-INT-01 checks at runtime whether the actions are stored unchanged.
3. **02_BACKEND_SPEC.md is stale.** It is missing 7 request fields (season … submittedAt) and the newer response
   fields. It still describes `X-API-Key` auth, `GET /api/images/{id}`, the "Below Standard" grade and the
   "unreviewed" status.

Notes: (1) `weight` is called `immatureWeight` on the read side, which is consistent between backend and
dashboard. (2) The app does not cap `technicianName` at 100 characters; the backend rejects longer names
with 400. (3) The app holds `confidence` as a 32-bit Float, which is enough for a 0 to 1 score.

---

## TC-INT-01, TC-INT-02, TC-INT-04 (integration) and TC-SYS-03 (performance)

**Not run yet.** The machine these tests were written on has no JDK, Android SDK/adb, .NET SDK or Docker, so
neither the app nor the local backend can be built there. Run them with:

```powershell
powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite integration
powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite performance
```

Environment for every run: local test backend (real API + ONNX detector + PostgreSQL 17 in Docker; test-signed
tokens; local image store instead of Cloudinary) on the PC, reached through `adb reverse`, plus the emulator or
phone you name. Copy each `testing/build/results/<suite>-summary.md` into the tables below.

### TC-INT-01: Box Actions From App to Dashboard

| Row | Step | Expected | Actual | P/F | Evidence |
|---|---|---|---|---|---|
| a | 6 proposed boxes; in the app keep 3, remove 2, add 1; submit | Backend stores 3 kept, 2 removed, 1 added; all coordinates unchanged | | | EV-TC-INT-01-a |
| b | Open the record on the dashboard | Same actions at the same coordinates | Not run: dashboard suite | — | — |

### TC-INT-02: Displayed Values Match Stored Values

| Row | Step | Expected | Actual | P/F | Evidence |
|---|---|---|---|---|---|
| a1 | Submit 1.5 g; compare app Result vs DB | Same percentage and grade | | | EV-TC-INT-02-a |
| a2 | Same submission vs STD values | 5.2% and G2 (STD predicts Fail until TC-203: the app has no total-weight field, the backend divides by 30 g, so expect 5.0% G1) | | | EV-TC-INT-02-a |
| — | Dashboard value | 5.2% G2 | Not run: dashboard suite | — | — |

### TC-INT-04: Stored Image Integrity

| Row | Step | Expected | Actual | P/F | Evidence |
|---|---|---|---|---|---|
| a′ | App builds 1024² JPEG, uploads with POST /api/replicate; compare SHA-256 sent vs SHA-256 received by the API | Hashes equal | | | EV-TC-INT-04-a |
| a | GET /api/images/{id} and compare hash | Hashes equal | Can't run: the endpoint doesn't exist (images go to Cloudinary) and real Cloudinary is off-limits | — | — |

### TC-SYS-03: Performance Targets (rows a, b, measured on the phone through the app's network code)

Cold start (first request after the host starts): predict ___ ms, replicate ___ ms.

| Endpoint | Users | Requests | p50 ms | p95 ms | max ms | Error % | Target p95 ms | P/F |
|---|---|---|---|---|---|---|---|---|
| POST /api/predict (row a) | 1 | 20 | | | | | ≤ 5000 | |
| POST /api/replicate (row b) | 1 | 20 | | | | | ≤ 3000 | |

Not covered here: rows c to e (dashboard list, detail canvases, 5 reviewers) and the server-side k6 load runs
for rows a and b. Those belong to the dashboard and backend suites. Times are against a local PC host, not
Render, so they show the app and API cost without hosting latency.
