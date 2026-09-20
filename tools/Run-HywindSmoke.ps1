[CmdletBinding()]
param(
    [string]$CandidateJar = '',
    [string]$RunDirectory = '',
    [string]$LegacyDataSource = '',
    [string]$TavernDataSource = '',
    [string]$EvidenceDirectory = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($CandidateJar)) { $CandidateJar = Join-Path $root 'build\libs\Hywind.jar' }
$CandidateJar = (Resolve-Path -LiteralPath $CandidateJar).Path
$package = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$liveSave = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG'
if ([string]::IsNullOrWhiteSpace($RunDirectory)) { $RunDirectory = Join-Path $root 'run\hywind-isolated-smoke' }
$RunDirectory = [IO.Path]::GetFullPath($RunDirectory)
$liveResolved = (Resolve-Path -LiteralPath $liveSave).Path
if ($RunDirectory.TrimEnd('\') -eq $liveResolved.TrimEnd('\')) { throw 'Smoke refuses to run against the live RPG save.' }
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) { $EvidenceDirectory = Join-Path $root 'evidence\hywind\isolated-smoke' }
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
$mods = Join-Path $RunDirectory 'mods'
New-Item -ItemType Directory -Force -Path $mods,$EvidenceDirectory | Out-Null

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose(); $stream.Dispose() }
}

$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
    ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
})
if ($running.Count) { throw 'Hytale or HytaleServer is running; isolated smoke requires a stopped runtime.' }

Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $CandidateJar -Destination (Join-Path $mods 'Hywind.jar') -Force
$devLib = Join-Path $liveSave 'mods\HYTALEDEVLIB-0.5.0.jar'
if (Test-Path -LiteralPath $devLib) { Copy-Item -LiteralPath $devLib -Destination $mods -Force }
$permissions = Join-Path $liveSave 'permissions.json'
if (Test-Path -LiteralPath $permissions) { Copy-Item -LiteralPath $permissions -Destination $RunDirectory -Force }

if (-not [string]::IsNullOrWhiteSpace($LegacyDataSource)) {
    $source = (Resolve-Path -LiteralPath $LegacyDataSource).Path
    if ($source.TrimEnd('\') -eq $liveResolved.TrimEnd('\')) {
        throw 'Copied-save smoke requires a backup/copy source, not the live save.'
    }
    foreach ($name in @('ImmersiveNPCs','InigmasGames_CanvasUI','InigmasGames_HytaleRPGPhase00Audit','InigmasGames_Taverns')) {
        $from = Join-Path $source "mods\$name"
        $to = Join-Path $mods $name
        if (Test-Path -LiteralPath $from) { Copy-Item -LiteralPath $from -Destination $to -Recurse -Force }
    }
}

if (-not [string]::IsNullOrWhiteSpace($TavernDataSource)) {
    $tavernSource = (Resolve-Path -LiteralPath $TavernDataSource).Path
    if ($tavernSource.TrimEnd('\') -eq (Join-Path $liveResolved 'mods\InigmasGames_Taverns').TrimEnd('\')) {
        throw 'Copied-save smoke requires a backup/copy Tavern source, not the live save.'
    }
    $tavernTarget = Join-Path $mods 'InigmasGames_Taverns'
    if (Test-Path -LiteralPath $tavernTarget) {
        throw 'Tavern smoke target already exists; use a fresh isolated run directory.'
    }
    Copy-Item -LiteralPath $tavernSource -Destination $tavernTarget -Recurse -Force
}

$start = [Diagnostics.ProcessStartInfo]::new('java',
    "-jar `"$serverJar`" --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
$start.WorkingDirectory = $RunDirectory
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$process = [Diagnostics.Process]::new()
$process.StartInfo = $start
if (-not $process.Start()) { throw 'Could not start the isolated Hywind server.' }
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
Start-Sleep -Seconds 35
if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
if (-not $process.WaitForExit(45000)) { $process.Kill($true); throw 'Hywind smoke server did not stop in time.' }
$plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$logPath = Join-Path $EvidenceDirectory 'server-smoke.txt'
$plain.TrimEnd() | Set-Content -LiteralPath $logPath -Encoding utf8

$summary = [ordered]@{
    result = 'PENDING'
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $process.ExitCode
    candidateSha256 = Get-Sha256 $CandidateJar
    installedSmokeSha256 = Get-Sha256 (Join-Path $mods 'Hywind.jar')
    firstPartyJarCount = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object Name -ne 'HYTALEDEVLIB-0.5.0.jar').Count
    hywindDiscovered = [bool]($plain -match 'InigmasGames:Hywind from path Hywind\.jar')
    hywindSetup = [bool]($plain -match 'HYWIND_SETUP version=0\.1\.0-merge\.15 revision=R060 .*TAVERNS')
    legacyRootsSelected = [bool]($plain -match 'HYWIND_DATA_ROOTS .*gameplay=.*InigmasGames_HytaleRPGPhase00Audit .*presentation=.*InigmasGames_CanvasUI .*characters=.*ImmersiveNPCs .*taverns=.*InigmasGames_Taverns')
    canvasOwned = [bool]($plain -match 'CANVASUI_SETUP revision=R060 .*owner=HYWIND')
    rpgOwned = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R060 version=0\.1\.0-merge\.15 hytale=0\.7\.0-pre\.3\.1 stage=13')
    npcStarted = [bool]($plain -match 'Immersive AI .* started')
    tavernsStarted = [bool]($plain -match 'Taverns revision R056 started with persistence schema 3 and generic Core support')
    hywindStarted = [bool]($plain -match 'HYWIND_STARTED version=0\.1\.0-merge\.15 revision=R060')
    hywindShutdown = [bool]($plain -match 'HYWIND_SHUTDOWN version=0\.1\.0-merge\.15 revision=R060')
    pluginManagerStarted = [bool]($plain -match 'Plugin manager started!')
    serverBooted = [bool]($plain -match 'Hytale Server Booted')
    legacyPluginDiscovered = [bool]($plain -match 'InigmasGames:(HytaleRPGPhase00Audit|CanvasUI|ImmersiveNPCs|Taverns) from path')
    scopedFailure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:Hywind|shutdownReason\.pluginError|reason: mod_error|HYWIND_PARTIAL_CLEANUP_FAILED|Failed to create HytaleServer)')
    connectedClientVerified = $false
}
$summary.result = if ($summary.processExitCode -eq 0 -and
    $summary.candidateSha256 -eq $summary.installedSmokeSha256 -and
    $summary.firstPartyJarCount -eq 1 -and $summary.hywindDiscovered -and
    $summary.hywindSetup -and $summary.legacyRootsSelected -and $summary.canvasOwned -and
    $summary.rpgOwned -and $summary.npcStarted -and $summary.tavernsStarted -and $summary.hywindStarted -and
    $summary.hywindShutdown -and $summary.pluginManagerStarted -and $summary.serverBooted -and
    -not $summary.legacyPluginDiscovered -and -not $summary.scopedFailure) { 'PASS' } else { 'FAIL' }
$summaryPath = Join-Path $EvidenceDirectory 'server-smoke-summary.json'
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
$summary | ConvertTo-Json -Depth 5
if ($summary.result -ne 'PASS') { throw "Hywind isolated smoke failed; inspect $logPath" }
