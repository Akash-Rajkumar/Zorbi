/**
 * Zorbi Live Projector Dashboard — Application Logic
 * Stage 3: Native Supabase Realtime WebSocket Integration (R8) & R7 PostgREST
 *
 * Implements:
 * - R7 Endpoint: GET /rest/v1/v_session_stats?select=*
 * - Safe Session-Weighted Metric Aggregation (Total Sessions, Avg FPS, Avg P95 Latency)
 * - R8 Realtime WebSocket: Native WebSocket connection to /realtime/v1/websocket
 * - Phoenix Protocol: phx_join to realtime:public:gesture_events with postgres_changes filter
 * - Protocol-Correct Heartbeat (topic: "phoenix", event: "heartbeat") at 30s intervals
 * - Live Gesture Activity Feed & Distribution Counter Updates from postgres_changes INSERTs
 * - Primary Key Event Deduplication (record.id) & Sliding 60s Stream Velocity Window
 * - Bounded Exponential Backoff with Jitter for Socket Reconnections
 * - Idempotent Teardown on beforeunload / pagehide
 * - Decoupled R7 Polling and R8 Realtime Lifecycles
 */
(function () {
  'use strict';

  // ---------------------------------------------------------------------------
  // 1. Connection State Enumeration & Constants
  // ---------------------------------------------------------------------------
  const ConnectionState = Object.freeze({
    CONFIG_REQUIRED: 'CONFIG_REQUIRED',
    CONNECTING: 'CONNECTING',
    CONNECTED: 'CONNECTED',
    RECONNECTING: 'RECONNECTING',
    DISCONNECTED: 'DISCONNECTED'
  });

  const GESTURE_TYPES = Object.freeze(['STEER', 'SWIPE', 'SLASH', 'PAUSE']);
  const MAX_FEED_ENTRIES = 100;
  const DEFAULT_REFRESH_INTERVAL_MS = 10000;
  const BASE_BACKOFF_DELAY_MS = 5000;
  const MAX_BACKOFF_DELAY_MS = 60000;
  const REQUEST_TIMEOUT_MS = 10000;

  // Realtime Protocol Constants
  const REALTIME_TOPIC = 'realtime:public:gesture_events';
  const HEARTBEAT_INTERVAL_MS = 30000;
  const HEARTBEAT_ACK_TIMEOUT_MS = 15000; // R8-06: 15s deadline for heartbeat reply
  const JOIN_TIMEOUT_MS = 10000; // R8-05: 10s deadline for join acknowledgement
  const RECONNECT_BASE_DELAY_MS = 1000;
  const RECONNECT_MAX_DELAY_MS = 30000;
  const RECONNECT_BACKOFF_FACTOR = 1.5;
  const MAX_SEEN_EVENT_IDS = 500;
  const VELOCITY_WINDOW_MS = 60000; // 60 seconds rolling window
  const PG_BIGINT_MAX = '9223372036854775807';
  const ISO_TIMESTAMP_REGEX = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])$/i;

  // ---------------------------------------------------------------------------
  // Data Integrity Validators (H-01 BigInt Safety & H-02 Strict Timestamp)
  // ---------------------------------------------------------------------------
  function parseBigIntId(rawId) {
    if (typeof rawId === 'number') {
      if (Number.isSafeInteger(rawId) && rawId > 0) {
        return String(rawId);
      }
      return null;
    }
    if (typeof rawId === 'string') {
      const trimmed = rawId.trim();
      if (!/^[1-9]\d*$/.test(trimmed)) {
        return null;
      }
      if (trimmed.length > PG_BIGINT_MAX.length) {
        return null;
      }
      if (trimmed.length === PG_BIGINT_MAX.length && trimmed > PG_BIGINT_MAX) {
        return null;
      }
      return trimmed;
    }
    return null;
  }

  function parseStrictIsoTimestamp(str) {
    if (typeof str !== 'string' || !str.trim()) {
      return null;
    }
    const trimmed = str.trim();
    const match = ISO_TIMESTAMP_REGEX.exec(trimmed);
    if (!match) {
      return null;
    }

    const year = parseInt(match[1], 10);
    const month = parseInt(match[2], 10);
    const day = parseInt(match[3], 10);
    const hour = parseInt(match[4], 10);
    const minute = parseInt(match[5], 10);
    const second = parseInt(match[6], 10);

    if (month < 1 || month > 12) return null;
    if (hour < 0 || hour > 23) return null;
    if (minute < 0 || minute > 59) return null;
    if (second < 0 || second > 59) return null;

    const isLeap = (year % 4 === 0 && year % 100 !== 0) || (year % 400 === 0);
    const daysInMonth = [31, isLeap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (day < 1 || day > daysInMonth[month - 1]) return null;

    const date = new Date(trimmed);
    if (isNaN(date.getTime())) {
      return null;
    }
    return date;
  }

  const IMF_FIXDATE_REGEX = /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), ([0-3][0-9]) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) (\d{4}) ([0-2][0-9]):([0-5][0-9]):([0-5][0-9]) GMT$/;
  const MONTH_MAP = Object.freeze({
    Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5,
    Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11
  });
  const WEEKDAYS = Object.freeze(['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']);

  function parseRetryAfter(headerValue) {
    if (typeof headerValue !== 'string' || !headerValue.trim()) {
      return null;
    }
    const trimmed = headerValue.trim();

    // 1. Delta-seconds form: non-negative integer digits only
    if (/^\d+$/.test(trimmed)) {
      const seconds = Number(trimmed);
      if (Number.isSafeInteger(seconds) && seconds >= 0) {
        const maxSafeSeconds = Math.floor(Number.MAX_SAFE_INTEGER / 1000);
        if (seconds > maxSafeSeconds) {
          return Number.MAX_SAFE_INTEGER;
        }
        return seconds * 1000;
      }
      return null;
    }

    // 2. Strict IMF-fixdate form (RFC 7231 / RFC 9110)
    const match = IMF_FIXDATE_REGEX.exec(trimmed);
    if (!match) {
      return null;
    }

    const dayName = match[1];
    const day = parseInt(match[2], 10);
    const monthIndex = MONTH_MAP[match[3]];
    const year = parseInt(match[4], 10);
    const hour = parseInt(match[5], 10);
    const minute = parseInt(match[6], 10);
    const second = parseInt(match[7], 10);

    if (hour > 23 || minute > 59 || second > 59) {
      return null;
    }

    const d = new Date(Date.UTC(year, monthIndex, day, hour, minute, second));
    if (
      d.getUTCFullYear() !== year ||
      d.getUTCMonth() !== monthIndex ||
      d.getUTCDate() !== day ||
      d.getUTCHours() !== hour ||
      d.getUTCMinutes() !== minute ||
      d.getUTCSeconds() !== second
    ) {
      return null;
    }

    if (WEEKDAYS[d.getUTCDay()] !== dayName) {
      return null;
    }

    const delayMs = d.getTime() - Date.now();
    if (delayMs <= 0) return 0;
    return Math.min(delayMs, Number.MAX_SAFE_INTEGER);
  }

  // ---------------------------------------------------------------------------
  // 2. Application State Model
  // ---------------------------------------------------------------------------
  const state = {
    connectionState: ConnectionState.CONFIG_REQUIRED,
    config: null,
    activeSessionId: null,
    lastUpdated: null,
    statsPolling: {
      timerId: null,
      isFetching: false,
      requestId: 0,
      pollingGeneration: 0,
      failureCount: 0,
      hasSuccessfulRefresh: false,
      lastRefreshTime: null,
      refreshIntervalMs: DEFAULT_REFRESH_INTERVAL_MS,
      abortController: null,
      retryDeadlineMs: null
    },
    realtime: {
      socket: null,
      socketGeneration: 0,
      expectedJoinRef: null,
      joinTimeoutId: null,
      heartbeatTimeoutId: null,
      lastHeartbeatRef: null,
      reconnectTimerId: null,
      heartbeatTimerId: null,
      isExplicitTeardown: false,
      isBfcacheSuspended: false,
      joinRef: 0,
      messageRef: 0,
      reconnectAttempts: 0,
      seenEventIds: new Set(),
      recentEventTimestamps: []
    },
    stats: {
      totalSessions: null,
      avgFps: null,
      avgP95LatencyMs: null,
      totalGestures: 0,
      leadingGesture: null,
      eventVelocity: null,
      distribution: {
        STEER: 0,
        SWIPE: 0,
        SLASH: 0,
        PAUSE: 0
      }
    },
    feed: []
  };

  // ---------------------------------------------------------------------------
  // 3. Cached DOM Element References
  // ---------------------------------------------------------------------------
  let elements = {};

  function queryElements() {
    elements = {
      configBanner: document.getElementById('config-banner'),
      connectionPill: document.getElementById('connection-pill'),
      connectionText: document.getElementById('connection-text'),
      activeSessionText: document.getElementById('active-session-text'),
      lastUpdatedText: document.getElementById('last-updated-text'),
      statTotalSessions: document.getElementById('stat-total-sessions'),
      statAvgFps: document.getElementById('stat-avg-fps'),
      statAvgLatency: document.getElementById('stat-avg-latency'),
      statTotalGestures: document.getElementById('stat-total-gestures'),
      statLeadingGesture: document.getElementById('stat-leading-gesture'),
      statEventVelocity: document.getElementById('stat-event-velocity'),
      feedCountBadge: document.getElementById('feed-count-badge'),
      gestureFeedBody: document.getElementById('gesture-feed-body'),
      feedEmptyRow: document.getElementById('feed-empty-row'),
      distEmptyMessage: document.getElementById('dist-empty-message'),
      diagDataSource: document.getElementById('diag-data-source'),
      diagRealtimeChannel: document.getElementById('diag-realtime-channel'),
      diagRefreshStatus: document.getElementById('diag-refresh-status'),
      diagSystemHealth: document.getElementById('diag-system-health'),
      distRows: {
        STEER: {
          count: document.getElementById('dist-count-steer'),
          pct: document.getElementById('dist-pct-steer'),
          bar: document.getElementById('dist-bar-steer')
        },
        SWIPE: {
          count: document.getElementById('dist-count-swipe'),
          pct: document.getElementById('dist-pct-swipe'),
          bar: document.getElementById('dist-bar-swipe')
        },
        SLASH: {
          count: document.getElementById('dist-count-slash'),
          pct: document.getElementById('dist-pct-slash'),
          bar: document.getElementById('dist-bar-slash')
        },
        PAUSE: {
          count: document.getElementById('dist-count-pause'),
          pct: document.getElementById('dist-pct-pause'),
          bar: document.getElementById('dist-bar-pause')
        }
      }
    };
  }

  // ---------------------------------------------------------------------------
  // 4. Configuration Loading & Validation (JSON-based)
  // ---------------------------------------------------------------------------
  function isValidSupabaseUrl(url) {
    if (typeof url !== 'string' || !url.trim()) {
      return false;
    }
    const trimmed = url.trim();

    // Explicitly allow localhost development
    const isLocalhost = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?(\/.*)?$/i.test(trimmed);
    if (isLocalhost) {
      return true;
    }

    // Must strictly use HTTPS for all external/production hosts
    try {
      const parsed = new URL(trimmed);
      return parsed.protocol === 'https:';
    } catch {
      return false;
    }
  }

  function parseConfig(rawConfig) {
    if (!rawConfig || typeof rawConfig !== 'object') {
      return null;
    }

    const url = typeof rawConfig.SUPABASE_URL === 'string' ? rawConfig.SUPABASE_URL.trim() : '';
    const key = typeof rawConfig.SUPABASE_ANON_KEY === 'string' ? rawConfig.SUPABASE_ANON_KEY.trim() : '';

    // Validate that required fields are non-empty
    if (!url || !key) {
      return null;
    }

    // Validate that required fields are not default placeholders
    const isUrlPlaceholder = url.includes('your-project.supabase.co');
    const isKeyPlaceholder = key.includes('your-anon-key-here');
    if (isUrlPlaceholder || isKeyPlaceholder) {
      return null;
    }

    // Validate URL scheme (HTTPS required, or localhost)
    if (!isValidSupabaseUrl(url)) {
      return null;
    }

    return {
      supabaseUrl: url.replace(/\/+$/, ''), // strip trailing slashes
      supabaseAnonKey: key
    };
  }

  async function loadConfigAsync() {
    if (typeof window !== 'undefined' && window.ZORBI_CONFIG) {
      return parseConfig(window.ZORBI_CONFIG);
    }

    const fetchFn = typeof fetch === 'function' ? fetch : (typeof window !== 'undefined' && window.fetch ? window.fetch : null);
    if (!fetchFn) {
      return null;
    }

    try {
      const response = await fetchFn('config.json');

      if (!response.ok) {
        return null;
      }

      const json = await response.json();
      return parseConfig(json);
    } catch {
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // 5. Connection Status & Truthful Aggregate Health Rendering (UX-01)
  // ---------------------------------------------------------------------------
  function updateSystemHealthDisplay() {
    if (!elements.connectionPill || !elements.connectionText) {
      return;
    }

    if (state.connectionState === ConnectionState.CONFIG_REQUIRED) {
      elements.connectionPill.className = 'status-pill status-pill--config-required';
      elements.connectionText.textContent = 'CONFIGURATION REQUIRED';
      if (elements.configBanner) {
        elements.configBanner.classList.remove('hidden');
      }
      if (elements.diagSystemHealth) {
        elements.diagSystemHealth.textContent = 'Awaiting Configuration';
        elements.diagSystemHealth.className = 'diagnostic-value';
      }
      return;
    }

    if (elements.configBanner) {
      elements.configBanner.classList.add('hidden');
    }

    const isR7Healthy = state.statsPolling.hasSuccessfulRefresh === true && state.statsPolling.failureCount === 0;
    const isR7Degraded = state.statsPolling.failureCount > 0;
    const r8State = state.connectionState;

    elements.connectionPill.className = 'status-pill';

    if (r8State === ConnectionState.CONNECTED) {
      if (isR7Healthy) {
        elements.connectionPill.classList.add('status-pill--connected');
        elements.connectionText.textContent = 'CONNECTED';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Nominal (Streaming R8)';
          elements.diagSystemHealth.className = 'diagnostic-value diagnostic-value--nominal';
        }
      } else if (isR7Degraded) {
        elements.connectionPill.classList.add('status-pill--reconnecting');
        elements.connectionText.textContent = 'DEGRADED — STATS FAILING';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Degraded — Statistics failing';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      } else {
        // R7 state is unknown / pending first successful fetch: must not produce green CONNECTED
        elements.connectionPill.classList.add('status-pill--connecting');
        elements.connectionText.textContent = 'CONNECTING...';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Connecting (Awaiting Stats)';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      }
    } else if (r8State === ConnectionState.RECONNECTING) {
      if (!isR7Degraded) {
        elements.connectionPill.classList.add('status-pill--reconnecting');
        elements.connectionText.textContent = 'RECONNECTING...';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = isR7Healthy
            ? 'Reconnecting — Live stream'
            : 'Reconnecting Socket (Stats Pending)';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      } else {
        elements.connectionPill.classList.add('status-pill--disconnected');
        elements.connectionText.textContent = 'OUTAGE — BOTH SYSTEMS DEGRADED';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Outage / Both systems degraded';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      }
    } else if (r8State === ConnectionState.CONNECTING) {
      elements.connectionPill.classList.add('status-pill--connecting');
      elements.connectionText.textContent = 'CONNECTING...';
      if (elements.diagSystemHealth) {
        elements.diagSystemHealth.textContent = isR7Degraded
          ? 'Connecting WebSocket (Stats Failing)'
          : 'Connecting WebSocket...';
        elements.diagSystemHealth.className = 'diagnostic-value';
      }
    } else { // DISCONNECTED
      if (isR7Healthy) {
        elements.connectionPill.classList.add('status-pill--reconnecting');
        elements.connectionText.textContent = 'DEGRADED — STREAM UNAVAILABLE';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Degraded — Live stream unavailable';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      } else if (isR7Degraded && state.config) {
        elements.connectionPill.classList.add('status-pill--disconnected');
        elements.connectionText.textContent = 'OUTAGE — BOTH SYSTEMS DEGRADED';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Outage / Both systems degraded';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      } else {
        elements.connectionPill.classList.add('status-pill--disconnected');
        elements.connectionText.textContent = 'DISCONNECTED';
        if (elements.diagSystemHealth) {
          elements.diagSystemHealth.textContent = 'Offline';
          elements.diagSystemHealth.className = 'diagnostic-value';
        }
      }
    }
  }

  function setConnectionStatus(status) {
    state.connectionState = status;
    updateSystemHealthDisplay();
  }

  // ---------------------------------------------------------------------------
  // 6. R7 Data Aggregation & Calculations
  // ---------------------------------------------------------------------------
  function aggregateSessionStats(rows) {
    if (!Array.isArray(rows)) {
      return null;
    }

    let totalSessions = 0;
    let weightedFpsSum = 0;
    let totalFpsWeight = 0;
    let weightedLatencySum = 0;
    let totalLatencyWeight = 0;

    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      if (!row || typeof row !== 'object') {
        continue;
      }

      const rawSessions = Number(row.sessions);
      const sessions = Number.isFinite(rawSessions) && rawSessions > 0 ? Math.floor(rawSessions) : 0;
      totalSessions += sessions;

      if (sessions > 0 && row.avg_fps !== null && row.avg_fps !== undefined) {
        const fps = Number(row.avg_fps);
        if (Number.isFinite(fps) && fps >= 0) {
          weightedFpsSum += fps * sessions;
          totalFpsWeight += sessions;
        }
      }

      if (sessions > 0 && row.avg_p95_latency_ms !== null && row.avg_p95_latency_ms !== undefined) {
        const latency = Number(row.avg_p95_latency_ms);
        if (Number.isFinite(latency) && latency >= 0) {
          weightedLatencySum += latency * sessions;
          totalLatencyWeight += sessions;
        }
      }
    }

    return {
      totalSessions: totalSessions,
      avgFps: totalFpsWeight > 0 ? (weightedFpsSum / totalFpsWeight) : null,
      avgP95LatencyMs: totalLatencyWeight > 0 ? (weightedLatencySum / totalLatencyWeight) : null
    };
  }

  // ---------------------------------------------------------------------------
  // 7. Statistics Rendering (Safe textContent DOM APIs)
  // ---------------------------------------------------------------------------
  function renderStats(statsData) {
    if (!statsData) {
      if (elements.statTotalSessions) {
        elements.statTotalSessions.textContent = '—';
        elements.statTotalSessions.classList.add('stat-card__value--empty');
      }
      if (elements.statAvgFps) {
        elements.statAvgFps.textContent = '—';
        elements.statAvgFps.classList.add('stat-card__value--empty');
      }
      if (elements.statAvgLatency) {
        elements.statAvgLatency.textContent = '—';
        elements.statAvgLatency.classList.add('stat-card__value--empty');
      }
      if (elements.statTotalGestures) {
        elements.statTotalGestures.textContent = '—';
        elements.statTotalGestures.classList.add('stat-card__value--empty');
      }
      if (elements.statLeadingGesture) {
        elements.statLeadingGesture.textContent = '—';
        elements.statLeadingGesture.classList.add('stat-card__value--empty');
      }
      if (elements.statEventVelocity) {
        elements.statEventVelocity.textContent = '—';
        elements.statEventVelocity.classList.add('stat-card__value--empty');
      }
      renderDistribution(null);
      return;
    }

    // Total Sessions (R7)
    if (elements.statTotalSessions) {
      if (typeof statsData.totalSessions === 'number') {
        elements.statTotalSessions.textContent = statsData.totalSessions.toLocaleString();
        elements.statTotalSessions.classList.remove('stat-card__value--empty');
      } else {
        elements.statTotalSessions.textContent = '—';
        elements.statTotalSessions.classList.add('stat-card__value--empty');
      }
    }

    // Average FPS (R7)
    if (elements.statAvgFps) {
      if (typeof statsData.avgFps === 'number' && Number.isFinite(statsData.avgFps)) {
        elements.statAvgFps.textContent = statsData.avgFps.toFixed(1);
        elements.statAvgFps.classList.remove('stat-card__value--empty');
      } else {
        elements.statAvgFps.textContent = '—';
        elements.statAvgFps.classList.add('stat-card__value--empty');
      }
    }

    // Average Session P95 Latency (R7)
    if (elements.statAvgLatency) {
      if (typeof statsData.avgP95LatencyMs === 'number' && Number.isFinite(statsData.avgP95LatencyMs)) {
        elements.statAvgLatency.textContent = `${statsData.avgP95LatencyMs.toFixed(1)} ms`;
        elements.statAvgLatency.classList.remove('stat-card__value--empty');
      } else {
        elements.statAvgLatency.textContent = '—';
        elements.statAvgLatency.classList.add('stat-card__value--empty');
      }
    }

    // Total Gestures (R8 Realtime Stream)
    if (elements.statTotalGestures) {
      if (typeof statsData.totalGestures === 'number' && statsData.totalGestures > 0) {
        elements.statTotalGestures.textContent = statsData.totalGestures.toLocaleString();
        elements.statTotalGestures.classList.remove('stat-card__value--empty');
      } else {
        elements.statTotalGestures.textContent = '—';
        elements.statTotalGestures.classList.add('stat-card__value--empty');
      }
    }

    // Leading Gesture (R8 Realtime Stream)
    if (elements.statLeadingGesture) {
      elements.statLeadingGesture.textContent = statsData.leadingGesture || '—';
      elements.statLeadingGesture.classList.toggle('stat-card__value--empty', !statsData.leadingGesture);
    }

    // Event Velocity (R8 Realtime Stream)
    if (elements.statEventVelocity) {
      if (typeof statsData.eventVelocity === 'number' && Number.isFinite(statsData.eventVelocity) && statsData.eventVelocity >= 0) {
        elements.statEventVelocity.textContent = `${statsData.eventVelocity}/min`;
        elements.statEventVelocity.classList.remove('stat-card__value--empty');
      } else {
        elements.statEventVelocity.textContent = '—';
        elements.statEventVelocity.classList.add('stat-card__value--empty');
      }
    }

    renderDistribution(statsData.distribution);
  }

  function renderDistribution(distribution) {
    const total = distribution
      ? Object.values(distribution).reduce((acc, val) => acc + (typeof val === 'number' && Number.isFinite(val) ? val : 0), 0)
      : 0;

    GESTURE_TYPES.forEach(function (type) {
      const row = elements.distRows && elements.distRows[type];
      if (!row) return;

      const count = distribution && typeof distribution[type] === 'number' && Number.isFinite(distribution[type])
        ? distribution[type]
        : 0;
      const pct = total > 0 ? Math.round((count / total) * 100) : 0;

      if (row.count) {
        row.count.textContent = total > 0 ? count.toLocaleString() : '—';
      }
      if (row.pct) {
        row.pct.textContent = total > 0 ? `${pct}%` : '0%';
      }
      if (row.bar) {
        row.bar.style.width = `${pct}%`;
      }
    });

    if (elements.distEmptyMessage) {
      elements.distEmptyMessage.style.display = total > 0 ? 'none' : 'block';
    }
  }

  // ---------------------------------------------------------------------------
  // 8. R7 PostgREST Fetch & Polling Management
  // ---------------------------------------------------------------------------
  async function fetchSessionStats() {
    if (!state.config) {
      return null;
    }

    if (state.statsPolling.isFetching) {
      return null;
    }

    state.statsPolling.isFetching = true;
    const currentRequestId = ++state.statsPolling.requestId;

    if (elements.diagRefreshStatus) {
      elements.diagRefreshStatus.textContent = 'Refreshing...';
    }

    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    state.statsPolling.abortController = controller;
    const timeoutId = setTimeout(function () {
      if (controller) controller.abort();
    }, REQUEST_TIMEOUT_MS);

    const url = `${state.config.supabaseUrl}/rest/v1/v_session_stats?select=*`;

    try {
      const fetchFn = typeof fetch === 'function' ? fetch : (typeof window !== 'undefined' && window.fetch ? window.fetch : null);
      if (!fetchFn) {
        throw new Error('Fetch API is not available');
      }

      const response = await fetchFn(url, {
        method: 'GET',
        headers: {
          'apikey': state.config.supabaseAnonKey,
          'Authorization': `Bearer ${state.config.supabaseAnonKey}`,
          'Accept': 'application/json'
        },
        signal: controller ? controller.signal : undefined
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        let retryAfter = null;
        if (response.status === 429 && response.headers && typeof response.headers.get === 'function') {
          retryAfter = response.headers.get('Retry-After');
        }
        const err = new Error(`HTTP ${response.status} ${response.statusText || ''}`.trim());
        err.status = response.status;
        err.retryAfter = retryAfter;
        throw err;
      }

      const rows = await response.json();

      if (currentRequestId !== state.statsPolling.requestId) {
        return null;
      }

      const aggregated = aggregateSessionStats(rows);

      if (aggregated) {
        state.stats.totalSessions = aggregated.totalSessions;
        state.stats.avgFps = aggregated.avgFps;
        state.stats.avgP95LatencyMs = aggregated.avgP95LatencyMs;
        renderStats(state.stats);
      }

      const now = new Date();
      state.statsPolling.lastRefreshTime = now;
      state.statsPolling.hasSuccessfulRefresh = true;
      state.statsPolling.failureCount = 0;
      updateSystemHealthDisplay();

      if (elements.lastUpdatedText) {
        elements.lastUpdatedText.textContent = formatTimestamp(now);
        elements.lastUpdatedText.setAttribute('datetime', now.toISOString());
      }

      if (elements.diagRefreshStatus) {
        elements.diagRefreshStatus.textContent = 'Nominal (R7 Active)';
      }

      scheduleNextPoll(state.statsPolling.refreshIntervalMs);
      return aggregated;
    } catch (err) {
      clearTimeout(timeoutId);

      if (currentRequestId !== state.statsPolling.requestId) {
        return null;
      }

      state.statsPolling.failureCount++;
      updateSystemHealthDisplay();

      if (elements.diagRefreshStatus) {
        elements.diagRefreshStatus.textContent = `Error: ${err.message || 'Fetch failed'}`;
      }

      const baseDelay = BASE_BACKOFF_DELAY_MS * Math.pow(1.5, Math.min(state.statsPolling.failureCount, 6));
      const jitter = Math.random() * 1000;
      let backoffDelay = Math.min(MAX_BACKOFF_DELAY_MS, Math.round(baseDelay + jitter));

      if (err && err.status === 429 && err.retryAfter) {
        const serverDelayMs = parseRetryAfter(err.retryAfter);
        if (serverDelayMs !== null) {
          // R7-01: Never retry earlier than server-requested delay
          backoffDelay = Math.max(backoffDelay, serverDelayMs);
          if (elements.diagRefreshStatus) {
            elements.diagRefreshStatus.textContent = `Rate Limited (429): retry in ${Math.round(backoffDelay / 1000)}s`;
          }
        }
      }

      scheduleNextPoll(backoffDelay);
      return null;
    } finally {
      if (currentRequestId === state.statsPolling.requestId) {
        state.statsPolling.isFetching = false;
        state.statsPolling.abortController = null;
      }
    }
  }

  const MAX_SAFE_TIMEOUT_MS = 2147483647;

  function clearPollTimer() {
    if (state.statsPolling.timerId) {
      clearTimeout(state.statsPolling.timerId);
      state.statsPolling.timerId = null;
    }
    state.statsPolling.pollingGeneration++;
  }

  function scheduleNextPoll(delayMs) {
    clearPollTimer();

    if (state.realtime.isExplicitTeardown) {
      state.statsPolling.retryDeadlineMs = null;
      return;
    }

    if (typeof document !== 'undefined' && document.hidden) {
      state.statsPolling.retryDeadlineMs = null;
      return;
    }

    if (!state.config) {
      state.statsPolling.retryDeadlineMs = null;
      return;
    }

    const boundedDelay = (typeof delayMs === 'number' && Number.isFinite(delayMs) && delayMs > 0) ? delayMs : 0;
    const now = Date.now();
    // Guard against arithmetic overflow beyond Number.MAX_SAFE_INTEGER
    const maxSafeDelay = Number.MAX_SAFE_INTEGER - now;
    const safeDelay = Math.min(boundedDelay, maxSafeDelay > 0 ? maxSafeDelay : 0);
    const deadline = now + safeDelay;
    state.statsPolling.retryDeadlineMs = deadline;

    const chunkPollGen = state.statsPolling.pollingGeneration;
    const chunkInitGen = initGeneration;

    function scheduleChunk() {
      // Validate lifecycle and generation before scheduling
      if (
        state.statsPolling.pollingGeneration !== chunkPollGen ||
        state.realtime.isExplicitTeardown ||
        state.realtime.isBfcacheSuspended ||
        initGeneration !== chunkInitGen
      ) {
        return;
      }

      const currentDeadline = state.statsPolling.retryDeadlineMs;
      if (currentDeadline === null || typeof currentDeadline !== 'number') {
        state.statsPolling.timerId = null;
        return;
      }

      const currentNow = Date.now();
      // Never call fetchSessionStats until Date.now() >= retryDeadlineMs
      if (currentNow >= currentDeadline) {
        state.statsPolling.timerId = null;
        state.statsPolling.retryDeadlineMs = null;
        fetchSessionStats();
        return;
      }

      // Deadline not reached yet: compute remaining time strictly without early allowance
      const remainingMs = currentDeadline - currentNow;
      const chunkDelay = Math.min(remainingMs, MAX_SAFE_TIMEOUT_MS);

      let chunkTimerId = null;
      chunkTimerId = setTimeout(function () {
        // Ownership check: only clear timerId if it still owns this exact timer
        if (state.statsPolling.timerId === chunkTimerId) {
          state.statsPolling.timerId = null;
        }

        if (
          state.statsPolling.pollingGeneration !== chunkPollGen ||
          state.realtime.isExplicitTeardown ||
          state.realtime.isBfcacheSuspended ||
          initGeneration !== chunkInitGen
        ) {
          return;
        }

        scheduleChunk();
      }, chunkDelay);

      state.statsPolling.timerId = chunkTimerId;
    }

    scheduleChunk();
  }

  function startPolling() {
    if (!state.config) {
      return Promise.resolve(null);
    }

    clearPollTimer();
    state.statsPolling.retryDeadlineMs = null;

    return fetchSessionStats();
  }

  function stopPolling() {
    clearPollTimer();
    state.statsPolling.retryDeadlineMs = null;

    state.statsPolling.requestId++;

    if (state.statsPolling.abortController) {
      try {
        state.statsPolling.abortController.abort();
      } catch (_) { }
      state.statsPolling.abortController = null;
    }

    state.statsPolling.isFetching = false;
  }

  function handleVisibilityChange() {
    if (typeof document === 'undefined') return;

    if (document.hidden) {
      stopPolling();
      if (elements.diagRefreshStatus) {
        elements.diagRefreshStatus.textContent = 'Paused (Background)';
      }
    } else {
      if (state.config) {
        const lastRefresh = state.statsPolling.lastRefreshTime;
        const isStale = !lastRefresh || (Date.now() - lastRefresh.getTime() > state.statsPolling.refreshIntervalMs);
        if (isStale) {
          fetchSessionStats();
        } else {
          scheduleNextPoll(state.statsPolling.refreshIntervalMs);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 9. R8 Realtime WebSocket Client Implementation
  // ---------------------------------------------------------------------------
  function getRealtimeWebSocketUrl(supabaseUrl, anonKey) {
    const wsBase = supabaseUrl.replace(/^http:\/\//i, 'ws://').replace(/^https:\/\//i, 'wss://');
    const encodedKey = encodeURIComponent(anonKey);
    return `${wsBase}/realtime/v1/websocket?apikey=${encodedKey}&vsn=1.0.0`;
  }

  function connectRealtime() {
    if (state.realtime.isExplicitTeardown || !state.config) {
      return;
    }

    // Clean up any existing socket without triggering reconnect
    cleanupSocket();
    clearReconnectTimer();

    setConnectionStatus(ConnectionState.CONNECTING);
    if (elements.diagRealtimeChannel) {
      elements.diagRealtimeChannel.textContent = 'Connecting...';
    }

    const currentGeneration = ++state.realtime.socketGeneration;
    state.realtime.expectedJoinRef = null;

    const wsUrl = getRealtimeWebSocketUrl(state.config.supabaseUrl, state.config.supabaseAnonKey);
    const WebSocketConstructor = typeof WebSocket !== 'undefined'
      ? WebSocket
      : (typeof window !== 'undefined' && window.WebSocket ? window.WebSocket : null);

    if (!WebSocketConstructor) {
      setConnectionStatus(ConnectionState.DISCONNECTED);
      return;
    }

    let socket;
    try {
      socket = new WebSocketConstructor(wsUrl);
      state.realtime.socket = socket;
    } catch {
      scheduleReconnect();
      return;
    }

    socket.onopen = function () {
      if (state.realtime.socket !== socket || state.realtime.socketGeneration !== currentGeneration) return;

      // Socket open alone must NEVER produce CONNECTED!
      // Send Phoenix phx_join to subscribe to postgres_changes
      const joinRef = String(++state.realtime.joinRef);
      state.realtime.expectedJoinRef = joinRef;

      const joinMessage = {
        topic: REALTIME_TOPIC,
        event: 'phx_join',
        payload: {
          config: {
            postgres_changes: [
              { event: 'INSERT', schema: 'public', table: 'gesture_events' }
            ]
          }
        },
        ref: joinRef,
        join_ref: joinRef
      };

      try {
        socket.send(JSON.stringify(joinMessage));
      } catch {
        handleSocketDrop();
        return;
      }

      // R8-05: Start join acknowledgement timeout (10s) bound to exact join reference and timer ownership
      clearJoinTimeout();
      const joinGeneration = currentGeneration;
      const scheduledJoinRef = joinRef;
      let scheduledTimerId = null;
      scheduledTimerId = setTimeout(function () {
        if (state.realtime.joinTimeoutId === scheduledTimerId) {
          state.realtime.joinTimeoutId = null;
        }
        if (
          state.realtime.socket === socket &&
          state.realtime.socketGeneration === joinGeneration &&
          state.realtime.expectedJoinRef &&
          String(state.realtime.expectedJoinRef) === String(scheduledJoinRef)
        ) {
          if (elements.diagRealtimeChannel) {
            elements.diagRealtimeChannel.textContent = 'Join Timeout';
          }
          handleSocketDrop();
        }
      }, JOIN_TIMEOUT_MS);
      state.realtime.joinTimeoutId = scheduledTimerId;

      // Start 30s Phoenix heartbeat maintenance
      startHeartbeat();
    };

    socket.onmessage = function (event) {
      if (state.realtime.socket !== socket || state.realtime.socketGeneration !== currentGeneration) return;

      let msg;
      try {
        msg = JSON.parse(event.data);
      } catch {
        return; // Ignore malformed frames safely
      }

      if (!msg || typeof msg !== 'object') {
        return;
      }

      // 0. R8-06: Handle Phoenix Heartbeat Reply
      if (msg.topic === 'phoenix' && msg.event === 'phx_reply') {
        if (state.realtime.lastHeartbeatRef && String(msg.ref) === String(state.realtime.lastHeartbeatRef)) {
          clearHeartbeatTimeout();
        }
        return; // Heartbeat replies must never enter feed or channel processing
      }

      // 1. R8-01: Handle Phoenix Channel Join Reply
      if (msg.event === 'phx_reply') {
        // Must match exact expected channel topic (heartbeat or wrong-topic replies must NEVER connect)
        if (msg.topic !== REALTIME_TOPIC) {
          return;
        }

        // Must match the outstanding join reference for this specific socket
        if (!state.realtime.expectedJoinRef || String(msg.ref) !== String(state.realtime.expectedJoinRef)) {
          return;
        }

        clearJoinTimeout();
        state.realtime.expectedJoinRef = null;

        const payload = msg.payload || {};
        if (payload.status === 'ok') {
          // Join acknowledged successfully!
          if (state.connectionState !== ConnectionState.CONNECTED) {
            setConnectionStatus(ConnectionState.CONNECTED);
            state.realtime.reconnectAttempts = 0;
            if (elements.diagRealtimeChannel) {
              elements.diagRealtimeChannel.textContent = 'Subscribed (public:gesture_events)';
            }
          }
        } else {
          // Join rejected by server
          if (elements.diagRealtimeChannel) {
            elements.diagRealtimeChannel.textContent = 'Join Rejected';
          }
          handleSocketDrop();
        }
        return;
      }

      // 2. R8-04: Handle Postgres INSERT Changes
      if (msg.event === 'postgres_changes') {
        if (msg.topic !== REALTIME_TOPIC) {
          return;
        }
        handlePostgresChanges(msg.payload);
        return;
      }

      // 3. Handle Channel Error or Close
      if (msg.event === 'phx_error' || msg.event === 'phx_close') {
        handleSocketDrop();
      }
    };

    socket.onerror = function () {
      if (state.realtime.socket !== socket || state.realtime.socketGeneration !== currentGeneration) return;
      handleSocketDrop();
    };

    socket.onclose = function () {
      if (state.realtime.socket !== socket || state.realtime.socketGeneration !== currentGeneration) return;
      handleSocketDrop();
    };
  }

  function handlePostgresChanges(payload) {
    if (!payload || typeof payload !== 'object') {
      return;
    }

    // R8-04: Require payload.data object
    const data = payload.data;
    if (!data || typeof data !== 'object') {
      return;
    }

    // R8-04: Strict validation: Reject missing or mismatched schema/table/type fields
    if (data.type !== 'INSERT') {
      return;
    }
    if (data.schema !== 'public') {
      return;
    }
    if (data.table !== 'gesture_events') {
      return;
    }

    const record = data.record;
    if (!record || typeof record !== 'object') {
      return;
    }

    processGestureInsert(record);
  }

  function processGestureInsert(record) {
    if (!record || typeof record !== 'object') {
      return;
    }

    // R8-02: Exclude mock events (strictly record.is_mock === false)
    if (record.is_mock !== false) {
      return;
    }

    // H-01: Validate required database field: id (PostgreSQL bigint-safe ID)
    const validId = parseBigIntId(record.id);
    if (!validId) {
      return;
    }

    // R8-04: Validate required database field: session_id (valid UUID)
    if (typeof record.session_id !== 'string') {
      return;
    }
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const trimmedSessionId = record.session_id.trim();
    if (!uuidRegex.test(trimmedSessionId)) {
      return;
    }

    // R8-04: Validate required database field: event_type (string)
    if (typeof record.event_type !== 'string') {
      return;
    }
    const trimmedType = record.event_type.trim();
    if (!trimmedType) {
      return;
    }
    const upperType = trimmedType.toUpperCase();
    const gestureType = GESTURE_TYPES.includes(upperType) ? upperType : 'UNKNOWN';

    // H-02: Strict database timestamp validation (ISO-8601/RFC3339 with mandatory timezone)
    const eventDate = parseStrictIsoTimestamp(record.created_at);
    if (!eventDate) {
      return;
    }

    // R8-04: Validate magnitude: null or finite numeric value
    let magnitude = null;
    if (record.magnitude === null || record.magnitude === undefined) {
      magnitude = null;
    } else if (typeof record.magnitude === 'number' && Number.isFinite(record.magnitude)) {
      magnitude = record.magnitude;
    } else {
      return; // Malformed magnitude rejected
    }

    // Primary key deduplication (exact validated decimal string key preserves bigint distinction)
    const eventKey = validId;
    if (state.realtime.seenEventIds.has(eventKey)) {
      return; // Duplicate event ignored
    }
    state.realtime.seenEventIds.add(eventKey);
    if (state.realtime.seenEventIds.size > MAX_SEEN_EVENT_IDS) {
      const oldestId = state.realtime.seenEventIds.values().next().value;
      state.realtime.seenEventIds.delete(oldestId);
    }

    // Distribution & count updates (canonical gestures)
    if (gestureType !== 'UNKNOWN') {
      state.stats.distribution[gestureType] = (state.stats.distribution[gestureType] || 0) + 1;
      state.stats.totalGestures = (state.stats.totalGestures || 0) + 1;

      // Recompute leading gesture
      let maxCount = -1;
      let leading = null;
      GESTURE_TYPES.forEach(function (type) {
        const count = state.stats.distribution[type] || 0;
        if (count > maxCount) {
          maxCount = count;
          leading = type;
        }
      });
      state.stats.leadingGesture = maxCount > 0 ? leading : null;
    }

    // R8-03: Event velocity calculation (sliding 60-second window anchored to newest observed event timestamp)
    const eventTimeMs = eventDate.getTime();
    state.realtime.recentEventTimestamps.push(eventTimeMs);
    let maxTimeMs = eventTimeMs;
    for (let i = 0; i < state.realtime.recentEventTimestamps.length; i++) {
      if (state.realtime.recentEventTimestamps[i] > maxTimeMs) {
        maxTimeMs = state.realtime.recentEventTimestamps[i];
      }
    }
    const windowStart = maxTimeMs - VELOCITY_WINDOW_MS;
    state.realtime.recentEventTimestamps = state.realtime.recentEventTimestamps.filter(function (t) {
      return t >= windowStart;
    });
    state.stats.eventVelocity = state.realtime.recentEventTimestamps.length;

    // Re-render stats & distribution
    renderStats(state.stats);

    // Append to live feed table with validated timestamp
    addFeedEvent({
      id: validId,
      timestamp: eventDate,
      gestureType: gestureType,
      magnitude: magnitude,
      sessionId: trimmedSessionId
    });

    // Update header session & last updated
    updateMetaDisplay(trimmedSessionId, eventDate);
  }

  function clearJoinTimeout() {
    if (state.realtime.joinTimeoutId) {
      clearTimeout(state.realtime.joinTimeoutId);
      state.realtime.joinTimeoutId = null;
    }
  }

  function clearHeartbeatTimeout() {
    if (state.realtime.heartbeatTimeoutId) {
      clearTimeout(state.realtime.heartbeatTimeoutId);
      state.realtime.heartbeatTimeoutId = null;
    }
    state.realtime.lastHeartbeatRef = null;
  }

  function clearHeartbeat() {
    if (state.realtime.heartbeatTimerId) {
      clearInterval(state.realtime.heartbeatTimerId);
      state.realtime.heartbeatTimerId = null;
    }
    clearHeartbeatTimeout();
  }

  function startHeartbeat() {
    clearHeartbeat();
    const hbGeneration = state.realtime.socketGeneration;
    state.realtime.heartbeatTimerId = setInterval(function () {
      const socket = state.realtime.socket;
      const isOpen = socket && socket.readyState === (typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1);
      if (isOpen && state.realtime.socketGeneration === hbGeneration) {
        clearHeartbeatTimeout();
        const hbRef = String(++state.realtime.messageRef);
        state.realtime.lastHeartbeatRef = hbRef;

        const hbMessage = {
          topic: 'phoenix',
          event: 'heartbeat',
          payload: {},
          ref: hbRef
        };

        let scheduledHbTimeoutId = null;
        scheduledHbTimeoutId = setTimeout(function () {
          if (state.realtime.heartbeatTimeoutId === scheduledHbTimeoutId) {
            state.realtime.heartbeatTimeoutId = null;
          }
          if (state.realtime.socket === socket && state.realtime.socketGeneration === hbGeneration) {
            if (elements.diagRealtimeChannel) {
              elements.diagRealtimeChannel.textContent = 'Heartbeat Timeout';
            }
            handleSocketDrop();
          }
        }, HEARTBEAT_ACK_TIMEOUT_MS);
        state.realtime.heartbeatTimeoutId = scheduledHbTimeoutId;

        try {
          socket.send(JSON.stringify(hbMessage));
        } catch {
          handleSocketDrop();
        }
      }
    }, HEARTBEAT_INTERVAL_MS);
  }

  function handleSocketDrop() {
    cleanupSocket();
    if (!state.realtime.isExplicitTeardown && state.config) {
      scheduleReconnect();
    } else {
      setConnectionStatus(ConnectionState.DISCONNECTED);
    }
  }

  function scheduleReconnect() {
    if (state.realtime.isExplicitTeardown || !state.config) {
      return;
    }

    clearReconnectTimer();
    setConnectionStatus(ConnectionState.RECONNECTING);
    if (elements.diagRealtimeChannel) {
      elements.diagRealtimeChannel.textContent = 'Reconnecting Socket...';
    }

    const attempts = state.realtime.reconnectAttempts++;
    const baseDelay = RECONNECT_BASE_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, Math.min(attempts, 8));
    const jitter = Math.random() * 1000;
    const delay = Math.min(RECONNECT_MAX_DELAY_MS, Math.round(baseDelay + jitter));

    state.realtime.reconnectTimerId = setTimeout(function () {
      state.realtime.reconnectTimerId = null;
      connectRealtime();
    }, delay);
  }

  function clearReconnectTimer() {
    if (state.realtime.reconnectTimerId) {
      clearTimeout(state.realtime.reconnectTimerId);
      state.realtime.reconnectTimerId = null;
    }
  }

  function cleanupSocket() {
    clearJoinTimeout();
    clearHeartbeat();
    state.realtime.expectedJoinRef = null;
    const socket = state.realtime.socket;
    if (socket) {
      state.realtime.socket = null;
      try {
        socket.onopen = null;
        socket.onmessage = null;
        socket.onerror = null;
        socket.onclose = null;
        socket.close();
      } catch (_) { }
    }
  }

  function teardownRealtime() {
    state.realtime.isExplicitTeardown = true;
    clearReconnectTimer();
    clearJoinTimeout();
    clearHeartbeat();
    state.realtime.expectedJoinRef = null;
    cleanupSocket();
    setConnectionStatus(ConnectionState.DISCONNECTED);
    if (elements.diagRealtimeChannel) {
      elements.diagRealtimeChannel.textContent = 'Disconnected';
    }
  }

  function teardown() {
    initGeneration++;
    stopPolling();
    teardownRealtime();
  }

  // ---------------------------------------------------------------------------
  // 10. Live Activity Feed Rendering (Resolves L-2 & L-3)
  // ---------------------------------------------------------------------------
  function addFeedEvent(event) {
    if (!event || !elements.gestureFeedBody) {
      return;
    }

    if (elements.feedEmptyRow && elements.feedEmptyRow.parentNode) {
      elements.feedEmptyRow.parentNode.removeChild(elements.feedEmptyRow);
    }

    const row = document.createElement('tr');

    // Column 1: Timestamp
    const tdTime = document.createElement('td');
    tdTime.textContent = event.timestamp ? formatTimestamp(event.timestamp) : '—';
    row.appendChild(tdTime);

    // Column 2: Gesture Type Badge
    const tdType = document.createElement('td');
    const badge = document.createElement('span');
    const rawType = typeof event.gestureType === 'string' ? event.gestureType.trim().toUpperCase() : '';
    const gestureType = GESTURE_TYPES.includes(rawType) ? rawType : 'UNKNOWN';

    badge.className = `gesture-badge gesture-badge--${gestureType.toLowerCase()}`;
    badge.textContent = gestureType;
    tdType.appendChild(badge);
    row.appendChild(tdType);

    // Column 3: Magnitude
    const tdMagnitude = document.createElement('td');
    const isValidMagnitude = typeof event.magnitude === 'number' && Number.isFinite(event.magnitude);
    tdMagnitude.textContent = isValidMagnitude ? event.magnitude.toFixed(2) : '—';
    row.appendChild(tdMagnitude);

    // Column 4: Session Reference
    const tdSession = document.createElement('td');
    const sessionId = typeof event.sessionId === 'string' ? event.sessionId : '—';
    tdSession.textContent = sessionId.length > 8 ? `${sessionId.slice(0, 8)}...` : sessionId;
    tdSession.title = sessionId;
    row.appendChild(tdSession);

    // Prepend to show most recent events at the top
    elements.gestureFeedBody.insertBefore(row, elements.gestureFeedBody.firstChild);

    // Enforce bounded circular memory limit
    while (elements.gestureFeedBody.children.length > MAX_FEED_ENTRIES) {
      elements.gestureFeedBody.removeChild(elements.gestureFeedBody.lastChild);
    }

    state.feed.unshift(event);
    if (state.feed.length > MAX_FEED_ENTRIES) {
      state.feed.pop();
    }

    if (elements.feedCountBadge) {
      elements.feedCountBadge.textContent = `${state.feed.length} events buffered`;
    }
  }

  function formatTimestamp(isoOrDate) {
    try {
      const d = new Date(isoOrDate);
      if (isNaN(d.getTime())) return String(isoOrDate);
      return d.toTimeString().split(' ')[0] + '.' + String(d.getMilliseconds()).padStart(3, '0');
    } catch {
      return String(isoOrDate);
    }
  }

  function updateMetaDisplay(sessionId, timestamp) {
    if (sessionId && elements.activeSessionText) {
      state.activeSessionId = sessionId;
      elements.activeSessionText.textContent = sessionId.length > 8 ? `${sessionId.slice(0, 8)}...` : sessionId;
      elements.activeSessionText.title = sessionId;
    }

    if (timestamp && elements.lastUpdatedText) {
      state.lastUpdated = timestamp;
      elements.lastUpdatedText.textContent = formatTimestamp(timestamp);
      elements.lastUpdatedText.setAttribute('datetime', new Date(timestamp).toISOString());
    }
  }

  // ---------------------------------------------------------------------------
  // 11. Application Initialization, bfcache & Unload Hooks (L-01 & INIT-01)
  // ---------------------------------------------------------------------------
  let initGeneration = 0;
  let isListenersAttached = false;

  function handlePageHide(event) {
    if (event && event.persisted) {
      // L-01: Page is suspended into Back-Forward Cache (bfcache)
      state.realtime.isBfcacheSuspended = true;
      stopPolling();
      cleanupSocket();
      clearReconnectTimer();
    } else {
      // Permanent page termination
      teardown();
    }
  }

  function handlePageShow(event) {
    if (event && event.persisted) {
      // L-01: Page restored from Back-Forward Cache (bfcache)
      state.realtime.isBfcacheSuspended = false;
      state.realtime.isExplicitTeardown = false;

      if (state.config) {
        stopPolling();
        cleanupSocket();
        clearReconnectTimer();

        startPolling();
        connectRealtime();
      }
    }
  }

  async function init() {
    queryElements();

    // INIT-01: Guard against duplicate document/window listener registration
    if (!isListenersAttached) {
      if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
        document.addEventListener('visibilitychange', handleVisibilityChange);
      }

      if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
        window.addEventListener('beforeunload', teardown);
        window.addEventListener('pagehide', handlePageHide);
        window.addEventListener('pageshow', handlePageShow);
      }
      isListenersAttached = true;
    }

    // INIT-01: Single-flight generation token to invalidate concurrent/stale init calls
    const currentInitGen = ++initGeneration;

    // INIT-01: Stop existing polling or sockets before re-initialization
    if (state.statsPolling.timerId !== null || state.statsPolling.isFetching) {
      stopPolling();
    }
    if (state.realtime.socket || state.realtime.reconnectTimerId) {
      cleanupSocket();
      clearReconnectTimer();
    }
    state.realtime.isExplicitTeardown = false;
    state.realtime.isBfcacheSuspended = false;

    const config = await loadConfigAsync();

    // Invalidate if superseded by a newer init call or explicit teardown
    if (currentInitGen !== initGeneration || state.realtime.isExplicitTeardown) {
      return;
    }

    if (!config) {
      state.config = null;
      setConnectionStatus(ConnectionState.CONFIG_REQUIRED);
      renderStats(null);
    } else {
      state.config = config;
      setConnectionStatus(ConnectionState.DISCONNECTED);
      renderStats(state.stats);

      // Start independent R7 statistics polling
      startPolling();

      // Connect R8 Realtime WebSocket
      connectRealtime();
    }
  }

  // Expose testable internals for QA/automated testing without polluting global namespace
  window.ZorbiDashboard = Object.freeze({
    init,
    parseConfig,
    isValidSupabaseUrl,
    loadConfigAsync,
    setConnectionStatus,
    updateSystemHealthDisplay,
    aggregateSessionStats,
    fetchSessionStats,
    startPolling,
    stopPolling,
    scheduleNextPoll,
    handleVisibilityChange,
    handlePageHide,
    handlePageShow,
    getRealtimeWebSocketUrl,
    connectRealtime,
    cleanupSocket,
    teardown,
    teardownRealtime,
    scheduleReconnect,
    parseBigIntId,
    parseStrictIsoTimestamp,
    parseRetryAfter,
    processGestureInsert,
    handlePostgresChanges,
    startHeartbeat,
    clearHeartbeat,
    clearHeartbeatTimeout,
    clearJoinTimeout,
    renderStats,
    addFeedEvent,
    updateMetaDisplay,
    ConnectionState,
    GESTURE_TYPES,
    getState: function () {
      return {
        connectionState: state.connectionState,
        config: state.config ? { ...state.config } : null,
        activeSessionId: state.activeSessionId,
        lastUpdated: state.lastUpdated,
        statsPolling: {
          isFetching: state.statsPolling.isFetching,
          requestId: state.statsPolling.requestId,
          pollingGeneration: state.statsPolling.pollingGeneration,
          failureCount: state.statsPolling.failureCount,
          hasSuccessfulRefresh: state.statsPolling.hasSuccessfulRefresh,
          lastRefreshTime: state.statsPolling.lastRefreshTime,
          refreshIntervalMs: state.statsPolling.refreshIntervalMs,
          timerActive: state.statsPolling.timerId !== null,
          retryDeadlineMs: state.statsPolling.retryDeadlineMs
        },
        realtime: {
          connected: state.connectionState === ConnectionState.CONNECTED,
          reconnectAttempts: state.realtime.reconnectAttempts,
          reconnectTimerActive: state.realtime.reconnectTimerId !== null,
          heartbeatActive: state.realtime.heartbeatTimerId !== null,
          joinTimeoutActive: state.realtime.joinTimeoutId !== null,
          heartbeatTimeoutActive: state.realtime.heartbeatTimeoutId !== null,
          lastHeartbeatRef: state.realtime.lastHeartbeatRef,
          isExplicitTeardown: state.realtime.isExplicitTeardown,
          isBfcacheSuspended: state.realtime.isBfcacheSuspended,
          seenEventIdsCount: state.realtime.seenEventIds.size,
          expectedJoinRef: state.realtime.expectedJoinRef,
          socketGeneration: state.realtime.socketGeneration,
          initGeneration: initGeneration
        },
        stats: {
          totalSessions: state.stats.totalSessions,
          avgFps: state.stats.avgFps,
          avgP95LatencyMs: state.stats.avgP95LatencyMs,
          totalGestures: state.stats.totalGestures,
          leadingGesture: state.stats.leadingGesture,
          eventVelocity: state.stats.eventVelocity,
          distribution: { ...state.stats.distribution }
        },
        feed: [...state.feed]
      };
    }
  });

  // Run on DOM readiness
  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', init);
    } else {
      init();
    }
  }
})();
