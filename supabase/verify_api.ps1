# ==============================================================================
# ZORBI — Supabase API & Security Verification Suite (Gate T-P4-1)
# Frozen Specification: Master Prompt (v1.1) Section 6 & 11.1
# Owned by: Person 4 (Backend, Tools, CI, QA, Docs & Demo)
# ==============================================================================

[CmdletBinding()]
param(
    [string]$Url = $env:SUPABASE_URL,
    [string]$AnonKey = $env:SUPABASE_ANON_KEY
)

$ErrorActionPreference = "Stop"

# Try loading from local.properties if not provided in environment
if (-not $Url -or -not $AnonKey) {
    $localPropsPath = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path $localPropsPath) {
        Write-Host "Loading credentials from local.properties..."
        Get-Content $localPropsPath | ForEach-Object {
            if ($_ -match "^\s*SUPABASE_URL\s*=\s*(.+)$") {
                if (-not $Url) { $Url = $matches[1].Trim().Trim('"').Trim("'") }
            }
            if ($_ -match "^\s*SUPABASE_ANON_KEY\s*=\s*(.+)$") {
                if (-not $AnonKey) { $AnonKey = $matches[1].Trim().Trim('"').Trim("'") }
            }
        }
    }
}

if ($Url) {
    $Url = $Url.TrimEnd('/')
}

if (-not $Url -or -not $AnonKey -or $Url -like "*<*>*" -or $AnonKey -like "*<*>*") {
    Write-Warning "SUPABASE_URL or SUPABASE_ANON_KEY is not configured or contains placeholder values."
    Write-Host "Set environment variables SUPABASE_URL and SUPABASE_ANON_KEY, or populate local.properties."
    Write-Host "Example:"
    Write-Host '  $env:SUPABASE_URL="https://your-project.supabase.co"'
    Write-Host '  $env:SUPABASE_ANON_KEY="eyJhbGciOi..."'
    Write-Host "  .\supabase\verify_api.ps1"
    Write-Host "`nAll live network tests marked: NOT RUN (Awaiting hosted credentials per Q8)."
    exit 2
}

# Redact function for secure logging
function Mask-Secret([string]$val) {
    if (-not $val -or $val.Length -lt 8) { return "****" }
    return $val.Substring(0, 4) + "..." + $val.Substring($val.Length - 4)
}

Write-Host "================================================================="
Write-Host "ZORBI SUPABASE API VERIFICATION SUITE (Gate T-P4-1)"
Write-Host "Target URL: $Url"
Write-Host "Anon Key  : $(Mask-Secret $AnonKey)"
Write-Host "================================================================="

$passed = 0
$failed = 0

function Run-Test([string]$testId, [string]$description, [scriptblock]$action) {
    Write-Host "`n[$testId] $description"
    try {
        $result = & $action
        if ($result.Pass) {
            Write-Host "  RESULT: PASS (HTTP $($result.Status))" -ForegroundColor Green
            if ($result.Message) { Write-Host "  DETAIL: $($result.Message)" }
            $script:passed++
        } else {
            Write-Host "  RESULT: FAIL (HTTP $($result.Status)) - $($result.Message)" -ForegroundColor Red
            $script:failed++
        }
    } catch {
        Write-Host "  RESULT: ERROR - $_" -ForegroundColor Red
        $script:failed++
    }
}

