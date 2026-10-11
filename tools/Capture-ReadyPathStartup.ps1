[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$Restore,
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
    Write-Output 'No environment variable, game process, JAR, or save was changed.'
    return
}

$run = [Guid]::NewGuid().ToString('N')
$armedAt = [DateTime]::UtcNow
$folder = Join-Path $captureRoot ($armedAt.ToString('yyyyMMddTHHmmssZ') + '-' + $run.Substring(0, 8))
New-Item -ItemType Directory -Path $folder -ErrorAction Stop | Out-Null
$recording = Join-Path $folder 'integrated-server.jfr'
$options = "-Dhywind.readypath.enabled=true -Dhywind.readypath.run=$run " +
    "-XX:StartFlightRecording=name=HywindStartup,settings=profile,filename=$recording," +
    "duration=${RecordingSeconds}s,maxsize=256m,dumponexit=true"
$installed = $false
$server = $null
$captureStatus = 'NO_SERVER_OBSERVED'
try {
    [Environment]::SetEnvironmentVariable($optionName, $options, 'User')
    Broadcast-Environment
    $installed = $true
    Write-Output "ARMED: $folder"
    Write-Output 'Now launch Hytale normally and enter the existing RPG save. Leave this PowerShell window open.'
    $deadline = (Get-Date).AddMinutes($WaitMinutes)
    while ((Get-Date) -lt $deadline) {
        $server = @(Integrated-Server | Select-Object -First 1)
        if ($server.Count) { break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $server.Count) { throw "No integrated server appeared within $WaitMinutes minutes." }
    $server = $server[0]
    $observedAt = [DateTime]::UtcNow
    $captureStatus = 'SERVER_SEEN_RECORDING_PENDING'
    Write-Output "Integrated server PID $($server.ProcessId) detected. Waiting for the bounded JFR recording."
    # The server already inherited the option; remove the user-level setting now.
    if ([Environment]::GetEnvironmentVariable($optionName, 'User') -ceq $options) {
        [Environment]::SetEnvironmentVariable($optionName, $null, 'User')
        Broadcast-Environment
        $installed = $false
    }
    $finishBy = (Get-Date).AddSeconds($RecordingSeconds + 45)
    while ((Get-Date) -lt $finishBy) {
        if (Test-Path -LiteralPath $recording -PathType Leaf) {
            $null = & $jfr summary $recording 2>$null
            if ($LASTEXITCODE -eq 0) { $captureStatus = 'JFR_VALID'; break }
        }
        Start-Sleep -Seconds 2
    }
    if ($captureStatus -ne 'JFR_VALID') {
        $captureStatus = 'JFR_MISSING_OR_INCOMPLETE'
        Write-Warning 'No complete JFR was found. Check whether the launcher inherited JAVA_TOOL_OPTIONS.'
    }
    $jarFiles = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | ForEach-Object {
        [ordered]@{name=$_.Name;bytes=$_.Length;sha256=(Sha256 $_.FullName)}
    })
    $assetFile = Get-Item -LiteralPath (Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest\Assets.zip')
    $clientLog = @(Get-ChildItem -LiteralPath (Join-Path $data 'Logs') -File -Filter '*_client.log' |
        Where-Object { $_.CreationTimeUtc -ge $armedAt.AddSeconds(-10) } |
        Sort-Object CreationTimeUtc -Descending | Select-Object -First 1)
    $serverLog = @(Get-ChildItem -LiteralPath (Join-Path $data 'Saves\RPG\logs') -File -Filter '*_server.log' |
        Where-Object { $_.CreationTimeUtc -ge $armedAt.AddSeconds(-10) } |
        Sort-Object CreationTimeUtc -Descending | Select-Object -First 1)
    [ordered]@{
        schemaVersion=1;run=$run;status=$captureStatus;armedAtUtc=$armedAt.ToString('o')
        serverObservedAtUtc=$observedAt.ToString('o');serverPid=$server.ProcessId
        serverProcessCreatedAtUtc=$server.CreationDate.ToUniversalTime().ToString('o')
        recordingSeconds=$RecordingSeconds;jfrPath=$recording
        jfrSha256=if($captureStatus -eq 'JFR_VALID'){Sha256 $recording}else{$null}
        clientLog=if($clientLog.Count){$clientLog[0].FullName}else{$null}
        serverLog=if($serverLog.Count){$serverLog[0].FullName}else{$null}
        nativeAssetsZipBytes=$assetFile.Length;nativeAssetsZipModifiedUtc=$assetFile.LastWriteTimeUtc.ToString('o')
        mods=$jarFiles;connectedOutcome='OWNER_TO_LABEL_NORMAL_OR_SLOW'
        note='JFR and ReadyPath observe server work; logs do not prove client-visible play.'
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $folder 'manifest.json') -Encoding UTF8
    Write-Output "CAPTURE $captureStatus`: $folder"
    Write-Output 'Tell Codex which runs felt normal or delayed, and provide this capture folder with its client/server logs.'
} finally {
    if ($installed -and [Environment]::GetEnvironmentVariable($optionName, 'User') -ceq $options) {
        [Environment]::SetEnvironmentVariable($optionName, $null, 'User')
        Broadcast-Environment
        Write-Output 'ReadyPath Java startup option restored to unset.'
    }
}
