[CmdletBinding()]
param(
    [string]$CandidateJar = '',
    [string]$TargetMods = '',
    [string]$RollbackRoot = 'C:\HytaleRollback',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
        finally { $algorithm.Dispose() }
    } finally { $stream.Dispose() }
}
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($CandidateJar)) { $CandidateJar = Join-Path $root 'build\libs\HyARPG.jar' }
$CandidateJar = (Resolve-Path -LiteralPath $CandidateJar).Path
if ([string]::IsNullOrWhiteSpace($TargetMods)) {
    $TargetMods = Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods'
}
$TargetMods = (Resolve-Path -LiteralPath $TargetMods).Path
$targetJar = Join-Path $TargetMods 'HyARPG.jar'

$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
    ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
})
if ($running.Count) { throw 'Hytale or HytaleServer is running; deployment is intentionally blocked.' }

$validationJson = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-HyArpgPackage.ps1') `
    -JarPath $CandidateJar -ExpectedVersion '0.2.0-R139' -ExpectedRevision 'R139'
if ($LASTEXITCODE -ne 0) { throw 'Candidate package validation failed.' }
$validation = $validationJson | ConvertFrom-Json
if ($validation.result -ne 'PASS') { throw 'Candidate package validation did not report PASS.' }

$knownActive = @('Hywind.jar', 'HyARPG.jar') | ForEach-Object { Join-Path $TargetMods $_ } | Where-Object { Test-Path -LiteralPath $_ }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rollback = Join-Path $RollbackRoot "HyARPG-R139-CustomUI-Hotfix-$stamp"
$plan = [ordered]@{
    result = if ($DryRun) { 'DRY_RUN' } else { 'PENDING' }
    candidate = $CandidateJar
    candidateSha256 = $validation.sha256
    target = $targetJar
    rollback = $rollback
    activeArtifactsToMove = @($knownActive)
    dataRootsTouched = @()
}
if ($DryRun) { $plan | ConvertTo-Json -Depth 4; exit 0 }

New-Item -ItemType Directory -Force -Path $rollback | Out-Null
$moved = [Collections.Generic.List[object]]::new()
try {
    foreach ($path in $knownActive) {
        $destination = Join-Path $rollback ([IO.Path]::GetFileName($path))
        Move-Item -LiteralPath $path -Destination $destination
        $moved.Add([ordered]@{from=$path;to=$destination;sha256=(Get-Sha256 $destination)})
    }
    Copy-Item -LiteralPath $CandidateJar -Destination $targetJar
    $installedSha = Get-Sha256 $targetJar
    if ($installedSha -ne $validation.sha256) { throw 'Installed JAR hash differs from the validated candidate.' }
    $activeProjectJars = @(Get-ChildItem -LiteralPath $TargetMods -File | Where-Object Name -in @('Hywind.jar','HyARPG.jar'))
    if ($activeProjectJars.Count -ne 1 -or $activeProjectJars[0].Name -ne 'HyARPG.jar') {
        throw 'Deployment did not leave exactly one active HyARPG project JAR.'
    }
    $plan.result = 'PASS'
    $plan.installedSha256 = $installedSha
    $plan.movedArtifacts = @($moved)
    $plan | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $rollback 'deployment.json') -Encoding utf8
    $plan | ConvertTo-Json -Depth 5
} catch {
    if (Test-Path -LiteralPath $targetJar) { Remove-Item -LiteralPath $targetJar -Force }
    foreach ($item in $moved) {
        if (Test-Path -LiteralPath $item.to) { Move-Item -LiteralPath $item.to -Destination $item.from -Force }
    }
    throw
}
