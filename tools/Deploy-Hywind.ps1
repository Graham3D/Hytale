[CmdletBinding()]
param(
    [string]$CandidateJar = '',
    [string]$TargetSave = '',
    [string]$RollbackRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($CandidateJar)) { $CandidateJar = Join-Path $repo 'build\libs\Hywind.jar' }
if ([string]::IsNullOrWhiteSpace($TargetSave)) { $TargetSave = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG' }
if ([string]::IsNullOrWhiteSpace($RollbackRoot)) { $RollbackRoot = Join-Path (Split-Path $repo -Parent) 'Hytale-rollback' }
$CandidateJar = (Resolve-Path -LiteralPath $CandidateJar).Path
$TargetSave = (Resolve-Path -LiteralPath $TargetSave).Path.TrimEnd('\')
$RollbackRoot = [IO.Path]::GetFullPath($RollbackRoot).TrimEnd('\')
$mods = Join-Path $TargetSave 'mods'
if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw "Target is not a Hytale save with a mods directory: $TargetSave" }
if ($RollbackRoot.StartsWith($TargetSave + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Rollback root must be outside the target save.' }
if ([IO.Path]::GetFileName($CandidateJar) -ne 'Hywind.jar') { throw 'Candidate artifact must be named Hywind.jar.' }

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose(); $stream.Dispose() }
}
function Get-TreeMeasure([string]$Path) {
    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force)
    [ordered]@{ files = $files.Count; bytes = [long](($files | Measure-Object Length -Sum).Sum) }
}
function Assert-Stopped {
    $running = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
        ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
    })
    if ($running.Count) { throw 'Hytale or HytaleServer is running. Close it before deploying Hywind.' }
}
function Write-Journal([string]$Path,[string]$Step,[object]$Details) {
    [ordered]@{timestampUtc=[DateTime]::UtcNow.ToString('o');step=$Step;details=$Details} |
        ConvertTo-Json -Compress -Depth 8 | Add-Content -LiteralPath $Path -Encoding utf8
}

Assert-Stopped
$zip = [IO.Compression.ZipFile]::OpenRead($CandidateJar)
try {
    $entry = $zip.GetEntry('manifest.json')
    if ($null -eq $entry) { throw 'Candidate lacks manifest.json.' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $zip.Dispose() }
if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'Hywind' -or
    $manifest.Version -ne '0.1.0-merge.14' -or $manifest.Metadata.RpgRevision -ne 'R059' -or
    $manifest.Metadata.TavernSourceRevision -ne 'R056' -or $manifest.Main -ne 'com.inigmasgames.hywind.HywindPlugin') {
    throw 'Candidate is not the approved Hywind 0.1.0-merge.14 / R059 package with Taverns R056.'
}

$candidateHash = Get-Sha256 $CandidateJar
$superseded = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object {
    $_.Name -eq 'HyARPG.jar' -or $_.Name -like 'HytaleRPG-*.jar' -or
    $_.Name -like 'CanvasUI-*.jar' -or $_.Name -like 'ImmersiveNPCs-*.jar' -or
    $_.Name -like 'Taverns-*.jar' -or $_.Name -eq 'Taverns.jar' -or
    $_.Name -eq 'Hywind.jar'
} | Sort-Object Name)
$unrelated = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object {
    $superseded.FullName -notcontains $_.FullName
} | Sort-Object Name)
$plan = [ordered]@{
    mode = if ($DryRun) { 'DRY_RUN' } else { 'DEPLOY' }
    targetSave = $TargetSave
    candidate = $CandidateJar
    candidateSha256 = $candidateHash
    supersededArtifacts = @($superseded | ForEach-Object { [ordered]@{name=$_.Name;sha256=(Get-Sha256 $_.FullName)} })
    preservedArtifacts = @($unrelated | ForEach-Object { [ordered]@{name=$_.Name;sha256=(Get-Sha256 $_.FullName)} })
    preservedDataRoots = @('ImmersiveNPCs','InigmasGames_CanvasUI','InigmasGames_HytaleRPGPhase00Audit','InigmasGames_Taverns')
}
if ($DryRun) { $plan | ConvertTo-Json -Depth 8; exit 0 }

$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupDirectory = Join-Path $RollbackRoot "hywind-deploy-$timestamp"
if (Test-Path -LiteralPath $backupDirectory) { throw "Backup path already exists: $backupDirectory" }
$backupSave = Join-Path $backupDirectory 'save'
$retired = Join-Path $backupDirectory 'retired-artifacts'
New-Item -ItemType Directory -Path $backupSave,$retired -Force | Out-Null
$journal = Join-Path $backupDirectory 'operation-journal.jsonl'
Write-Journal $journal 'PLAN' $plan

