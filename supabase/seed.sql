-- ==============================================================================
-- ZORBI — Section 6.4 Seed Profiles & Initial Test Data
-- Real demo profiles have approved=true, is_mock=false, author='zorbi-team'
-- Test/mock data have is_mock=true (purged via supabase/purge_mocks.sql)
-- Deterministic UUIDs and ON CONFLICT (id) DO NOTHING prevent duplicate entries
-- ==============================================================================

-- 1. Real approved game profiles for demo ladder (11 target columns matched)
insert into public.game_profiles (
  id,
  package_name,
  display_name,
  tracker,
  mapping,
  profile_json,
  schema_version,
  author,
  approved,
  downloads,
  is_mock
) values
(
  '10000000-0000-0000-0000-000000000001'::uuid,
  'com.kiloo.subwaysurf',
  'Subway Surfers',
  'HAND_SINGLE',
  'LANE_SWIPE',
  '{
    "schemaVersion": 1,
    "packageName": "com.kiloo.subwaysurf",
    "displayName": "Subway Surfers",
    "orientation": "PORTRAIT",
    "tracker": "HAND_SINGLE",
    "mapping": "LANE_SWIPE",
    "targets": {
      "left": { "x": 0.20, "y": 0.50 },
      "right": { "x": 0.80, "y": 0.50 },
      "center": { "x": 0.50, "y": 0.50 },
      "up": { "x": 0.50, "y": 0.20 },
      "down": { "x": 0.50, "y": 0.80 }
    },
    "params": {
      "deadzoneDeg": 8.0,
      "cooldownMs": 350,
      "swipeMinSpeed": 0.18,
      "slashMinLength": 0.15,
      "smoothing": { "minCutoff": 1.0, "beta": 0.01 }
    }
  }'::jsonb,
  1,
  'zorbi-team',
  true,
  142,
  false
),
(
  '10000000-0000-0000-0000-000000000002'::uuid,
  'com.halfbrick.fruitninjafree',
  'Fruit Ninja',
  'HAND_SINGLE',
  'SLASH',
  '{
    "schemaVersion": 1,
    "packageName": "com.halfbrick.fruitninjafree",
    "displayName": "Fruit Ninja",
    "orientation": "LANDSCAPE",
    "tracker": "HAND_SINGLE",
    "mapping": "SLASH",
    "targets": {},
    "params": {
      "deadzoneDeg": 8.0,
      "cooldownMs": 150,
      "swipeMinSpeed": 1.0,
      "slashMinLength": 0.10,
      "smoothing": { "minCutoff": 1.0, "beta": 0.01 }
    }
  }'::jsonb,
  1,
  'zorbi-team',
  true,
  98,
  false
),
(
  '10000000-0000-0000-0000-000000000003'::uuid,
  'com.fingersoft.hillclimb',
  'Hill Climb Racing',
  'HAND_DUAL_WHEEL',
  'STEER_BUTTONS',
  '{
    "schemaVersion": 1,
    "packageName": "com.fingersoft.hillclimb",
    "displayName": "Hill Climb Racing",
    "orientation": "LANDSCAPE",
    "tracker": "HAND_DUAL_WHEEL",
    "mapping": "STEER_BUTTONS",
    "targets": {
      "left": { "x": 0.15, "y": 0.85 },
      "right": { "x": 0.85, "y": 0.85 }
    },
    "params": {
      "deadzoneDeg": 10.0,
      "cooldownMs": 200,
      "swipeMinSpeed": 1.0,
      "slashMinLength": 0.15,
      "smoothing": { "minCutoff": 1.2, "beta": 0.02 }
    }
  }'::jsonb,
  1,
  'zorbi-team',
  true,
  76,
  false
)
on conflict (id) do nothing;

-- 2. Mock session & events for testing purge_mocks.sql (is_mock = true)
insert into public.sessions (
  id,
  device_id,
  device_model,
  android_version,
  game_package,
  tracker,
  perf_mode,
  avg_fps,
  p95_latency_ms,
  duration_s,
  gestures_count,
  is_mock
) values (
  '00000000-0000-0000-0000-000000000001'::uuid,
  '00000000-0000-0000-0000-000000000002'::uuid,
  'Test Mock Phone',
  34,
  'com.test.mockgame',
  'HAND_SINGLE',
  'BALANCED',
  24.5,
  68.2,
  60,
  45,
  true
) on conflict (id) do nothing;
