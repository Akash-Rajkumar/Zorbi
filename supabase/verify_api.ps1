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

    # Diagnostic message counters using a single shared object across all scopes
    $wsStats = [PSCustomObject]@{
        Total           = 0
        PhxReply        = 0
        System          = 0
        PostgresChanges = 0
        Unrecognized    = 0
    }
    $statsEmitted = $false

    $wsReader = @{
        Socket      = $ws
        PendingTask = $null
        Buffer      = [byte[]]::new(8192)
        Memory      = [System.IO.MemoryStream]::new()
    }

    try {
        # Helper: Extract underlying exception message recursively without secrets
        function Get-DetailedException([System.Exception]$ex, [System.Net.WebSockets.ClientWebSocket]$socket, [string]$stage) {
            $wsState = if ($socket) { $socket.State.ToString() } else { "Unknown" }
            $chain = @()
            $curr = $ex
            while ($curr) {
                $chain += "$($curr.GetType().FullName): $($curr.Message)"
                if ($curr -is [System.AggregateException] -and $curr.InnerExceptions) {
                    foreach ($sub in $curr.InnerExceptions) {
                        if ($sub -ne $curr.InnerException) {
                            $chain += "  [Inner] $($sub.GetType().FullName): $($sub.Message)"
                        }
                    }
                }
                $curr = $curr.InnerException
            }
            return "Stage: $stage | WebSocket State: $wsState | Details: $($chain -join ' ---> ')"
        }

        # Helper: Log raw sanitized WebSocket diagnostics and update counters
        function Log-WsMessageDiagnostic([object]$msgObj, $stats) {
            if ($null -eq $msgObj) { return }
            $stats.Total++

            $evt = $msgObj.event
            $topic = $msgObj.topic
            $ref = $msgObj.ref
            $joinRef = $msgObj.join_ref
            $p = $msgObj.payload
            $pProps = if ($p -and $p.PSObject) { ($p.PSObject.Properties.Name -join ", ") } else { "<none>" }

            Write-Host "    [WS RECV] event='$evt' topic='$topic' ref='$ref' join_ref='$joinRef' payload_keys=[$pProps]"

            if ($evt -eq "phx_reply") {
                $stats.PhxReply++
                $status = if ($p -and $p.status) { $p.status } else { "<none>" }
                $subIds = "<none>"
                if ($p -and $p.response -and $p.response.postgres_changes) {
                    $subIds = ($p.response.postgres_changes | ForEach-Object { "$($_.id) [$($_.event) $($_.schema).$($_.table)]" }) -join "; "
                }
                Write-Host "      -> phx_reply: status='$status' postgres_changes_subs=[$subIds]"
            } elseif ($evt -eq "system") {
                $stats.System++
                $sysStatus = if ($p -and $p.status) { $p.status } else { "<none>" }
                $sysMsg = if ($p -and $p.message) { $p.message } else { "<none>" }
                $sysExt = if ($p -and $p.extension) { $p.extension } else { "<none>" }
                $sysChan = if ($p -and $p.channel) { $p.channel } else { "<unavailable>" }
                Write-Host "      -> system: status='$sysStatus' extension='$sysExt' channel='$sysChan' message='$sysMsg'"
            } elseif ($evt -eq "postgres_changes") {
                $stats.PostgresChanges++
                $d = if ($p) { $p.data } else { $null }
                $schema = if ($d -and $d.schema) { $d.schema } elseif ($p -and $p.schema) { $p.schema } else { "<none>" }
                $table  = if ($d -and $d.table) { $d.table } elseif ($p -and $p.table) { $p.table } else { "<none>" }
                $type   = if ($d -and $d.type) { $d.type } elseif ($p -and $p.eventType) { $p.eventType } elseif ($p -and $p.type) { $p.type } else { "<none>" }
                $rec    = if ($d -and $null -ne $d.record) { $d.record } elseif ($p -and $null -ne $p.record) { $p.record } elseif ($p -and $null -ne $p.new) { $p.new } else { $null }
                $hasSession = if ($rec -and $rec.session_id) { "session_id=$($rec.session_id)" } else { "session_id=<missing>" }
                $hasMag = if ($rec -and $null -ne $rec.magnitude) { "magnitude=$($rec.magnitude)" } else { "magnitude=<missing>" }
                Write-Host "      -> postgres_changes: schema='$schema' table='$table' type='$type' record=[$hasSession, $hasMag]"
            } elseif ($evt -eq "phx_error" -or $evt -eq "phx_close") {
                $reason = if ($p -and $p.reason) { $p.reason } else { "<none>" }
                Write-Host "      -> $($evt): reason='$reason'"
            } else {
                $stats.Unrecognized++
                Write-Host "      -> unrecognized event: '$evt'"
            }
        }

        # Helper: Bounded receive without cancelling in-flight ReceiveAsync operation
        function Read-WsMessageWithTimeout {
            param(
                [Parameter(Mandatory=$true)]$Reader,
                [Parameter(Mandatory=$true)][int]$TimeoutMs
            )

            $socket = $Reader.Socket
            $deadline = [System.DateTime]::UtcNow.AddMilliseconds($TimeoutMs)

            while ($true) {
                $remainingMs = [int]($deadline - [System.DateTime]::UtcNow).TotalMilliseconds
                if ($remainingMs -le 0) {
                    return @{ Status = "Timeout"; Message = $null; State = $socket.State }
                }

                if ($null -eq $Reader.PendingTask) {
                    if ($socket.State -ne [System.Net.WebSockets.WebSocketState]::Open -and
                        $socket.State -ne [System.Net.WebSockets.WebSocketState]::CloseReceived) {
                        return @{ Status = "SocketClosed"; Message = $null; State = $socket.State }
                    }

                    $seg = [System.ArraySegment[byte]]::new($Reader.Buffer)
                    $Reader.PendingTask = $socket.ReceiveAsync($seg, [System.Threading.CancellationToken]::None)
                }

                $waitSlice = [Math]::Max(1, [Math]::Min($remainingMs, 250))
                $completed = $false
                try {
                    $completed = $Reader.PendingTask.Wait($waitSlice)
                } catch {
                    $taskEx = $Reader.PendingTask.Exception
                    $Reader.PendingTask = $null
                    $unwrapped = if ($taskEx -and $taskEx.InnerException) { $taskEx.InnerException } else { $_.Exception }
                    return @{ Status = "Error"; Exception = $unwrapped; State = $socket.State }
                }

                if (-not $completed) {
                    if ([System.DateTime]::UtcNow -ge $deadline) {
                        return @{ Status = "Timeout"; Message = $null; State = $socket.State }
                    }
                    continue
                }

                $recvResult = $Reader.PendingTask.Result
                $Reader.PendingTask = $null

                if ($recvResult.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                    return @{
                        Status           = "Close"
                        Message          = $null
                        CloseStatus      = $socket.CloseStatus
                        CloseDescription = $socket.CloseStatusDescription
                        State            = $socket.State
                    }
                }

                $Reader.Memory.Write($Reader.Buffer, 0, $recvResult.Count)

                if ($recvResult.EndOfMessage) {
                    $bytes = $Reader.Memory.ToArray()
                    $Reader.Memory.SetLength(0)
                    $rawJson = [System.Text.Encoding]::UTF8.GetString($bytes)
                    try {
                        $jsonObj = $rawJson | ConvertFrom-Json
                        return @{ Status = "Ok"; Message = $jsonObj; Raw = $rawJson }
                    } catch {
                        return @{ Status = "ParseError"; Error = $_; Raw = $rawJson; State = $socket.State }
                    }
                }
            }
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

        # Phase 2A: Read until join reply is received (Budget: 6000 ms)
        $channelJoined = $false
        $subscriptionIds = @()
        $subscriptionReady = $false
        $readinessState = "pending"
        $subDetails = "<none>"

        $joinDeadline = [System.DateTime]::UtcNow.AddMilliseconds(6000)
        while (-not $channelJoined -and [System.DateTime]::UtcNow -lt $joinDeadline) {
            $rem = [int]($joinDeadline - [System.DateTime]::UtcNow).TotalMilliseconds
            if ($rem -le 0) { break }
            $res = Read-WsMessageWithTimeout $wsReader $rem

            if ($res.Status -eq "Timeout") {
                break
            } elseif ($res.Status -eq "Close") {
                $closeDesc = if ($res.CloseDescription) { " ($($res.CloseStatus): $($res.CloseDescription))" } else { "" }
                return @{ Pass = $false; Status = 0; Message = "WebSocket closed by server while waiting for phx_join acknowledgment$closeDesc" }
            } elseif ($res.Status -eq "Error") {
                return @{ Pass = $false; Status = 0; Message = "WebSocket transport error during join acknowledgment: $(Get-DetailedException $res.Exception $ws 'join_ack')" }
            } elseif ($res.Status -eq "Ok") {
                $msgObj = $res.Message
                Log-WsMessageDiagnostic $msgObj $wsStats

                if ($msgObj.event -eq "phx_reply" -and $msgObj.topic -eq $joinTopic -and $msgObj.ref -eq $joinRef) {
                    if ($msgObj.payload -and $msgObj.payload.status -eq "ok") {
                        $channelJoined = $true
                        if ($msgObj.payload.response -and $msgObj.payload.response.postgres_changes) {
                            $subscriptionIds = @($msgObj.payload.response.postgres_changes | ForEach-Object { $_.id })
                            $subDetails = ($msgObj.payload.response.postgres_changes | ForEach-Object { "$($_.id) [$($_.event) $($_.schema).$($_.table)]" }) -join "; "
                        }
                        $readinessState = "join_acknowledged"
                        break
                    } else {
                        return @{ Pass = $false; Status = 0; Message = "phx_join rejected with status: $($msgObj.payload.status)" }
                    }
                }
            }
        }

        if (-not $channelJoined) {
            return @{ Pass = $false; Status = 0; Message = "Timed out waiting for Realtime channel phx_join acknowledgment (6s) | State: $($ws.State)" }
        }

        # Phase 2B: Optional system confirmation window (Budget: 1500 ms)
        # Note: Bounded wait preserves any in-flight receive task without cancelling or aborting socket
        if ($channelJoined) {
            $sysDeadline = [System.DateTime]::UtcNow.AddMilliseconds(1500)
            while (-not $subscriptionReady -and [System.DateTime]::UtcNow -lt $sysDeadline) {
                $rem = [int]($sysDeadline - [System.DateTime]::UtcNow).TotalMilliseconds
                if ($rem -le 0) { break }
                $res = Read-WsMessageWithTimeout $wsReader $rem

                if ($res.Status -eq "Timeout") {
                    # Optional system confirmation not sent in this window; in-flight receive task is preserved
                    break
                } elseif ($res.Status -eq "Close") {
                    $closeDesc = if ($res.CloseDescription) { " ($($res.CloseStatus): $($res.CloseDescription))" } else { "" }
                    return @{ Pass = $false; Status = 0; Message = "WebSocket closed by server during system confirmation window$closeDesc" }
                } elseif ($res.Status -eq "Error") {
                    return @{ Pass = $false; Status = 0; Message = "WebSocket transport error during system confirmation: $(Get-DetailedException $res.Exception $ws 'system_check')" }
                } elseif ($res.Status -eq "Ok") {
                    $sysMsgObj = $res.Message
                    Log-WsMessageDiagnostic $sysMsgObj $wsStats
                    if ($sysMsgObj.event -eq "system" -and $null -ne $sysMsgObj.payload) {
                        $sp = $sysMsgObj.payload
                        $isExtMatch = ($sp.extension -eq "postgres_changes")
                        $isStatusOk = ($sp.status -eq "ok")
                        $isChanMatch = ($sp.channel -eq "realtime:public:gesture_events" -or $sp.channel -eq "public:gesture_events")

                        if ($isExtMatch -and $isStatusOk -and $isChanMatch) {
                            $subscriptionReady = $true
                            $readinessState = "system_confirmed"
                            break
                        }
                    }
                }
            }
        }

        Write-Host "    Realtime channel joined: $channelJoined | Readiness: $readinessState | Subscription IDs: [$(($subscriptionIds -join ', '))] (sub_details: $subDetails)"

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

        # 4. Listen on WebSocket for delivery of event matching exact record (Budget: 10000 ms)
        $eventReceived = $false
        $eventSw = [System.Diagnostics.Stopwatch]::StartNew()
        $eventDeadline = [System.DateTime]::UtcNow.AddMilliseconds(10000)

        while (-not $eventReceived -and [System.DateTime]::UtcNow -lt $eventDeadline) {
            $rem = [int]($eventDeadline - [System.DateTime]::UtcNow).TotalMilliseconds
            if ($rem -le 0) { break }
            $res = Read-WsMessageWithTimeout $wsReader $rem

            if ($res.Status -eq "Timeout") {
                break
            } elseif ($res.Status -eq "Close") {
                $closeDesc = if ($res.CloseDescription) { " ($($res.CloseStatus): $($res.CloseDescription))" } else { "" }
                return @{ Pass = $false; Status = 0; Message = "WebSocket closed by server while waiting for Realtime event delivery$closeDesc" }
            } elseif ($res.Status -eq "Error") {
                return @{ Pass = $false; Status = 0; Message = "WebSocket transport error while waiting for event: $(Get-DetailedException $res.Exception $ws 'event_receive')" }
            } elseif ($res.Status -eq "Ok") {
                $msgObj = $res.Message
                Log-WsMessageDiagnostic $msgObj $wsStats

                # Check if system message arrives during event phase
                if ($msgObj.event -eq "system" -and $null -ne $msgObj.payload) {
                    $sp = $msgObj.payload
                    $isExtMatch = ($sp.extension -eq "postgres_changes")
                    $isStatusOk = ($sp.status -eq "ok")
                    $isChanMatch = ($sp.channel -eq "realtime:public:gesture_events" -or $sp.channel -eq "public:gesture_events")
                    if ($isExtMatch -and $isStatusOk -and $isChanMatch) {
                        $subscriptionReady = $true
                        $readinessState = "system_confirmed"
                    }
                    continue
                }

                # Validate exact record structure:
                # Primary: raw Phoenix v1.0.0 protocol: payload.data.schema, payload.data.table, payload.data.type, payload.data.record
                # Safe fallback: payload.schema, payload.table, payload.eventType, payload.new
                if ($msgObj.event -eq "postgres_changes") {
                    $p = $msgObj.payload
                    $d = if ($p) { $p.data } else { $null }

                    # If incoming message includes subscription IDs, validate against join acknowledgement IDs
                    $incomingSubIds = @()
                    if ($p -and $p.ids) {
                        $incomingSubIds = @($p.ids)
                    } elseif ($p -and $p.subscription_ids) {
                        $incomingSubIds = @($p.subscription_ids)
                    } elseif ($d -and $d.ids) {
                        $incomingSubIds = @($d.ids)
                    }

                    if ($incomingSubIds.Count -gt 0 -and $subscriptionIds.Count -gt 0) {
                        $matchedSubId = $false
                        foreach ($subId in $incomingSubIds) {
                            if ($subscriptionIds -contains $subId) {
                                $matchedSubId = $true
                                break
                            }
                        }
                        if (-not $matchedSubId) {
                            Write-Host "    [WS RECV] Skipping postgres_changes message with unrelated subscription IDs: [$($incomingSubIds -join ', ')] (expected: [$($subscriptionIds -join ', ')])"
                            continue
                        }
                    }

                    $schema  = if ($d -and $d.schema) { $d.schema } elseif ($p -and $p.schema) { $p.schema } else { $null }
                    $table   = if ($d -and $d.table) { $d.table } elseif ($p -and $p.table) { $p.table } else { $null }
                    $evtType = if ($d -and $d.type) { $d.type } elseif ($p -and $p.eventType) { $p.eventType } elseif ($p -and $p.type) { $p.type } else { $null }
                    $rec     = if ($d -and $null -ne $d.record) { $d.record } elseif ($p -and $null -ne $p.record) { $p.record } elseif ($p -and $null -ne $p.new) { $p.new } else { $null }

                    $schemaMatches = ($schema -eq "public")
                    $tableMatches  = ($table -eq "gesture_events")
                    $typeMatches   = ($evtType -eq "INSERT")

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
        $eventSw.Stop()

        if ($eventReceived) {
            return @{ Pass = $true; Status = 200; Message = "Realtime INSERT received and validated via WebSocket subscription" }
        } else {
            $elapsedS = [Math]::Round($eventSw.Elapsed.TotalSeconds, 2)
            $pgCount = $wsStats.PostgresChanges
            $sysCount = $wsStats.System
            $totCount = $wsStats.Total
            $subIdsStr = ($subscriptionIds -join ', ')
            return @{
                Pass    = $false
                Status  = 0
                Message = "Event receive timed out after ${elapsedS}s | Stage: event_receive | WebSocket State: $($ws.State) | frames_received=$totCount (phx_reply=$($wsStats.PhxReply), system=$sysCount, postgres_changes=$pgCount) | join_ack=$channelJoined | sub_ids=[$subIdsStr] | system_confirmed=$subscriptionReady"
            }
        }

    } catch {
        $ex = if ($_.Exception) { $_.Exception } else { $_ }
        return @{ Pass = $false; Status = 0; Message = "Realtime WebSocket error: $(Get-DetailedException $ex $ws 'general')" }
    } finally {
        if (-not $statsEmitted) {
            Write-Host "    [WS STATS] Total=$($wsStats.Total) | phx_reply=$($wsStats.PhxReply) | system=$($wsStats.System) | postgres_changes=$($wsStats.PostgresChanges) | unrecognized=$($wsStats.Unrecognized)"
            $statsEmitted = $true
        }
        if ($ws) {
            try {
                # Handle completed pending task safely before deciding teardown action
                if ($wsReader -and $wsReader.PendingTask -and $wsReader.PendingTask.IsCompleted) {
                    if ($wsReader.PendingTask.IsFaulted) {
                        $null = $wsReader.PendingTask.Exception
                    }
                    $wsReader.PendingTask = $null
                }

                $hasPendingReceive = ($wsReader -and $wsReader.PendingTask -and -not $wsReader.PendingTask.IsCompleted)
                if ($hasPendingReceive) {
                    # Incomplete pending receive exists: abort socket to terminate receive without concurrent CloseAsync
                    try { $ws.Abort() } catch { }
                    $wsReader.PendingTask = $null
                } elseif ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
                    # No pending receive and socket is Open: bounded graceful close handshake
                    $closeTask = $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "Closing test", [System.Threading.CancellationToken]::None)
                    $null = $closeTask.Wait(2000)
                }
            } catch { }
            try { $ws.Dispose() } catch { }
        }
        if ($wsReader -and $wsReader.Memory) {
            try { $wsReader.Memory.Dispose() } catch { }
        }
        if ($connectCts) {
            try { $connectCts.Dispose() } catch { }
        }
    }
}

Write-Host "`n================================================================="
Write-Host "SUMMARY: Passed=$passed, Failed=$failed"
Write-Host "================================================================="
if ($failed -gt 0) { exit 1 } else { exit 0 }
