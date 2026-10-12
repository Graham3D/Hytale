[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$Restore,
    [switch]$EnableDurableCyclone,
    [ValidateRange(30, 600)][int]$RecordingSeconds = 150,
    [ValidateRange(1, 20)][int]$WaitMinutes = 10
)

# Owner-run capture only. This script never starts Hytale or a Java server.
$ErrorActionPreference = 'Stop'
$data = Join-Path $env:APPDATA 'Hytale\data\pre-release'
$runtime = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\jre\latest\bin'
$java = Join-Path $runtime 'java.exe'
$jfr = Join-Path $runtime 'jfr.exe'
$mods = Join-Path $data 'Saves\RPG\mods'
$captureRoot = Join-Path $data 'StartupCaptures'
$optionName = 'JAVA_TOOL_OPTIONS'
. (Join-Path $PSScriptRoot 'ReadyPathCapture.Core.ps1')
$serverJar = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar'

function Broadcast-Environment {
    if (-not ('ReadyPathEnvironmentNotice' -as [type])) {
        Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class ReadyPathEnvironmentNotice {
    [DllImport("user32.dll", CharSet=CharSet.Unicode)]
    public static extern IntPtr SendMessageTimeout(IntPtr window, uint message,
        UIntPtr parameter, string value, uint flags, uint timeout, out UIntPtr result);
}
'@
    }
    $result = [UIntPtr]::Zero
    [void][ReadyPathEnvironmentNotice]::SendMessageTimeout(
        [IntPtr]65535, 26, [UIntPtr]::Zero, 'Environment', 2, 1000, [ref]$result)
}
function Integrated-Server {
    @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -match '(?i)HytaleServer\.jar' -and
            $_.CommandLine -match '(?i)(?:^|\s)--singleplayer(?:\s|$)' })
}
function Sha256([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function Copy-LaunchLog([string]$sourceDir, [string]$filter, [datetime]$since, [datetime]$until, [string]$destination) {
    if (-not (Test-Path -LiteralPath $sourceDir -PathType Container)) { return $null }
    $source = Get-ChildItem -LiteralPath $sourceDir -File -Filter $filter |
        Where-Object { $_.CreationTimeUtc -ge $since.AddSeconds(-10) -and $_.CreationTimeUtc -le $until.AddSeconds(10) } |
        Sort-Object CreationTimeUtc -Descending | Select-Object -First 1
    if (-not $source) { return $null }
    $inputStream = [IO.File]::Open($source.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $outputStream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose() }
    } finally { $inputStream.Dispose() }
    return [ordered]@{source=$source.FullName;copy=$destination;bytes=(Get-Item -LiteralPath $destination).Length;sha256=(Sha256 $destination)}
}

$userOption = [Environment]::GetEnvironmentVariable($optionName, 'User')
if ($Restore) {
    if ($userOption -match '(?:^|\s)-Dhywind\.readypath\.run=') {
        [Environment]::SetEnvironmentVariable($optionName, $null, 'User')
        Broadcast-Environment
        Write-Output 'ReadyPath Java startup option removed. Existing recordings were retained.'
    } else {
        Write-Output 'No ReadyPath Java startup option is armed; nothing changed.'
    }
    return
}
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
    -not (Test-Path -LiteralPath $jfr -PathType Leaf)) {
    throw 'The installed Hytale Java runtime and jfr.exe are required.'
}
if (-not (Test-Path -LiteralPath (Join-Path $mods 'Hywind.jar') -PathType Leaf)) {
    throw 'The active RPG save has no Hywind.jar.'
}
if ($userOption -or [Environment]::GetEnvironmentVariable($optionName, 'Process')) {
    throw 'JAVA_TOOL_OPTIONS is already set. Preserve its existing value; this capture will not replace it.'
}
if ((Integrated-Server).Count) { throw 'The integrated Hytale server is already running.' }
$client = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '(?i)^HytaleClient\.exe$|^HytaleLauncher\.exe$|^Hytale Launcher\.exe$'
})
if ($client.Count) { throw 'Close Hytale and its launcher before arming, so the new JVM inherits the capture option.' }
if ($CheckOnly) {
    Write-Output "CHECK PASSED: Hytale is closed; Java and JFR are available. Capture root: $captureRoot"
    if ($EnableDurableCyclone) { Write-Output 'R261 Cyclone gate will be armed and checked against the integrated-server log.' }
    Write-Output 'No environment variable, game process, JAR, or save was changed.'
    return
}