# ------------------------------------------------------------------------------
# Reliable HTTP Helper: Uses UTF-8 without BOM temporary file for curl payloads
# ------------------------------------------------------------------------------
function Invoke-SupabaseRequest {
    param(
        [Parameter(Mandatory=$true)][string]$Method,
        [Parameter(Mandatory=$true)][string]$Uri,
        [Parameter(Mandatory=$false)]$Body,
        [Parameter(Mandatory=$false)][string]$Prefer
    )

    $tmpFile = $null
    try {
        $curlArgs = @("-s", "-w", "`n%{http_code}")
        $curlArgs += @("-H", "apikey: $AnonKey")
        $curlArgs += @("-H", "Authorization: Bearer $AnonKey")
        $curlArgs += @("-H", "Content-Type: application/json")

        if ($Prefer) {
            $curlArgs += @("-H", "Prefer: $Prefer")
        }

        if ($Method -and $Method -ne "GET" -and $Method -ne "POST") {
            $curlArgs += @("-X", $Method)
        }

        if ($null -ne $Body) {
            $jsonString = ""
            if ($Body -is [string]) {
                $jsonString = $Body
            } else {
                $jsonString = $Body | ConvertTo-Json -Depth 10 -Compress
            }

            # Validate JSON locally before sending
            try {
                $null = ConvertFrom-Json -InputObject $jsonString -ErrorAction Stop
            } catch {
                throw "Local JSON validation failed: $_`nPayload: $jsonString"
            }

            # Write JSON request body to temp file using UTF-8 without BOM
            $tmpFile = [System.IO.Path]::GetTempFileName()
            $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
            [System.IO.File]::WriteAllText($tmpFile, $jsonString, $utf8NoBom)

            $curlArgs += @("--data-binary", "@$tmpFile")
        }

        $curlArgs += $Uri

        $rawOutput = & curl.exe @curlArgs
        $curlExitCode = $LASTEXITCODE

        # Immediately inspect $LASTEXITCODE and fail clearly if curl exits unsuccessfully
        if ($curlExitCode -ne 0) {
            throw "curl.exe exited with non-zero code $curlExitCode. Output: $rawOutput"
        }

        # Validate output is not empty or whitespace
        if ([string]::IsNullOrWhiteSpace($rawOutput)) {
            throw "curl.exe returned empty output (expected response body and HTTP status code)."
        }

        # Split output and validate final line is a valid numeric HTTP status
        $lines = $rawOutput -split "`n"
        $statusStr = $lines[-1].Trim()
        $status = 0
        if (-not [int]::TryParse($statusStr, [ref]$status) -or $status -le 0) {
            throw "curl.exe output did not terminate with a valid numeric HTTP status code (got '$statusStr'). Full output: $rawOutput"
        }

        $respBody = if ($lines.Length -gt 1) { ($lines[0..($lines.Length - 2)] -join "`n") } else { "" }

        return @{
            Status = $status
            Body   = $respBody
        }
    } finally {
        if ($tmpFile -and (Test-Path $tmpFile)) {
            Remove-Item -Path $tmpFile -Force -ErrorAction SilentlyContinue
        }
    }
}

# ------------------------------------------------------------------------------
# R1: List approved profiles
# ------------------------------------------------------------------------------
$approvedProfileId = $null
$approvedProfileDownloadsBefore = $null

Run-Test "R1" "GET approved game profiles" {
    $endpoint = "$Url/rest/v1/game_profiles?approved=eq.true&select=*&order=downloads.desc"
    $res = Invoke-SupabaseRequest -Method "GET" -Uri $endpoint
    $status = $res.Status
    $body = $res.Body

    if ($status -ne 200) {
        return @{ Pass = $false; Status = $status; Message = "Expected HTTP 200, got: $body" }
    }

    try {
        $json = $body | ConvertFrom-Json
        $items = @($json)
        if ($items.Count -gt 0) {
            $script:approvedProfileId = $items[0].id
            $script:approvedProfileDownloadsBefore = [int]$items[0].downloads
            Write-Host "    Tracking approved profile ID: $($script:approvedProfileId) (downloads: $($script:approvedProfileDownloadsBefore))"
        }
        return @{ Pass = $true; Status = $status; Message = "Retrieved $($items.Count) approved profiles" }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "Failed to parse JSON response: $_`nBody: $body" }
    }
}

