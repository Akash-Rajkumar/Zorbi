# ZORBI — Backend, Tools, CI & QA Specification (`:data`, `supabase/`, `tools/`)

> **Author:** Person 4 (Backend, Tools, CI, QA, Docs & Demo)
> **Master Prompt Authority:** `ZORBI_MASTER_PROMPT_v1_1.md.pdf` (Sections 4.4, 6, 7.1, 8, 9, 11)
> **Module Ownership:** `data/`, `supabase/`, `web/dashboard/`, `tools/`, `fixtures/`, `scripts/`, `.github/`, `docs/`, `README.md`

---

## 1. Supabase Project Architecture & Setup

Zorbi uses a free-tier Supabase project providing PostgreSQL + PostgREST + Realtime. No Edge Functions are required. All Android client network communication operates via plain REST over OkHttp (no heavy client SDKs) and must function gracefully in offline mode.

### 1.1 Credential & Configuration Management
- Keys must **never** be checked into version control.
- App runtime configuration reads keys from `local.properties` via `BuildConfig`:
  - `SUPABASE_URL=https://<project-ref>.supabase.co`
  - `SUPABASE_ANON_KEY=eyJhbGciOi...`
- Web Dashboard (`web/dashboard/index.html`) reads from `web/dashboard/config.js` (git-ignored; `config.example.js` provided).
- Safe local testing reads `$env:SUPABASE_URL` and `$env:SUPABASE_ANON_KEY`.

---

## 2. Frozen Database Schema (Section 6.1)

The complete schema is maintained canonically in [`supabase/schema.sql`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/schema.sql) and partitioned into versioned migrations under [`supabase/migrations/`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/migrations/).

### 2.1 Table: `public.game_profiles`
Stores community-submitted and pre-bundled game configuration profiles.
- `id`: `uuid primary key default gen_random_uuid()`
- `package_name`: `text not null`
- `display_name`: `text not null`
- `tracker`: `text not null check (tracker in ('HAND_SINGLE','HAND_DUAL_WHEEL','POSE_LITE'))`
- `mapping`: `text not null check (mapping in ('STEER_BUTTONS','STEER_DRAG','LANE_SWIPE','SLASH'))`
- `profile_json`: `jsonb not null`
- `schema_version`: `int not null default 1`
- `author`: `text not null default 'zorbi-team'`
- `approved`: `boolean not null default false`
- `downloads`: `int not null default 0`
- `is_mock`: `boolean not null default false`
- `created_at`: `timestamptz not null default now()`
- **Index:** `idx_game_profiles_package_name on public.game_profiles (package_name)`

### 2.2 Table: `public.sessions`
Records opt-in telemetry sessions from user devices.
- `id`: `uuid primary key default gen_random_uuid()`
- `device_id`: `uuid not null` (generated once, stored in DataStore)
- `device_model`: `text`
- `android_version`: `int`
- `game_package`: `text`
- `tracker`: `text not null`
- `perf_mode`: `text not null`
- `avg_fps`: `real`
- `p95_latency_ms`: `real`
- `duration_s`: `int`
- `gestures_count`: `int`
- `is_mock`: `boolean not null default false`
- `created_at`: `timestamptz not null default now()`

### 2.3 Table: `public.gesture_events`
Streams batched gesture detection events for real-time live demo dashboards.
- `id`: `bigint generated always as identity primary key`
- `session_id`: `uuid not null references public.sessions(id) on delete cascade`
- `event_type`: `text not null check (event_type in ('STEER','SWIPE','SLASH','PAUSE'))`
- `magnitude`: `real`
- `is_mock`: `boolean not null default false`
- `created_at`: `timestamptz not null default now()`

### 2.4 View: `public.v_session_stats`
Aggregates performance statistics by device, tracker, and performance mode, strictly excluding mock records:
```sql
create or replace view public.v_session_stats as
select
  device_model,
  tracker,
  perf_mode,
  count(*) as sessions,
  avg(avg_fps) as avg_fps,
  avg(p95_latency_ms) as avg_p95_latency_ms
from public.sessions
where is_mock = false
group by 1, 2, 3;
```

### 2.5 Stored Procedure: `public.increment_profile_downloads`
Increments download counters on approved profiles without granting general update privileges. Hardened with `SET search_path = ''` and fully-qualified schema references to prevent search-path hijacking:
```sql
create or replace function public.increment_profile_downloads(profile_id uuid) returns void
language sql security definer
set search_path = ''
as
$$
  update public.game_profiles
  set downloads = downloads + 1
  where id = profile_id and approved;
$$;
```

---

## 3. Row Level Security & Access Grants (Section 6.2)

RLS is enabled on all tables with strict principle-of-least-privilege policies:

| Table | Operation | Role | Policy / Grant Condition |
| :--- | :--- | :--- | :--- |
| `game_profiles` | `SELECT` | `anon` | `using (approved)` |
| `game_profiles` | `INSERT` | `anon` | `with check (approved = false)` |
| `game_profiles` | `UPDATE`/`DELETE` | `anon` | **DENIED** (No policy; grants revoked) |
| `sessions` | `INSERT` | `anon` | `with check (true)` |
| `sessions` | `SELECT` | `anon` | `using (true)` |
| `gesture_events` | `INSERT` | `anon` | `with check (true)` |
| `gesture_events` | `SELECT` | `anon` | `using (true)` |
| `v_session_stats` | `SELECT` | `anon` | Allowed (read-only view) |
| `increment_profile_downloads` | `EXECUTE` | `anon` | Allowed (`security definer`) |

