[CmdletBinding()]
param([ValidateSet('j','k')][string]$Cohort='j')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $taskRoot "evidence\stage-13\cohort-$Cohort"
$verification=Get-Content -Raw -LiteralPath (Join-Path $out 'verification.json')|ConvertFrom-Json
$performance=Get-Content -Raw -LiteralPath (Join-Path $out 'hardening\durable-load.json')|ConvertFrom-Json
$tests=Get-Content -Raw -LiteralPath (Join-Path $out 'test-results.json')|ConvertFrom-Json
& "$PSScriptRoot\Test-Stage13HandoffScope.ps1" -Tests $tests -Cohort $Cohort
if($verification.failures -or $verification.errors -or $verification.skipped -or -not $verification.normalThreeModSmoke -or $verification.tests -lt 2087){throw 'Nonblocking candidate local gate incomplete'}
$records=0;foreach($entry in $performance.grouping.recordsPerGroup.PSObject.Properties){$records+=[int]$entry.Name*[long]$entry.Value}
if($performance.samples -ne 60 -or $performance.actors -ne 4 -or $performance.victims -ne 16 -or $performance.updatesPerSample -ne 64 -or
    $records -ne 3840 -or $performance.persistenceTimings.DURABLE_ACK.count -ne 3840 -or
    -not $performance.sampleEndsAfterAll64DurableAcknowledgements -or -not $performance.checkpointWorkerConcurrentWithSamples){throw 'Original real-storage diagnostic incomplete'}
$result=[ordered]@{
    stage=13;cohort=$Cohort;revision='R032';version='0.0.25';startingCommit=if($Cohort -eq 'k'){'eb42b1c8a437c2dbf0210b2b7b38be2e63d2ad28'}else{'d28a9f2006f4bdd56878b3c81637ba1eb9d691dd'};jarSha256=$verification.jarSha256
    status='BLOCKED';releaseCandidate=$false;isolatedQaEligibility='ELIGIBLE_FOR_ISOLATED_CONNECTED_QA';connectedGate='UNVERIFIED'
    earliestFailingBoundary='NATIVE_RPG_TICK_WORK_NOT_MEASURED'
    blockers=@('NATIVE_RPG_TICK_WORK_NOT_MEASURED','COMBINED_FOUR_PLAYER_NATIVE_LOAD_AND_INTENDED_PLAYER_SCALING_NOT_VERIFIED','NATIVE_INTEGRATION_EXCEPTIONS_NOT_PROVEN_CAPABILITY_IMPOSSIBILITIES','REMAINING_MASTER_FAULT_MATRIX_AND_FINAL_STAGE13_RELEASE_CANDIDATE_NOT_CLOSED')
    requirementMapping=@{
        source='Pinned master v1.2 section 12.3; authoritative Stage_13_Nonblocking_Persistence_Codex_Task.md; subsequent owner shield escrow exception'
        NATIVE_RPG_TICK_WORK=@{status='NOT_MEASURED';requiredP95Ms=4;requiredP99Ms=8;identifier='World.getTick()';scope='ACTUAL_OWNER_WORLD_CALLBACK_WALL_TIME_IN_DECLARED_BATTLE'}
        DURABLE_ACK_LATENCY=@{status='MEASURED_DIAGNOSTIC_NOT_NATIVE_TICK_PROOF';scope=$performance.scope;p50Ms=$performance.p50Ms;p95Ms=$performance.p95Ms;p99Ms=$performance.p99Ms;historicalNominalComparison=@{p95Ms=4;p99Ms=8;within=($performance.p95Ms -le 4 -and $performance.p99Ms -le 8)};separateOwnerAckSlo='NONE_IN_CURRENT_AUTHORITATIVE_CORRECTION';operationalDeadlineMs=5000;deadlineIsNotNormalLatencySlo=$true}
        END_TO_END_PROGRESSION_LATENCY=@{status='NATIVE_NOT_MEASURED';instrumented='PROGRESSION_OWNER_PUBLISHED';includesDeliveryCadenceSeconds=1;publicationIsNotClientRendering=$true}
    }
    durability=@{walVersion=2;forceMetadata=$true;completion='AFTER_COVERING_FORCE';shield='PRE_DURABLE_FULL_CAPACITY_DEBIT_OWNER_MEMORY_CONSUMPTION_CRASH_FORFEITURE';exactOnceRewards='UNCHANGED';physicalPowerLoss='NOT_SIMULATED_PROCESS_HALT_IS_DISTINCT'}
    localGates=@{retainedBaselineIdentities=2053;tests=$verification.tests;failed=0;skipped=0;heldStorageIsolation='PASS';shieldCrashCases=7;retainedHaltCases=48;threeModSmoke='PASS';archive='PASS';actualStageIRollbackReader='PASS'}
    persistenceTimings=$performance.persistenceTimings;grouping=$performance.grouping;barriers=$performance.barriers;version2=$performance.version2
    liveDeploymentPerformed=$false;connectedQaPerformed=$false;capturedAtUtc=[DateTime]::UtcNow.ToString('o')
}
$result|ConvertTo-Json -Depth 14|Set-Content -LiteralPath (Join-Path $out 'release-readiness.json') -Encoding utf8
Write-Output ($result|ConvertTo-Json -Depth 5)
throw 'Stage13 release blocked: native tick and connected qualification remain unverified; isolated QA eligibility is not release readiness.'
