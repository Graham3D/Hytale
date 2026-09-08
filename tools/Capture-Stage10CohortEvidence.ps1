[CmdletBinding()]
param([ValidateSet('a','b')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$summonRoot=(Resolve-Path "$PSScriptRoot\..").Path
$summonExpectedTests=if($Cohort -eq 'a'){535}else{555}
$summonExpectedTriggers=if($Cohort -eq 'a'){52}else{53}
$summonSkills=if($Cohort -eq 'a'){@('wolf_summon')}else{@('revive_fallen')}
$summonEvidence=Join-Path $summonRoot "evidence\stage-10\cohort-$Cohort"
$summonJar=Join-Path $summonRoot 'build\libs\HytaleRPG-0.0.22.jar'
$summonArchive=Join-Path $summonEvidence 'artifacts\HytaleRPG-0.0.22.jar'
$summonHash=(Get-FileHash -LiteralPath $summonJar).Hash
if((Test-Path -LiteralPath $summonArchive) -and (Get-FileHash -LiteralPath $summonArchive).Hash -ne $summonHash){throw 'Never overwrite an archived cohort build'}
$summonTests=@();$summonCount=0
foreach($summonFile in Get-ChildItem -Path "$summonRoot\build\test-results\test","$summonRoot\build\test-results\nativeControlTest","$summonRoot\canvas-ui\build\test-results\test" -Filter 'TEST-*.xml'){
    [xml]$summonXml=Get-Content -Raw -LiteralPath $summonFile.FullName;$summonSuite=$summonXml.testsuite
    if([int]$summonSuite.failures -or [int]$summonSuite.errors -or [int]$summonSuite.skipped){throw 'All retained tests must pass without skips'}
    $summonCount += [int]$summonSuite.tests
    $summonTests+=@{name=$summonSuite.name;tests=[int]$summonSuite.tests;seconds=$summonSuite.time;cases=@($summonSuite.testcase | ForEach-Object {$_.name})}
}
if($summonCount -lt $summonExpectedTests){throw 'Incomplete retained regression suite'}
$summonSmoke=Get-Content -Raw -LiteralPath (Join-Path $summonEvidence 'server-smoke-summary.json') | ConvertFrom-Json
if($summonSmoke.jarSha256 -ne $summonHash -or $summonSmoke.processExitCode -ne 0 -or $summonSmoke.failure -or
    -not $summonSmoke.networkBooted -or -not $summonSmoke.cleanShutdown -or -not $summonSmoke.summonAssetsResolved -or -not $summonSmoke.exactlyThreeMods){throw 'Exact build must pass normal isolated smoke'}
& "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $summonJar
$summonProtected=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
    'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
    'src/main/resources/Server/ProjectileConfigs','src/main/java/com/inigmasgames/hytalerpg/execution/projectile',
    'src/main/resources/rpg/balance','src/main/java/com/inigmasgames/hytalerpg/combat/resource','src/main/java/com/inigmasgames/hytalerpg/combat/cooldown')
$summonChanged=@(& git -C $summonRoot diff --name-only 837ed80 -- @summonProtected)
if($summonChanged.Count){throw "Protected behavior changed: $summonChanged"}
$summonZip=[IO.Compression.ZipFile]::OpenRead($summonJar)
try{
    $summonAbilityAssets=@($summonZip.Entries | Where-Object {$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
    if($summonAbilityAssets.Count -ne $summonExpectedTriggers){throw 'Unexpected native trigger inventory'}
    foreach($summonEntry in $summonAbilityAssets){
        $summonReader=[IO.StreamReader]::new($summonEntry.Open())
        try{$summonAbility=($summonReader.ReadToEnd() | ConvertFrom-Json).Ability}finally{$summonReader.Dispose()}
        if($summonAbility.Cost -ne 0 -or $summonAbility.Cooldown -ne 0 -or $summonAbility.CostType -ne 'None' -or
            $summonAbility.Cast -ne 'Root_RPG_Ability_Bridge'){throw 'Native ability must not duplicate RPG gameplay'}
    }
}finally{$summonZip.Dispose()}
New-Item -ItemType Directory -Force -Path (Join-Path $summonEvidence 'artifacts'),(Join-Path $summonEvidence 'rollback') | Out-Null
Copy-Item -LiteralPath $summonJar -Destination $summonArchive -Force
$summonRollback=Join-Path $summonRoot 'evidence\stage-09\cohort-f\artifacts\HytaleRPG-0.0.21.jar'
if($Cohort -eq 'b'){$summonRollback=Join-Path $summonRoot 'evidence\stage-10\cohort-a\artifacts\HytaleRPG-0.0.22.jar'}
Copy-Item -LiteralPath $summonRollback -Destination (Join-Path $summonEvidence ('rollback\'+[IO.Path]::GetFileName($summonRollback))) -Force
$summonTests | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $summonEvidence 'test-results.json') -Encoding utf8
$summonApi=Get-Content -Raw -LiteralPath (Join-Path $summonRoot 'evidence\stage-10\api\manifest.json') | ConvertFrom-Json
[ordered]@{
    capturedAtUtc=[DateTime]::UtcNow.ToString('o');stage=10;cohort=$Cohort;revision='R029';version='0.0.22';playerSchema=5;compiledPlanSchema=6
    sourceHead=(& git -C $summonRoot rev-parse HEAD).Trim();branch=(& git -C $summonRoot branch --show-current).Trim()
    status='IMPLEMENTATION_IN_PROGRESS';cohortStatus='IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION';localGate='PASS';connectedGate='UNVERIFIED'
    cohortSkills=@($summonSkills);cohortPassives=@();tests=$summonCount;failures=0;errors=0;skipped=0
    jarSha256=$summonHash;rollbackSha256=(Get-FileHash -LiteralPath $summonRollback).Hash
    serverSha256=$summonApi.serverSha256;assetsSha256=$summonApi.assetsSha256;zeroNativeCostTriggers=$summonAbilityAssets.Count
    normalThreeModSmoke=$true;nativeSpawnOrMotionProven=$false;nativeCastingFixed=$false;liveDeploymentPerformed=$false
    protectedPathsChanged=$summonChanged;remainingStage10Skills=(60-$summonExpectedTriggers);stage10Complete=$false
    rollbackStateRequirement='No migration from Stage09 schema5. Earlier live R023 schema3 still needs its own backup before any eventual deployment.'
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $summonEvidence 'verification.json') -Encoding utf8
[pscustomobject]@{tests=$summonCount;jarSha256=$summonHash;connectedGate='UNVERIFIED'}
