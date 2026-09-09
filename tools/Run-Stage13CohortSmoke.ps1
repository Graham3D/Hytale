[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z')][string]$Cohort = 'a')

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$savePermissions = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\permissions.json"
$runDirectory = Join-Path $projectRoot "run\stage13-cohort-$Cohort-smoke"
$mods = Join-Path $runDirectory 'mods'
$evidence = Join-Path $projectRoot "evidence\stage-13\cohort-$Cohort"
$stage13Archived = Join-Path $evidence 'artifacts\HytaleRPG-0.0.25.jar'
if ((Test-Path -LiteralPath $stage13Archived) -and
    (Get-FileHash -LiteralPath $stage13Archived).Hash -ne (Get-FileHash -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.25.jar')).Hash) {
    throw 'This cohort already archives a different build. Use the next cohort; do not overwrite evidence.'
}
$expectedConnections = 8
$expectedConnectionCauses = 5
$expectedProfiles = 15
$expectedStatusAssets = 10
$expectedSupport = 16
$expectedPlayerSchema = 9
$expectedSummons = 9
$expectedNativeBiomes = 4
$expectedAwardHook = 'true'
$expectedMastery = 'true'
New-Item -ItemType Directory -Force -Path $mods, $evidence | Out-Null
$resolved = (Resolve-Path -LiteralPath $mods).Path
if (-not $resolved.StartsWith(($projectRoot + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe smoke path: $resolved" }
Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.25.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $resolved
Copy-Item -LiteralPath $savePermissions -Destination (Join-Path $runDirectory 'permissions.json') -Force

Push-Location $runDirectory
try {
    $start = [Diagnostics.ProcessStartInfo]::new('java', "-jar `"$serverJar`" --bind 127.0.0.1:0 --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
    $start.WorkingDirectory = $runDirectory; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start R032 smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 30
    if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(30000)) { $process.Kill($true); throw 'R032 smoke server timeout.' }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
    $exitCode = $process.ExitCode
}
finally { Pop-Location }
$plain.TrimEnd() | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    jarSha256 = (Get-FileHash -LiteralPath (Join-Path $resolved 'HytaleRPG-0.0.25.jar')).Hash
    exactlyThreeMods = @(Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File).Count -eq 3
    rpgDiscovered = [bool]($plain -match 'HytaleRPG-0\.0\.25\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R032 version=0\.0\.25 hytale=0\.7\.0-pre\.1 stage=13')
    ready = [bool]($plain -match 'RPG_STAGE05_READY revision=R032 .* abilityHud=NATIVE_HYTALE_ONLY')
    packagedRootResolved = [bool]($plain -match 'RPG_NATIVE_BRIDGE_AUDIT revision=R032 root=Root_RPG_Ability_Bridge exists=true operations=2 operation=FirstClickInteraction\+NativeSkillActivationInteraction waitFor=Client operationRemote=true rootRemote=true effectFree=true result=PASS')
    shippedRuneResolved = [bool]($plain -match 'RPG_NATIVE_RUNE_CONTROL_AUDIT revision=R032 item=Rune_Fireball root=Root_Ability_Fireball .* result=PASS connectedProof=false')
    nativeAbilityAssetsRejected = [bool]($plain -match '(?i)(RPG_Ability_|Root_RPG_Ability_Bridge).{0,240}(error|failed|invalid|unknown)')
    pluginEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    managerStarted = [bool]($plain -match 'Plugin manager started!')
    networkBooted = [bool]($plain -match 'Hytale Server Booted')
    cleanShutdown = [bool]($plain -match 'Shutting down\.\.\. 0\s')
    areaAssetsResolved = [bool]($plain -match "RPG_STAGE06_ASSETS revision=R032 areaProfiles=$expectedProfiles requiredStatusAssets=$expectedStatusAssets nativeDamageChannels=2 result=PASS connectedProof=false")
    connectionAssetsResolved = [bool]($plain -match "RPG_STAGE08_ASSETS revision=R032 connectionProfiles=$expectedConnections nativeDamageChannels=$expectedConnectionCauses result=PASS connectedProof=false")
    supportConfigured = [bool]($plain -match "RPG_STAGE09_READY revision=R032 supportProfiles=$expectedSupport playerSchema=$expectedPlayerSchema regenAdapter=NATIVE_ENTRY_DECORATOR reservationProjection=STATIC_MAX allyPolicy=SELF_OR_NATIVE_FRIENDLY connectedProof=false")
    summonAssetsResolved = [bool]($plain -match "RPG_STAGE10_ASSETS revision=R032 summonProfiles=$expectedSummons role=RPG_Summon_Wolf result=PASS connectedProof=false")
    batchRolesResolved = [bool]($plain -match 'RPG_STAGE10_BATCH_ROLES count=3 result=PASS connectedProof=false')
    decoyRoleResolved = [bool]($plain -match 'RPG_STAGE10_DECOY_ROLE appearance=Mannequin attacks=0 result=PASS connectedProof=false')
    strikeLockResolved = [bool]($plain -match 'RPG_STAGE11_STRIKE_ACTION_LOCK asset=RPG_Strike_Action_Lock disabledInteractions=6 movementUnchanged=true result=PASS connectedProof=false')
    hitProcAssetsResolved = [bool]($plain -match 'RPG_STAGE11_HIT_PROC_ASSETS bleedVisual=RPG_Bleed_Visual nativeDamage=false movementUnchanged=true result=PASS connectedProof=false')
    progressionProfilesResolved = [bool]($plain -match "RPG_STAGE12_PROFILES revision=R032 bands=5 difficulties=3 nativeBiomeBindings=$expectedNativeBiomes awardHook=$expectedAwardHook connectedProof=false")
    rewardStoreConfigured = [bool]($plain -match "RPG_STAGE12_REWARD_STORE playerSchema=$expectedPlayerSchema writeAhead=true immutableReceipts=true awardHook=$expectedAwardHook connectedProof=false")
    encounterRegistryResolved = [bool]($plain -match "RPG_STAGE12_ENCOUNTER_REGISTRY roles=3 biomes=4 rankAuthority=RPG_PROFILE awardHook=$expectedAwardHook connectedProof=false")
    encounterStoreConfigured = [bool]($plain -match "RPG_STAGE12_ENCOUNTER_STORE schema=1 frozenDeathPlans=true permanentExclusions=true pending=0 awardHook=$expectedAwardHook connectedProof=false")
    nativeRewardHooksRegistered = [bool]($plain -match 'RPG_STAGE12_NATIVE_REWARDS spawn=LEGACY_WORLD_SPAWN contribution=POST_APPLY_HEALTH_LOSS death=NATIVE_DEATH_COMPONENT deliveryBudget=8_per_second party=SOLO_ONLY connectedProof=false')
    supportCreditHooksRegistered = [bool]($plain -match "RPG_STAGE12_SUPPORT_CREDIT healing=POST_NATIVE_WRITE absorption=ACTUAL_CONSUMPTION partyProvider=NATIVE_PARTY_PROVIDER_UNAVAILABLE_SOLO_ONLY mastery=$expectedMastery connectedProof=false")
    masteryHooksRegistered = [bool]($plain -match 'RPG_STAGE12_MASTERY damage=INSPECT_BEFORE_DEATH control=NATIVE_STATE_CHANGE healing=HOSTILE_INJURY_ONLY rootDedup=DURABLE sustainedIntervalSeconds=5 movementAvoidance=UNAVAILABLE connectedProof=false')
    acquisitionConfigured = [bool]($plain -match 'RPG_STAGE12_ACQUISITION playerSchema=9 verifiedLearningBindings=0 pity=DURABLE spending=SAME_REWARD_AUTHORITY respec=TEN_SECONDS_AND_NO_PENDING_CAST import=IDS_AND_FIXED_LAYOUT_ONLY connectedProof=false')
    strikeClosureAssetsResolved = [bool]($plain -match 'RPG_STAGE13_STRIKE_ASSETS ordinaryQueryLimit=64 fullHeight=2.5 finiteAnimationProfiles=7 actionLockAssets=2 result=PASS connectedProof=false')
    projectileClosureAssetsResolved = [bool]($plain -match 'RPG_STAGE13_PROJECTILE_ASSETS result=PASS')
    failure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|shutdownReason\.pluginError|reason: mod_error|Failed to create HytaleServer|Failed to shutdown Hytale:ServerManager|Listeners is empty)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if($expectedPlayerSchema -eq 9 -and -not $summary.acquisitionConfigured){throw 'Stage12 acquisition/respec registration gate failed'}
if($expectedMastery -eq 'true' -and -not ($summary.masteryHooksRegistered -and $summary.supportCreditHooksRegistered -and $summary.nativeRewardHooksRegistered -and $summary.encounterStoreConfigured -and $summary.encounterRegistryResolved)){throw 'Stage12 mastery/support native registration gate failed'}
if (($Cohort -eq 'c' -and -not $summary.encounterRegistryResolved) -or ($Cohort -ne 'a' -and -not $summary.rewardStoreConfigured) -or -not ($summary.progressionProfilesResolved -and $summary.hitProcAssetsResolved -and $summary.strikeLockResolved -and $summary.exactlyThreeMods -and $summary.rpgDiscovered -and $summary.rpgSetup -and $summary.ready -and
    $summary.decoyRoleResolved -and $summary.batchRolesResolved -and $summary.summonAssetsResolved -and $summary.supportConfigured -and $summary.connectionAssetsResolved -and $summary.areaAssetsResolved -and $summary.packagedRootResolved -and $summary.shippedRuneResolved -and $summary.pluginEnabled -and $summary.managerStarted -and $summary.networkBooted -and $summary.cleanShutdown) -or
    $summary.nativeAbilityAssetsRejected -or $summary.failure) { throw 'R032 smoke gate failed.' }
if(-not $summary.strikeClosureAssetsResolved -or -not $summary.rewardStoreConfigured -or $summary.processExitCode -ne 0){throw 'Stage13 strike assets or retained durability smoke gate failed'}
if($Cohort -ne 'a'){
    $auditLine=[regex]::Match($plain,'RPG_STAGE13_PROJECTILE_ASSETS result=PASS (\{[^\r\n]*\})')
    if(-not $auditLine.Success){throw 'Stage13 resolved projectile/equipment audit missing'}
    $audit=$auditLine.Groups[1].Value|ConvertFrom-Json
    if($audit.connectedProof -ne $false -or -not $audit.emptyNativeInteractions -or -not $audit.typedElements){throw 'Projectile audit authority mismatch'}
    if($Cohort -eq 'b' -and ($audit.resolvedConfigs -ne 13 -or $audit.shippedCrossbowSpeed -ne 40 -or $audit.shippedCrossbowRadius -ne .075 -or $audit.shippedCrossbowGravity -ne 10 -or
        $audit.equipment.Weapon_Crossbow_Iron.basicPower -ne 10 -or $audit.equipment.Weapon_Spear_Iron.basicPower -ne 6)){throw 'Cohort B native numeric contract mismatch'}
    if($Cohort -in @('c','d','e','f') -and ($audit.resolvedConfigs -ne 19 -or $audit.equipment.Weapon_Gun_Blunderbuss.basicPower -ne 200 -or
        $audit.nativeChargedBow.speed -ne 85 -or $audit.nativeChargedBow.gravity -ne 25 -or $audit.nativeChargedBow.radius -ne .075 -or
        $audit.snipeActivationGate -ne 'NATIVE_BOW_MAX_RANGE_UNVERIFIED')){throw 'Cohort C native source/capability audit mismatch'}
    $audit|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $evidence 'native-projectile-equipment-audit.json') -Encoding utf8
}
if($Cohort -in @('d','e','f')){
    $movementLine=[regex]::Match($plain,'RPG_STAGE13_MOVEMENT_ASSETS result=PASS (\{[^\r\n]*\})')
    if(-not $movementLine.Success){throw 'Native movement/Guard control asset audit missing'}
    $movement=$movementLine.Groups[1].Value|ConvertFrom-Json
    if($movement.operation -ne 'WieldingInteraction' -or $movement.baseDrain -ne 7 -or
        $movement.activationGate -ne 'NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED' -or $movement.connectedProof){throw 'Guard native ownership/capability gate mismatch'}
    $movement|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $evidence 'native-movement-guard-audit.json') -Encoding utf8
}
if($Cohort -in @('e','f')){
    $basicLine=[regex]::Match($plain,'RPG_STAGE13_NATIVE_BASIC_PATHS result=PASS (\{[^\r\n]*\})')
    if(-not $basicLine.Success){throw 'Installed native basic-attack path audit missing'}
    $basic=$basicLine.Groups[1].Value|ConvertFrom-Json
    if($basic.connectedProof -ne $false -or $basic.classification -ne 'RESOLVED_CHARGING_TAG_AND_ACTUAL_ITEM_REPLACE_VARIABLES'){throw 'Native basic source audit authority mismatch'}
    $expectedBasic=@('Weapon_Sword_Iron','Weapon_Sword_Copper','Weapon_Longsword_Iron','Weapon_Daggers_Iron','Weapon_Battleaxe_Iron','Weapon_Mace_Iron','Weapon_Spear_Iron')
    if(@($basic.items.PSObject.Properties).Count -ne $expectedBasic.Count){throw 'Native basic source registry size mismatch'}
    foreach($item in $expectedBasic){
        $paths=@($basic.items.$item.damagePaths.PSObject.Properties)
        if(-not $basic.items.$item.root -or -not $paths.Count -or @($paths|Where-Object{$_.Value -notin @('NORMAL','CHARGED')}).Count){throw "Unclassified installed basic path: $item"}
    }
    if($plain -notmatch 'RPG_STAGE13_NATIVE_BASIC_HOOK start=INTERACTION_CHAIN_START before=POST_FILTER after=POST_APPLY scope=AUDITED_MELEE recovery=ROOT_HEALTH_LOSS finisher=ROOT_HEALTH_LOSS connectedProof=false'){throw 'Native basic witness systems not registered'}
    $basic|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $evidence 'native-basic-path-audit.json') -Encoding utf8
}