# ------------------------------------------------------------------------------
# R2: Find approved profile by package name (Strict validation)
# ------------------------------------------------------------------------------
Run-Test "R2" "GET approved profile by package name" {
    $endpoint = "$Url/rest/v1/game_profiles?package_name=eq.com.kiloo.subwaysurf&approved=eq.true&select=*"
    $res = Invoke-SupabaseRequest -Method "GET" -Uri $endpoint
    $status = $res.Status
    $body = $res.Body

    if ($status -ne 200) {
        return @{ Pass = $false; Status = $status; Message = "Expected HTTP 200, got: $body" }
    }

    try {
        $json = $body | ConvertFrom-Json
        $items = @($json)
        if ($items.Count -lt 1) {
            return @{ Pass = $false; Status = $status; Message = "Expected at least 1 profile, got 0" }
        }

        $match = $items | Where-Object { $_.package_name -eq "com.kiloo.subwaysurf" -and $_.approved -eq $true }
        if ($null -eq $match -or @($match).Count -lt 1) {
            return @{ Pass = $false; Status = $status; Message = "No profile found with package_name='com.kiloo.subwaysurf' and approved=true" }
        }

        $profile = @($match)[0]
        return @{ Pass = $true; Status = $status; Message = "Matched approved profile for Subway Surfers: $($profile.display_name) (id=$($profile.id))" }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "Failed to parse JSON response: $_`nBody: $body" }
    }
}

# ------------------------------------------------------------------------------
# R3: Submit an unapproved profile
# Note: Uses full valid GameProfile structure with Prefer: return=minimal because
# unapproved profiles cannot be read back by anon under the SELECT RLS policy.
# ------------------------------------------------------------------------------
$testPackage = "com.test.tmp_" + [System.Guid]::NewGuid().ToString("N").Substring(0, 8)
$submittedProfileId = [System.Guid]::NewGuid().ToString()

Run-Test "R3" "POST submit unapproved profile (approved=false, return=minimal)" {
    $endpoint = "$Url/rest/v1/game_profiles"

    # Complete valid GameProfile JSON structure (schemaVersion 1, Section 5.2)
    $fullProfileJson = @{
        schemaVersion = 1
        packageName   = $testPackage
        displayName   = "Temporary Test Game"
        orientation   = "PORTRAIT"
        tracker       = "HAND_SINGLE"
        mapping       = "LANE_SWIPE"
        targets       = @{
            left   = @{ x = 0.20; y = 0.80 }
            right  = @{ x = 0.80; y = 0.80 }
            center = @{ x = 0.50; y = 0.50 }
        }
        params        = @{
            deadzoneDeg    = 8.0
            cooldownMs     = 350
            swipeMinSpeed  = 1.2
            slashMinLength = 0.15
            smoothing      = @{
                minCutoff = 1.0
                beta      = 0.01
            }
        }
    }

    $payload = @{
        id             = $submittedProfileId
        package_name   = $testPackage
        display_name   = "Temporary Test Game"
        tracker        = "HAND_SINGLE"
        mapping        = "LANE_SWIPE"
        profile_json   = $fullProfileJson
        schema_version = 1
        author         = "zorbi-team"
        approved       = $false
        is_mock        = $true
    }

    $res = Invoke-SupabaseRequest -Method "POST" -Uri $endpoint -Body $payload -Prefer "return=minimal"
    $status = $res.Status
    $body = $res.Body

    $pass = ($status -eq 201)
    if ($pass) {
        return @{ Pass = $true; Status = $status; Message = "Unapproved profile accepted with ID: $submittedProfileId" }
    } else {
        return @{ Pass = $false; Status = $status; Message = "Failed to insert unapproved profile: $body" }
    }
}

