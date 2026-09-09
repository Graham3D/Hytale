[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$passiveRoot=(Resolve-Path "$PSScriptRoot\..").Path
$passiveConfig=@{
    a=@{tests=1405;passives=@();planSchema=35;rollback='evidence\stage-11\cohort-z\artifacts\HytaleRPG-0.0.23.jar'}
    b=@{tests=1447;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-a\artifacts\HytaleRPG-0.0.24.jar'}
    c=@{tests=1486;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-b\artifacts\HytaleRPG-0.0.24.jar'}
    d=@{tests=1529;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-c\artifacts\HytaleRPG-0.0.24.jar'}
    e=@{tests=1552;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-d\artifacts\HytaleRPG-0.0.24.jar'}
    f=@{tests=1576;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-e\artifacts\HytaleRPG-0.0.24.jar'}
    g=@{tests=530;targeted=$true;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-f\artifacts\HytaleRPG-0.0.24.jar'}
    h=@{tests=1653;closure=$true;passives=@();planSchema=35;rollback='evidence\stage-12\cohort-g\artifacts\HytaleRPG-0.0.24.jar'}
}[$Cohort]
if(-not $passiveConfig){throw 'Cohort must declare tested scope before evidence capture'}
$passiveEvidence=Join-Path $passiveRoot "evidence\stage-12\cohort-$Cohort"
$passiveJar=Join-Path $passiveRoot 'build\libs\HytaleRPG-0.0.24.jar'
$passiveArchive=Join-Path $passiveEvidence 'artifacts\HytaleRPG-0.0.24.jar'
$passiveHash=(Get-FileHash -LiteralPath $passiveJar).Hash
if((Test-Path -LiteralPath $passiveArchive) -and (Get-FileHash -LiteralPath $passiveArchive).Hash -ne $passiveHash){throw 'Never overwrite an archived cohort build'}
$passiveTests=@();$passiveCount=0
$passiveTestPaths=@("$passiveRoot\build\test-results\test","$passiveRoot\build\test-results\nativeControlTest")
if(-not $passiveConfig.targeted){$passiveTestPaths+="$passiveRoot\canvas-ui\build\test-results\test"}
foreach($passiveFile in Get-ChildItem -Path $passiveTestPaths -Filter 'TEST-*.xml'){
    [xml]$passiveXml=Get-Content -Raw -LiteralPath $passiveFile.FullName;$passiveSuite=$passiveXml.testsuite
    if([int]$passiveSuite.failures -or [int]$passiveSuite.errors -or [int]$passiveSuite.skipped){throw 'All retained tests must pass without skips'}
    $passiveCount += [int]$passiveSuite.tests
    $passiveTests+=@{name=$passiveSuite.name;tests=[int]$passiveSuite.tests;seconds=$passiveSuite.time;cases=@($passiveSuite.testcase | ForEach-Object {$_.name})}
}
if($passiveCount -lt $passiveConfig.tests){throw 'Incomplete retained regression suite'}
$passiveSmoke=Get-Content -Raw -LiteralPath (Join-Path $passiveEvidence 'server-smoke-summary.json') | ConvertFrom-Json
if($passiveSmoke.jarSha256 -ne $passiveHash -or $passiveSmoke.processExitCode -ne 0 -or $passiveSmoke.failure -or
    -not $passiveSmoke.networkBooted -or -not $passiveSmoke.cleanShutdown -or -not $passiveSmoke.summonAssetsResolved -or
    -not $passiveSmoke.exactlyThreeMods -or -not $passiveSmoke.progressionProfilesResolved -or -not $passiveSmoke.batchRolesResolved -or -not $passiveSmoke.decoyRoleResolved){throw 'Exact build must pass normal isolated smoke'}
& "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $passiveJar
if($Cohort -ne 'a' -and -not $passiveSmoke.rewardStoreConfigured){throw 'Durable reward store must be configured at startup'}
if($Cohort -notin @('a','b') -and -not $passiveSmoke.encounterRegistryResolved){throw 'Audited native pilot roles must resolve at startup'}
if($Cohort -in @('d','e','f','g','h') -and -not $passiveSmoke.encounterStoreConfigured){throw 'Persistent encounter store must be configured at startup'}
if($Cohort -in @('e','f','g','h') -and -not $passiveSmoke.nativeRewardHooksRegistered){throw 'Native reward hooks must be registered at startup'}
if($Cohort -in @('f','g','h') -and -not $passiveSmoke.supportCreditHooksRegistered){throw 'Support credit callbacks must be configured at startup'}
if($Cohort -in @('g','h') -and -not $passiveSmoke.masteryHooksRegistered){throw 'Meaningful mastery callbacks must be configured at startup'}
if($Cohort -eq 'h'){
    if(-not $passiveSmoke.acquisitionConfigured){throw 'Acquisition/respec startup gate missing'}
    if(-not ($passiveTests | Where-Object {$_.name -eq 'com.inigmasgames.hytalerpg.Stage12ArchivedRollbackTest' -and $_.tests -eq 1})){throw 'Actual archived-JAR rollback drill must pass'}
    # Count each retained class, not only the aggregate, so added tests cannot mask a removed suite.
    foreach($baseline in (Get-Content -Raw -LiteralPath (Join-Path $passiveRoot 'evidence\stage-12\cohort-f\test-results.json') | ConvertFrom-Json)){
        $current=@($passiveTests | Where-Object {$_.name -eq $baseline.name})
        if($current.Count -ne 1 -or $current[0].tests -lt $baseline.tests){throw "Retained regression class reduced: $($baseline.name)"}
    }
}
$passiveProtected=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
    'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
    'src/main/resources/Server/ProjectileConfigs','src/main/resources/rpg/balance',
    'src/main/java/com/inigmasgames/hytalerpg/input')
$passiveChanged=@(& git -C $passiveRoot diff --name-only e9944e1 -- @passiveProtected)
if($passiveChanged.Count){throw "Protected behavior changed: $passiveChanged"}
$passiveZip=[IO.Compression.ZipFile]::OpenRead($passiveJar)
try{
    $passiveAbilities=@($passiveZip.Entries | Where-Object {$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
    if($passiveAbilities.Count -ne 60){throw 'Unexpected native trigger inventory'}
    foreach($passiveEntry in $passiveAbilities){
        $passiveReader=[IO.StreamReader]::new($passiveEntry.Open())
        try{$passiveAbility=($passiveReader.ReadToEnd() | ConvertFrom-Json).Ability}finally{$passiveReader.Dispose()}
        if($passiveAbility.Cost -ne 0 -or $passiveAbility.Cooldown -ne 0 -or $passiveAbility.CostType -ne 'None' -or
            $passiveAbility.Cast -ne 'Root_RPG_Ability_Bridge'){throw 'Native ability must not duplicate RPG gameplay'}
    }
}finally{$passiveZip.Dispose()}
$passiveSkills=Get-Content -Raw -LiteralPath (Join-Path $passiveRoot 'src\main\resources\rpg\catalog\skills.json')|ConvertFrom-Json
$passiveCatalog=Get-Content -Raw -LiteralPath (Join-Path $passiveRoot 'src\main\resources\rpg\catalog\passives.json')|ConvertFrom-Json
if($passiveSkills.Count -ne 87 -or $passiveCatalog.Count -ne 66){throw 'Canonical catalog count drift'}
New-Item -ItemType Directory -Force -Path (Join-Path $passiveEvidence 'artifacts'),(Join-Path $passiveEvidence 'rollback') | Out-Null
Copy-Item -LiteralPath $passiveJar -Destination $passiveArchive -Force
$passiveRollback=Join-Path $passiveRoot $passiveConfig.rollback
Copy-Item -LiteralPath $passiveRollback -Destination (Join-Path $passiveEvidence ('rollback\'+[IO.Path]::GetFileName($passiveRollback))) -Force
$passiveTests | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $passiveEvidence 'test-results.json') -Encoding utf8
$passivePackage="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$passiveLive=@(Get-ChildItem -LiteralPath "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods" -File -Filter '*.jar' | ForEach-Object {@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
[ordered]@{
    capturedAtUtc=[DateTime]::UtcNow.ToString('o');stage=12;cohort=$Cohort;revision='R031';version='0.0.24';playerSchema=$(if($Cohort -eq 'a'){7}elseif($Cohort -eq 'h'){9}else{8});compiledPlanSchema=$passiveConfig.planSchema
    sourceHead=(& git -C $passiveRoot rev-parse HEAD).Trim();branch=(& git -C $passiveRoot branch --show-current).Trim()
    status=$(if($passiveConfig.closure){'IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION'}else{'IMPLEMENTATION_IN_PROGRESS'});cohortStatus='IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION';localGate='PASS';connectedGate='UNVERIFIED'
    cohortSkills=@();cohortPassives=$passiveConfig.passives;tests=$passiveCount;failures=0;errors=0;skipped=0
    regressionScope=$(if($passiveConfig.targeted){'TARGETED_INTERMEDIATE_NOT_STAGE_CLOSURE'}else{'FULL_RETAINED'})
    jarSha256=$passiveHash;rollbackSha256=(Get-FileHash -LiteralPath $passiveRollback).Hash
    serverSha256=(Get-FileHash -LiteralPath (Join-Path $passivePackage 'Server\HytaleServer.jar')).Hash
    assetsSha256=(Get-FileHash -LiteralPath (Join-Path $passivePackage 'Assets.zip')).Hash
    normalThreeModSmoke=$true;zeroNativeCostTriggers=$passiveAbilities.Count;nativeCastingFixed=$false;liveDeploymentPerformed=$false;liveArtifacts=$passiveLive
    archivedCodeRollbackDrill=$(if($passiveConfig.closure){'PASS_SCHEMA8_ARCHIVE_READER_COORDINATED_RESTORE_AND_DEDUP'}else{'NOT_A_STAGE_CLOSURE'})
    protectedPathsChanged=$passiveChanged;canonicalSkillCount=87;canonicalPassiveCount=66
    rollbackStateRequirement=$(if($Cohort -eq 'a'){'Player schema7 and compiled schema35 unchanged in cohort A; prior-stage code artifact retained. Live R023 schema3 remains untouched.'}elseif($Cohort -eq 'h'){'Schema9 adds acquisition/pity/spending; original schema8 pending/receipt hashes remain readable. Restore the complete pre-migration players + earned-rewards + encounters checkpoint to run G. Schema-v8 backup alone is insufficient. Archived G rejects schema9 in place. Live R023 untouched.'}else{'Player schema8 adds earned-reward checkpoint. Full rollback requires coordinated players + earned-rewards directory backup, never one side. To run cohortA restore pre-migration .schema-v7.bak with its matching pre-award ledger checkpoint; do not downgrade schema8. Live R023 remains untouched.'})
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $passiveEvidence 'verification.json') -Encoding utf8
[pscustomobject]@{tests=$passiveCount;jarSha256=$passiveHash;connectedGate='UNVERIFIED'}
