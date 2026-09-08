[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$stage9Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage9Evidence=Join-Path $stage9Root "evidence\stage-09\cohort-$Cohort"
$stage9Package="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage9Jar=Join-Path $stage9Root 'build\libs\HytaleRPG-0.0.21.jar'
$stage9Archived=Join-Path $stage9Evidence 'artifacts\HytaleRPG-0.0.21.jar'
if((Test-Path -LiteralPath $stage9Archived) -and (Get-FileHash -LiteralPath $stage9Archived).Hash -ne (Get-FileHash -LiteralPath $stage9Jar).Hash){throw 'Do not overwrite an earlier cohort artifact.'}
New-Item -ItemType Directory -Force -Path $stage9Evidence,(Join-Path $stage9Evidence 'artifacts'),(Join-Path $stage9Evidence 'rollback') | Out-Null
Push-Location $stage9Root
try {
    $stage9Results=@();$stage9Count=0;$stage9Failures=0;$stage9Errors=0;$stage9Skipped=0
    foreach($stage9File in Get-ChildItem -Recurse -Path 'build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test' -Filter 'TEST-*.xml') {
        [xml]$stage9Xml=Get-Content -Raw -LiteralPath $stage9File.FullName
        $stage9Suite=$stage9Xml.testsuite
        $stage9Count += [int]$stage9Suite.tests;$stage9Failures += [int]$stage9Suite.failures
        $stage9Errors += [int]$stage9Suite.errors;$stage9Skipped += [int]$stage9Suite.skipped
        $stage9Results += @{name=$stage9Suite.name;tests=[int]$stage9Suite.tests;failures=[int]$stage9Suite.failures;
            errors=[int]$stage9Suite.errors;skipped=[int]$stage9Suite.skipped;seconds=$stage9Suite.time;cases=@($stage9Suite.testcase | ForEach-Object {$_.name})}
    }
    $stage9Minimum=switch($Cohort){'a'{374};'b'{402};'c'{428};'d'{451};'e'{476};'f'{516}}
    if($stage9Count -lt $stage9Minimum -or $stage9Failures -or $stage9Errors -or $stage9Skipped){throw 'Incomplete or failing retained regression suite.'}
    & "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $stage9Jar
    $stage9ProtectedPaths=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
        'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
        'src/main/resources/Server/ProjectileConfigs','src/main/java/com/inigmasgames/hytalerpg/execution/projectile',
        'src/main/resources/rpg/balance',
        'src/main/java/com/inigmasgames/hytalerpg/combat/resource/ResourceCost.java')
    $stage9Protected=@(& git diff --name-only 31d4a74 -- @stage9ProtectedPaths)
    if($stage9Protected.Count){throw "Protected HUD/resource/profile changes: $stage9Protected"}
    $stage9CooldownChanges=@(& git diff --name-only 31d4a74 -- 'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown')
    $stage9AllowedCooldown='src/main/java/com/inigmasgames/hytalerpg/combat/cooldown/RpgCooldownService.java'
    if($Cohort -eq 'f'){$stage9AllowedCooldown=@($stage9AllowedCooldown,'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown/SavedCooldown.java')}
    if($stage9CooldownChanges | Where-Object {$Cohort -notin @('d','e','f') -or $_ -notin $stage9AllowedCooldown}){throw 'Unapproved cooldown changes outside the Pedanticism/durable work integration.'}
    $stage9Smoke=Get-Content -Raw -LiteralPath (Join-Path $stage9Evidence 'server-smoke-summary.json') | ConvertFrom-Json
    $stage9Hash=(Get-FileHash -LiteralPath $stage9Jar).Hash
    if(-not $stage9Smoke.networkBooted -or -not $stage9Smoke.cleanShutdown -or -not $stage9Smoke.supportConfigured -or $stage9Smoke.failure -or
        $stage9Smoke.processExitCode -ne 0 -or $stage9Smoke.jarSha256 -ne $stage9Hash){throw 'Smoke must establish normal boot/stop of this exact JAR.'}
    $stage9Zip=[IO.Compression.ZipFile]::OpenRead($stage9Jar)
    try {
        $stage9AbilityAssets=@($stage9Zip.Entries | Where-Object {$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
        $stage9Triggers=switch($Cohort){'a'{38};'b'{43};'c'{47};'d'{51};'e'{51};'f'{51}}
        if($stage9AbilityAssets.Count -ne $stage9Triggers){throw 'Unexpected trigger inventory.'}
        foreach($stage9Entry in $stage9AbilityAssets) {
            $stage9Reader=[IO.StreamReader]::new($stage9Entry.Open())
            try{$stage9Asset=$stage9Reader.ReadToEnd() | ConvertFrom-Json}finally{$stage9Reader.Dispose()}
            if($stage9Asset.Ability.Cost -ne 0 -or $stage9Asset.Ability.CostType -ne 'None' -or $stage9Asset.Ability.Cooldown -ne 0 -or
                $stage9Asset.Ability.Cast -ne 'Root_RPG_Ability_Bridge'){throw "Duplicated native gameplay cost/trigger: $($stage9Entry.FullName)"}
        }
    } finally {$stage9Zip.Dispose()}
    Copy-Item -LiteralPath $stage9Jar -Destination $stage9Archived -Force
    Copy-Item -LiteralPath 'evidence/stage-08/cohort-b/artifacts/HytaleRPG-0.0.20.jar' -Destination (Join-Path $stage9Evidence 'rollback\HytaleRPG-0.0.20.jar') -Force
    $stage9Results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage9Evidence 'test-results.json') -Encoding utf8
    $stage9Summary=[ordered]@{
        capturedAtUtc=[DateTime]::UtcNow.ToString('o');revision='R028';version='0.0.21';stage='09';cohort=$Cohort;playerSchema=$(if($Cohort -eq 'f'){5}else{4})
        branch=(& git branch --show-current).Trim();sourceHead=(& git rev-parse HEAD).Trim();worktreeDirty=[bool](& git status --porcelain)
        stageStatus=$(if($Cohort -eq 'f'){'IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION'}else{'IMPLEMENTATION_IN_PROGRESS'});completeStageGate=($Cohort -eq 'f');gateScope='LOCAL_ENGINEERING_ONLY'
        cohortSkills=$(switch($Cohort){'a'{@('minor_heal','managuard','emanatism')};'b'{@('taunt','weakening_hex','hunter_s_mark','intimidate','battle_cry')};'c'{@('pack_howl','reflective_hide','flame_weapon','spirit_shield')};'d'{@('thorns_aura','chilling_aura','pedanticism','reaping_storm')}})
        cohortPassives=$(switch($Cohort){'e'{@('selflessness','conservation','resonance','overflow')};'f'{@('triage','shared_aegis','reflective_ward')};default{@()}})
        compiledPlanSchema=$(switch($Cohort){'e'{5};'f'{6};default{4}})
        capabilityGates=$(if($Cohort -in @('c','d','e','f')){@{flame_weapon='NATIVE_ROOT_WEAPON_CONTACT_ID_UNAVAILABLE; no native hit callback is wired; rejects before cost/cooldown';pedanticismEnemy=$(if($Cohort -in @('d','e','f')){'NATIVE_COOLDOWN_REMAINING_WORK_NOT_EXPOSED; ally RPG rate implemented, enemy native branch not modified'}else{'NOT_IN_COHORT'})}}else{@{}})
        tests=$stage9Count;failures=$stage9Failures;errors=$stage9Errors;skipped=$stage9Skipped
        connectedVerification='UNVERIFIED';nativeCastingFixed=$false;nativeSupportBehaviorVerified=$false;liveDeploymentPerformed=$false
        protectedPathsChanged=$stage9Protected;zeroNativeCostTriggerAssets=$stage9AbilityAssets.Count
        explicitlyScopedCooldownChanges=$stage9CooldownChanges
        cooldownPersistence=$(if($Cohort -eq 'f'){'Remaining RPG work persisted before dispatch, <=1Hz checkpoint, disconnect save not reset; no offline credit or saved Aura rate'}else{'Runtime only, not persisted yet'})
        reservationAuthority='Existing ReservationService; 03.3 transactional debit; actual native static spendable capacity'
        regenerationAuthority='Original native RegeneratingValue clock and conditions; positive Mana entry decorator'
        allyPolicy='SELF_OR_NATIVE_FRIENDLY_OR_REVERED; affirmative player-party provider pending Stage 12'
        jarSha256=$stage9Hash;serverSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Package 'Server\HytaleServer.jar')).Hash
        assetsSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Package 'Assets.zip')).Hash
        rollbackSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Evidence 'rollback\HytaleRPG-0.0.20.jar')).Hash
        rollbackStateRequirement='Before eventual live upgrade back up the full schema-3 player directory; .20 cannot read newer schemas. Cohort F also requires pre-F state to downgrade to cohort E. No live state was migrated.'
    }
    if($Cohort -eq 'f'){
        [xml]$stage9LoadXml=Get-Content -Raw -LiteralPath 'build/test-results/test/TEST-com.inigmasgames.hytalerpg.Stage09LoadTest.xml'
        $stage9Summary.loadMeasurements=@(($stage9LoadXml.SelectSingleNode('/testsuite/system-out').InnerText -split "`n") | Where-Object {$_ -match '^STAGE09_LOAD'})
    }
    $stage9Summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage9Evidence 'verification.json') -Encoding utf8
    [pscustomobject]$stage9Summary | Format-List
} finally {Pop-Location}
