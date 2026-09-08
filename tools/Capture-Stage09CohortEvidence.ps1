[CmdletBinding()]
param([ValidateSet('a','b')][string]$Cohort='a')
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
    $stage9Minimum=if($Cohort -eq 'a'){374}else{402}
    if($stage9Count -lt $stage9Minimum -or $stage9Failures -or $stage9Errors -or $stage9Skipped){throw 'Incomplete or failing retained regression suite.'}
    & "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $stage9Jar
    $stage9ProtectedPaths=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
        'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
        'src/main/resources/Server/ProjectileConfigs','src/main/java/com/inigmasgames/hytalerpg/execution/projectile',
        'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown','src/main/resources/rpg/balance',
        'src/main/java/com/inigmasgames/hytalerpg/combat/resource/ResourceCost.java')
    $stage9Protected=@(& git diff --name-only 31d4a74 -- @stage9ProtectedPaths)
    if($stage9Protected.Count){throw "Protected HUD/resource/profile changes: $stage9Protected"}
    $stage9Smoke=Get-Content -Raw -LiteralPath (Join-Path $stage9Evidence 'server-smoke-summary.json') | ConvertFrom-Json
    $stage9Hash=(Get-FileHash -LiteralPath $stage9Jar).Hash
    if(-not $stage9Smoke.networkBooted -or -not $stage9Smoke.cleanShutdown -or -not $stage9Smoke.supportConfigured -or $stage9Smoke.failure -or
        $stage9Smoke.processExitCode -ne 0 -or $stage9Smoke.jarSha256 -ne $stage9Hash){throw 'Smoke must establish normal boot/stop of this exact JAR.'}
    $stage9Zip=[IO.Compression.ZipFile]::OpenRead($stage9Jar)
    try {
        $stage9AbilityAssets=@($stage9Zip.Entries | Where-Object {$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
        $stage9Triggers=if($Cohort -eq 'a'){38}else{43}
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
        capturedAtUtc=[DateTime]::UtcNow.ToString('o');revision='R028';version='0.0.21';stage='09';cohort=$Cohort;playerSchema=4
        branch=(& git branch --show-current).Trim();sourceHead=(& git rev-parse HEAD).Trim();worktreeDirty=[bool](& git status --porcelain)
        stageStatus='IMPLEMENTATION_IN_PROGRESS';completeStageGate=$false;gateScope='LOCAL_ENGINEERING_ONLY'
        cohortSkills=$(if($Cohort -eq 'a'){@('minor_heal','managuard','emanatism')}else{@('taunt','weakening_hex','hunter_s_mark','intimidate','battle_cry')})
        tests=$stage9Count;failures=$stage9Failures;errors=$stage9Errors;skipped=$stage9Skipped
        connectedVerification='UNVERIFIED';nativeCastingFixed=$false;nativeSupportBehaviorVerified=$false;liveDeploymentPerformed=$false
        protectedPathsChanged=$stage9Protected;zeroNativeCostTriggerAssets=$stage9AbilityAssets.Count
        reservationAuthority='Existing ReservationService; 03.3 transactional debit; actual native static spendable capacity'
        regenerationAuthority='Original native RegeneratingValue clock and conditions; positive Mana entry decorator'
        allyPolicy='SELF_OR_NATIVE_FRIENDLY_OR_REVERED; affirmative player-party provider pending Stage 12'
        jarSha256=$stage9Hash;serverSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Package 'Server\HytaleServer.jar')).Hash
        assetsSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Package 'Assets.zip')).Hash
        rollbackSha256=(Get-FileHash -LiteralPath (Join-Path $stage9Evidence 'rollback\HytaleRPG-0.0.20.jar')).Hash
        rollbackStateRequirement='Before eventual live upgrade back up the full schema-3 player directory; .20 cannot read schema 4. No live state was migrated.'
    }
    $stage9Summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage9Evidence 'verification.json') -Encoding utf8
    [pscustomobject]$stage9Summary | Format-List
} finally {Pop-Location}
