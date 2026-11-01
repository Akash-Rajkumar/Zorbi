# Zorbi Live Projector Dashboard

A high-contrast, projector-optimized operational dashboard for monitoring real-time motion gaming telemetry during Zorbi live demonstration sessions.

---

## 1. Purpose

The Zorbi Live Projector Dashboard provides audience and operator visibility into live motion gameplay. Designed specifically for 1080p+ projector displays in dark venues, it visualizes:
- **Session Totals & Frequency (R7):** Cumulative sessions and performance metrics aggregated from Supabase PostgREST.
- **Real-Time Gesture Distribution (R8):** Relative proportions and counts of player actions across the four canonical gesture types: `STEER`, `SWIPE`, `SLASH`, and `PAUSE`.
- **Live Stream Ticker (R8):** Sub-second latency feed of incoming gesture events, showing gesture type, magnitude, and active session linkage (bounded to 100 rows).
- **Stream Velocity (R8):** Dynamic gestures-per-minute (gpm) velocity calculated over a rolling 60-second sliding window.
- **System Health:** Backend connectivity, data source indicators, socket heartbeat status, and error states.

---

## 2. Directory Structure

```
web/dashboard/
├── .gitignore            # Ignores local `config.json` to safeguard API credentials
├── README.md             # Dashboard documentation and developer quickstart guide
├── app.js                # State machine, WebSocket client, PostgREST poller, and DOM renderer
├── config.example.json   # Configuration template for Supabase credentials (JSON)
├── index.html            # Semantic HTML5 layout, accessible headings, and grid structure
└── style.css             # High-contrast projector theme (Dark / Neon-Lime) and responsive CSS Grid
```

---

## 3. Browser Requirements

The dashboard is built entirely with native web standards (HTML5, CSS3, ES6 JavaScript) with **zero external build tools or runtime framework dependencies**.

Supported browsers:
- Google Chrome / Chromium 90+
- Mozilla Firefox 88+
- Microsoft Edge 90+
- Apple Safari 14+

Minimum screen resolution: 1280x720 (optimized for 1920x1080 and 4K projector displays).

---

## 4. Local Configuration

1. Copy the example JSON configuration template to create your local `config.json`:
   ```bash
   cp web/dashboard/config.example.json web/dashboard/config.json
   ```
2. Open `web/dashboard/config.json` and specify your Supabase project parameters:
   ```json
   {
     "SUPABASE_URL": "https://your-supabase-project.supabase.co",
     "SUPABASE_ANON_KEY": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   }
   ```
3. **URL Validation Rules:**
   - Production/remote URLs must strictly use `https://`.
   - Local development URLs may use `http://localhost:<port>` or `http://127.0.0.1:<port>`.

---

## 5. Starting a Local HTTP Server

Because browsers enforce Cross-Origin / Security policies on fetch and WebSockets over `file://`, serve the dashboard using any local HTTP server:

### Using Python 3:
```bash
python -m http.server 8080 --directory web/dashboard
```

### Using Node.js (npx):
```bash
npx serve web/dashboard -p 8080
```

---

## 6. Accessing the Dashboard

Open your browser and navigate to:
```
http://localhost:8080
```

- When `config.json` is absent or unconfigured, the dashboard displays a clean **CONFIGURATION REQUIRED** state with setup guidance.
- When valid configuration is provided, the dashboard initializes both R7 PostgREST polling and the R8 native WebSocket connection.

---

## 7. Credential & Security Model

- **Public Anon Key Only:** The dashboard must ONLY be configured with the Supabase public `anon` key.
- **Forbidden Secrets:** Never input or commit the Supabase `service_role` key, database credentials, or private access tokens.
- **Git Protection:** `web/dashboard/.gitignore` prevents `config.json` and `config.js` from being tracked or committed to Git.
- **Row-Level Security (RLS):** All data consumed by the dashboard is strictly governed by Supabase RLS policies on the `public` schema.
- **Safe DOM Manipulation:** Strictly zero `innerHTML`, `outerHTML`, or `document.write` calls. All dynamic values are escaped and rendered using `textContent` and `document.createElement`.

