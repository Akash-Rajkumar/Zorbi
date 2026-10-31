-- ==============================================================================
-- ZORBI — Section 6.4 Mock Purge Script
-- Removes all mock rows inserted during local testing or load verification.
-- Strictly scoped to is_mock = true records to preserve legitimate data.
-- ==============================================================================

begin;

-- Delete mock gesture events first to respect session foreign key relationships
delete from public.gesture_events
where is_mock = true;

-- Delete mock telemetry sessions
delete from public.sessions
where is_mock = true;

-- Delete mock game profiles
delete from public.game_profiles
where is_mock = true;

commit;
