[CmdletBinding()]
param([ValidateSet('f','g','h','i')][string]$Cohort='f')
$ErrorActionPreference='Stop'
$manifestRoot=(Resolve-Path "$PSScriptRoot\..").Path
$manifestEvidence=Join-Path $manifestRoot "evidence\stage-13\cohort-$Cohort"
$verification=Get-Content -Raw -LiteralPath (Join-Path $manifestEvidence 'verification.json')|ConvertFrom-Json
$files=[ordered]@{}
foreach($file in Get-ChildItem -LiteralPath $manifestEvidence -File -Recurse|Sort-Object FullName){
    if($file.Name -eq 'checkpoint-manifest.json' -or $file.FullName -match '[\\/]before[\\/]'){continue}
    $files[[IO.Path]::GetRelativePath($manifestEvidence,$file.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $file.FullName).Hash
}
$manifest=[ordered]@{manifestSchema=1;purpose='CHECKSUM_VERIFIED_BLOCKED_DEVELOPMENT_CHECKPOINT_NOT_RELEASE';
    code=@{revision='R032';version='0.0.25';sha256=$verification.jarSha256};
    content=@{canonicalSkills=87;canonicalPassives=66;runtimeProfiles=87;compiledPlanSchema=41};
    adapter=@{hytaleVersion='0.7.0-pre.1';build='e8b4d191fc98a977bf5546a951a7b25473d323e3';
        serverSha256='EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3';
        assetsSha256='46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39';connected='UNVERIFIED'};
    schema=@{player=9;earnedRewardIntent=1;encounterEnvelope=1;rollback='COORDINATED_COPY_ONLY'};
    art=@{version='R020_XP_UNCHANGED_PLUS_STAGE13_E_FINISHER_TEMPLATE';xpFrame='702x28';xpBackground='696x28';xpFill='1x22';connectedVisualQa='UNVERIFIED'};
    rollback=@{checkpoint='Stage12H de60a02';sha256=$verification.rollbackSha256;liveSaveUntouched=$true;copiedCheckpointTest='Stage13ArchivedRollbackTest'};
    signing='CHECKSUM_VERIFIED_NOT_CRYPTOGRAPHICALLY_SIGNED';files=$files}
$path=Join-Path $manifestEvidence 'checkpoint-manifest.json'
if($Cohort -in @('g','h','i')){
    $manifest.schema.encounterJournal=1
    $manifest.schema.rollback='STOP_WRITERS_AND_RESTORE_MATCHING_PRE_WAL_COORDINATED_COPY_NEVER_OLD_BINARY_ON_WAL_STATE'
    $manifest.rollback.preWalCheckpoint='Stage13F 14a0f42; documentation checkpoint 344bfed'
    $manifest.rollback.preWalSha256='F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83'
    $manifest.rollback.preWalCopyTest='Stage13JournalRollbackTest'
}
if($Cohort -eq 'h'){
    $manifest.schema.rollback='WAL_V1_G_READER_COMPATIBLE_STOP_WRITERS_AND_RESTORE_COORDINATED_COPY_FOR_ROLLBACK'
    $manifest.rollback.immediateCheckpoint='Stage13G f25bf99764285e3fb340bad4bd693a9fe5cd03d8'
    $manifest.rollback.immediateSha256='9B81FAA34D8F41D5C7B43205C52F3E17A44F585D1420C87EB1EEADAB0B7D4FEE'
    $manifest.rollback.compatibilityTests='Stage13GroupRollbackTest plus retained Stage13JournalRollbackTest and Stage13ArchivedRollbackTest'
    $manifest.rollback.preflight='tools/Test-EncounterRollbackCompatibility.ps1; F is forbidden on any WAL directory'
    $manifest.durability=@{submission='PROVISIONAL_NOT_DURABLE';acknowledgement='AFTER_COVERING_FORCE_TRUE';grouping='DRAIN_ALREADY_QUEUED';checkpoint='IMMUTABLE_SEQUENCE_CAPTURE_ASYNC_PUBLICATION';connected='UNVERIFIED'}
    $manifest.report=@{path='docs/stage-13/encounter-group-commit-report.md';sha256=(Get-FileHash -LiteralPath (Join-Path $manifestRoot 'docs/stage-13/encounter-group-commit-report.md')).Hash}
}
if($Cohort -eq 'i'){
    $manifest.schema.encounterJournal=2;$manifest.schema.encounterCheckpoint=2
    $manifest.schema.rollback='NO_IN_PLACE_DOWNGRADE_RESTORE_MATCHING_PLAYER_REWARD_ENCOUNTER_COORDINATED_COPY'
    $manifest.rollback.immediateCheckpoint='Stage13H eecd64cc4b7c5345288ffef51083fadc3c8348e4'
    $manifest.rollback.immediateSha256='8849F88CB9225E847C05580CE1EBE3E2FB6BE0D487C73AA4408526C1ED6381D6'
    $manifest.rollback.compatibilityTests='Stage13V2RecoveryEdgesTest actual H rejection and preserved coordinated legacy copy; retained G/F/Stage12 rollback tests'
    $manifest.durability=@{submission='PROVISIONAL_NOT_DURABLE';acknowledgement='AFTER_COVERING_FORCE_TRUE';grouping='SEALED_OPERATIONS_DRAIN_ALREADY_QUEUED_NO_SYNTHETIC_64_DAMAGE_EPOCH';checkpoint='BOUNDED_BUNDLES_AND_SINGLE_V2_MANIFEST';connected='UNVERIFIED'}
    $manifest.report=@{path='docs/stage-13/encounter-durability-final-boundary-report.md';sha256=(Get-FileHash -LiteralPath (Join-Path $manifestRoot 'docs/stage-13/encounter-durability-final-boundary-report.md')).Hash}
}
$manifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $path -Encoding utf8
$read=Get-Content -Raw -LiteralPath $path|ConvertFrom-Json -AsHashtable
foreach($entry in $read.files.GetEnumerator()){
    $target=[IO.Path]::GetFullPath((Join-Path $manifestEvidence $entry.Key))
    if(-not $target.StartsWith($manifestEvidence+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Manifest path escaped evidence directory'}
    if((Get-FileHash -LiteralPath $target).Hash -ne $entry.Value){throw "Archive checksum mismatch: $($entry.Key)"}
}
if(@(Get-ChildItem -LiteralPath (Join-Path $manifestEvidence 'artifacts') -Filter '*.jar').Count -ne 3){throw 'Exactly three mods required'}
Write-Output "Verified $($files.Count) evidence checksums; blocked checkpoint, not a release candidate."
