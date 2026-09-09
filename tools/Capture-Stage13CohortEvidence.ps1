[CmdletBinding()]
param([ValidateSet('a','b','c','d','e')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$closureRoot=(Resolve-Path "$PSScriptRoot\..").Path
$closureOut=Join-Path $closureRoot "evidence\stage-13\cohort-$Cohort"
$expectedTests=@{a=815;b=1251;c=1298;d=1329;e=1378}[$Cohort]
$expectedRuntime=@{a=66;b=72;c=78;d=83;e=86}[$Cohort]
$expectedPlan=@{a=36;b=37;c=38;d=39;e=40}[$Cohort]
$closureJar=Join-Path $closureRoot 'build\libs\HytaleRPG-0.0.25.jar'
$closureHash=(Get-FileHash -LiteralPath $closureJar).Hash
$closureArchive=Join-Path $closureOut 'artifacts\HytaleRPG-0.0.25.jar'
if((Test-Path -LiteralPath $closureArchive) -and (Get-FileHash -LiteralPath $closureArchive).Hash -ne $closureHash){throw 'Never overwrite archived cohort evidence'}
if((& git -C $closureRoot branch --show-current).Trim() -ne 'RPG'){throw 'Expected long-lived RPG branch'}
$closureTests=@()
$testDirectories=@('build\test-results\test','build\test-results\nativeControlTest')
if($Cohort -eq 'e'){$testDirectories+='canvas-ui\build\test-results\test'}
foreach($directory in $testDirectories){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $closureRoot $directory) -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $suite=$xml.testsuite
        if([int]$suite.failures -or [int]$suite.errors -or [int]$suite.skipped){throw "Regression failed/skipped: $($suite.name)"}
        $closureTests+=@{name=$suite.name;tests=[int]$suite.tests;seconds=$suite.time;cases=@($suite.testcase|ForEach-Object{$_.name})}
    }
}
$closureCount=($closureTests|Measure-Object -Property tests -Sum).Sum
if($closureCount -lt $expectedTests){throw 'Incomplete retained affected-family/Stage13 and native suite'}
$closureBaseline=Get-Content -Raw -LiteralPath (Join-Path $closureRoot 'evidence\stage-12\cohort-h\test-results.json')|ConvertFrom-Json
if($Cohort -eq 'e'){
    foreach($baseline in $closureBaseline){
        $current=@($closureTests|Where-Object {$_.name -eq $baseline.name})
        if($current.Count -ne 1 -or $current[0].tests -lt $baseline.tests){throw "Complete retained baseline class reduced: $($baseline.name)"}
    }
}
foreach($baseline in $closureBaseline|Where-Object {$_.name -match '\.Stage(04|05|11)'}){
    $current=@($closureTests|Where-Object {$_.name -eq $baseline.name})
    if($current.Count -ne 1 -or $current[0].tests -lt $baseline.tests){throw "Retained targeted class reduced: $($baseline.name)"}
}
if(-not ($closureTests|Where-Object {$_.name -eq 'com.inigmasgames.hytalerpg.Stage13StrikeClosureTest' -and $_.tests -eq 30})){throw 'Missing exact six-strike cohort tests'}
if($Cohort -ne 'a'){
    foreach($baseline in $closureBaseline|Where-Object {$_.name -match '\.Stage(02|04|05|06|07|08|09|11)'}){
        $current=@($closureTests|Where-Object {$_.name -eq $baseline.name})
        if($current.Count -ne 1 -or $current[0].tests -lt $baseline.tests){throw "Retained affected-family test class reduced: $($baseline.name)"}
    }
    foreach($entry in @(@{name='Stage13ProjectileClosureTest';count=43},@{name='Stage13NativeEquipmentSourceTest';count=7})){
        if(-not ($closureTests|Where-Object {$_.name -eq "com.inigmasgames.hytalerpg.$($entry.name)" -and $_.tests -eq $entry.count})){throw "Missing exact cohort B tests: $($entry.name)"}
    }
}
if($Cohort -in @('c','d','e')){
    foreach($entry in @(@{name='Stage13AuthoredProjectileTest';count=40},@{name='Stage13SharedRootBudgetTest';count=7})){
        if(-not ($closureTests|Where-Object {$_.name -eq "com.inigmasgames.hytalerpg.$($entry.name)" -and $_.tests -eq $entry.count})){throw "Missing exact cohort C tests: $($entry.name)"}
    }
}
if($Cohort -in @('d','e')){
    if(-not ($closureTests|Where-Object {$_.name -eq 'com.inigmasgames.hytalerpg.Stage13MovementClosureTest' -and $_.tests -eq 31})){throw 'Missing exact movement closure tests'}
    $movement=Get-Content -Raw -LiteralPath (Join-Path $closureOut 'native-movement-guard-audit.json')|ConvertFrom-Json
    if($movement.operation -ne 'WieldingInteraction' -or $movement.baseDrain -ne 7 -or
        $movement.activationGate -ne 'NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED' -or $movement.connectedProof){throw 'Guard control audit missing or altered'}
}
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
if($Cohort -ne 'a' -and -not $closureSmoke.projectileClosureAssetsResolved){throw 'Native projectile/equipment closure assets not resolved'}
$protected=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI','canvas-ui/src',
    'src/main/java/com/inigmasgames/hytalerpg/input','src/main/resources/rpg/balance','src/main/resources/rpg/catalog')
