# FastAPI Backend — Complete Build Specification

This file contains everything needed to build the backend in isolation. You do not need to read any other document. One value below is a placeholder (SUPABASE_CONNECTION_STRING) — get this from whoever sets up Supabase before you start Phase 3, but Phases 1, 2, and 4 need zero database access and can be built and tested immediately.

---

## Connection Details (fill in this one value)

```
SUPABASE_DATABASE_URL = "postgresql://<TO BE PROVIDED>"
```

Store this in a `.env` file, read via environment variables. Never hardcode it in a `.py` file.

You also OWN the API key. You decide its value and give it to both the Android developer and the Dashboard developer. Put it in the same `.env` file:

```
EXPECTED_API_KEY = "<YOU CHOOSE THIS VALUE — e.g. a long random string — then share it with the other two people>"
```

You also OWN the base URL other people connect to. Once your server is running (locally, or deployed), give that address to both the Android and Dashboard developers.

---

## What You Receive (Inbound Contracts — exact shapes, do not deviate)

### From Android: `POST /api/predict`

Multipart form-data request. You will receive:
```
image           -> binary JPEG file, exactly 1024x1024 pixels
technicianName  -> string
sampleId        -> string
Header: X-API-Key -> must match EXPECTED_API_KEY, reject with 401 if not
```

**What you must do:**
1. Open the image bytes as a PIL Image.
2. Call `active_detector.detect(image)` — this returns a list of grain box dictionaries.
3. Filter the list to keep only entries where `confidence >= 0.5`.
4. Generate an `imageId` (a UUID string is fine).
5. Return this exact JSON shape:
```json
{
  "imageId": "generated-uuid-string",
  "grains": [
    { "x": 112, "y": 340, "width": 24, "height": 24, "confidence": 0.81, "action": null }
  ]
}
```
Note: `grains` can be an empty list `[]`. This is valid and expected sometimes. Do not treat it as an error.

You do NOT need to save anything to the database at this step. This is a prediction-only call.

### From Android: `POST /api/replicate`

Multipart form-data request. You will receive:
```
image               -> binary JPEG file, 1024x1024 pixels
technicianName      -> string
sampleId            -> string
aiPredictedGrains   -> string containing JSON array of grain box objects
confirmedGrains     -> string containing JSON array of grain box objects
weight              -> string or float, grams
Header: X-API-Key -> must match EXPECTED_API_KEY, reject with 401 if not
```

**What you must do:**
1. Parse `aiPredictedGrains` and `confirmedGrains` using `json.loads()` — they arrive as JSON-encoded strings inside form fields, not native JSON, because this is a multipart request.
2. Calculate `percentage = (weight / 30) * 100`.
3. Assign grade using these exact boundaries:
```python
if percentage < 2.0:
    grade = "Pr"
elif percentage <= 5.0:
    grade = "G1"
elif percentage <= 10.0:
    grade = "G2"
elif percentage <= 15.0:
    grade = "G3"
else:
    grade = "Below Standard"  # confirm this label later, currently a placeholder
```
4. Insert one new row into the `replicates` table (see Database Contract below) with all received fields plus the calculated percentage and grade, and `review_status` defaulted to `"unreviewed"`.
5. Return this exact JSON shape:
```json
{
  "id": "the-new-row-id",
  "percentage": 41.5,
  "grade": "G3"
}
```

### From Dashboard: `GET /api/replicates`

No body. Just the API key header.

