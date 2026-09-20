[CmdletBinding()]
param(
    [string]$TargetSave = '',
    [string]$ExpectedSha256 = '',
    [string]$EvidenceDirectory = ''
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($TargetSave)) {
    $TargetSave = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG'
}
$TargetSave = (Resolve-Path -LiteralPath $TargetSave).Path.TrimEnd('\')
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $repo 'evidence\hywind\post-deployment'
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose(); $stream.Dispose() }
}
function Assert-Stopped {
    $running = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
        ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
    })
    if ($running.Count) { throw 'Hytale or HytaleServer is already running.' }
}
function Get-TreeMeasure([string]$Path) {
    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force)
    [ordered]@{ files = $files.Count; bytes = [long](($files | Measure-Object Length -Sum).Sum) }
}

Assert-Stopped
$mods = Join-Path $TargetSave 'mods'
$hywind = Join-Path $mods 'Hywind.jar'
if (-not (Test-Path -LiteralPath $hywind -PathType Leaf)) { throw 'Active Hywind.jar is missing.' }
$installedHash = Get-Sha256 $hywind
if (-not [string]::IsNullOrWhiteSpace($ExpectedSha256) -and $installedHash -ne $ExpectedSha256) {
    throw "Installed Hywind hash mismatch: $installedHash"
}
$legacyJars = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object {
    $_.Name -eq 'HyARPG.jar' -or $_.Name -like 'HytaleRPG-*.jar' -or
    $_.Name -like 'CanvasUI-*.jar' -or $_.Name -like 'ImmersiveNPCs-*.jar' -or
    $_.Name -like 'Taverns-*.jar' -or $_.Name -eq 'Taverns.jar'
})
if ($legacyJars.Count) { throw "Superseded JARs remain active: $($legacyJars.Name -join ', ')" }

$dataRoots = @('ImmersiveNPCs','InigmasGames_CanvasUI','InigmasGames_HytaleRPGPhase00Audit','InigmasGames_Taverns')
$before = [ordered]@{}
foreach ($name in $dataRoots) {
    $path = Join-Path $mods $name
    $before[$name] = if (Test-Path -LiteralPath $path -PathType Container) { Get-TreeMeasure $path } else { [ordered]@{files=0;bytes=0;absentBeforeStartup=$true} }
}

$package = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$runs = @()
foreach ($iteration in 1..2) {
    Assert-Stopped
    $start = [Diagnostics.ProcessStartInfo]::new('java',
        "-jar `"$serverJar`" --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
    $start.WorkingDirectory = $TargetSave
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw "Could not start deployed Hywind runtime $iteration." }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 35
    if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(45000)) {
        $process.Kill($true)
        throw "Deployed Hywind runtime $iteration did not stop in time."
    }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
    $log = Join-Path $EvidenceDirectory "server-$iteration.txt"
    $plain.TrimEnd() | Set-Content -LiteralPath $log -Encoding utf8
    $run = [ordered]@{
        iteration = $iteration
        exitCode = $process.ExitCode
        hywindDiscovered = [bool]($plain -match 'InigmasGames:Hywind from path Hywind\.jar')
        hywindStarted = [bool]($plain -match 'HYWIND_STARTED version=0\.1\.0-merge\.16 revision=R061')
        tavernsStarted = [bool]($plain -match 'Taverns revision R056 started with persistence schema 3 and generic Core support')
        hywindShutdown = [bool]($plain -match 'HYWIND_SHUTDOWN version=0\.1\.0-merge\.16 revision=R061')
        legacyPluginDiscovered = [bool]($plain -match 'InigmasGames:(HytaleRPGPhase00Audit|CanvasUI|ImmersiveNPCs|Taverns) from path')
        scopedFailure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:Hywind|shutdownReason\.pluginError|reason: mod_error|HYWIND_PARTIAL_CLEANUP_FAILED|Failed to create HytaleServer)')
        log = $log
    }
    if ($run.exitCode -ne 0 -or -not $run.hywindDiscovered -or -not $run.hywindStarted -or
        -not $run.tavernsStarted -or -not $run.hywindShutdown -or $run.legacyPluginDiscovered -or $run.scopedFailure) {
        throw "Deployed Hywind runtime $iteration failed; inspect $log"
    }
    $runs += $run
}

$after = [ordered]@{}
foreach ($name in $dataRoots) {
    $path = Join-Path $mods $name
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Data root disappeared: $path" }
    $after[$name] = Get-TreeMeasure $path
}
$summary = [ordered]@{
    result = 'PASS'
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    targetSave = $TargetSave
    installedJar = $hywind
    installedSha256 = $installedHash
    restartCount = $runs.Count
    runs = $runs
    dataRootsBefore = $before
    dataRootsAfter = $after
    connectedClientVerified = $false
}
$summaryPath = Join-Path $EvidenceDirectory 'post-deployment-summary.json'
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
$summary | ConvertTo-Json -Depth 8