# ------------------------------------------------------------------------------
# R4: Increment approved profile downloads (RPC) - Strict counter verification
# ------------------------------------------------------------------------------
Run-Test "R4" "POST increment approved profile downloads via RPC" {
    # Baseline checks: require valid approved profile and baseline count from R1
    if ($null -eq $approvedProfileId -or $null -eq $approvedProfileDownloadsBefore) {
        return @{ Pass = $false; Status = 0; Message = "FAIL: Missing baseline from R1 (approved profile ID or downloads count not available)" }
    }

    $targetId = $approvedProfileId
    $endpoint = "$Url/rest/v1/rpc/increment_profile_downloads"
    $payload = @{ profile_id = $targetId }

    $res = Invoke-SupabaseRequest -Method "POST" -Uri $endpoint -Body $payload
    $status = $res.Status
    $body = $res.Body

    if ($status -ne 200 -and $status -ne 204) {
        return @{ Pass = $false; Status = $status; Message = "RPC call failed with HTTP $($status): $body" }
    }

    # Strict counter verification: follow-up GET MUST return HTTP 200
    $verifyEndpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId&select=downloads"
    $vRes = Invoke-SupabaseRequest -Method "GET" -Uri $verifyEndpoint
    if ($vRes.Status -ne 200) {
        return @{ Pass = $false; Status = $vRes.Status; Message = "Verification GET failed with HTTP $($vRes.Status): $($vRes.Body)" }
    }

    try {
        $vJson = $vRes.Body | ConvertFrom-Json
        $items = @($vJson)
        if ($items.Count -ne 1) {
            return @{ Pass = $false; Status = $vRes.Status; Message = "Verification GET returned $($items.Count) profiles, expected exactly 1" }
        }
        $downloadsAfter = [int]$items[0].downloads
        $diff = $downloadsAfter - $approvedProfileDownloadsBefore
        if ($diff -ne 1) {
            return @{ Pass = $false; Status = $status; Message = "Download counter did not increment by exactly 1 (before=$approvedProfileDownloadsBefore, after=$downloadsAfter, diff=$diff)" }
        }
        return @{ Pass = $true; Status = $status; Message = "RPC succeeded ($status) and counter incremented by exactly 1 ($approvedProfileDownloadsBefore -> $downloadsAfter)" }
    } catch {
        return @{ Pass = $false; Status = $vRes.Status; Message = "Failed to parse verification GET response JSON: $_" }
    }
}

# ------------------------------------------------------------------------------
# SEC-01: Attempt submitting approved=true via anon (MUST FAIL via RLS check)
# ------------------------------------------------------------------------------
Run-Test "SEC-01" "POST reject approved=true by anon role (RLS check)" {
    $endpoint = "$Url/rest/v1/game_profiles"
    $payload = @{
        package_name   = "com.hacker.exploit"
        display_name   = "Exploit Game"
        tracker        = "HAND_SINGLE"
        mapping        = "LANE_SWIPE"
        profile_json   = @{
            schemaVersion = 1
            packageName   = "com.hacker.exploit"
            displayName   = "Exploit Game"
            orientation   = "PORTRAIT"
            tracker       = "HAND_SINGLE"
            mapping       = "LANE_SWIPE"
        }
        schema_version = 1
        approved       = $true
        is_mock        = $true
    }

    $res = Invoke-SupabaseRequest -Method "POST" -Uri $endpoint -Body $payload
    $status = $res.Status
    $body = $res.Body

    # 1. Must be rejected by RLS boundary, NEVER because of malformed JSON (PGRST102)
    if ($body -match "PGRST102") {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Request had malformed JSON (PGRST102) rather than testing security boundary: $body" }
    }

    # 2. Must return 400, 401, or 403
    if ($status -notin 400, 401, 403) {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Expected HTTP 401/403/400 for RLS check violation, got: $status ($body)" }
    }

    # 3. Inspect JSON for exact RLS policy violation
    try {
        $json = $body | ConvertFrom-Json
        $isAuthFailure = ($json.message -match "JWT" -or $json.message -match "token" -or $json.code -match "PGRST3")
        if ($isAuthFailure) {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Rejected by auth failure rather than RLS check: $body" }
        }

        $isRlsDenied = ($json.code -eq "42501") -or ($json.message -match "row-level security")
        if ($isRlsDenied) {
            return @{ Pass = $true; Status = $status; Message = "Correctly rejected by RLS policy (HTTP $status, code $($json.code)): $($json.message)" }
        } else {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Unexpected error response (not an RLS check violation): $body" }
        }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Could not parse error response JSON: $body" }
    }
}

