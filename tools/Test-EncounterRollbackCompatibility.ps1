[CmdletBinding()]
param([Parameter(Mandatory)][string]$CandidateJar,[Parameter(Mandatory)][string]$EncounterDirectory)
$ErrorActionPreference='Stop'
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$target=(Resolve-Path -LiteralPath $EncounterDirectory).Path
$sha=(Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
$preWalF='F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83'
$walG='9B81FAA34D8F41D5C7B43205C52F3E17A44F585D1420C87EB1EEADAB0B7D4FEE'
if($sha -notin @($preWalF,$walG)){throw 'UNKNOWN_ROLLBACK_BINARY_REQUIRES_SEPARATE_AUDIT'}
if($sha -eq $preWalF -and ((Test-Path -LiteralPath (Join-Path $target 'journal')) -or
    (Test-Path -LiteralPath (Join-Path $target 'checkpoints')) -or (Test-Path -LiteralPath (Join-Path $target 'checkpoint-floor.json')))){
    throw 'PRE_WAL_BINARY_ON_WAL_STATE_FORBIDDEN_RESTORE_MATCHING_COORDINATED_BACKUP'
}
Write-Output 'ENCOUNTER_FORMAT_COMPATIBILITY_PASS_ONLY_NOT_A_COORDINATED_BACKUP_VALIDATION'
