[CmdletBinding()]
param([ValidateSet('a')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$closureRoot=(Resolve-Path "$PSScriptRoot\..").Path
$closureOut=Join-Path $closureRoot "evidence\stage-13\cohort-$Cohort"
$closureJar=Join-Path $closureRoot 'build\libs\HytaleRPG-0.0.25.jar'
$closureHash=(Get-FileHash -LiteralPath $closureJar).Hash
$closureArchive=Join-Path $closureOut 'artifacts\HytaleRPG-0.0.25.jar'
if((Test-Path -LiteralPath $closureArchive) -and (Get-FileHash -LiteralPath $closureArchive).Hash -ne $closureHash){throw 'Never overwrite archived cohort evidence'}
if((& git -C $closureRoot branch --show-current).Trim() -ne 'RPG'){throw 'Expected long-lived RPG branch'}
$closureTests=@()
foreach($directory in @('build\test-results\test','build\test-results\nativeControlTest')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $closureRoot $directory) -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $suite=$xml.testsuite
        if([int]$suite.failures -or [int]$suite.errors -or [int]$suite.skipped){throw "Regression failed/skipped: $($suite.name)"}
        $closureTests+=@{name=$suite.name;tests=[int]$suite.tests;seconds=$suite.time;cases=@($suite.testcase|ForEach-Object{$_.name})}
    }
}
$closureCount=($closureTests|Measure-Object -Property tests -Sum).Sum
if($closureCount -lt 815){throw 'Incomplete targeted Stage04/05/11/13 and native suite'}
$closureBaseline=Get-Content -Raw -LiteralPath (Join-Path $closureRoot 'evidence\stage-12\cohort-h\test-results.json')|ConvertFrom-Json
foreach($baseline in $closureBaseline|Where-Object {$_.name -match '\.Stage(04|05|11)'}){
    $current=@($closureTests|Where-Object {$_.name -eq $baseline.name})
    if($current.Count -ne 1 -or $current[0].tests -lt $baseline.tests){throw "Retained targeted class reduced: $($baseline.name)"}
}
if(-not ($closureTests|Where-Object {$_.name -eq 'com.inigmasgames.hytalerpg.Stage13StrikeClosureTest' -and $_.tests -eq 30})){throw 'Missing exact six-strike cohort tests'}
$closureSmoke=Get-Content -Raw -LiteralPath (Join-Path $closureOut 'server-smoke-summary.json')|ConvertFrom-Json
foreach($gate in @('exactlyThreeMods','rpgDiscovered','rpgSetup','ready','packagedRootResolved','shippedRuneResolved',
    'pluginEnabled','managerStarted','networkBooted','cleanShutdown','areaAssetsResolved','connectionAssetsResolved',
    'supportConfigured','summonAssetsResolved','batchRolesResolved','decoyRoleResolved','strikeLockResolved',
    'hitProcAssetsResolved','progressionProfilesResolved','rewardStoreConfigured','encounterRegistryResolved',
    'encounterStoreConfigured','nativeRewardHooksRegistered','supportCreditHooksRegistered','masteryHooksRegistered',
    'acquisitionConfigured','strikeClosureAssetsResolved')){
    if($closureSmoke.$gate -ne $true){throw "Normal three-mod smoke gate failed: $gate"}
}
if($closureSmoke.jarSha256 -ne $closureHash -or $closureSmoke.processExitCode -ne 0 -or $closureSmoke.failure -or $closureSmoke.nativeAbilityAssetsRejected){throw 'Smoke is not a passing run of this exact JAR'}
$protected=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
    'src/main/java/com/inigmasgames/hytalerpg/input','src/main/resources/rpg/balance','src/main/resources/rpg/catalog')
