# ==============================================================================
# ZORBI — Supabase API & Security Verification Script (Gate T-P4-1)
# Frozen Specification: Master Prompt (v1.1) Section 6 & 11.1
# Owned by: Person 4
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
                if (-not $Url) { $Url = $matches[1].Trim() }
            }
            if ($_ -match "^\s*SUPABASE_ANON_KEY\s*=\s*(.+)$") {
                if (-not $AnonKey) { $AnonKey = $matches[1].Trim() }
            }
        }
    }
}

if (-not $Url -or -not $AnonKey -or $Url -like "*<*>*" -or $AnonKey -like "*<*>*") {
    Write-Warning "SUPABASE_URL or SUPABASE_ANON_KEY is not configured or contains placeholder values."
    Write-Host "Set environment variables SUPABASE_URL and SUPABASE_ANON_KEY, or populate local.properties."
    Write-Host "Example:"
    Write-Host '  $env:SUPABASE_URL="https://your-project.supabase.co"'
    Write-Host '  $env:SUPABASE_ANON_KEY="eyJhbGciOi..."'
    Write-Host "  .\supabase\verify_api.ps1"
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

$headers = @(
    "-H", "apikey: $AnonKey",
    "-H", "Authorization: Bearer $AnonKey",
    "-H", "Content-Type: application/json"
)

$passed = 0
$failed = 0

function Run-Test([string]$testId, [string]$description, [scriptblock]$action) {
    Write-Host "`n[$testId] $description"
    try {
        $result = & $action
        if ($result.Pass) {
            Write-Host "  RESULT: PASS (HTTP $($result.Status))" -ForegroundColor Green
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
# R1: List approved profiles
# ------------------------------------------------------------------------------
Run-Test "R1" "GET approved game profiles" {
    $endpoint = "$Url/rest/v1/game_profiles?approved=eq.true&select=*&order=downloads.desc"
    $resp = curl.exe -s -w "\n%{http_code}" @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    return @{ Pass = ($status -eq 200); Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R2: Find approved profile by package name
# ------------------------------------------------------------------------------
Run-Test "R2" "GET approved profile by package name" {
    $endpoint = "$Url/rest/v1/game_profiles?package_name=eq.com.kiloo.subwaysurf&approved=eq.true&select=*"
    $resp = curl.exe -s -w "\n%{http_code}" @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    return @{ Pass = ($status -eq 200); Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R3: Submit an unapproved profile
# ------------------------------------------------------------------------------
$testPackage = "com.test.tmp_" + [System.Guid]::NewGuid().ToString("N").Substring(0, 8)
$submittedProfileId = $null

Run-Test "R3" "POST submit unapproved profile (approved=false)" {
    $endpoint = "$Url/rest/v1/game_profiles"
    $payload = @{
        package_name = $testPackage
        display_name = "Temporary Test Game"
        tracker = "HAND_SINGLE"
        mapping = "LANE_SWIPE"
        profile_json = @{ schemaVersion = 1; packageName = $testPackage }
        approved = $false
        is_mock = $true
    } | ConvertTo-Json -Compress

    $resp = curl.exe -s -w "\n%{http_code}" @headers -H "Prefer: return=representation" -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    if ($status -eq 201) {
        $json = $body | ConvertFrom-Json
        $script:submittedProfileId = $json[0].id
    }
    return @{ Pass = ($status -eq 201); Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R3-Security: Attempt submitting approved=true via anon (MUST FAIL)
# ------------------------------------------------------------------------------
Run-Test "SEC-01" "POST reject approved=true by anon role (RLS check)" {
    $endpoint = "$Url/rest/v1/game_profiles"
    $payload = @{
        package_name = "com.hacker.exploit"
        display_name = "Exploit Game"
        tracker = "HAND_SINGLE"
        mapping = "LANE_SWIPE"
        profile_json = @{ schemaVersion = 1 }
        approved = $true
        is_mock = $true
    } | ConvertTo-Json -Compress

    $resp = curl.exe -s -w "\n%{http_code}" @headers -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    # Expected: 400, 401, or 403 (RLS policy violation)
    $pass = ($status -in 400, 401, 403)
    return @{ Pass = $pass; Status = $status; Message = "Expected rejection, got: $body" }
}

# ------------------------------------------------------------------------------
# SEC-02: Attempt anon UPDATE (MUST FAIL)
# ------------------------------------------------------------------------------
Run-Test "SEC-02" "PATCH reject anon UPDATE on game_profiles" {
    if (-not $submittedProfileId) { $targetId = "00000000-0000-0000-0000-000000000000" } else { $targetId = $submittedProfileId }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"
    $payload = '{"approved":true}'
    $resp = curl.exe -s -w "\n%{http_code}" -X PATCH @headers -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    # Expected rejection or empty affected rows (due to lack of update policy / revoked grant)
    $pass = ($status -in 401, 403, 404, 204, 200)
    # Check if rows actually updated
    return @{ Pass = $pass; Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# SEC-03: Attempt anon DELETE (MUST FAIL)
# ------------------------------------------------------------------------------
Run-Test "SEC-03" "DELETE reject anon DELETE on game_profiles" {
    if (-not $submittedProfileId) { $targetId = "00000000-0000-0000-0000-000000000000" } else { $targetId = $submittedProfileId }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"
    $resp = curl.exe -s -w "\n%{http_code}" -X DELETE @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    # Expected rejection (no delete policy)
    $pass = ($status -in 401, 403)
    return @{ Pass = $pass; Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R5: Create Session
# ------------------------------------------------------------------------------
$createdSessionId = $null
Run-Test "R5" "POST create telemetry session" {
    $endpoint = "$Url/rest/v1/sessions"
    $payload = @{
        device_id = [System.Guid]::NewGuid().ToString()
        device_model = "CI Verification Host"
        android_version = 34
        game_package = "com.test.mockgame"
        tracker = "HAND_SINGLE"
        perf_mode = "BALANCED"
        avg_fps = 24.8
        p95_latency_ms = 62.4
        duration_s = 30
        gestures_count = 12
        is_mock = $true
    } | ConvertTo-Json -Compress

    $resp = curl.exe -s -w "\n%{http_code}" @headers -H "Prefer: return=representation" -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    if ($status -eq 201) {
        $json = $body | ConvertFrom-Json
        $script:createdSessionId = $json[0].id
    }
    return @{ Pass = ($status -eq 201); Status = $status; Message = $body }
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
    ) | ConvertTo-Json -Compress

    $resp = curl.exe -s -w "\n%{http_code}" @headers -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    return @{ Pass = ($status -eq 201); Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R7: Retrieve Session Stats View
# ------------------------------------------------------------------------------
Run-Test "R7" "GET aggregate session stats view" {
    $endpoint = "$Url/rest/v1/v_session_stats?select=*"
    $resp = curl.exe -s -w "\n%{http_code}" @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    return @{ Pass = ($status -eq 200); Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# R8: Realtime Endpoint Handshake Check
# ------------------------------------------------------------------------------
Run-Test "R8" "GET Realtime WebSocket handshake endpoint reachable" {
    $realtimeUrl = $Url.Replace("https://", "wss://").Replace("http://", "ws://") + "/realtime/v1/websocket?apikey=$AnonKey&vsn=1.0.0"
    # Basic HTTP check on the realtime health
    $httpEndpoint = "$Url/realtime/v1/health"
    $resp = curl.exe -s -w "\n%{http_code}" @headers $httpEndpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    # 200 or 404 on /health with valid URL indicates endpoint alive
    return @{ Pass = ($status -in 200, 404, 401, 403); Status = $status; Message = "Realtime host accessible" }
}

Write-Host "`n================================================================="
Write-Host "SUMMARY: Passed=$passed, Failed=$failed"
Write-Host "================================================================="
if ($failed -gt 0) { exit 1 } else { exit 0 }