---

## 8. Telemetry Architecture

The dashboard integrates two independent data streams:

```
+-----------------------------------------------------------------------------------+
|                           Zorbi Web Live Dashboard                                |
|                                                                                   |
|  +-------------------------------------+   +------------------------------------+ |
|  |     R7 PostgREST Statistics         |   |     R8 Supabase Realtime WS        | |
|  |   GET /rest/v1/v_session_stats      |   |   WSS /realtime/v1/websocket       | |
|  |   - 10s polling interval            |   |   - Channel: public:gesture_events | |
|  |   - Total non-mock sessions         |   |   - Event: INSERT broadcast        | |
|  |   - Session-weighted avg FPS        |   |   - Live activity feed (<= 100)    | |
|  |   - Session-weighted P95 latency    |   |   - Distribution & Velocity        | |
|  |   - Page Visibility pause/resume    |   |   - 30s Phoenix heartbeat         | |
|  |   - In-flight overlap guard         |   |   - Deduplication via record.id    | |
|  +-------------------------------------+   +------------------------------------+ |
+-----------------------------------------------------------------------------------+
```

### R7 PostgREST Integration
- Endpoint: `GET /rest/v1/v_session_stats?select=*`
- Headers: `apikey: <anonKey>`, `Authorization: Bearer <anonKey>`
- Polling cadence: Nominal 10 seconds.
- Metric Calculations:
  - **Total Sessions:** Sum of all recorded non-mock sessions (`SUM(sessions)`).
  - **Average Engine FPS:** Session-weighted average framerate across all reporting sessions:
    $$\text{Weighted Avg FPS} = \frac{\sum (\text{sessions} \times \text{avg\_fps})}{\sum \text{sessions}}$$
  - **Average Session P95 Latency:** Session-weighted average of grouped average P95 latency values:
    $$\text{Weighted Avg P95 Latency} = \frac{\sum (\text{sessions} \times \text{avg\_p95\_latency\_ms})}{\sum \text{sessions}}$$

### R8 Supabase Realtime WebSocket Integration
- **Endpoint:** `wss://<host>/realtime/v1/websocket?apikey=<anonKey>&vsn=1.0.0`
- **Topic:** `realtime:public:gesture_events`
- **Join Handshake:**
  - On WebSocket `open`, client transmits a Phoenix join message:
    ```json
    {
      "topic": "realtime:public:gesture_events",
      "event": "phx_join",
      "payload": {
        "config": {
          "postgres_changes": [
            { "event": "INSERT", "schema": "public", "table": "gesture_events" }
          ]
        }
      },
      "ref": "1"
    }
    ```
  - State transitions to `CONNECTED` **only** upon receiving a strictly correlated server reply:
    `{ topic: 'realtime:public:gesture_events', event: 'phx_reply', ref: joinRef, payload: { status: 'ok' } }`.
    Heartbeat replies, wrong-topic replies, or replies from previous socket generations are strictly rejected.
- **Heartbeat:** Transmits `{ topic: "phoenix", event: "heartbeat", payload: {}, ref: "<uuid>" }` every 30 seconds to maintain alive status and prevent reverse proxy timeouts. Heartbeat replies never alter channel state.
- **Strict Event Handling & Validation:**
  - Listens strictly for `{ topic: "realtime:public:gesture_events", event: "postgres_changes", payload: { data: { type: "INSERT", schema: "public", table: "gesture_events", record: { ... } } } }`.
  - Missing or mismatched `schema`, `table`, or `type` fields cause immediate event rejection.
  - **Mock Filtering (R8-02):** Events must have `record.is_mock === false` strictly; mock events are discarded without altering feed, stats, velocity, or active session.
  - **Timestamp Validation (R8-03):** `record.created_at` must be a non-empty string parsing into a valid `Date`. Malformed timestamps are rejected with zero browser-fallback fabrication.
  - **Field Types (R8-04):** `record.id` must represent a positive integer (bigint compatible); `record.session_id` must be a valid UUID; `record.magnitude` must be `null` or a finite number.