$changed=@(& git -C $closureRoot diff --name-only de60a02 -- @protected)
if($changed.Count){throw "Protected HUD/input/balance/catalog changed: $changed"}
& "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $closureJar
$archive=[IO.Compression.ZipFile]::OpenRead($closureJar)
try{
    $entries=@($archive.Entries|Where-Object{$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
    if($entries.Count -ne 66){throw 'Expected exactly 66 native zero-cost triggers in cohort A'}
    foreach($entry in $entries){
        $reader=[IO.StreamReader]::new($entry.Open());try{$ability=($reader.ReadToEnd()|ConvertFrom-Json).Ability}finally{$reader.Dispose()}
        if($ability.Cost -ne 0 -or $ability.Cooldown -ne 0 -or $ability.CostType -ne 'None' -or $ability.Cast -ne 'Root_RPG_Ability_Bridge'){throw 'Native trigger must remain gameplay-effect free'}
    }
    foreach($path in @('rpg/runtime/stage-13-strikes-cohort-a.json','Server/Item/Animations/RPG_Strike_Battleaxe.json',
        'Server/Entity/Effects/RPG/RPG_Strike_Action_Lock_Slowed.json')){
        $entry=$archive.GetEntry($path);if(-not $entry){throw "Required packaged asset missing: $path"}
        $stream=$entry.Open();try{$sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
        if($sha -ne (Get-FileHash -LiteralPath (Join-Path $closureRoot "src\main\resources\$path")).Hash){throw "Packaged asset differs from source: $path"}
    }
}finally{$archive.Dispose()}
$matrixSource=Join-Path $closureRoot 'build\stage11-matrix'
foreach($name in @('single-profile-gates.json','pair-profile-gates.json')){
    if((Get-Content -Raw -LiteralPath (Join-Path $matrixSource $name)|ConvertFrom-Json -AsHashtable).Count){throw 'Unresolved numeric profile gate'}
}
$singles=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'skill-passive-matrix.json')|ConvertFrom-Json
$pairs=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'passive-pair-matrix.json')|ConvertFrom-Json
$properties=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'six-link-property-summary.json')|ConvertFrom-Json
if($singles.Count -ne 5742 -or $pairs.Count -ne 2145 -or $properties.validGraphs -ne 1000 -or $properties.connectedEvidence){throw 'Matrix/property evidence incomplete'}
$rollback=Join-Path $closureRoot 'evidence\stage-12\cohort-h\artifacts\HytaleRPG-0.0.24.jar'
if((Get-FileHash -LiteralPath $rollback).Hash -ne 'C55DD5C1A939E5727AD01945FDC6DC0B7D7ECF1C185D94AC95B0BDAE885EB87B'){throw 'Rollback checkpoint mismatch'}
New-Item -ItemType Directory -Force -Path (Join-Path $closureOut 'artifacts'),(Join-Path $closureOut 'rollback'),(Join-Path $closureOut 'matrix')|Out-Null
Copy-Item -LiteralPath $closureJar -Destination $closureArchive -Force
Copy-Item -LiteralPath $rollback -Destination (Join-Path $closureOut 'rollback\HytaleRPG-0.0.24.jar') -Force
$mods=Join-Path $closureRoot "run\stage13-cohort-$Cohort-smoke\mods"
foreach($name in @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')){Copy-Item -LiteralPath (Join-Path $mods $name) -Destination (Join-Path $closureOut "artifacts\$name") -Force}
if(@(Get-ChildItem -LiteralPath (Join-Path $closureOut 'artifacts') -Filter '*.jar').Count -ne 3){throw 'Archive must contain exactly the three mods'}
foreach($file in Get-ChildItem -LiteralPath $matrixSource -Filter '*.json'){Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $closureOut "matrix\$($file.Name)") -Force}
$closureTests|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $closureOut 'test-results.json') -Encoding utf8
$inputHashes=@{}
foreach($base in @('src\main','src\test','src\nativeControlTest')){
    $dir=Join-Path $closureRoot $base;if(-not (Test-Path -LiteralPath $dir)){continue}
    foreach($file in Get-ChildItem -LiteralPath $dir -File -Recurse){$inputHashes[[IO.Path]::GetRelativePath($closureRoot,$file.FullName)]=(Get-FileHash -LiteralPath $file.FullName).Hash}
}
$inputHashes|ConvertTo-Json -Depth 3|Set-Content -LiteralPath (Join-Path $closureOut 'source-sha256.json') -Encoding utf8
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');stage=13;cohort=$Cohort;revision='R032';version='0.0.25';
    status='IMPLEMENTATION_IN_PROGRESS';cohortLocalGate='PASS';stageClosure='NOT_COMPLETE';connectedGate='UNVERIFIED';
    tests=$closureCount;failures=0;errors=0;skipped=0;regressionScope='TARGETED_INTERMEDIATE_NOT_STAGE_CLOSURE';
    runtimeProfiles=66;canonicalSkills=87;canonicalPassives=66;missingRuntimeProfiles=21;playerSchema=9;compiledPlanSchema=36;
    jarSha256=$closureHash;rollbackSha256=(Get-FileHash -LiteralPath $rollback).Hash;normalThreeModSmoke=$true;
    protectedPathsChanged=$changed;skillPassiveCells=5742;passivePairs=2145;sixLinkGraphs=1000;
    liveDeploymentPerformed=$false;nativeCastingFixed=$false;stage13RollbackDrill='REQUIRED_AT_CLOSURE_AND_RELEASE_CANDIDATE';
    artifacts=@(Get-ChildItem -LiteralPath (Join-Path $closureOut 'artifacts') -Filter '*.jar'|ForEach-Object{@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $closureOut 'verification.json') -Encoding utf8
[pscustomobject]@{tests=$closureCount;jarSha256=$closureHash;localCohort='PASS';stage13='IN_PROGRESS';connected='UNVERIFIED'}
