[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$auditRoot=(Resolve-Path "$PSScriptRoot\..").Path
$auditOutput=Join-Path $auditRoot 'evidence/stage-13/native-handoff-audit'
$nativeJar='C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar'
$javap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
$expectedServer='EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3'
if((Get-FileHash -LiteralPath $nativeJar).Hash -ne $expectedServer){throw 'Pinned native build changed; repeat the source/API audit'}
$inspectedTypes=@(
    'com.hypixel.hytale.component.system.EntityEventSystem',
    'com.hypixel.hytale.component.system.CancellableEcsEvent',
    'com.hypixel.hytale.component.ComponentAccessor',
    'com.hypixel.hytale.component.Store',
    'com.hypixel.hytale.component.CommandBuffer',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems'
)
$api=& $javap -classpath $nativeJar -p @inspectedTypes
if($LASTEXITCODE -ne 0){throw 'Could not inspect pinned public/protected native signatures'}
$execution=& $javap -classpath $nativeJar -c 'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems' 'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage'
if($LASTEXITCODE -ne 0){throw 'Could not inspect pinned damage bytecode'}
$routes=[ordered]@{
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSupportSystem.java'=@('loadouts.mutateSupport(actor,next.revision(),ignored->next)','support.runtime.absorbDetailed(actor,damage.getAmount()','damage.setAmount((float)hit.remainder())');
    'src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java'=@('Deficit is durable BEFORE','progress.save(actor,session.state.guard(absorbed.ledger()))','progress.save(owner,state.state.guard(absorption.ledger()))');
    'src/main/java/com/inigmasgames/hytalerpg/progress/RpgLoadoutService.java'=@('public SupportProgress mutateSupport','repository.save(candidate)');
    'src/main/java/com/inigmasgames/hytalerpg/progress/FileRpgPlayerStateRepository.java'=@('channel.force(true)')
}
$hashes=[ordered]@{}
foreach($route in $routes.GetEnumerator()){
    $sourcePath=Join-Path $auditRoot $route.Key
    $source=Get-Content -LiteralPath $sourcePath -Raw
    foreach($fragment in $route.Value){if(-not $source.Contains($fragment)){throw "Route changed; repeat audit: $($route.Key) / $fragment"}}
    $hashes[$route.Key]=(Get-FileHash -LiteralPath $sourcePath).Hash
}
if(-not (($api -join "`n") -match 'public abstract void handle\(')){throw 'Native callback signature changed'}
if(-not (($execution -join "`n") -match 'EntityStatMap.subtractStatValue')){throw 'Native ApplyDamage implementation changed'}
if(-not (($execution -join "`n") -match 'ComponentAccessor.invoke')){throw 'executeDamage invocation route changed'}
New-Item -ItemType Directory -Path $auditOutput -Force | Out-Null
$api | Set-Content -LiteralPath (Join-Path $auditOutput 'native-signatures.txt') -Encoding utf8
$execution | Set-Content -LiteralPath (Join-Path $auditOutput 'native-damage-bytecode.txt') -Encoding utf8
$result=[ordered]@{
    capturedAtUtc=[DateTime]::UtcNow.ToString('o');
    implementationHead='efe9e159b13de005a67a69bb90b84066d0e4efd6';
    serverSha256=$expectedServer;
    unchangedStageIJarSha256=(Get-FileHash -LiteralPath (Join-Path $auditRoot 'evidence/stage-13/cohort-i/artifacts/HytaleRPG-0.0.25.jar')).Hash;
    scope='STATIC_SOURCE_AND_INSTALLED_API_AUDIT_NOT_NONBLOCKING_OR_CONNECTED_PROOF';
    boundary='DURABLE_SUPPORT_DEFICIT_BEFORE_CURRENT_NATIVE_DAMAGE_APPLY';
    status='BLOCKED_PENDING_NATIVE_DAMAGE_CONTINUATION_CONTRACT';
    candidateBuilt=$false;connectedQaPerformed=$false;liveDeployment=$false;
    nativeTickGate='NOT_MEASURED';isolatedQaEligibility='NOT_ELIGIBLE';
    sources=$hashes
}
$testSuites=@()
foreach($testName in @('Stage09SupportRuntimeTest','Stage09FinalSupportPassivesTest','Stage09CooldownPersistenceTest')){
    $testPath=Join-Path $auditRoot "build/test-results/test/TEST-com.inigmasgames.hytalerpg.$testName.xml"
    if(-not (Test-Path -LiteralPath $testPath)){throw "Targeted validation must run first: $testName"}
    [xml]$testXml=Get-Content -LiteralPath $testPath -Raw
    $suite=$testXml.testsuite
    if([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0){throw "Targeted test failure: $testName"}
    $testSuites+=@{name=$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped;
        cases=@($suite.testcase | ForEach-Object {$_.name});resultSha256=(Get-FileHash -LiteralPath $testPath).Hash}
}
$result.targetedRetainedTests=$testSuites
$result.targetedTotal=($testSuites | Measure-Object -Property tests -Sum).Sum
$result.nonblockingHeldForceTests='NOT_IMPLEMENTED_NOT_RUN_SOURCE_AUDIT_STOP'
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $auditOutput 'audit.json') -Encoding utf8
Write-Output ($result | ConvertTo-Json -Depth 5)