- **Primary Key Deduplication:** Events are deduplicated using `record.id` through an in-memory cache (bounded to 500 recent IDs) to avoid duplicate counts on reconnect.
- **Live Feed Rendering:**
  - Feed table prepends new events up to a bounded maximum of 100 rows.
  - Formats timestamp using the validated database `created_at`.
  - Validates gesture types against whitelist (`STEER`, `SWIPE`, `SLASH`, `PAUSE`), sanitizing unknown types to `UNKNOWN`.
  - Formats `magnitude` (2 decimal places) with non-finite / null guard.
- **Stream Velocity:**
  - Calculates events per minute over a sliding 60-second window based on validated database event timestamps.
- **Resilience & Reconnection:**
  - Unexpected disconnects transition status to `RECONNECTING`.
  - Exponential backoff with jitter: $T = \min(30000, 1000 \times 1.5^{\text{attempts}}) + \text{rand}(0, 1000)$ ms.
  - Re-issues `phx_join` on every newly opened connection with incremented socket generation.
  - Page unload (`beforeunload` / `pagehide`) initiates idempotent teardown: closes socket cleanly, cancels heartbeat and reconnect timers, and sets state to `DISCONNECTED`.

---

## 9. Connection State Machine

The dashboard tracks connection state across five formal states:

| State | Status Pill Style | Display Text | Trigger |
| :--- | :--- | :--- | :--- |
| `CONFIG_REQUIRED` | `status-pill--config-required` | `CONFIGURATION REQUIRED` | Missing or invalid `config.json` |
| `DISCONNECTED` | `status-pill--disconnected` | `DISCONNECTED` | Initial valid state or explicit page unload |
| `CONNECTING` | `status-pill--connecting` | `CONNECTING...` | Socket opening or awaiting correlated Phoenix join ACK |
| `CONNECTED` | `status-pill--connected` | `CONNECTED` | Received correlated `phx_reply` with `status: "ok"` |
| `RECONNECTING` | `status-pill--reconnecting` | `RECONNECTING...` | Socket error or unexpected close |

---

## 10. Verification & Automated Testing

Automated test suites verify end-to-end functionality without requiring live cloud infrastructure:

- **Stage 3 Realtime & Remediation Test Suite (66 tests):**
  ```bash
  node scratch/test_stage3_r8.js
  ```
  Verifies 36 baseline Stage 3 tests and 30 remediation tests covering strict join acknowledgement correlation (R8-01), mock event exclusion (R8-02), strict database timestamp validation (R8-03), and Postgres INSERT envelope validation (R8-04).

- **Stage 2 Regression Test Suite (27 tests):**
  ```bash
  node scratch/test_stage2_r7.js
  ```
  Verifies PostgREST JSON loading, credential sanitization, session-weighted formulas, and polling lifecycle.

- **Scaffolding Static Check:**
  ```bash
  python scratch/test_dashboard_scaffold.py
  ```
  Verifies zero `innerHTML` usage, DOM ID parity, and CSS responsive typography.

---

## 11. Troubleshooting

1. **Dashboard shows "CONFIGURATION REQUIRED":**
   - Ensure `web/dashboard/config.json` exists and is readable by the HTTP server.
   - Verify that placeholder strings (`your-project.supabase.co`, `your-anon-key-here`) have been replaced.
   - Ensure the Supabase URL starts with `https://` (or `http://localhost` for local dev).

2. **Dashboard stuck on "CONNECTING...":**
   - Verify network connectivity to your Supabase host.
   - Check browser console for WebSocket handshake rejections (HTTP 401/403).
   - Ensure the Supabase Realtime service is enabled on table `public.gesture_events` (`ALTER PUBLICATION supabase_realtime ADD TABLE public.gesture_events;`).

3. **Feed not updating during gameplay:**
   - Confirm your Android client or test harness is inserting records into `public.gesture_events`.
   - Verify that Supabase RLS policies permit `SELECT` operations on `public.gesture_events` for the `anon` role.
