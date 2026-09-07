[CmdletBinding()]
param([ValidateSet('a','b')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$stage8Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage8Evidence=Join-Path $stage8Root "evidence\stage-08\cohort-$Cohort"
$stage8Package="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage8Jar=Join-Path $stage8Root 'build\libs\HytaleRPG-0.0.20.jar'
$stage8Archived=Join-Path $stage8Evidence 'artifacts\HytaleRPG-0.0.20.jar'
if((Test-Path -LiteralPath $stage8Archived) -and (Get-FileHash -LiteralPath $stage8Archived).Hash -ne (Get-FileHash -LiteralPath $stage8Jar).Hash){throw 'Do not overwrite an earlier cohort artifact.'}
New-Item -ItemType Directory -Force -Path $stage8Evidence,(Join-Path $stage8Evidence 'artifacts'),(Join-Path $stage8Evidence 'rollback') | Out-Null
Push-Location $stage8Root
try {
    $stage8Results=@();$stage8Count=0;$stage8Failures=0;$stage8Errors=0;$stage8Skipped=0
    foreach($stage8File in Get-ChildItem -Recurse -Path 'build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test' -Filter 'TEST-*.xml') {
        [xml]$stage8Xml=Get-Content -Raw -LiteralPath $stage8File.FullName
        $stage8Suite=$stage8Xml.testsuite
        $stage8Count += [int]$stage8Suite.tests;$stage8Failures += [int]$stage8Suite.failures
        $stage8Errors += [int]$stage8Suite.errors;$stage8Skipped += [int]$stage8Suite.skipped
        $stage8Results += @{name=$stage8Suite.name;tests=[int]$stage8Suite.tests;failures=[int]$stage8Suite.failures;
            errors=[int]$stage8Suite.errors;skipped=[int]$stage8Suite.skipped;seconds=$stage8Suite.time;cases=@($stage8Suite.testcase | ForEach-Object {$_.name})}
    }
    if($stage8Count -lt 298 -or $stage8Failures -or $stage8Errors -or $stage8Skipped){throw 'Incomplete or failing retained regression suite.'}
    & "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $stage8Jar
    $stage8ProtectedPaths=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
        'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
        'src/main/resources/Server/ProjectileConfigs','src/main/java/com/inigmasgames/hytalerpg/execution/projectile',
        'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown','src/main/resources/rpg/balance',
        'src/main/java/com/inigmasgames/hytalerpg/combat/resource/ResourceCost.java',
        'src/main/java/com/inigmasgames/hytalerpg/combat/resource/ReservationService.java')
    $stage8Protected=@(& git diff --name-only 61fcc86 -- @stage8ProtectedPaths)
    if($stage8Protected.Count){throw "Protected HUD/resource/profile changes: $stage8Protected"}
    $stage8Smoke=Get-Content -Raw -LiteralPath (Join-Path $stage8Evidence 'server-smoke-summary.json') | ConvertFrom-Json
    $stage8Hash=(Get-FileHash -LiteralPath $stage8Jar).Hash
    if(-not $stage8Smoke.networkBooted -or -not $stage8Smoke.cleanShutdown -or -not $stage8Smoke.connectionAssetsResolved -or $stage8Smoke.failure -or
        $stage8Smoke.processExitCode -ne 0 -or $stage8Smoke.jarSha256 -ne $stage8Hash){throw 'Smoke must establish normal boot/stop of this exact JAR.'}
    $stage8Zip=[IO.Compression.ZipFile]::OpenRead($stage8Jar)
    try {
        $stage8AbilityAssets=@($stage8Zip.Entries | Where-Object {$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
        $stage8Expected=if($Cohort -eq 'a'){30}else{35}
        if($stage8AbilityAssets.Count -ne $stage8Expected){throw 'Unexpected trigger inventory.'}
        foreach($stage8Entry in $stage8AbilityAssets) {
            $stage8Reader=[IO.StreamReader]::new($stage8Entry.Open())
            try{$stage8Asset=$stage8Reader.ReadToEnd() | ConvertFrom-Json}finally{$stage8Reader.Dispose()}
            if($stage8Asset.Ability.Cost -ne 0 -or $stage8Asset.Ability.CostType -ne 'None' -or $stage8Asset.Ability.Cooldown -ne 0 -or
                $stage8Asset.Ability.Cast -ne 'Root_RPG_Ability_Bridge'){throw "Duplicated native gameplay cost/trigger: $($stage8Entry.FullName)"}
        }
    } finally {$stage8Zip.Dispose()}
    Copy-Item -LiteralPath $stage8Jar -Destination $stage8Archived -Force
    Copy-Item -LiteralPath 'evidence/stage-07/cohort-c/artifacts/HytaleRPG-0.0.19.jar' -Destination (Join-Path $stage8Evidence 'rollback\HytaleRPG-0.0.19.jar') -Force
    $stage8Results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage8Evidence 'test-results.json') -Encoding utf8
    $stage8Summary=[ordered]@{
        capturedAtUtc=[DateTime]::UtcNow.ToString('o');revision='R027';version='0.0.20';stage='08';cohort=$Cohort
        branch=(& git branch --show-current).Trim();sourceHead=(& git rev-parse HEAD).Trim();worktreeDirty=[bool](& git status --porcelain)
        stageStatus=$(if($Cohort -eq 'b'){'IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION'}else{'IMPLEMENTATION_IN_PROGRESS'})
        completeStageGate=($Cohort -eq 'b');gateScope='LOCAL_ENGINEERING_ONLY'
        cohortSkills=$(if($Cohort -eq 'a'){@('wind_cutter','void_beam','ball_lightning')}else{@('root_lash','lightning_bolt','chain_lightning','orbiting_shadow_blades','life_drain')})
        tests=$stage8Count;failures=$stage8Failures;errors=$stage8Errors;skipped=$stage8Skipped
        connectedVerification='UNVERIFIED';nativeCastingFixed=$false;nativeConnectionBehaviorVerified=$false;liveDeploymentPerformed=$false
        protectedPathsChanged=$stage8Protected;zeroNativeCostTriggerAssets=$stage8AbilityAssets.Count
        fractionalUpkeepAuthority='RpgResourceService.evaluateUpkeep; unchanged upfront ceil/min-one rule'
        jarSha256=$stage8Hash;serverSha256=(Get-FileHash -LiteralPath (Join-Path $stage8Package 'Server\HytaleServer.jar')).Hash
        assetsSha256=(Get-FileHash -LiteralPath (Join-Path $stage8Package 'Assets.zip')).Hash
        rollbackSha256=(Get-FileHash -LiteralPath (Join-Path $stage8Evidence 'rollback\HytaleRPG-0.0.19.jar')).Hash
    }
    $stage8Summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage8Evidence 'verification.json') -Encoding utf8
    [pscustomobject]$stage8Summary | Format-List
} finally {Pop-Location}
