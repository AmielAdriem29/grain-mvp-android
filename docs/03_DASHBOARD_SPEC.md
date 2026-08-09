# Agree/Disagree Dashboard — Complete Build Specification

This file contains everything needed to build the dashboard in isolation. You do not need to read any other document. Two values below are placeholders (BACKEND_BASE_URL and API_KEY) — get these from whoever builds the backend before you start Phase 2. Phase 0/1 (project setup, types) can be done immediately with no live backend.

---

## Connection Details (fill in these two values)

```
VITE_BACKEND_URL = "http://<TO BE PROVIDED>:8000"
VITE_API_KEY = "<TO BE PROVIDED>"
```

Put both in a `.env` file at your project root:
```
VITE_BACKEND_URL=http://<TO BE PROVIDED>:8000
VITE_API_KEY=<TO BE PROVIDED>
```

Access them in code via `import.meta.env.VITE_BACKEND_URL` and `import.meta.env.VITE_API_KEY`.

---

## The Three Endpoints You Call

You call exactly three endpoints. You never touch the database directly. You never call anything related to AI prediction or submission — that's the Android app's job, not yours.

### Endpoint 1 — Get All Records

```
GET {BACKEND_URL}/api/replicates
Header: X-API-Key: {API_KEY}
```

**Response you receive (JSON array):**
```json
[
  {
    "id": "uuid-string",
    "technicianName": "Juan Dela Cruz",
    "createdAt": "2026-07-26T10:00:00Z",
    "sampleId": "RC-Dinorado-004",
    "aiPredictedGrains": [
      { "x": 112, "y": 340, "width": 24, "height": 24, "confidence": 0.81, "action": null }
    ],
    "confirmedGrains": [
      { "x": 112, "y": 340, "width": 24, "height": 24, "confidence": 0.81, "action": "kept" }
    ],
    "immatureWeight": 12.45,
    "percentage": 41.5,
    "grade": "G3",
    "reviewStatus": "unreviewed"
  }
]
```

This is the full list. Render it as a table. `reviewStatus` will be one of: `"unreviewed"`, `"accepted"`, `"denied"`.

### Endpoint 2 — Get One Record's Image

```
GET {BACKEND_URL}/api/images/{replicateId}?api_key={API_KEY}
```

**Important:** this endpoint takes the API key as a URL query parameter, not a header. This is different from every other endpoint. This is because you will most likely load it directly in an `<img>` tag, and `<img>` tags cannot send custom headers.

**Usage example:**
```jsx
<img src={`${BACKEND_URL}/api/images/${replicateId}?api_key=${API_KEY}`} />
```

**Response:** raw JPEG image bytes. No JSON wrapper. It's a direct image response, exactly like any normal image URL.

### Endpoint 3 — Update Review Status

```
PATCH {BACKEND_URL}/api/replicates/{replicateId}/status
Header: X-API-Key: {API_KEY}
Header: Content-Type: application/json
Body: { "status": "accepted" }
```

`status` must be exactly the string `"accepted"` or `"denied"`.

**Response you receive (JSON):**
```json
{ "id": "uuid-string", "reviewStatus": "accepted" }
```

---

## GrainBox Shape (used inside both grain arrays)

```typescript
interface GrainBox {
  x: number;
  y: number;
  width: number;
  height: number;
  confidence: number | null;
  action: "kept" | "removed" | "added" | null;
}
```

All coordinates are in a fixed 1024x1024 pixel space. Every image you fetch from Endpoint 2 is exactly 1024x1024 pixels. You never need to scale or convert these coordinates against anything — they map directly onto the image as-is.

---

## Build Order

### Phase 0 — Setup (no backend needed)

1. Create project: `npm create vite@latest -- --template react-ts`
2. Install dependencies: `npm install @tanstack/react-query`
3. Create the `.env` file with the two placeholder values above.
4. Create a single API client file, e.g. `src/api.ts`, that centralizes the base URL and API key. Example:

