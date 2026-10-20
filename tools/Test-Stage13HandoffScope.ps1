[CmdletBinding()]
param([Parameter(Mandatory)][object[]]$Tests)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot\..").Path
$baseline=Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'evidence\stage-13\cohort-i\test-results.json')|ConvertFrom-Json
if(($baseline|Measure-Object -Property tests -Sum).Sum -ne 2053){throw 'Stage I baseline identity mismatch'}
foreach($suite in $baseline){
    $current=@($Tests|Where-Object {$_.name -eq $suite.name})
    if($current.Count -ne 1 -or $current[0].tests -lt $suite.tests){throw "Missing retained suite: $($suite.name)"}
    foreach($case in $suite.cases){if($case -notin $current[0].cases){throw "Missing retained assertion identity: $($suite.name) $case"}}
}
$required=@{Stage13ShieldEscrowTest=6;Stage13NativeSupportHandoffTest=4;Stage13PlayerReadIsolationTest=4;Stage13EncounterNativeHandoffTest=2;Stage13NativeCooldownHandoffTest=4;Stage13ShieldHandoffRecoveryTest=7;Stage13LifecycleEdgeHandoffTest=3;Stage13EscrowArchiveRollbackTest=1;'diagnostics.Stage13NativeTickMetricsTest'=3}
foreach($item in $required.GetEnumerator()){
    $suite=@($Tests|Where-Object {$_.name -eq "com.inigmasgames.hytalerpg.$($item.Key)"})
    if($suite.Count -ne 1 -or $suite[0].tests -lt $item.Value){throw "New handoff gate missing: $($item.Key)"}
}
$allowed=@(
    'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown/RpgCooldownService.java',
    'src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HomeRestorationTickSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageLifecycleSystems.java',
    'src/main/java/com/inigmasgames/hytalerpg/commands/RpgManaguardCommand.java',
    'src/main/java/com/inigmasgames/hytalerpg/diagnostics/NativeRpgTickMetrics.java',
    'src/main/java/com/inigmasgames/hytalerpg/diagnostics/ProgressionHandoffMetrics.java',
    'src/main/java/com/inigmasgames/hytalerpg/diagnostics/RpgTraceEventType.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionPort.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/AreaNpcControlSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/AreaStatusProjectionSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleConversionSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleCorpseSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleEncounterRewards.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytalePlayerPersistenceReady.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleRetaliationSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleStatusDeathSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSupportSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeBasicAttackObserver.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/StatusRemovalSystem.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/SupportDamageSystems.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/SupportNativeEffects.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/support/ShieldEscrow.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportProgressStore.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java',
    'src/main/java/com/inigmasgames/hytalerpg/phase00/Phase00Plugin.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/DurableEncounterEffects.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/EncounterContributions.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/EncounterGroupCommit.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/FileEncounterStore.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/MasteryRootBudget.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/PersistentEncounterRuntime.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/RpgLoadoutService.java',
    'src/main/java/com/inigmasgames/hytalerpg/progress/RpgLoadoutView.java',
    'src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.java',
    'src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudTickSystem.java'
)
$scope=@(& git -c core.safecrlf=false -C $taskRoot diff --name-only d28a9f2006f4bdd56878b3c81637ba1eb9d691dd -- src/main canvas-ui/src)
$scope+=@(& git -C $taskRoot ls-files --others --exclude-standard -- src/main canvas-ui/src)
if(@($scope|Where-Object {$_ -notin $allowed}).Count){throw 'Out-of-scope handoff source change'}
$testDiff=@(& git -c core.safecrlf=false -C $taskRoot diff --unified=0 d28a9f2006f4bdd56878b3c81637ba1eb9d691dd -- src/test)
if(@($testDiff|Where-Object {$_ -match '^-.*(assert|@Test|@ParameterizedTest)' -and $_ -notmatch '^---'}).Count){throw 'Retained assertion removed; review required'}
$crashes=Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'build\stage13-hardening\handoff-crash-matrix.json')|ConvertFrom-Json
if($crashes.Count -ne 7 -or @($crashes|Where-Object {$_.exitCode -ne 73 -or $_.restarts -ne 3 -or -not $_.processHaltNotPowerLoss}).Count){throw 'Handoff crash matrix incomplete'}
# Original workload source, including all 64 waits, is immutable for this correction.
if(@(& git -c core.safecrlf=false -C $taskRoot diff --name-only d28a9f2006f4bdd56878b3c81637ba1eb9d691dd -- src/test/java/com/inigmasgames/hytalerpg/Stage13DurabilityLoadTest.java).Count){throw 'Original 64-update diagnostic changed'}
Write-Output 'All 2053 Stage I test identities retained; new nonblocking/escrow/rollback gates present.'