**What you must do:** run `SELECT * FROM replicates` (all columns except the raw image bytes — exclude `original_image` from this response, since it's binary and the dashboard fetches images separately). Return a JSON array:

```json
[
  {
    "id": "uuid-string",
    "technicianName": "Juan Dela Cruz",
    "createdAt": "2026-07-26T10:00:00Z",
    "sampleId": "RC-Dinorado-004",
    "aiPredictedGrains": [ ... ],
    "confirmedGrains": [ ... ],
    "immatureWeight": 12.45,
    "percentage": 41.5,
    "grade": "G3",
    "reviewStatus": "unreviewed"
  }
]
```

### From Dashboard: `GET /api/images/{replicate_id}`

`replicate_id` is a path parameter, e.g. `/api/images/abc-123`.

**What you must do:** look up that row, return the raw `original_image` bytes with `Content-Type: image/jpeg`. 

**Important note on auth for this specific endpoint:** the dashboard will likely load this via a plain HTML `<img>` tag, which cannot send custom headers like `X-API-Key`. Support the API key as a query parameter as a fallback for this one endpoint specifically, e.g. `/api/images/{replicate_id}?api_key=xxx`, checked the same way as the header. Every other endpoint only needs the header.

### From Dashboard: `PATCH /api/replicates/{id}/status`

```
Header: X-API-Key -> required
Body (JSON): { "status": "accepted" }   // or "denied"
```

**What you must do:** update that row's `review_status` column to the given value. Return the updated status:
```json
{ "id": "abc-123", "reviewStatus": "accepted" }
```

---

## What You Send to the Database (Exact Schema)

Run this once, at startup or via a setup script, against the Supabase connection:

```sql
CREATE TABLE replicates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  technician_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sample_id TEXT NOT NULL,
  ai_predicted_grains JSONB NOT NULL,
  confirmed_grains JSONB NOT NULL,
  immature_weight NUMERIC(6,2) NOT NULL,
  percentage NUMERIC(5,2) NOT NULL,
  grade TEXT NOT NULL,
  original_image BYTEA,
  review_status TEXT NOT NULL DEFAULT 'unreviewed'
);
```

If `gen_random_uuid()` errors on first run, first execute:
```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

You do not design this schema. It is fixed. Use SQLModel or SQLAlchemy + asyncpg to connect and read/write rows matching this exact shape.

---

## The AI Detection Layer (This Is Yours to Build, But Follow This Exact Pattern)

Create this file structure:

```
detection/
  base.py         <- defines the contract, write once, never touch again
  placeholder.py  <- your temporary detector, used today
```

`detection/base.py`:
```python
from typing import Protocol, TypedDict

class GrainBox(TypedDict):
    x: int
    y: int
    width: int
    height: int
    confidence: float | None
    action: str | None

class GrainDetector(Protocol):
    def detect(self, image) -> list[GrainBox]:
        ...
```

`detection/placeholder.py`:
```python
class PlaceholderDetector:
    """
    Temporary stand-in until a trained model exists.
    Document here exactly what logic this uses.
    """
    def detect(self, image) -> list[dict]:
        # your placeholder logic goes here
        # can be simple image thresholding, or fixed test data
        return [
            {"x": 100, "y": 200, "width": 24, "height": 24, "confidence": 0.7, "action": None}
        ]
```

In `config.py`:
```python
from detection.placeholder import PlaceholderDetector
active_detector = PlaceholderDetector()
```

**The one rule that matters:** every other file in your backend calls `active_detector.detect(image)` and touches nothing else about detection. When a real trained model exists later, someone writes a new class implementing the same `detect()` method and changes one import line in `config.py`. Nothing else in the whole backend changes. Do not let any other file reach into `detection/placeholder.py` directly.

---

## Authentication (Build Once, Applies Everywhere)

```python
from fastapi import Header, HTTPException
from config import EXPECTED_API_KEY

async def verify_api_key(x_api_key: str = Header(...)):
    if x_api_key != EXPECTED_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")
```

Apply this as a dependency at the router or app level (`dependencies=[Depends(verify_api_key)]`), so every route is protected automatically without needing to remember it per-route. Remember the `/api/images/{id}` exception noted above (query param fallback).

---

## Build Order

### Phase 1 — Core contracts, no database, no HTTP yet
1. Write `models.py` with the Pydantic models: `GrainBox`, `PredictResponse`, `ReplicateSubmission` — matching field names exactly as shown above.
2. Write `detection/base.py` and `detection/placeholder.py`.
3. Write `config.py` wiring `active_detector`, reading `EXPECTED_API_KEY` and `SUPABASE_DATABASE_URL` from environment variables.
4. **Checkpoint: a plain Python script can import `active_detector` and call `.detect()` on a test image, no server running.**

### Phase 2 — Auth and skeleton
1. Write `auth.py`.
2. Write `main.py`, create the FastAPI app, apply the auth dependency globally.
3. Run `uvicorn main:app --reload` and confirm it boots.
4. **Checkpoint: hitting any test route returns 401 without a valid key.**

### Phase 3 — Database (needs SUPABASE_DATABASE_URL to be real)
1. Write the SQLModel/SQLAlchemy `Replicate` class matching the schema exactly.
2. Connect using the real connection string, run the table creation.
3. Write helper functions: `save_replicate(...)`, `fetch_all_replicates()`, `fetch_image_bytes(id)`, `update_review_status(id, status)`.
4. **Checkpoint: a standalone script inserts and reads back one row successfully.**

### Phase 4 — Calculation logic (fully independent, do this anytime)
1. Write `calculations.py` with `calculate_percentage` and `assign_grade`.
2. Write `pytest` tests for every boundary: 1.99, 2.0, 5.0, 5.01, 10.0, 10.01, 15.0, and something above 15.0.
3. **Checkpoint: all tests pass, zero dependency on anything else in the project.**

### Phase 5 — Wire up the four endpoints
1. Build `POST /api/predict` exactly as specified above.
2. Build `POST /api/replicate` exactly as specified above.
3. Build `GET /api/images/{replicate_id}` with the query-param auth fallback.
4. Build `GET /api/replicates`.
5. Build `PATCH /api/replicates/{id}/status`.
6. **Checkpoint: every endpoint works correctly when tested manually via the automatic `/docs` page FastAPI generates, using fake test data, before anyone else's app touches it.**

---

## Things You Do NOT Need To Do

- You do not scale coordinates up or down between different image sizes. Android always sends exactly 1024x1024. That is the only coordinate space anywhere in this system.
- You do not implement any IoU or box-matching comparison logic. There is no metrics/accuracy feature in this MVP.
- You do not implement retry or duplicate-submission protection. Accept duplicate rows as a known possibility.
- You do not need a separate `reviews` table. Review status lives directly on the `replicates` row.
