[CmdletBinding()]
param([ValidateSet('a','b')][string]$Cohort = 'a')

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$savePermissions = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\permissions.json"
$runDirectory = Join-Path $projectRoot "run\stage06-cohort-$Cohort-smoke"
$mods = Join-Path $runDirectory 'mods'
$evidence = Join-Path $projectRoot "evidence\stage-06\cohort-$Cohort"
$expectedProfiles = if ($Cohort -eq 'a') { 3 } else { 9 }
$expectedStatusAssets = if ($Cohort -eq 'a') { 8 } else { 10 }
New-Item -ItemType Directory -Force -Path $mods, $evidence | Out-Null
$resolved = (Resolve-Path -LiteralPath $mods).Path
if (-not $resolved.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe smoke path: $resolved" }
Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.18.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $resolved
Copy-Item -LiteralPath $savePermissions -Destination (Join-Path $runDirectory 'permissions.json') -Force

Push-Location $runDirectory
try {
    $start = [Diagnostics.ProcessStartInfo]::new('java', "-jar `"$serverJar`" --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
    $start.WorkingDirectory = $runDirectory; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start R025 smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 30
    if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(30000)) { $process.Kill($true); throw 'R025 smoke server timeout.' }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
    $exitCode = $process.ExitCode
}
finally { Pop-Location }
$plain.TrimEnd() | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    exactlyThreeMods = @(Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File).Count -eq 3
    rpgDiscovered = [bool]($plain -match 'HytaleRPG-0\.0\.18\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R025 version=0\.0\.18 hytale=0\.7\.0-pre\.1 stage=06')
    ready = [bool]($plain -match 'RPG_STAGE05_READY revision=R025 .* abilityHud=NATIVE_HYTALE_ONLY')
    packagedRootResolved = [bool]($plain -match 'RPG_NATIVE_BRIDGE_AUDIT revision=R025 root=Root_RPG_Ability_Bridge exists=true operations=2 operation=FirstClickInteraction\+NativeSkillActivationInteraction waitFor=Client operationRemote=true rootRemote=true effectFree=true result=PASS')
    shippedRuneResolved = [bool]($plain -match 'RPG_NATIVE_RUNE_CONTROL_AUDIT revision=R025 item=Rune_Fireball root=Root_Ability_Fireball .* result=PASS connectedProof=false')
    nativeAbilityAssetsRejected = [bool]($plain -match '(?i)(RPG_Ability_|Root_RPG_Ability_Bridge).{0,240}(error|failed|invalid|unknown)')
    pluginEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    managerStarted = [bool]($plain -match 'Plugin manager started!')
    networkBooted = [bool]($plain -match 'Hytale Server Booted')
    cleanShutdown = [bool]($plain -match 'Shutting down\.\.\. 0\s')
    areaAssetsResolved = [bool]($plain -match "RPG_STAGE06_ASSETS revision=R025 areaProfiles=$expectedProfiles requiredStatusAssets=$expectedStatusAssets nativeDamageChannels=2 result=PASS connectedProof=false")
    failure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|shutdownReason\.pluginError|reason: mod_error|Failed to create HytaleServer|Failed to shutdown Hytale:ServerManager|Listeners is empty)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.exactlyThreeMods -and $summary.rpgDiscovered -and $summary.rpgSetup -and $summary.ready -and
    $summary.areaAssetsResolved -and $summary.packagedRootResolved -and $summary.shippedRuneResolved -and $summary.pluginEnabled -and $summary.managerStarted -and $summary.networkBooted -and $summary.cleanShutdown) -or
    $summary.nativeAbilityAssetsRejected -or $summary.failure) { throw 'R025 smoke gate failed.' }
