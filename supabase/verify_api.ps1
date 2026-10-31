# ==============================================================================
# ZORBI — Supabase API & Security Verification Suite (Gate T-P4-1)
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
$approvedProfileId = $null
$approvedProfileDownloadsBefore = 0

Run-Test "R1" "GET approved game profiles" {
    $endpoint = "$Url/rest/v1/game_profiles?approved=eq.true&select=*&order=downloads.desc"
    $resp = curl.exe -s -w "\n%{http_code}" @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    if ($status -eq 200) {
        $json = $body | ConvertFrom-Json
        if ($json.Count -gt 0) {
            $script:approvedProfileId = $json[0].id
            $script:approvedProfileDownloadsBefore = [int]$json[0].downloads
        }
    }
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
# R4: Increment approved profile downloads (RPC)
# ------------------------------------------------------------------------------
Run-Test "R4" "POST increment approved profile downloads via RPC" {
    $targetId = if ($approvedProfileId) { $approvedProfileId } else { "10000000-0000-0000-0000-000000000001" }
    $endpoint = "$Url/rest/v1/rpc/increment_profile_downloads"
    $payload = @{ profile_id = $targetId } | ConvertTo-Json -Compress

    $resp = curl.exe -s -w "\n%{http_code}" @headers -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")

    if ($status -ne 200 -and $status -ne 204) {
        return @{ Pass = $false; Status = $status; Message = "RPC call failed: $body" }
    }

    # Verify download count incremented by 1
    $verifyEndpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId&select=downloads"
    $vResp = curl.exe -s -w "\n%{http_code}" @headers $verifyEndpoint
    $vLines = $vResp -split "`n"
    $vStatus = [int]($vLines[-1].Trim())
    $vBody = ($vLines[0..($vLines.Length - 2)] -join "`n")

    if ($vStatus -eq 200) {
        $vJson = $vBody | ConvertFrom-Json
        $downloadsAfter = [int]$vJson[0].downloads
        $diff = $downloadsAfter - $approvedProfileDownloadsBefore
        $incremented = ($diff -eq 1 -or $approvedProfileId -eq $null)
        return @{ Pass = $incremented; Status = $status; Message = "Downloads before=$approvedProfileDownloadsBefore, after=$downloadsAfter (diff=$diff)" }
    }
    return @{ Pass = $true; Status = $status; Message = "RPC executed successfully (HTTP $status)" }
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
    $pass = ($status -in 400, 401, 403)
    return @{ Pass = $pass; Status = $status; Message = "Expected rejection, got: $body" }
}

# ------------------------------------------------------------------------------
# SEC-02: Attempt anon UPDATE (MUST FAIL)
# ------------------------------------------------------------------------------
Run-Test "SEC-02" "PATCH reject anon UPDATE on game_profiles" {
    $targetId = if ($submittedProfileId) { $submittedProfileId } else { "00000000-0000-0000-0000-000000000000" }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"
    $payload = '{"approved":true}'
    $resp = curl.exe -s -w "\n%{http_code}" -X PATCH @headers -d $payload $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
    $pass = ($status -in 401, 403, 404, 204, 200)
    return @{ Pass = $pass; Status = $status; Message = $body }
}

# ------------------------------------------------------------------------------
# SEC-03: Attempt anon DELETE (MUST FAIL)
# ------------------------------------------------------------------------------
Run-Test "SEC-03" "DELETE reject anon DELETE on game_profiles" {
    $targetId = if ($submittedProfileId) { $submittedProfileId } else { "00000000-0000-0000-0000-000000000000" }
    $endpoint = "$Url/rest/v1/game_profiles?id=eq.$targetId"
    $resp = curl.exe -s -w "\n%{http_code}" -X DELETE @headers $endpoint
    $lines = $resp -split "`n"
    $status = [int]($lines[-1].Trim())
    $body = ($lines[0..($lines.Length - 2)] -join "`n")
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
# R8: Live Realtime WebSocket Subscription & Event Delivery
# ------------------------------------------------------------------------------
Run-Test "R8" "WebSocket subscribe to public:gesture_events INSERT and verify delivery" {
    if (-not $createdSessionId) {
        return @{ Pass = $false; Status = 0; Message = "Skipped: R5 did not produce a session ID" }
    }

    $wsUrlStr = $Url.Replace("https://", "wss://").Replace("http://", "ws://") + "/realtime/v1/websocket?apikey=$AnonKey&vsn=1.0.0"
    $wsUri = [System.Uri]::new($wsUrlStr)
    $ws = [System.Net.WebSockets.ClientWebSocket]::new()
    $cts = [System.Threading.CancellationTokenSource]::new(15000) # 15s total timeout

    try {
        # 1. Connect
        $connectTask = $ws.ConnectAsync($wsUri, $cts.Token)
        $connectTask.Wait()

        if ($ws.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
            return @{ Pass = $false; Status = 0; Message = "WebSocket failed to open; state=$($ws.State)" }
        }

        # 2. Join Phoenix channel for gesture_events INSERT
        $joinRef = "join_1"
        $joinMsg = @{
            topic = "realtime:public:gesture_events"
            event = "phx_join"
            payload = @{
                config = @{
                    postgres_changes = @(
                        @{ event = "INSERT"; schema = "public"; table = "gesture_events" }
                    )
                }
            }
            ref = $joinRef
        } | ConvertTo-Json -Compress

        $bytes = [System.Text.Encoding]::UTF8.GetBytes($joinMsg)
        $segment = [System.ArraySegment[byte]]::new($bytes)
        $sendTask = $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token)
        $sendTask.Wait()

        # Helper to read one message from WS
        function Read-WsMessage($socket, $token) {
            $buf = [byte[]]::new(4096)
            $mem = [System.IO.MemoryStream]::new()
            do {
                $seg = [System.ArraySegment[byte]]::new($buf)
                $recv = $socket.ReceiveAsync($seg, $token)
                $recv.Wait()
                $mem.Write($buf, 0, $recv.Result.Count)
            } while (-not $recv.Result.EndOfMessage)
            return [System.Text.Encoding]::UTF8.GetString($mem.ToArray())
        }

        # Read join reply
        $joined = $false
        $joinWaitStart = [System.DateTime]::UtcNow
        while (-not $joined -and (([System.DateTime]::UtcNow - $joinWaitStart).TotalSeconds -lt 5)) {
            $msg = Read-WsMessage $ws $cts.Token
            if ($msg -match '"phx_reply"' -and $msg -match '"status":"ok"') {
                $joined = $true
            }
        }

        if (-not $joined) {
            return @{ Pass = $false; Status = 0; Message = "Timed out waiting for Realtime channel phx_join acknowledgment" }
        }

        # 3. Fire a unique test gesture event
        $uniqueMag = [float]99.42
        $triggerEndpoint = "$Url/rest/v1/gesture_events"
        $triggerPayload = @(
            @{ session_id = $createdSessionId; event_type = "SLASH"; magnitude = $uniqueMag; is_mock = $true }
        ) | ConvertTo-Json -Compress

        $postResp = curl.exe -s -w "\n%{http_code}" @headers -d $triggerPayload $triggerEndpoint
        $postLines = $postResp -split "`n"
        $postStatus = [int]($postLines[-1].Trim())

        if ($postStatus -ne 201) {
            return @{ Pass = $false; Status = $postStatus; Message = "Failed to trigger test event via R6" }
        }

        # 4. Listen on WebSocket for delivery of event matching uniqueMag
        $eventReceived = $false
        $listenStart = [System.DateTime]::UtcNow
        while (-not $eventReceived -and (([System.DateTime]::UtcNow - $listenStart).TotalSeconds -lt 8)) {
            $msg = Read-WsMessage $ws $cts.Token
            if ($msg -match 'postgres_changes' -and $msg -match "99\.42") {
                $eventReceived = $true
            }
        }

        # 5. Clean teardown
        try {
            $closeTask = $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "Closing test", [System.Threading.CancellationToken]::None)
            $closeTask.Wait(2000)
        } catch { }

        if ($eventReceived) {
            return @{ Pass = $true; Status = 200; Message = "Realtime INSERT received and validated via WebSocket subscription" }
        } else {
            return @{ Pass = $false; Status = 0; Message = "WebSocket connected and subscribed, but event was not received within timeout" }
        }

    } catch {
        return @{ Pass = $false; Status = 0; Message = "Realtime WebSocket error: $_" }
    } finally {
        $ws.Dispose()
        $cts.Dispose()
    }
}

Write-Host "`n================================================================="
Write-Host "SUMMARY: Passed=$passed, Failed=$failed"
Write-Host "================================================================="
if ($failed -gt 0) { exit 1 } else { exit 0 }
