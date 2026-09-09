[CmdletBinding()]
param([ValidateSet('f','g','h','i','j')][string]$Cohort='f')
$ErrorActionPreference='Stop'
if($Cohort -eq 'j'){ & "$PSScriptRoot\Test-Stage13HandoffReadiness.ps1";return }
$releaseRoot=(Resolve-Path "$PSScriptRoot\..").Path
$releaseEvidence=Join-Path $releaseRoot "evidence\stage-13\cohort-$Cohort"
$verification=Get-Content -Raw -LiteralPath (Join-Path $releaseEvidence 'verification.json')|ConvertFrom-Json
$performance=Get-Content -Raw -LiteralPath (Join-Path $releaseEvidence 'hardening\durable-load.json')|ConvertFrom-Json
$coverage=Get-Content -Raw -LiteralPath (Join-Path $releaseEvidence 'hardening\coverage.json')|ConvertFrom-Json
$blockers=[Collections.Generic.List[string]]::new()
if($verification.failures -or $verification.errors -or $verification.skipped -or $verification.tests -lt 1886){$blockers.Add('RETAINED_REGRESSION_INCOMPLETE')}
if(-not $verification.normalThreeModSmoke){$blockers.Add('THREE_MOD_SMOKE_UNVERIFIED')}
if(-not [double]::IsFinite($performance.p95Ms) -or -not [double]::IsFinite($performance.p99Ms) -or $performance.p95Ms -gt 4 -or $performance.p99Ms -gt 8){
    $blockers.Add('SYNCHRONOUS_DURABLE_ENCOUNTER_CONTRIBUTION_EXCEEDS_RPG_TICK_BUDGET')
}
if(-not $coverage.classificationCompleteForRelease){$blockers.Add('NATIVE_INTEGRATION_EXCEPTIONS_NOT_PROVEN_CAPABILITY_IMPOSSIBILITIES')}
# This intermediate checkpoint has no completed full-system load/fault closure. Explicitly fail;
# a future implementation must replace these with recorded passing gates, never just remove them.
$blockers.Add('COMBINED_FOUR_PLAYER_NATIVE_LOAD_AND_200_PLAYER_SCALING_NOT_VERIFIED')
$blockers.Add('REMAINING_MASTER_FAULT_INJECTION_MATRIX_NOT_CLOSED')
$blockers.Add('FINAL_STAGE13_REGRESSION_SMOKE_ARCHIVE_ROLLBACK_RERUN_NOT_PERFORMED')
$result=[ordered]@{stage=13;revision='R032';version='0.0.25';status='BLOCKED';releaseCandidate=$false;
    jarSha256=$verification.jarSha256;earliestFailingBoundary=$blockers[0];blockers=@($blockers);
    connectedGate='UNVERIFIED';functionalRegressionPass=($verification.failures -eq 0 -and $verification.errors -eq 0);
    performanceIsSeparateGate=$true;durableAcknowledgementWeakened=$false;testsOrExpectedBehaviorWeakened=$false;
    measuredScope=$performance.scope;p95Ms=$performance.p95Ms;p99Ms=$performance.p99Ms;requiredP95Ms=4;requiredP99Ms=8;
    cpu=@(Get-CimInstance Win32_Processor|ForEach-Object{@{model=$_.Name;physicalCores=$_.NumberOfCores;logicalProcessors=$_.NumberOfLogicalProcessors}});
    physicalMemoryGiB=[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB,2);
    os=(Get-CimInstance Win32_OperatingSystem).Caption;storagePath='JUnit @TempDir on the Java system temporary C: directory; not the live RPG save';
    capturedAtUtc=[DateTime]::UtcNow.ToString('o')}
