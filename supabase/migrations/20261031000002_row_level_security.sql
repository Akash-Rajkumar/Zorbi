-- Migration: 20261031000002_row_level_security.sql
-- Description: Enable Row Level Security (RLS), define frozen policies, and grant PostgREST role permissions
-- Frozen Reference: ZORBI Master Prompt (v1.1) Section 6.2

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

-- Schema & Table Grants for PostgREST
grant usage on schema public to anon, authenticated;

grant select, insert on public.game_profiles to anon, authenticated;
grant select, insert on public.sessions to anon, authenticated;
grant select, insert on public.gesture_events to anon, authenticated;

grant select on public.v_session_stats to anon, authenticated;
grant execute on function public.increment_profile_downloads(uuid) to anon, authenticated;

-- Strictly revoke update/delete from anonymous and authenticated roles
revoke update, delete on public.game_profiles from anon, authenticated;
revoke update, delete on public.sessions from anon, authenticated;
revoke update, delete on public.gesture_events from anon, authenticated;