$run = [Guid]::NewGuid().ToString('N')
$armedAt = [DateTime]::UtcNow
$folder = Join-Path $captureRoot ($armedAt.ToString('yyyyMMddTHHmmssZ') + '-' + $run.Substring(0, 8))
New-Item -ItemType Directory -Path $folder -ErrorAction Stop | Out-Null
$recording = Join-Path $folder 'integrated-server.jfr'
$options = New-ReadyPathJavaOptions $run $recording $RecordingSeconds ([bool]$EnableDurableCyclone)
$installed = $false
$server = $null
$captureStatus = 'NO_SERVER_OBSERVED'
$failureReason = $null
$observedAt = $null
$matchedClient = $null
$lastJfrReason = $null
$clientLog = $null
$serverLog = $null
$cycloneGate = $null
$clients = @{}
try {
    [Environment]::SetEnvironmentVariable($optionName, $options, 'User')
    $installed = $true
    Broadcast-Environment
    Write-Output "ARMED: $folder"
    Write-Output 'Now launch Hytale normally and enter the existing RPG save. Leave this PowerShell window open.'
    $deadline = (Get-Date).AddMinutes($WaitMinutes)
    while ((Get-Date) -lt $deadline) {
        foreach ($candidate in @(Get-CimInstance Win32_Process -Filter "Name = 'HytaleClient.exe'")) {
            if ($candidate.CreationDate.ToUniversalTime() -ge $armedAt.AddSeconds(-2)) { $clients[$candidate.ProcessId] = $candidate }
        }
        $match = Select-ReadyPathServer @($clients.Values) @(Integrated-Server) $armedAt $java $serverJar
        if ($match) { $server = $match.Server; $matchedClient = $match.Client; break }
        Start-Sleep -Seconds 1
    }
    if (-not $server) { throw "No integrated server belonging to a newly launched Hytale client appeared within $WaitMinutes minutes." }
    $observedAt = [DateTime]::UtcNow
    $captureStatus = 'SERVER_SEEN_RECORDING_PENDING'
    Write-Output "Integrated server PID $($server.ProcessId) detected. Waiting for the bounded JFR recording."
    # The server already inherited the option; remove the user-level setting now.
    if ([Environment]::GetEnvironmentVariable($optionName, 'User') -ceq $options) {
        [Environment]::SetEnvironmentVariable($optionName, $null, 'User')
        Broadcast-Environment
        $installed = $false
    }
    $serverStarted = $server.CreationDate.ToUniversalTime()
    $finishBy = $serverStarted.AddSeconds($RecordingSeconds + 60)
    $previousLength = -1L
    $stablePolls = 0
    $serverExitedAt = $null
    while ([DateTime]::UtcNow -lt $finishBy) {
        $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($server.ProcessId)" -ErrorAction SilentlyContinue
        if (-not $running -and -not $serverExitedAt) { $serverExitedAt = [DateTime]::UtcNow }
        $finalizationExpected = [DateTime]::UtcNow -ge $serverStarted.AddSeconds($RecordingSeconds + 3) -or
            ($serverExitedAt -and [DateTime]::UtcNow -ge $serverExitedAt.AddSeconds(4))
        $check = Test-ReadyPathRecording $recording $jfr $previousLength $stablePolls ([bool]$finalizationExpected)
        $previousLength = $check.Length
        $stablePolls = $check.StablePolls
        $lastJfrReason = $check.Reason
        if ($check.Ready) { $captureStatus = 'JFR_VALID'; break }
        Start-Sleep -Seconds 2
    }
    if ($captureStatus -ne 'JFR_VALID') {
        $captureStatus = 'JFR_MISSING_OR_INCOMPLETE'
        $failureReason = "No complete JFR was ready before the deadline. Last check: $lastJfrReason"
    }
} catch {
    if ($captureStatus -ne 'JFR_MISSING_OR_INCOMPLETE') { $captureStatus = 'FAILED' }
    $failureReason = $_.Exception.Message
} finally {
    if ($installed -and [Environment]::GetEnvironmentVariable($optionName, 'User') -ceq $options) {
        try {
            [Environment]::SetEnvironmentVariable($optionName, $null, 'User')
            Broadcast-Environment
            Write-Output 'ReadyPath Java startup option restored to unset.'
        } catch { $captureStatus = 'FAILED'; $failureReason = "Environment cleanup failed: $($_.Exception.Message)" }
    }
    try {
        $clientUntil = if ($matchedClient) { $matchedClient.CreationDate.ToUniversalTime().AddSeconds(60) } else { [DateTime]::UtcNow }
        $clientLog = Copy-LaunchLog (Join-Path $data 'Logs') '*_client.log' $armedAt $clientUntil (Join-Path $folder 'client.log')
        if ($server) {
            $serverStart = $server.CreationDate.ToUniversalTime()
            $serverLog = Copy-LaunchLog (Join-Path $data 'Saves\RPG\logs') '*_server.log' $serverStart.AddSeconds(-10) $serverStart.AddSeconds(60) (Join-Path $folder 'server.log')
        }
    } catch { $captureStatus = 'FAILED'; $failureReason = "Log snapshot failed: $($_.Exception.Message)" }
    if ($EnableDurableCyclone) {
        $cycloneGate = Test-ReadyPathCycloneGate $(if ($serverLog) { $serverLog.copy } else { $null })
        if ($cycloneGate.Confirmed) {
            Write-Output 'CYCLONE GATE CONFIRMED: RPG_CYCLONE_DURABLE_GATE enabled=true'
        } elseif ($captureStatus -eq 'JFR_VALID') {
            $captureStatus = 'CYCLONE_GATE_UNVERIFIED'
            $failureReason = $cycloneGate.Reason
        }
    }
    try {
        $jarFiles = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | ForEach-Object {
            [ordered]@{name=$_.Name;bytes=$_.Length;sha256=(Sha256 $_.FullName)}
        })
        $assetFile = Get-Item -LiteralPath (Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest\Assets.zip')
        [ordered]@{
            schemaVersion=3;run=$run;status=$captureStatus;failureReason=$failureReason;armedAtUtc=$armedAt.ToString('o')
            cycloneDurableRequested=[bool]$EnableDurableCyclone
            cycloneGateConfirmed=if($cycloneGate){$cycloneGate.Confirmed}else{$null}
            cycloneGateReason=if($cycloneGate){$cycloneGate.Reason}else{$null}
            serverObservedAtUtc=if($observedAt){$observedAt.ToString('o')}else{$null}
            serverPid=if($server){$server.ProcessId}else{$null}
            clientPid=if($matchedClient){$matchedClient.ProcessId}else{$null}
            serverProcessCreatedAtUtc=if($server){$server.CreationDate.ToUniversalTime().ToString('o')}else{$null}
            recordingSeconds=$RecordingSeconds;jfrPath=$recording
            jfrBytes=if(Test-Path -LiteralPath $recording -PathType Leaf){(Get-Item -LiteralPath $recording).Length}else{0}
            jfrSha256=if($captureStatus -eq 'JFR_VALID'){Sha256 $recording}else{$null}
            clientLog=$clientLog;serverLog=$serverLog
            nativeAssetsZipBytes=$assetFile.Length;nativeAssetsZipModifiedUtc=$assetFile.LastWriteTimeUtc.ToString('o')
            mods=$jarFiles;connectedOutcome='OWNER_TO_LABEL_NORMAL_OR_SLOW'
            note='JFR and ReadyPath observe server work; logs do not prove client-visible play.'
        } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $folder 'manifest.json') -Encoding UTF8
    } catch { $captureStatus = 'FAILED'; $failureReason = "Manifest write failed: $($_.Exception.Message)" }
}
Write-Output "CAPTURE $($captureStatus): $folder"
if ($captureStatus -ne 'JFR_VALID') { throw "Capture failed: $failureReason. Partial files were retained in $folder" }
Write-Output 'JFR and client/server log snapshots are in this folder. Tell Codex whether this launch felt normal or delayed.'
