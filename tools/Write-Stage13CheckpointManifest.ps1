[CmdletBinding()]
param([ValidateSet('f','g')][string]$Cohort='f')
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
if($Cohort -eq 'g'){
    $manifest.schema.encounterJournal=1
    $manifest.schema.rollback='STOP_WRITERS_AND_RESTORE_MATCHING_PRE_WAL_COORDINATED_COPY_NEVER_OLD_BINARY_ON_WAL_STATE'
    $manifest.rollback.preWalCheckpoint='Stage13F 14a0f42; documentation checkpoint 344bfed'
    $manifest.rollback.preWalSha256='F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83'
    $manifest.rollback.preWalCopyTest='Stage13JournalRollbackTest'
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
