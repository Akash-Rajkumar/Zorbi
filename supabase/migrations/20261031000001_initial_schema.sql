-- Migration: 20261031000001_initial_schema.sql
-- Description: Core tables, constraints, index, session stats view, and download counter RPC function
-- Frozen Reference: ZORBI Master Prompt (v1.1) Section 6.1

create extension if not exists pgcrypto;

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

create table if not exists public.gesture_events (
  id bigint generated always as identity primary key,
  session_id uuid not null references public.sessions(id) on delete cascade,
  event_type text not null check (event_type in ('STEER','SWIPE','SLASH','PAUSE')),
  magnitude real,
  is_mock boolean not null default false,
  created_at timestamptz not null default now()
);

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

create or replace function public.increment_profile_downloads(profile_id uuid) returns void
language sql security definer as
$$ 
  update public.game_profiles 
  set downloads = downloads + 1 
  where id = profile_id and approved;
$$;
