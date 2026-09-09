[CmdletBinding()]
param([ValidateSet('f')][string]$Cohort='f')
$ErrorActionPreference='Stop'
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
$result|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $releaseEvidence 'release-readiness.json') -Encoding utf8
Write-Output ($result|ConvertTo-Json -Depth 6)
throw "Stage13 release blocked: $($blockers -join '; ')"