if($Cohort -eq 'g'){
    $result.persistenceImplementation='APPEND_ONLY_FORCE_BEFORE_ACK_WAL_V1'
    $result.persistenceTimings=$performance.persistenceTimings
    $result.journalForcePer64UpdateSampleMs=$performance.journalForcePer64UpdateSampleMs
    $result.storageBoundary='SERIAL_FILECHANNEL_FORCE_TRUE_REFERENCE_STORAGE_STACK'
    $result.storageDevice=@(Get-Partition -DriveLetter C|Get-Disk|ForEach-Object{@{model=$_.FriendlyName;bus=$_.BusType.ToString()}})
    if($performance.persistenceTimings.JOURNAL_FORCE.count -ne 3840 -or $performance.persistenceTimings.JOURNAL_APPEND.count -ne 3840){throw 'WAL force-per-contribution evidence missing'}
}
if($Cohort -in @('h','i')){
    $result.persistenceImplementation='BOUNDED_GROUP_COMMIT_FORCE_BEFORE_DURABLE_COMPLETION_WAL_V1_ASYNC_CHECKPOINT'
    $result.persistenceTimings=$performance.persistenceTimings
    $result.grouping=$performance.grouping
    $result.forcesPerSample=$performance.forcesPerSample
    $result.journalForcePer64UpdateSampleMs=$performance.journalForcePer64UpdateSampleMs
    $result.storageBoundary='GROUPED_FORCE_TRUE_PLUS_GROUP_PREPARATION_ROTATION_AND_CHECKPOINT_STORAGE_CONTENTION'
    $result.storageDevice=@(Get-Partition -DriveLetter C|Get-Disk|ForEach-Object{@{model=$_.FriendlyName;bus=$_.BusType.ToString()}})
    $records=0;foreach($entry in $performance.grouping.recordsPerGroup.PSObject.Properties){$records+=[int]$entry.Name*[long]$entry.Value}
    $forces=($performance.forcesPerSample|Measure-Object -Sum).Sum
    if($verification.tests -lt 1963 -or $performance.samples -ne 60 -or $performance.actors -ne 4 -or $performance.victims -ne 16 -or $performance.updatesPerSample -ne 64 -or
        $records -ne 3840 -or $performance.persistenceTimings.DURABLE_ACK.count -ne 3840 -or $forces -ne $performance.persistenceTimings.JOURNAL_FORCE.count -or $forces -ge 3840 -or
        $performance.persistenceTimings.JOURNAL_APPEND.count -ne $forces -or -not $performance.sampleEndsAfterAll64DurableAcknowledgements -or -not $performance.checkpointWorkerConcurrentWithSamples){throw 'Group-commit production workload or force/completion evidence missing'}
}
if($Cohort -eq 'i'){
    $result.persistenceImplementation='V2_PREPARED_CONTENT_LINKED_WAL_BUNDLED_CHECKPOINT_PRIORITY_FORCE_TRUE'
    $result.barriers=$performance.barriers;$result.version2=$performance.version2
    $result.storageBoundary='RPG_PERSISTENCE_ARCHITECTURE_BLOCKED'
    $result.storageQualification=@(Get-ChildItem -LiteralPath (Join-Path $releaseEvidence 'storage-qualification') -Filter '*force-true.json'|ForEach-Object {
        $probe=Get-Content -Raw -LiteralPath $_.FullName|ConvertFrom-Json
        if($probe.force.samples -ne 10000 -or $probe.discardedSamples -ne 0){throw 'Storage qualification samples weakened'}
        @{mode=$probe.mode;force=$probe.force;fileBytes=$probe.fileBytes;forceMetadata=$probe.forceMetadata;preallocated=$probe.preallocated}
    })
    if($result.storageQualification.Count -ne 4){throw 'Growing/preallocated sparse/group qualification incomplete'}
    if($performance.p95Ms -le 4 -and $performance.p99Ms -le 8){$result.storageBoundary='PERFORMANCE_GATE_PASS'}
    # Standalone slow maximum alone does NOT fail a p95/p99 gate. No platform-only attribution
    # while the remaining producer/closure boundary is concrete application overhead.
}
$result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $releaseEvidence 'release-readiness.json') -Encoding utf8
Write-Output ($result|ConvertTo-Json -Depth 6)
throw "Stage13 release blocked: $($blockers -join '; ')"
