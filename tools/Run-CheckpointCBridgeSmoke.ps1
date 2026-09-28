[CmdletBinding()]
param(
    [ValidateSet('compatible','incompatible')][string]$Mode = 'compatible',
    [int]$RunSeconds = 45
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$package = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$hyarpg = Join-Path $root 'build\libs\HyARPG.jar'
$immersive = Join-Path $root 'persistent-npcs\dist\ImmersiveNPCs-0.6.4-R172-PRE4-COMPAT.jar'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $root "run\checkpoint-c-$Mode-$stamp"
$mods = Join-Path $run 'mods'
$evidence = Join-Path $root "evidence\checkpoint-c\$Mode"
New-Item -ItemType Directory -Force -Path $mods,$evidence | Out-Null

foreach ($required in $serverJar,$assets,$hyarpg) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing input: $required" }
}
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
    ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
})
if ($running.Count) { throw 'Hytale or HytaleServer is running; isolated bridge smoke requires a stopped runtime.' }

Copy-Item -LiteralPath $hyarpg -Destination (Join-Path $mods 'HyARPG.jar')
$liveRpg = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG'
$devLib = Join-Path $liveRpg 'mods\HYTALEDEVLIB-0.5.0.jar'
if (Test-Path -LiteralPath $devLib -PathType Leaf) { Copy-Item -LiteralPath $devLib -Destination $mods }
$permissions = Join-Path $liveRpg 'permissions.json'
if (Test-Path -LiteralPath $permissions -PathType Leaf) {
    Copy-Item -LiteralPath $permissions -Destination (Join-Path $run 'permissions.json')
}

if ($Mode -eq 'compatible') {
    if (-not (Test-Path -LiteralPath $immersive -PathType Leaf)) { throw "Missing input: $immersive" }
    Copy-Item -LiteralPath $immersive -Destination $mods
} else {
    $fixtureRoot = Join-Path $root 'tools\checkpoint-c-fixture'
    $fixtureClasses = Join-Path $run 'fixture-classes'
    New-Item -ItemType Directory -Force -Path $fixtureClasses | Out-Null
    $source = Join-Path $fixtureRoot 'src\checkpointc\fixture\IncompatibleImmersivePlugin.java'
    & javac -encoding UTF-8 -source 25 -target 25 -classpath $serverJar -d $fixtureClasses $source
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile incompatible fixture.' }
    Copy-Item -LiteralPath (Join-Path $fixtureRoot 'manifest.json') -Destination $fixtureClasses
    & jar --create --file (Join-Path $mods 'ImmersiveNPCs-Incompatible-Fixture.jar') -C $fixtureClasses .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package incompatible fixture.' }
}

$start = [Diagnostics.ProcessStartInfo]::new('java',
    "-jar `"$serverJar`" --bare --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
$start.WorkingDirectory = $run
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$process = [Diagnostics.Process]::new()
$process.StartInfo = $start
if (-not $process.Start()) { throw 'Could not start Checkpoint C smoke server.' }
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
$deadline = [DateTime]::UtcNow.AddSeconds([Math]::Max(20, $RunSeconds))
while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 500 }
if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
if (-not $process.WaitForExit(45000)) { $process.Kill($true); throw 'Checkpoint C server did not stop.' }
$plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;?]*[A-Za-z]", ''
$log = Join-Path $evidence "server-smoke-$stamp.txt"
$plain.TrimEnd() | Set-Content -LiteralPath $log -Encoding utf8

$summary = [ordered]@{
    result = 'PENDING'
    mode = $Mode
    processExitCode = $process.ExitCode
    hyarpgStarted = [bool]($plain -match 'HYARPG_STARTED version=0\.2\.0-R141 revision=R141')
    pluginManagerStarted = [bool]($plain -match 'Plugin manager started!')
    compatibleHandshakeHyArpg = [bool]($plain -match 'HYARPG_IMMERSIVE_BRIDGE status=CONNECTED api=1 capabilities=COMBAT_EVENTS')
    compatibleHandshakeImmersive = [bool]($plain -match 'IMMERSIVENPCS_HYARPG_BRIDGE status=CONNECTED api=1 capabilities=COMBAT_EVENTS,NPC_EVENT_OBSERVATION')
    immersiveStarted = [bool]($plain -match 'Immersive AI R172-PRE4-COMPAT started')
    incompatibleFixtureStarted = [bool]($plain -match 'CHECKPOINT_C_INCOMPATIBLE_FIXTURE start=PASS')
    incompatibleBridgeDisabled = [bool]($plain -match 'HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=(CONTRACT_UNAVAILABLE|INCOMPATIBLE_PROVIDER_CONTRACT)')
    fatalFailure = [bool]($plain -match '(?i)(Failed to setup plugin|shutdownReason\.pluginError|reason: mod_error|HYARPG_PARTIAL_CLEANUP_FAILED)')
    logPath = $log
}
$pass = $summary.processExitCode -eq 0 -and $summary.hyarpgStarted -and
    $summary.pluginManagerStarted -and -not $summary.fatalFailure -and
    (($Mode -eq 'compatible' -and $summary.compatibleHandshakeHyArpg -and
        $summary.compatibleHandshakeImmersive -and $summary.immersiveStarted) -or
     ($Mode -eq 'incompatible' -and $summary.incompatibleFixtureStarted -and
        $summary.incompatibleBridgeDisabled))
$summary.result = if ($pass) { 'PASS' } else { 'FAIL' }
$summaryPath = Join-Path $evidence "server-smoke-$stamp.json"
$summary | ConvertTo-Json | Set-Content -LiteralPath $summaryPath -Encoding utf8
$summary | ConvertTo-Json
if (-not $pass) { throw "Checkpoint C $Mode smoke failed; inspect $summaryPath" }