# ------------------------------------------------------------------------------
# SEC-02: Attempt anon UPDATE (MUST FAIL: update grant revoked on game_profiles)
# ------------------------------------------------------------------------------
Run-Test "SEC-02" "PATCH reject anon UPDATE on game_profiles (grant check)" {
    $targetId = if ($submittedProfileId) { $submittedProfileId } else { "00000000-0000-0000-0000-000000000000" }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"
    $payload = @{ approved = $true }

    $res = Invoke-SupabaseRequest -Method "PATCH" -Uri $endpoint -Body $payload
    $status = $res.Status
    $body = $res.Body

    # 1. A malformed JSON response must never count as a successful security test
    if ($body -match "PGRST102") {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Request had malformed JSON (PGRST102): $body" }
    }

    # 2. Must return 401 or 403
    if ($status -notin 401, 403) {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Expected HTTP 401/403 for revoked UPDATE grant, got: $status ($body)" }
    }

    # 3. Parse JSON and inspect for exact permission denied / insufficient privilege
    try {
        $json = $body | ConvertFrom-Json
        $isAuthFailure = ($json.message -match "JWT" -or $json.message -match "token" -or $json.code -match "PGRST3")
        if ($isAuthFailure) {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Rejected by auth failure rather than permission check: $body" }
        }

        $isPrivilegeDenied = ($json.code -eq "42501") -or ($json.message -match "permission denied.*game_profiles" -or $json.message -match "permission denied")
        if ($isPrivilegeDenied) {
            return @{ Pass = $true; Status = $status; Message = "Correctly rejected with permission denied (HTTP $status, code $($json.code)): $($json.message)" }
        } else {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Unexpected error response (not a permission denied error): $body" }
        }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Could not parse error response JSON: $body" }
    }
}

# ------------------------------------------------------------------------------
# SEC-03: Attempt anon DELETE (MUST FAIL: delete grant revoked on game_profiles)
# ------------------------------------------------------------------------------
Run-Test "SEC-03" "DELETE reject anon DELETE on game_profiles (grant check)" {
    $targetId = if ($submittedProfileId) { $submittedProfileId } else { "00000000-0000-0000-0000-000000000000" }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"

    $res = Invoke-SupabaseRequest -Method "DELETE" -Uri $endpoint
    $status = $res.Status
    $body = $res.Body

    # 1. Must return 401 or 403
    if ($status -notin 401, 403) {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Expected HTTP 401/403 for revoked DELETE grant, got: $status ($body)" }
    }

    # 2. Parse JSON and inspect for exact permission denied / insufficient privilege
    try {
        $json = $body | ConvertFrom-Json
        $isAuthFailure = ($json.message -match "JWT" -or $json.message -match "token" -or $json.code -match "PGRST3")
        if ($isAuthFailure) {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Rejected by auth failure rather than permission check: $body" }
        }

        $isPrivilegeDenied = ($json.code -eq "42501") -or ($json.message -match "permission denied.*game_profiles" -or $json.message -match "permission denied")
        if ($isPrivilegeDenied) {
            return @{ Pass = $true; Status = $status; Message = "Correctly rejected with permission denied (HTTP $status, code $($json.code)): $($json.message)" }
        } else {
            return @{ Pass = $false; Status = $status; Message = "FAILED: Unexpected error response (not a permission denied error): $body" }
        }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "FAILED: Could not parse error response JSON: $body" }
    }
}

# ------------------------------------------------------------------------------
# SEC-04: Verify unapproved profiles hidden from anon SELECT (RLS check)
# ------------------------------------------------------------------------------
Run-Test "SEC-04" "GET reject anon SELECT on unapproved profiles (RLS check)" {
    $endpoint = "$Url/rest/v1/game_profiles?package_name=eq.$testPackage&select=*"
    $res = Invoke-SupabaseRequest -Method "GET" -Uri $endpoint
    $status = $res.Status
    $body = $res.Body

    if ($status -ne 200) {
        return @{ Pass = $false; Status = $status; Message = "Expected HTTP 200, got: $status ($body)" }
    }

    try {
        $json = $body | ConvertFrom-Json
        $items = @($json)
        # Unapproved profiles must be filtered out by RLS for anon role, returning 0 rows
        $pass = ($items.Count -eq 0)
        return @{
            Pass = $pass
            Status = $status
            Message = if ($pass) { "Unapproved profile is successfully hidden by RLS (0 rows returned)" } else { "Security breach: unapproved profile returned to anon role!" }
        }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "Failed to parse JSON: $_" }
    }
}