```typescript
const BASE_URL = import.meta.env.VITE_BACKEND_URL;
const API_KEY = import.meta.env.VITE_API_KEY;

export async function fetchReplicates() {
  const res = await fetch(`${BASE_URL}/api/replicates`, {
    headers: { "X-API-Key": API_KEY }
  });
  if (!res.ok) throw new Error("Failed to fetch replicates");
  return res.json();
}

export function imageUrl(replicateId: string) {
  return `${BASE_URL}/api/images/${replicateId}?api_key=${API_KEY}`;
}

export async function updateStatus(replicateId: string, status: "accepted" | "denied") {
  const res = await fetch(`${BASE_URL}/api/replicates/${replicateId}/status`, {
    method: "PATCH",
    headers: {
      "X-API-Key": API_KEY,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ status })
  });
  if (!res.ok) throw new Error("Failed to update status");
  return res.json();
}
```

### Phase 1 — Types

Create `src/types.ts`:

```typescript
export interface GrainBox {
  x: number;
  y: number;
  width: number;
  height: number;
  confidence: number | null;
  action: "kept" | "removed" | "added" | null;
}

export interface Replicate {
  id: string;
  technicianName: string;
  createdAt: string;
  sampleId: string;
  aiPredictedGrains: GrainBox[];
  confirmedGrains: GrainBox[];
  immatureWeight: number;
  percentage: number;
  grade: string;
  reviewStatus: "unreviewed" | "accepted" | "denied";
}
```

### Phase 2 — Record List (needs live backend)

1. Set up `QueryClientProvider` at your app's root, per standard TanStack Query setup.
2. Build a component using `useQuery` calling `fetchReplicates()`.
3. Render a table with columns: sampleId, technicianName, createdAt, grade, reviewStatus.
4. Make each row clickable — either navigate to a detail view (if using React Router) or expand inline, your choice, since this dashboard is simple enough that either works.
5. Show a loading message while `isLoading`, and an error message if the query fails.
6. **Checkpoint: the table shows real data from the database.**

### Phase 3 — Comparison View

For a selected record:

1. Fetch the image using the `imageUrl()` helper directly in two `<img>` tags, OR draw it onto two `<canvas>` elements if you want to overlay boxes on top of the image itself (canvas is the better choice here, since you need to draw rectangles on top of the image, not just show the image alone).

2. For the canvas approach, write one shared drawing function:

```typescript
function drawGrainOverlay(
  canvas: HTMLCanvasElement,
  image: HTMLImageElement,
  grains: GrainBox[],
  color: string
) {
  const ctx = canvas.getContext("2d")!;
  canvas.width = image.width;
  canvas.height = image.height;
  ctx.drawImage(image, 0, 0);
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  for (const box of grains) {
    ctx.strokeRect(box.x, box.y, box.width, box.height);
  }
}
```

3. Load the image once (e.g., via a hidden `<img>` element or `new Image()`), then call `drawGrainOverlay` twice: once with `aiPredictedGrains` in one color (e.g. blue), once with `confirmedGrains` in a different color (e.g. green), onto two separate canvas elements.
4. Place both canvases side by side using basic CSS flexbox.
5. Since there is no zoom, wrap each canvas in a container with `overflow: auto` in case the 1024x1024 image doesn't fully fit the screen.
6. **Checkpoint: you can see both the AI's original boxes and the technician's corrected boxes, clearly, side by side, for one record.**

### Phase 4 — Agree / Disagree Buttons

1. Add two buttons: "Agree" and "Disagree."
2. Use `useMutation` from TanStack Query, calling `updateStatus(replicateId, "accepted")` or `updateStatus(replicateId, "denied")`.
3. Disable both buttons while the mutation is in flight.
4. On success, either refetch the list query (`queryClient.invalidateQueries`) or update the local cache directly, so the change is visible immediately without a manual page reload.
5. **Checkpoint: clicking a button updates the backend, and the record's status visibly changes in the UI.**

### Phase 5 — Polish

1. Wrap the app in a basic React error boundary.
2. Show a clear message if the record list is empty (no submissions yet).
3. If an image fails to load, show a placeholder message rather than a broken image icon.

---

## Things You Do NOT Need To Do

- You do not implement zoom or pan on the images. Fixed-size display with scroll is enough.
- You do not implement any metrics, accuracy, precision, or recall calculations. This feature does not exist in this MVP.
- You do not implement a login system. The API key is a single hardcoded value for everyone.
- You do not call any AI prediction or submission endpoints. Those belong only to the Android app.