$changed=@(& git -C $closureRoot diff --name-only de60a02 -- @protected)
if($Cohort -eq 'e'){
    $allowed=@('src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.java',
        'src/main/java/com/inigmasgames/hytalerpg/ui/hud/FinisherHud.java','src/main/resources/Common/UI/Custom/RpgFinisherPips.ui')
    # Master SK-038 explicitly requires three combo pips. No exception for resource/ability/XP ownership or artwork.
    $changed=@($changed|Where-Object{$_ -notin $allowed})
    foreach($entry in @(@{name='Stage13NativeBasicHitTest';count=22},@{name='Stage13NativeBasicPathTest';count=3},
        @{name='Stage13VictimCoefficientTest';count=21},@{name='Stage13FinisherPresentationTest';count=3},@{name='ui.hud.R020HudCorrectionTest';count=6})){
        if(-not ($closureTests|Where-Object {$_.name -eq "com.inigmasgames.hytalerpg.$($entry.name)" -and $_.tests -eq $entry.count})){throw "Missing E/native ownership assertions: $($entry.name)"}
    }
    if(-not (Test-Path -LiteralPath (Join-Path $closureOut 'native-basic-path-audit.json'))){throw 'Missing installed native basic-attack source audit'}
}
if($changed.Count){throw "Protected HUD/input/balance/catalog changed: $changed"}
& "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $closureJar
$archive=[IO.Compression.ZipFile]::OpenRead($closureJar)
try{
    $entries=@($archive.Entries|Where-Object{$_.FullName -like 'Server/Item/Items/RPG/Abilities/*.json'})
    if($entries.Count -ne $expectedRuntime){throw "Expected exactly $expectedRuntime native zero-cost triggers"}
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
    if($Cohort -ne 'a'){
        $required=@('rpg/runtime/stage-13-projectiles-cohort-b.json','rpg/runtime/native-item-power-r032.json','rpg/runtime/stage-05-projectiles.json','Server/Entity/Damage/RPG_Arcane.json')
        foreach($skill in @('Spear_Toss','Crossbow_Bolt','Web_Shot','Void_Bolt','Bone_Shard','Cold_Blast')){
            $required+="Server/ProjectileConfigs/RPG/Projectile_Config_RPG_$skill.json"
            $required+="Server/Models/Projectiles/RPG_$skill.json"
        }
        if($Cohort -in @('c','d','e')){
            $required+='rpg/runtime/stage-13-projectiles-cohort-c.json'
            foreach($skill in @('Blunderbuss_Shot','Snipe','Explosive_Flask','Bomb_Toss','Arcane_Missiles','Fireball')){
                $required+="Server/ProjectileConfigs/RPG/Projectile_Config_RPG_$skill.json"
                $required+="Server/Models/Projectiles/RPG_$skill.json"
                $required+="Server/Item/Items/RPG/Abilities/RPG_Ability_$skill.json"
            }
        }
        if($Cohort -in @('d','e')){
            $required+='rpg/runtime/stage-13-movement-cohort-d.json'
            foreach($skill in @('Dive_Strike','Jump_Strike','Charge','Void_Dash','Guard')){$required+="Server/Item/Items/RPG/Abilities/RPG_Ability_$skill.json"}
        }
        if($Cohort -eq 'e'){
            $required+='rpg/runtime/stage-13-combat-cohort-e.json'
            $required+='Common/UI/Custom/RpgFinisherPips.ui'
            foreach($skill in @('Finishing_Strike','Execution_Strike','Backstab')){$required+="Server/Item/Items/RPG/Abilities/RPG_Ability_$skill.json"}
            $previous=[IO.Compression.ZipFile]::OpenRead((Join-Path $closureRoot 'evidence\stage-12\cohort-h\artifacts\HytaleRPG-0.0.24.jar'))
            try{
                foreach($old in $previous.Entries|Where-Object{$_.FullName -like 'Common/UI/*' -and $_.Length -gt 0}){
                    $new=$archive.GetEntry($old.FullName);if(-not $new){throw "Existing UI asset removed: $($old.FullName)"}
                    $a=$old.Open();$b=$new.Open()
                    try{if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($a)) -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b))){throw "Protected packaged UI bytes changed: $($old.FullName)"}}
                    finally{$a.Dispose();$b.Dispose()}
                }
            }finally{$previous.Dispose()}
        }
        foreach($path in $required){
            $entry=$archive.GetEntry($path);if(-not $entry){throw "Cohort B asset missing: $path"}
            $stream=$entry.Open();try{$sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
            if($sha -ne (Get-FileHash -LiteralPath (Join-Path $closureRoot "src\main\resources\$path")).Hash){throw "Cohort B packaged asset differs: $path"}
        }
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
if($Cohort -in @('c','d','e')){
    $gates=@('NATIVE_BOW_MAX_RANGE_UNVERIFIED','BONE_CAGE_NATIVE_ENEMY_ONLY_COLLISION_UNVERIFIED')
    if($Cohort -in @('d','e')){$gates+='NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED'}
    foreach($gate in $gates){
        if(-not @($singles|Where-Object {$_.gate -eq 'COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE' -and $_.detail -eq $gate}).Count){throw "Missing explicit runtime capability evidence: $gate"}
    }
    $native=Get-Content -Raw -LiteralPath (Join-Path $closureOut 'native-projectile-equipment-audit.json')|ConvertFrom-Json
    if($native.resolvedConfigs -ne 19 -or $native.snipeActivationGate -ne 'NATIVE_BOW_MAX_RANGE_UNVERIFIED' -or $native.connectedProof){throw 'Cohort C asset/capability evidence incomplete'}
}
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
    tests=$closureCount;failures=0;errors=0;skipped=0;regressionScope=if($Cohort -eq 'e'){'COMPLETE_RETAINED_INTERMEDIATE_NOT_STAGE_CLOSURE'}else{'TARGETED_INTERMEDIATE_NOT_STAGE_CLOSURE'};
    runtimeProfiles=$expectedRuntime;canonicalSkills=87;canonicalPassives=66;missingRuntimeProfiles=(87-$expectedRuntime);playerSchema=9;compiledPlanSchema=$expectedPlan;
    jarSha256=$closureHash;rollbackSha256=(Get-FileHash -LiteralPath $rollback).Hash;normalThreeModSmoke=$true;
    protectedPathsChanged=$changed;skillPassiveCells=5742;passivePairs=2145;sixLinkGraphs=1000;
    liveDeploymentPerformed=$false;nativeCastingFixed=$false;stage13RollbackDrill='REQUIRED_AT_CLOSURE_AND_RELEASE_CANDIDATE';
    explicitRuntimeGates=if($Cohort -in @('c','d','e')){$gates}else{@('BONE_CAGE_NATIVE_ENEMY_ONLY_COLLISION_UNVERIFIED')};
    artifacts=@(Get-ChildItem -LiteralPath (Join-Path $closureOut 'artifacts') -Filter '*.jar'|ForEach-Object{@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $closureOut 'verification.json') -Encoding utf8
[pscustomobject]@{tests=$closureCount;jarSha256=$closureHash;localCohort='PASS';stage13='IN_PROGRESS';connected='UNVERIFIED'}