# ------------------------------------------------------------------------------
# R5: Create Session
# ------------------------------------------------------------------------------
$createdSessionId = $null
Run-Test "R5" "POST create telemetry session" {
    $endpoint = "$Url/rest/v1/sessions"
    $payload = @{
        device_id       = [System.Guid]::NewGuid().ToString()
        device_model    = "CI Verification Host"
        android_version = 34
        game_package    = "com.test.mockgame"
        tracker         = "HAND_SINGLE"
        perf_mode       = "BALANCED"
        avg_fps         = 24.8
        p95_latency_ms  = 62.4
        duration_s      = 30
        gestures_count  = 12
        is_mock         = $true
    }

    $res = Invoke-SupabaseRequest -Method "POST" -Uri $endpoint -Body $payload -Prefer "return=representation"
    $status = $res.Status
    $body = $res.Body

    if ($status -eq 201) {
        try {
            $json = $body | ConvertFrom-Json
            $items = @($json)
            if ($items.Count -gt 0 -and $items[0].id) {
                $parsedGuid = [System.Guid]::Empty
                if ([System.Guid]::TryParse($items[0].id.ToString(), [ref]$parsedGuid)) {
                    $script:createdSessionId = $items[0].id.ToString()
                    return @{ Pass = $true; Status = $status; Message = "Session created with ID: $script:createdSessionId" }
                }
            }
            return @{ Pass = $false; Status = $status; Message = "Created (201) but failed to capture valid session UUID: $body" }
        } catch {
            return @{ Pass = $false; Status = $status; Message = "Created (201) but JSON parse failed: $_`nBody: $body" }
        }
    }
    return @{ Pass = $false; Status = $status; Message = "Failed to create session: $body" }
}

# ------------------------------------------------------------------------------
# R6: Insert Gesture Events
# ------------------------------------------------------------------------------
Run-Test "R6" "POST batch insert gesture events" {
    if (-not $createdSessionId) {
        return @{ Pass = $false; Status = 0; Message = "Skipped: R5 did not produce a session ID" }
    }

    $endpoint = "$Url/rest/v1/gesture_events"
    $payload = @(
        @{ session_id = $createdSessionId; event_type = "SWIPE"; magnitude = 1.4; is_mock = $true },
        @{ session_id = $createdSessionId; event_type = "SLASH"; magnitude = 0.8; is_mock = $true }
    )

    $res = Invoke-SupabaseRequest -Method "POST" -Uri $endpoint -Body $payload
    $status = $res.Status
    $body = $res.Body

    $pass = ($status -eq 201)
    return @{ Pass = $pass; Status = $status; Message = if ($pass) { "Batch gesture events inserted successfully" } else { "Failed to insert gesture events: $body" } }
}

# ------------------------------------------------------------------------------
# R7: Retrieve Session Stats View
# ------------------------------------------------------------------------------
Run-Test "R7" "GET aggregate session stats view" {
    $endpoint = "$Url/rest/v1/v_session_stats?select=*"
    $res = Invoke-SupabaseRequest -Method "GET" -Uri $endpoint
    $status = $res.Status
    $body = $res.Body

    if ($status -ne 200) {
        return @{ Pass = $false; Status = $status; Message = "Failed to query view: $body" }
    }

    try {
        $json = $body | ConvertFrom-Json
        $items = @($json)

        # When records are returned, validate that expected analytics fields exist
        if ($items.Count -gt 0) {
            foreach ($row in $items) {
                $props = $row.PSObject.Properties.Name
                $hasDeviceModel = $props -contains "device_model"
                $hasTracker     = $props -contains "tracker"
                $hasPerfMode    = $props -contains "perf_mode"
                $hasSessions    = $props -contains "sessions"
                $hasAvgFps      = $props -contains "avg_fps"
                $hasAvgLatency  = $props -contains "avg_p95_latency_ms"

                if (-not ($hasDeviceModel -and $hasTracker -and $hasPerfMode -and $hasSessions -and $hasAvgFps -and $hasAvgLatency)) {
                    return @{ Pass = $false; Status = $status; Message = "Record in v_session_stats is missing required analytics fields: $($row | ConvertTo-Json -Compress)" }
                }
            }
            return @{ Pass = $true; Status = $status; Message = "View returned $($items.Count) rows with valid analytics fields (device_model, tracker, perf_mode, sessions, avg_fps, avg_p95_latency_ms)" }
        }

        # Empty result array is valid (e.g. before real telemetry is recorded)
        return @{ Pass = $true; Status = $status; Message = "View returned 0 rows (HTTP 200)" }
    } catch {
        return @{ Pass = $false; Status = $status; Message = "HTTP 200 but failed to parse JSON: $_" }
    }
}

