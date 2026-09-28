[CmdletBinding()]
param(
    [string]$CandidateJar = '',
    [string]$RunDirectory = '',
    [string]$EvidenceDirectory = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($CandidateJar)) { $CandidateJar = Join-Path $root 'build\libs\HyARPG.jar' }
$CandidateJar = (Resolve-Path -LiteralPath $CandidateJar).Path
$package = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$liveSave = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG'
if ([string]::IsNullOrWhiteSpace($RunDirectory)) { $RunDirectory = Join-Path $root 'run\hyarpg-checkpoint-a-smoke' }
$RunDirectory = [IO.Path]::GetFullPath($RunDirectory)
$liveResolved = (Resolve-Path -LiteralPath $liveSave).Path
if ($RunDirectory.TrimEnd('\') -eq $liveResolved.TrimEnd('\')) { throw 'Smoke refuses to run against the live RPG save.' }
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) { $EvidenceDirectory = Join-Path $root 'evidence\hyarpg\checkpoint-a' }
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
$mods = Join-Path $RunDirectory 'mods'
New-Item -ItemType Directory -Force -Path $mods,$EvidenceDirectory | Out-Null

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
        finally { $algorithm.Dispose() }
    } finally { $stream.Dispose() }
}
function Tree-State([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return [ordered]@{exists=$false;files=0;bytes=0} }
    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force)
    [ordered]@{exists=$true;files=$files.Count;bytes=[long](($files | Measure-Object Length -Sum).Sum)}
}

$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
    ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
})
if ($running.Count) { throw 'Hytale or HytaleServer is running; isolated smoke requires a stopped runtime.' }

Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $CandidateJar -Destination (Join-Path $mods 'HyARPG.jar') -Force
$devLib = Join-Path $liveSave 'mods\HYTALEDEVLIB-0.5.0.jar'
if (Test-Path -LiteralPath $devLib) { Copy-Item -LiteralPath $devLib -Destination $mods -Force }
$beforeNpc = Tree-State (Join-Path $mods 'ImmersiveNPCs')

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
if (-not $process.Start()) { throw 'Could not start the isolated HyARPG server.' }
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
Start-Sleep -Seconds 35
if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
if (-not $process.WaitForExit(45000)) { $process.Kill($true); throw 'HyARPG smoke server did not stop in time.' }
$ansiPattern = [Regex]::Escape([string][char]27) + '\[[0-9;]*[A-Za-z]'
$plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace $ansiPattern, ''
$logPath = Join-Path $EvidenceDirectory 'server-smoke.txt'
$plain.TrimEnd() | Set-Content -LiteralPath $logPath -Encoding utf8
$afterNpc = Tree-State (Join-Path $mods 'ImmersiveNPCs')

$summary = [ordered]@{
    result = 'PENDING'
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $process.ExitCode
    candidateSha256 = Get-Sha256 $CandidateJar
    installedSmokeSha256 = Get-Sha256 (Join-Path $mods 'HyARPG.jar')
    firstPartyJarCount = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object Name -ne 'HYTALEDEVLIB-0.5.0.jar').Count
    hyarpgDiscovered = [bool]($plain -match 'InigmasGames:HyARPG from path HyARPG\.jar')
    hyarpgSetup = [bool]($plain -match 'HYARPG_SETUP version=0\.2\.0-R140 revision=R140 modules=GAMEPLAY,PRESENTATION,TAVERNS aiIntegration=NONE')
    tavernsSetup = [bool]($plain -match 'TAVERNS_SETUP revision=R056 .* aiIntegration=NONE')
    tavernsStarted = [bool]($plain -match 'Taverns revision R056 started with persistence schema .* generic Core support')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R140 version=0\.2\.0-R140 hytale=0\.7\.0-pre\.4 stage=13')
    ability4Audit = [bool]($plain -match '(?i)(Ability4|nativeAbility4)')
    hyarpgStarted = [bool]($plain -match 'HYARPG_STARTED version=0\.2\.0-R140 revision=R140')
    hyarpgShutdown = [bool]($plain -match 'HYARPG_SHUTDOWN version=0\.2\.0-R140 revision=R140')
    pluginManagerStarted = [bool]($plain -match 'Plugin manager started!')
    serverBooted = [bool]($plain -match 'Hytale Server Booted')
    immersiveDiscovered = [bool]($plain -match 'InigmasGames:(ImmersiveNPCs|PersistentNPCs) from path')
    aiRuntimeObserved = [bool]($plain -match '(?i)(Nemotron|Ollama|Moonshine|Chatterbox|Faster[-_ ]?Whisper|VOICE_WORKER_READY|ORBIS_EVENT)')
    npcDataBefore = $beforeNpc
    npcDataAfter = $afterNpc
    scopedFailure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HyARPG|shutdownReason\.pluginError|reason: mod_error|HYARPG_PARTIAL_CLEANUP_FAILED|Failed to create HytaleServer)')
    connectedClientVerified = $false
}
$summary.result = if ($summary.processExitCode -eq 0 -and
    $summary.candidateSha256 -eq $summary.installedSmokeSha256 -and $summary.firstPartyJarCount -eq 1 -and
    $summary.hyarpgDiscovered -and $summary.hyarpgSetup -and $summary.tavernsSetup -and $summary.tavernsStarted -and
    $summary.rpgSetup -and $summary.ability4Audit -and
    $summary.hyarpgStarted -and $summary.hyarpgShutdown -and $summary.pluginManagerStarted -and $summary.serverBooted -and
    -not $summary.immersiveDiscovered -and -not $summary.aiRuntimeObserved -and
    ($summary.npcDataBefore | ConvertTo-Json -Compress) -eq ($summary.npcDataAfter | ConvertTo-Json -Compress) -and
    -not $summary.scopedFailure) { 'PASS' } else { 'FAIL' }
$summaryPath = Join-Path $EvidenceDirectory 'server-smoke-summary.json'
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
$summary | ConvertTo-Json -Depth 5
if ($summary.result -ne 'PASS') { throw "HyARPG isolated smoke failed; inspect $logPath" }