---

## 4. Supabase Realtime Configuration (Section 6.2 & 6.3 R8)

To support the live projector dashboard (`web/dashboard/index.html`):
1. The table `public.gesture_events` is added to the `supabase_realtime` publication:
   ```sql
   alter publication supabase_realtime add table public.gesture_events;
   ```
2. The dashboard subscribes to postgres change events on channel `public:gesture_events`:
   ```javascript
   supabase
     .channel('live-gestures')
     .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'gesture_events' }, payload => {
       renderLiveGesture(payload.new);
     })
     .subscribe();
   ```

---

## 5. Frozen REST API Reference (Section 6.3)

Base URL: `https://<project-ref>.supabase.co`
Required Headers:
- `apikey: <anon_key>`
- `Authorization: Bearer <anon_key>`
- `Content-Type: application/json`

| ID | Method | Endpoint / Query | Purpose | Used By |
|---|---|---|---|---|
| **R1** | `GET` | `/rest/v1/game_profiles?approved=eq.true&select=*&order=downloads.desc` | Community profile catalog | App (Community), Dashboard |
| **R2** | `GET` | `/rest/v1/game_profiles?package_name=eq.<pkg>&approved=eq.true&select=*` | Look up profile for installed game | App |
| **R3** | `POST` | `/rest/v1/game_profiles` | Submit unapproved community profile | App |
| **R4** | `POST` | `/rest/v1/rpc/increment_profile_downloads` | Increment profile download counter | App |
| **R5** | `POST` | `/rest/v1/sessions` (Header `Prefer: return=representation`) | Log telemetry session | App (Opt-in) |
| **R6** | `POST` | `/rest/v1/gesture_events` | Stream live gesture batches (<=5/s) | App (Opt-in) |
| **R7** | `GET` | `/rest/v1/v_session_stats?select=*` | Fetch device performance aggregations | Dashboard |
| **R8** | `WS` | `/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0` | Realtime INSERT subscription | Dashboard |

---

## 6. Migration & Deployment Instructions

### 6.1 Hosted Deployment via Supabase Dashboard SQL Editor
1. Log into [Supabase Dashboard](https://supabase.com/dashboard).
2. Open the project SQL Editor.
3. Paste and execute [`supabase/schema.sql`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/schema.sql).
4. Paste and execute [`supabase/seed.sql`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/seed.sql) to populate initial approved profiles with deterministic UUIDs (`10000000-0000-0000-0000-000000000001` through `...0003`).
5. In **Database -> Replication**, confirm `gesture_events` shows as enabled under `supabase_realtime`.

### 6.2 Running Mock Purge
To clean mock telemetry or temporary test entries prior to integration gates:
Run [`supabase/purge_mocks.sql`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/purge_mocks.sql) in SQL Editor or via CLI.

---

## 7. Verification Suite (Gate T-P4-1)

Automated testing is implemented in [`supabase/verify_api.ps1`](file:///C:/Users/Lenovo/.gemini/antigravity/scratch/Zorbi/supabase/verify_api.ps1).

### Running Tests:
```powershell
$env:SUPABASE_URL = "https://your-project.supabase.co"
$env:SUPABASE_ANON_KEY = "eyJhbGciOi..."
.\supabase\verify_api.ps1
```

### Verification Checks:
1. **R1 / R2:** Validates only profiles with `approved = true` are returned.
2. **R3:** Submits unapproved profile (`approved = false`) and confirms 201 Created.
3. **R4:** Invokes `increment_profile_downloads` RPC and verifies download count increments by exactly 1.
4. **SEC-01 (Security Boundary):** Submits `approved = true` using `anon` key; confirms request is rejected by RLS.
5. **SEC-02 (Security Boundary):** Attempts `PATCH` on `game_profiles`; confirms rejected / no updates allowed.
6. **SEC-03 (Security Boundary):** Attempts `DELETE` on `game_profiles`; confirms rejected / no deletes allowed.
7. **R5 / R6:** Inserts telemetry session and batched gesture events.
8. **R7:** Confirms aggregate view returns 200 without exposing mock data.
9. **R8:** Connects via `System.Net.WebSockets.ClientWebSocket`, joins `realtime:public:gesture_events`, subscribes to `INSERT` events, triggers an event, and verifies payload delivery over the live socket.

---

## 8. Status, Current Limitations & Blockers
- **SQL Artifacts & Repeatable Migrations:** **COMPLETE & HARDENED**.
- **Test Automation Script (R1–R8 + SEC-01..04):** **COMPLETE & LOCALLY VALIDATED**.
- **Execution State:** All live network tests are currently **NOT RUN** awaiting allocation of hosted Supabase credentials by the account owner (Open Question Q8).
- **Next Task:** `P4-2` (GitHub Actions CI workflow for Android debug/release builds).
