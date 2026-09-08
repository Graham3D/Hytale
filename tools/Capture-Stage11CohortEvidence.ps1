[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h','i','j','k','l','m','n')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$passiveRoot=(Resolve-Path "$PSScriptRoot\..").Path
$passiveConfig=@{a=@{tests=687;passives=@('efficiency','long_reach','rapid_invocation');planSchema=10;rollback='evidence\stage-10\cohort-g\artifacts\HytaleRPG-0.0.22.jar'}}[$Cohort]
if(-not $passiveConfig){throw 'Cohort must declare tested scope before evidence capture'}
$passiveEvidence=Join-Path $passiveRoot "evidence\stage-11\cohort-$Cohort"
$passiveJar=Join-Path $passiveRoot 'build\libs\HytaleRPG-0.0.23.jar'
$passiveArchive=Join-Path $passiveEvidence 'artifacts\HytaleRPG-0.0.23.jar'
$passiveHash=(Get-FileHash -LiteralPath $passiveJar).Hash
if((Test-Path -LiteralPath $passiveArchive) -and (Get-FileHash -LiteralPath $passiveArchive).Hash -ne $passiveHash){throw 'Never overwrite an archived cohort build'}
$passiveTests=@();$passiveCount=0
foreach($passiveFile in Get-ChildItem -Path "$passiveRoot\build\test-results\test","$passiveRoot\build\test-results\nativeControlTest","$passiveRoot\canvas-ui\build\test-results\test" -Filter 'TEST-*.xml'){
    [xml]$passiveXml=Get-Content -Raw -LiteralPath $passiveFile.FullName;$passiveSuite=$passiveXml.testsuite
    if([int]$passiveSuite.failures -or [int]$passiveSuite.errors -or [int]$passiveSuite.skipped){throw 'All retained tests must pass without skips'}
    $passiveCount += [int]$passiveSuite.tests
    $passiveTests+=@{name=$passiveSuite.name;tests=[int]$passiveSuite.tests;seconds=$passiveSuite.time;cases=@($passiveSuite.testcase | ForEach-Object {$_.name})}
}
if($passiveCount -lt $passiveConfig.tests){throw 'Incomplete retained regression suite'}
$passiveSmoke=Get-Content -Raw -LiteralPath (Join-Path $passiveEvidence 'server-smoke-summary.json') | ConvertFrom-Json
if($passiveSmoke.jarSha256 -ne $passiveHash -or $passiveSmoke.processExitCode -ne 0 -or $passiveSmoke.failure -or
    -not $passiveSmoke.networkBooted -or -not $passiveSmoke.cleanShutdown -or -not $passiveSmoke.summonAssetsResolved -or
    -not $passiveSmoke.exactlyThreeMods -or -not $passiveSmoke.batchRolesResolved -or -not $passiveSmoke.decoyRoleResolved){throw 'Exact build must pass normal isolated smoke'}
& "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $passiveJar
$passiveProtected=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
    'src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
    'src/main/resources/Server/ProjectileConfigs','src/main/resources/rpg/balance',
    'src/main/java/com/inigmasgames/hytalerpg/input')
$passiveChanged=@(& git -C $passiveRoot diff --name-only 2e5d928 -- @passiveProtected)
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
    capturedAtUtc=[DateTime]::UtcNow.ToString('o');stage=11;cohort=$Cohort;revision='R030';version='0.0.23';playerSchema=5;compiledPlanSchema=$passiveConfig.planSchema
    sourceHead=(& git -C $passiveRoot rev-parse HEAD).Trim();branch=(& git -C $passiveRoot branch --show-current).Trim()
    status='IMPLEMENTATION_IN_PROGRESS';cohortStatus='IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION';localGate='PASS';connectedGate='UNVERIFIED'
    cohortSkills=@();cohortPassives=$passiveConfig.passives;tests=$passiveCount;failures=0;errors=0;skipped=0
    jarSha256=$passiveHash;rollbackSha256=(Get-FileHash -LiteralPath $passiveRollback).Hash
    serverSha256=(Get-FileHash -LiteralPath (Join-Path $passivePackage 'Server\HytaleServer.jar')).Hash
    assetsSha256=(Get-FileHash -LiteralPath (Join-Path $passivePackage 'Assets.zip')).Hash
    normalThreeModSmoke=$true;zeroNativeCostTriggers=$passiveAbilities.Count;nativeCastingFixed=$false;liveDeploymentPerformed=$false;liveArtifacts=$passiveLive
    protectedPathsChanged=$passiveChanged;canonicalSkillCount=87;canonicalPassiveCount=66
    rollbackStateRequirement='Player schema5 unchanged. Compiled cache invalidates by schema; prior-stage artifact retained. Live R023 schema3 requires its own backup before eventual deployment.'
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $passiveEvidence 'verification.json') -Encoding utf8
[pscustomobject]@{tests=$passiveCount;jarSha256=$passiveHash;connectedGate='UNVERIFIED'}
