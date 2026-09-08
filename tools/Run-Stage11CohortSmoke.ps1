[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z')][string]$Cohort = 'a')

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$savePermissions = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\permissions.json"
$runDirectory = Join-Path $projectRoot "run\stage11-cohort-$Cohort-smoke"
$mods = Join-Path $runDirectory 'mods'
$evidence = Join-Path $projectRoot "evidence\stage-11\cohort-$Cohort"
$stage11Archived = Join-Path $evidence 'artifacts\HytaleRPG-0.0.23.jar'
if ((Test-Path -LiteralPath $stage11Archived) -and
    (Get-FileHash -LiteralPath $stage11Archived).Hash -ne (Get-FileHash -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.23.jar')).Hash) {
    throw 'This cohort already archives a different build. Use the next cohort; do not overwrite evidence.'
}
$expectedConnections = 8
$expectedConnectionCauses = 5
$expectedProfiles = 15
$expectedStatusAssets = 10
$expectedSupport = 16
$expectedPlayerSchema = if($Cohort -in @('a','b')){5}else{6}
$expectedSummons = 9
New-Item -ItemType Directory -Force -Path $mods, $evidence | Out-Null
$resolved = (Resolve-Path -LiteralPath $mods).Path
if (-not $resolved.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe smoke path: $resolved" }
Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.23.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $resolved
Copy-Item -LiteralPath $savePermissions -Destination (Join-Path $runDirectory 'permissions.json') -Force

Push-Location $runDirectory
try {
    $start = [Diagnostics.ProcessStartInfo]::new('java', "-jar `"$serverJar`" --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
    $start.WorkingDirectory = $runDirectory; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start R030 smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 30
    if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(30000)) { $process.Kill($true); throw 'R030 smoke server timeout.' }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
    $exitCode = $process.ExitCode
}
finally { Pop-Location }
$plain.TrimEnd() | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    jarSha256 = (Get-FileHash -LiteralPath (Join-Path $resolved 'HytaleRPG-0.0.23.jar')).Hash
    exactlyThreeMods = @(Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File).Count -eq 3
    rpgDiscovered = [bool]($plain -match 'HytaleRPG-0\.0\.23\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R030 version=0\.0\.23 hytale=0\.7\.0-pre\.1 stage=11')
    ready = [bool]($plain -match 'RPG_STAGE05_READY revision=R030 .* abilityHud=NATIVE_HYTALE_ONLY')
    packagedRootResolved = [bool]($plain -match 'RPG_NATIVE_BRIDGE_AUDIT revision=R030 root=Root_RPG_Ability_Bridge exists=true operations=2 operation=FirstClickInteraction\+NativeSkillActivationInteraction waitFor=Client operationRemote=true rootRemote=true effectFree=true result=PASS')
    shippedRuneResolved = [bool]($plain -match 'RPG_NATIVE_RUNE_CONTROL_AUDIT revision=R030 item=Rune_Fireball root=Root_Ability_Fireball .* result=PASS connectedProof=false')
    nativeAbilityAssetsRejected = [bool]($plain -match '(?i)(RPG_Ability_|Root_RPG_Ability_Bridge).{0,240}(error|failed|invalid|unknown)')
    pluginEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    managerStarted = [bool]($plain -match 'Plugin manager started!')
    networkBooted = [bool]($plain -match 'Hytale Server Booted')
    cleanShutdown = [bool]($plain -match 'Shutting down\.\.\. 0\s')
    areaAssetsResolved = [bool]($plain -match "RPG_STAGE06_ASSETS revision=R030 areaProfiles=$expectedProfiles requiredStatusAssets=$expectedStatusAssets nativeDamageChannels=2 result=PASS connectedProof=false")
    connectionAssetsResolved = [bool]($plain -match "RPG_STAGE08_ASSETS revision=R030 connectionProfiles=$expectedConnections nativeDamageChannels=$expectedConnectionCauses result=PASS connectedProof=false")
    supportConfigured = [bool]($plain -match "RPG_STAGE09_READY revision=R030 supportProfiles=$expectedSupport playerSchema=$expectedPlayerSchema regenAdapter=NATIVE_ENTRY_DECORATOR reservationProjection=STATIC_MAX allyPolicy=SELF_OR_NATIVE_FRIENDLY connectedProof=false")
    summonAssetsResolved = [bool]($plain -match "RPG_STAGE10_ASSETS revision=R030 summonProfiles=$expectedSummons role=RPG_Summon_Wolf result=PASS connectedProof=false")
    batchRolesResolved = [bool]($plain -match 'RPG_STAGE10_BATCH_ROLES count=3 result=PASS connectedProof=false')
    decoyRoleResolved = [bool]($plain -match 'RPG_STAGE10_DECOY_ROLE appearance=Mannequin attacks=0 result=PASS connectedProof=false')
    strikeLockResolved = $Cohort -lt 'm' -or [bool]($plain -match 'RPG_STAGE11_STRIKE_ACTION_LOCK asset=RPG_Strike_Action_Lock disabledInteractions=6 movementUnchanged=true result=PASS connectedProof=false')
    hitProcAssetsResolved = $Cohort -lt 't' -or [bool]($plain -match 'RPG_STAGE11_HIT_PROC_ASSETS bleedVisual=RPG_Bleed_Visual nativeDamage=false movementUnchanged=true result=PASS connectedProof=false')
    failure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|shutdownReason\.pluginError|reason: mod_error|Failed to create HytaleServer|Failed to shutdown Hytale:ServerManager|Listeners is empty)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.hitProcAssetsResolved -and $summary.strikeLockResolved -and $summary.exactlyThreeMods -and $summary.rpgDiscovered -and $summary.rpgSetup -and $summary.ready -and
    $summary.decoyRoleResolved -and $summary.batchRolesResolved -and $summary.summonAssetsResolved -and $summary.supportConfigured -and $summary.connectionAssetsResolved -and $summary.areaAssetsResolved -and $summary.packagedRootResolved -and $summary.shippedRuneResolved -and $summary.pluginEnabled -and $summary.managerStarted -and $summary.networkBooted -and $summary.cleanShutdown) -or
    $summary.nativeAbilityAssetsRejected -or $summary.failure) { throw 'R030 smoke gate failed.' }
