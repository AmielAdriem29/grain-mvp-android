# Android integration and performance tests

Tests for the TC-IDs in the STD (Granular STD v1.3) that touch the Android app:

| TC-ID | Title | Type | Where |
|---|---|---|---|
| TC-INT-01 | Box Actions From App to Dashboard (row a: app to API to DB) | Integration | `app/src/androidTest/.../integration/BoxActionsIntegrationTest.kt` |
| TC-INT-02 | Displayed Values Match Stored Values (app vs DB) | Integration | `.../integration/DisplayedValuesIntegrationTest.kt` |
| TC-INT-04 | Stored Image Integrity (app to API leg) | Integration | `.../integration/StoredImageIntegrityIntegrationTest.kt` |
| TC-INT-05 | Field Contract Across Components | Integration (static review) | `testing/android-test-results.md` |
| TC-SYS-03 | Performance Targets, rows a and b from the phone | Performance | `.../performance/ApiLatencyPerformanceTest.kt` |

## Run (one command each)

```powershell
powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite integration
powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite performance
```

macOS/Linux/Git Bash: `testing/run-android-tests.sh integration` (or `performance`).

Results land in `testing/build/results/` (`<suite>-summary.md`, logcat evidence, host logs) and the JUnit
report in `app/build/reports/androidTests/connected/debug/index.html`.

## What you need

- .NET 10 SDK and Docker running (`docker ps` works)
- Android SDK with `adb`, and exactly one emulator or phone connected (Android 8.0+)
- The backend repo checked out next to this one: `GitHub/ImmatureBackend` (or pass `-BackendRoot`).
  Check out the branch the STD names (`dev`) before running, and record the commit.

## What is real and what is not

```
 phone/emulator                      this PC (testing/local-api-host)
 ┌──────────────────────┐  adb       ┌─────────────────────────────────────────┐
 │ app code:            │  reverse   │ real ImmatureBackend API                │
 │  ApiService,         │ ────────▶ │  controllers, validation, calculations, │
 │  AuthInterceptor,    │ :8000      │  ONNX grain detector                    │
 │  toImagePart,        │            │        │                                │
 │  correction logic,   │            │        ▼                                │
 │  formatPercentage    │            │ real PostgreSQL 17 (Docker, throwaway)  │
 └──────────────────────┘            └─────────────────────────────────────────┘
```

- **Real, connected, no mocks between them:** the app's network code → the API → the database.
- **Replaced, because the rules forbid production services:**
  - Supabase sign-in: the host signs test tokens with a key made fresh for each run. The app's test token is
    anonymous, like the real app's. No key or token is stored in the repo.
  - Cloudinary: `LocalImageStore` hashes the uploaded bytes and records the SHA-256 in `imagePublicId`.
- **One seam in the app:** `RetrofitClient` reads its URL from `BuildConfig` and its token from the live Supabase
  session. The tests build the same client (same `ApiService`, `AuthInterceptor`, JSON settings and 50 s
  timeouts) pointed at the local host. `submitLikeTheApp` in `TestData.kt` mirrors `submitQueuedReplicate`
  field for field.
- The tests refuse to run against anything but `127.0.0.1`, `localhost` or `10.0.2.2`, and a plain
  `gradlew connectedAndroidTest` skips them, since the runner supplies the backend address.
