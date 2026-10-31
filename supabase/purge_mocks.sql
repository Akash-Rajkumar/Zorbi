-- ==============================================================================
-- ZORBI — Section 6.4 Mock Purge Script
-- Removes all mock rows inserted during local testing or load verification
-- ==============================================================================

delete from public.gesture_events where is_mock;
delete from public.sessions where is_mock;
delete from public.game_profiles where is_mock;