# ------------------------------------------------------------------------------
# R8: Live Realtime WebSocket Subscription & Event Delivery
# ------------------------------------------------------------------------------
Run-Test "R8" "WebSocket subscribe to public:gesture_events INSERT and verify delivery" {
    if (-not $createdSessionId) {
        return @{ Pass = $false; Status = 0; Message = "Skipped: R5 did not produce a session ID" }
    }

    $wsUrlStr = $Url.Replace("https://", "wss://").Replace("http://", "ws://") + "/realtime/v1/websocket?apikey=$AnonKey&vsn=1.0.0"
    $wsUri = [System.Uri]::new($wsUrlStr)
    $ws = [System.Net.WebSockets.ClientWebSocket]::new()
    $connectCts = [System.Threading.CancellationTokenSource]::new(5000) # 5s connect timeout

    try {
        # Helper: Read a single full WebSocket message (with fragmented-message assembly)
        # Parses JSON immediately; if token times out, throws OperationCanceledException
        function Read-FullWsMessage([System.Net.WebSockets.ClientWebSocket]$socket, [System.Threading.CancellationToken]$token) {
            $buf = [byte[]]::new(8192)
            $mem = [System.IO.MemoryStream]::new()
            do {
                $seg = [System.ArraySegment[byte]]::new($buf)
                $recvTask = $socket.ReceiveAsync($seg, $token)
                $recvTask.Wait() # Throws if token cancels
                $result = $recvTask.Result
                if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                    return $null
                }
                $mem.Write($buf, 0, $result.Count)
            } while (-not $result.EndOfMessage)
            $raw = [System.Text.Encoding]::UTF8.GetString($mem.ToArray())
            return ($raw | ConvertFrom-Json)
        }

        # 1. Connect
        $connectTask = $ws.ConnectAsync($wsUri, $connectCts.Token)
        $connectTask.Wait(5000)

        if ($ws.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
            return @{ Pass = $false; Status = 0; Message = "WebSocket failed to connect; state=$($ws.State)" }
        }

        # 2. Join Phoenix channel for gesture_events INSERT
        $joinRef = "1"
        $joinTopic = "realtime:public:gesture_events"
        $joinMsgObj = @{
            topic    = $joinTopic
            event    = "phx_join"
            payload  = @{
                config = @{
                    postgres_changes = @(
                        @{ event = "INSERT"; schema = "public"; table = "gesture_events" }
                    )
                }
            }
            ref      = $joinRef
            join_ref = $joinRef
        }
        $joinMsgJson = $joinMsgObj | ConvertTo-Json -Depth 10 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($joinMsgJson)
        $segment = [System.ArraySegment[byte]]::new($bytes)
        $sendTask = $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None)
        $sendOk = $sendTask.Wait(3000)
        if (-not $sendOk -or $sendTask.IsFaulted -or $sendTask.IsCanceled) {
            return @{ Pass = $false; Status = 0; Message = "Failed to send phx_join message to WebSocket within 3s timeout" }
        }

        # Read until join reply is received
        $joined = $false
        $joinCts = [System.Threading.CancellationTokenSource]::new(6000)
        try {
            while (-not $joined) {
                $msgObj = Read-FullWsMessage $ws $joinCts.Token
                if ($null -eq $msgObj) {
                    return @{ Pass = $false; Status = 0; Message = "WebSocket closed while waiting for phx_join acknowledgment" }
                }
                if ($msgObj.event -eq "phx_reply" -and $msgObj.topic -eq $joinTopic -and $msgObj.ref -eq $joinRef) {
                    if ($msgObj.payload -and $msgObj.payload.status -eq "ok") {
                        $joined = $true
                        break
                    } else {
                        return @{ Pass = $false; Status = 0; Message = "phx_join rejected with status: $($msgObj.payload.status)" }
                    }
                }
            }
        } catch [System.AggregateException] {
            if ($_.InnerException -is [System.OperationCanceledException]) {
                return @{ Pass = $false; Status = 0; Message = "Timed out waiting for Realtime channel phx_join acknowledgment (6s)" }
            }
            return @{ Pass = $false; Status = 0; Message = "WebSocket error during join: $_" }
        } catch [System.OperationCanceledException] {
            return @{ Pass = $false; Status = 0; Message = "Timed out waiting for Realtime channel phx_join acknowledgment (6s)" }
        } finally {
            $joinCts.Dispose()
        }

        if (-not $joined) {
            return @{ Pass = $false; Status = 0; Message = "Failed to establish Realtime channel subscription" }
        }

        # 3. Fire a unique test gesture event
        $uniqueMag = 99.42
        $triggerEndpoint = "$Url/rest/v1/gesture_events"
        $triggerPayload = @(
            @{ session_id = $createdSessionId; event_type = "SLASH"; magnitude = $uniqueMag; is_mock = $true }
        )

        $postRes = Invoke-SupabaseRequest -Method "POST" -Uri $triggerEndpoint -Body $triggerPayload
        if ($postRes.Status -ne 201) {
            return @{ Pass = $false; Status = $postRes.Status; Message = "Failed to trigger test event via REST: $($postRes.Body)" }
        }

        # 4. Listen on WebSocket for delivery of event matching exact record
        $eventReceived = $false
        $listenCts = [System.Threading.CancellationTokenSource]::new(10000)
        try {
            while (-not $eventReceived) {
                $msgObj = Read-FullWsMessage $ws $listenCts.Token
                if ($null -eq $msgObj) {
                    return @{ Pass = $false; Status = 0; Message = "WebSocket closed while waiting for Realtime event delivery" }
                }

                # Validate exact record structure:
                # payload type is postgres_changes, schema=public, table=gesture_events, type=INSERT,
                # record.session_id equals createdSessionId, record.magnitude equals 99.42
                if ($msgObj.event -eq "postgres_changes") {
                    $data = $msgObj.payload.data
                    if ($null -ne $data) {
                        $schemaMatches = ($data.schema -eq "public")
                        $tableMatches  = ($data.table -eq "gesture_events")
                        $typeMatches   = ($data.type -eq "INSERT")
                        $rec           = $data.record

                        if ($schemaMatches -and $tableMatches -and $typeMatches -and $null -ne $rec) {
                            $sessionMatches = ($rec.session_id -eq $createdSessionId)
                            $magMatches     = ([Math]::Abs([double]$rec.magnitude - 99.42) -lt 0.001)

                            if ($sessionMatches -and $magMatches) {
                                $eventReceived = $true
                                break
                            }
                        }
                    }
                }
            }
        } catch [System.AggregateException] {
            if ($_.InnerException -is [System.OperationCanceledException]) {
                return @{ Pass = $false; Status = 0; Message = "WebSocket connected and subscribed, but test event was not received within timeout (10s)" }
            }
            return @{ Pass = $false; Status = 0; Message = "Realtime WebSocket error while waiting for event: $_" }
        } catch [System.OperationCanceledException] {
            return @{ Pass = $false; Status = 0; Message = "WebSocket connected and subscribed, but test event was not received within timeout (10s)" }
        } finally {
            $listenCts.Dispose()
        }

        # 5. Clean teardown
        try {
            if ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
                $closeTask = $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "Closing test", [System.Threading.CancellationToken]::None)
                $closeTask.Wait(2000)
            }
        } catch { }

        if ($eventReceived) {
            return @{ Pass = $true; Status = 200; Message = "Realtime INSERT received and validated via WebSocket subscription" }
        } else {
            return @{ Pass = $false; Status = 0; Message = "WebSocket connected and subscribed, but event was not received within timeout" }
        }

    } catch {
        return @{ Pass = $false; Status = 0; Message = "Realtime WebSocket error: $_" }
    } finally {
        if ($ws) { $ws.Dispose() }
        if ($connectCts) { $connectCts.Dispose() }
    }
}

Write-Host "`n================================================================="
Write-Host "SUMMARY: Passed=$passed, Failed=$failed"
Write-Host "================================================================="
if ($failed -gt 0) { exit 1 } else { exit 0 }