$sourceMeasure = Get-TreeMeasure $TargetSave
& robocopy $TargetSave $backupSave /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "Full-save backup failed with robocopy exit code $LASTEXITCODE" }
$backupMeasure = Get-TreeMeasure $backupSave
if ($sourceMeasure.files -ne $backupMeasure.files -or $sourceMeasure.bytes -ne $backupMeasure.bytes) {
    throw "Full-save backup verification failed: source=$($sourceMeasure|ConvertTo-Json -Compress) backup=$($backupMeasure|ConvertTo-Json -Compress)"
}
Write-Journal $journal 'FULL_SAVE_BACKUP_VERIFIED' @{path=$backupSave;measure=$backupMeasure}

$staging = Join-Path $mods ('.Hywind-' + [Guid]::NewGuid().ToString('N') + '.pending')
$published = Join-Path $mods 'Hywind.jar'
$moved = [Collections.Generic.List[object]]::new()
try {
    Copy-Item -LiteralPath $CandidateJar -Destination $staging
    if ((Get-Sha256 $staging) -ne $candidateHash) { throw 'Staged Hywind hash mismatch.' }
    Write-Journal $journal 'CANDIDATE_STAGED' @{path=$staging;sha256=$candidateHash}

    foreach ($artifact in $superseded) {
        $destination = Join-Path $retired $artifact.Name
        if (Test-Path -LiteralPath $destination) { throw "Retirement collision: $destination" }
        Move-Item -LiteralPath $artifact.FullName -Destination $destination
        $moved.Add([pscustomobject]@{source=$artifact.FullName;destination=$destination})
        Write-Journal $journal 'SUPERSEDED_RETIRED' @{name=$artifact.Name;destination=$destination}
    }
    if (Test-Path -LiteralPath $published) { throw 'Hywind.jar appeared during deployment.' }
    Move-Item -LiteralPath $staging -Destination $published
    if ((Get-Sha256 $published) -ne $candidateHash) { throw 'Published Hywind hash mismatch.' }
    Write-Journal $journal 'HYWIND_PUBLISHED' @{path=$published;sha256=$candidateHash}

    $remainingLegacy = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object {
        $_.Name -eq 'HyARPG.jar' -or $_.Name -like 'HytaleRPG-*.jar' -or
        $_.Name -like 'CanvasUI-*.jar' -or $_.Name -like 'ImmersiveNPCs-*.jar' -or
        $_.Name -like 'Taverns-*.jar' -or $_.Name -eq 'Taverns.jar'
    })
    if ($remainingLegacy.Count) { throw "Legacy artifacts remain active: $($remainingLegacy.Name -join ', ')" }

    $result = [ordered]@{
        result='DEPLOYED'; deployedAtUtc=[DateTime]::UtcNow.ToString('o')
        targetSave=$TargetSave; deployedJar=$published; deployedSha256=(Get-Sha256 $published)
        candidateSha256=$candidateHash; backupDirectory=$backupDirectory; fullBackup=$backupSave
        fullBackupMeasure=$backupMeasure
        retiredArtifacts=@($moved | ForEach-Object { [ordered]@{name=[IO.Path]::GetFileName($_.destination);path=$_.destination;sha256=(Get-Sha256 $_.destination)} })
        preservedArtifacts=@($unrelated | ForEach-Object { [ordered]@{name=$_.Name;sha256=(Get-Sha256 $_.FullName)} })
        preservedDataRoots=@('ImmersiveNPCs','InigmasGames_CanvasUI','InigmasGames_HytaleRPGPhase00Audit','InigmasGames_Taverns')
        connectedVerified=$false
    }
    $resultPath = Join-Path $backupDirectory 'deployment-result.json'
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Journal $journal 'DEPLOYMENT_COMPLETE' @{result=$resultPath}
    $result | ConvertTo-Json -Depth 8
} catch {
    if (Test-Path -LiteralPath $published) { Move-Item -LiteralPath $published -Destination (Join-Path $backupDirectory 'failed-Hywind.jar') -Force }
    if (Test-Path -LiteralPath $staging) { Move-Item -LiteralPath $staging -Destination (Join-Path $backupDirectory 'failed-staging.pending') -Force }
    foreach ($record in $moved) {
        if (Test-Path -LiteralPath $record.destination) { Move-Item -LiteralPath $record.destination -Destination $record.source -Force }
    }
    Write-Journal $journal 'DEPLOYMENT_ROLLED_BACK' @{message=$_.Exception.Message}
    throw
}
