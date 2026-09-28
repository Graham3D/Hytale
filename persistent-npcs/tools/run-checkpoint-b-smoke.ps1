[CmdletBinding()]
param(
    [int]$RunSeconds = 75
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$package = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$sourceSave = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\ImmersiveNPCs'
$artifact = Join-Path $projectRoot 'dist\ImmersiveNPCs-0.6.4-R171-PRE4-COMPAT.jar'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $projectRoot "run\checkpoint-b-$stamp"
$mods = Join-Path $runDirectory 'mods'
$voiceRoot = Join-Path $runDirectory 'exports\voices'
$evidence = Join-Path $projectRoot 'evidence\checkpoint-b'

foreach ($required in $serverJar,$assets,$artifact) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required Checkpoint B input is missing: $required"
    }
}
New-Item -ItemType Directory -Force -Path $mods,$voiceRoot,$evidence | Out-Null
$resolvedRun = [IO.Path]::GetFullPath($runDirectory)
if (-not $resolvedRun.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe Checkpoint B smoke path: $resolvedRun"
}

Copy-Item -LiteralPath $artifact -Destination $mods
$sourcePermissions = Join-Path $sourceSave 'permissions.json'
if (Test-Path -LiteralPath $sourcePermissions -PathType Leaf) {
    Copy-Item -LiteralPath $sourcePermissions `
        -Destination (Join-Path $runDirectory 'permissions.json')
}
$legacyData = Join-Path $sourceSave 'mods\InigmasGames_PersistentNPCs'
$legacyMigrationExpected = Test-Path -LiteralPath $legacyData -PathType Container
if ($legacyMigrationExpected) {
    Copy-Item -LiteralPath $legacyData -Destination $mods -Recurse
}
foreach ($relative in 'exports\voices\Mara','exports\skins\Mara') {
    $source = Join-Path $sourceSave $relative
    $targetParent = Split-Path (Join-Path $runDirectory $relative) -Parent
    New-Item -ItemType Directory -Force -Path $targetParent | Out-Null
    Copy-Item -LiteralPath $source -Destination $targetParent -Recurse
}

# Reuse the proven local Python installation without copying or modifying it.
$pythonEnvironment = Join-Path $sourceSave 'exports\voices\.venv-turbo'
if (-not (Test-Path -LiteralPath $pythonEnvironment -PathType Container)) {
    throw "Existing pre-release voice environment is missing: $pythonEnvironment"
}
New-Item -ItemType Junction -Path (Join-Path $voiceRoot '.venv-turbo') `
    -Target $pythonEnvironment | Out-Null

Push-Location $runDirectory
try {
    $arguments = "-jar `"$serverJar`" --bare --bind 127.0.0.1:0 --auth-mode offline " +
        "--allow-op --disable-sentry --assets=`"$assets`""
    $start = [Diagnostics.ProcessStartInfo]::new('java', $arguments)
    $start.WorkingDirectory = $runDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start Checkpoint B smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds([Math]::Max(15, $RunSeconds))
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
    }
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
    }
    if (-not $process.WaitForExit(30000)) {
        $process.Kill($true)
        throw 'Checkpoint B smoke server did not stop in time.'
    }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) `
        -replace "`e\[[0-9;?]*[A-Za-z]", ''
    $exitCode = $process.ExitCode
}
finally {
    Pop-Location
}

$logPath = Join-Path $evidence "server-smoke-$stamp.txt"
$plain.TrimEnd() | Set-Content -LiteralPath $logPath -Encoding utf8
$activeJars = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar')
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    runDirectory = $runDirectory
    processExitCode = $exitCode
    exactlyOneProjectJar = @($activeJars | Where-Object {
        $_.Name -like 'PersistentNPCs-*.jar' -or $_.Name -like 'ImmersiveNPCs-*.jar'
    }).Count -eq 1
    pluginDiscovered = [bool]($plain -match 'ImmersiveNPCs-0\.6\.4-R171-PRE4-COMPAT\.jar')
    pluginEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:ImmersiveNPCs')
    revisionStarted = [bool]($plain -match 'Immersive AI R171-PRE4-COMPAT started')
    migrationArchived = (-not $legacyMigrationExpected) -or
        [bool]($plain -match 'IMMERSIVE_NPC_DATA_MIGRATION .* archived=')
    profileStoreReady = [bool]($plain -match 'SAVE_WORLD_DATA_READY.*profiles=')
    moonshineReady = [bool]($plain -match 'PROVIDER_WARMUP_COMPLETED.*MOONSHINE')
    nemotronReady = [bool]($plain -match 'PROVIDER_WARMUP_COMPLETED.*NEMOTRON')
    chatterboxReady = [bool]($plain -match 'PROVIDER_WARMUP_COMPLETED.*CHATTERBOX')
    workerSttReady = [bool]($plain -match 'VOICE_WORKER_READY role=stt .*sttProvider=MOONSHINE')
    workerTtsReady = [bool]($plain -match 'VOICE_WORKER_READY role=tts .*ttsDevice=cuda')
    cleanShutdown = [bool]($plain -match 'Shutting down\.\.\. 0')
    fatalFailure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:ImmersiveNPCs|shutdownReason\.pluginError|reason: mod_error|NoSuchMethodError|NoClassDefFoundError)')
    logPath = $logPath
}
$summaryPath = Join-Path $evidence "server-smoke-$stamp.json"
$summary | ConvertTo-Json | Set-Content -LiteralPath $summaryPath -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.exactlyOneProjectJar -and $summary.pluginDiscovered -and
        $summary.pluginEnabled -and $summary.revisionStarted -and
        $summary.migrationArchived -and $summary.profileStoreReady -and
        $summary.moonshineReady -and
        $summary.nemotronReady -and $summary.chatterboxReady -and
        $summary.workerSttReady -and $summary.workerTtsReady -and
        $summary.cleanShutdown) -or $summary.fatalFailure) {
    throw "Checkpoint B isolated smoke gate failed. Inspect $summaryPath"
}
