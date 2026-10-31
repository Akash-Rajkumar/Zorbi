-- ==============================================================================
-- ZORBI — Section 6 Frozen Supabase Schema, RLS, Grants & Realtime
-- Single Source of Truth: ZORBI Master Prompt (v1.1), Section 6
-- Owned by: Person 4 (Backend, Tools, CI, QA, Docs & Demo)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. EXTENSIONS
-- ------------------------------------------------------------------------------
create extension if not exists pgcrypto;

-- ------------------------------------------------------------------------------
-- 2. TABLES & INDEXES
-- ------------------------------------------------------------------------------

-- Table: public.game_profiles (Section 6.1)
create table if not exists public.game_profiles (
  id uuid primary key default gen_random_uuid(),
  package_name text not null,
  display_name text not null,
  tracker text not null check (tracker in ('HAND_SINGLE','HAND_DUAL_WHEEL','POSE_LITE')),
  mapping text not null check (mapping in ('STEER_BUTTONS','STEER_DRAG','LANE_SWIPE','SLASH')),
  profile_json jsonb not null,
  schema_version int not null default 1,
  author text not null default 'zorbi-team',
  approved boolean not null default false,
  downloads int not null default 0,
  is_mock boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_game_profiles_package_name on public.game_profiles (package_name);

-- Table: public.sessions (Section 6.1)
create table if not exists public.sessions (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null,
  device_model text,
  android_version int,
  game_package text,
  tracker text not null,
  perf_mode text not null,
  avg_fps real,
  p95_latency_ms real,
  duration_s int,
  gestures_count int,
  is_mock boolean not null default false,
  created_at timestamptz not null default now()
);

-- Table: public.gesture_events (Section 6.1)
create table if not exists public.gesture_events (
  id bigint generated always as identity primary key,
  session_id uuid not null references public.sessions(id) on delete cascade,
  event_type text not null check (event_type in ('STEER','SWIPE','SLASH','PAUSE')),
  magnitude real,
  is_mock boolean not null default false,
  created_at timestamptz not null default now()
);

-- ------------------------------------------------------------------------------
-- 3. VIEWS & FUNCTIONS
-- ------------------------------------------------------------------------------

-- View: public.v_session_stats (Section 6.1)
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

-- Function: public.increment_profile_downloads (Section 6.1)
create or replace function public.increment_profile_downloads(profile_id uuid) returns void
language sql security definer as
$$ 
  update public.game_profiles 
  set downloads = downloads + 1 
  where id = profile_id and approved;
$$;

-- ------------------------------------------------------------------------------
-- 4. ROW LEVEL SECURITY (Section 6.2)
-- ------------------------------------------------------------------------------

alter table public.game_profiles enable row level security;
alter table public.sessions enable row level security;
alter table public.gesture_events enable row level security;

-- Policies for public.game_profiles
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'game_profiles' and policyname = 'read approved profiles') then
    create policy "read approved profiles" on public.game_profiles for select to anon using (approved);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'game_profiles' and policyname = 'submit profile (unapproved)') then
    create policy "submit profile (unapproved)" on public.game_profiles for insert to anon with check (approved = false);
  end if;
end $$;

-- Policies for public.sessions
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'sessions' and policyname = 'insert sessions') then
    create policy "insert sessions" on public.sessions for insert to anon with check (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'sessions' and policyname = 'read sessions') then
    create policy "read sessions" on public.sessions for select to anon using (true);
  end if;
end $$;

-- Policies for public.gesture_events
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'gesture_events' and policyname = 'insert events') then
    create policy "insert events" on public.gesture_events for insert to anon with check (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'gesture_events' and policyname = 'read events') then
    create policy "read events" on public.gesture_events for select to anon using (true);
  end if;
end $$;

-- ------------------------------------------------------------------------------
-- 5. GRANTS (PostgREST Role Permissions)
-- ------------------------------------------------------------------------------

grant usage on schema public to anon, authenticated;

grant select, insert on public.game_profiles to anon, authenticated;
grant select, insert on public.sessions to anon, authenticated;
grant select, insert on public.gesture_events to anon, authenticated;

grant select on public.v_session_stats to anon, authenticated;
grant execute on function public.increment_profile_downloads(uuid) to anon, authenticated;

-- Strictly NO update/delete grants for anon or authenticated roles
revoke update, delete on public.game_profiles from anon, authenticated;
revoke update, delete on public.sessions from anon, authenticated;
revoke update, delete on public.gesture_events from anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. REALTIME REPLICATION (Section 6.2 & 6.3 R8)
-- ------------------------------------------------------------------------------

-- Ensure gesture_events table is added to supabase_realtime publication
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    if not exists (
      select 1 from pg_publication_tables 
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'gesture_events'
    ) then
      alter publication supabase_realtime add table public.gesture_events;
    end if;
  end if;
end $$;
