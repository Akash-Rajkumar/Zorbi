-- Migration: 20261031000003_realtime.sql
-- Description: Add public.gesture_events to supabase_realtime publication
-- Frozen Reference: ZORBI Master Prompt (v1.1) Section 6.2 & 6.3 (R8)

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
