[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$DeploymentResult,
    [Parameter(Mandatory=$true)][string]$TargetSave,
    [switch]$FullSave,
    [switch]$ConfirmLiveRestore
)

$ErrorActionPreference = 'Stop'
$deployment = Get-Content -Raw -LiteralPath (Resolve-Path -LiteralPath $DeploymentResult) | ConvertFrom-Json
$target = (Resolve-Path -LiteralPath $TargetSave).Path.TrimEnd('\')
$backup = (Resolve-Path -LiteralPath $deployment.fullBackup).Path.TrimEnd('\')
$live = [IO.Path]::GetFullPath((Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG')).TrimEnd('\')
if ($target -eq $live -and -not $ConfirmLiveRestore) { throw 'Live full rollback requires -ConfirmLiveRestore.' }
if ($target -ne [IO.Path]::GetFullPath($deployment.targetSave).TrimEnd('\')) { throw 'Target does not match the deployment record.' }
if (-not (Test-Path -LiteralPath (Join-Path $backup 'mods'))) { throw 'Deployment backup is not a complete save.' }
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or
    ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')
})
if ($running.Count) { throw 'Hytale or HytaleServer is running.' }

if ($FullSave) {
    $quarantine = Join-Path (Split-Path (Resolve-Path -LiteralPath $DeploymentResult) -Parent) ('failed-state-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    if (Test-Path -LiteralPath $quarantine) { throw "Quarantine already exists: $quarantine" }
    Move-Item -LiteralPath $target -Destination $quarantine
    New-Item -ItemType Directory -Path $target | Out-Null
    & robocopy $backup $target /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "Rollback copy failed with robocopy exit code $LASTEXITCODE; failed state is at $quarantine" }
    $a=@(Get-ChildItem -LiteralPath $backup -Recurse -File -Force);$b=@(Get-ChildItem -LiteralPath $target -Recurse -File -Force)
    if($a.Count-ne$b.Count-or [long](($a|Measure-Object Length -Sum).Sum)-ne[long](($b|Measure-Object Length -Sum).Sum)){
        throw "Rollback verification failed; failed state is at $quarantine"
    }
    [ordered]@{result='FULL_SAVE_RESTORED';target=$target;source=$backup;quarantinedFailedState=$quarantine;files=$b.Count} | ConvertTo-Json
    exit 0
}

$mods = Join-Path $target 'mods'
$hywind = Join-Path $mods 'Hywind.jar'
if (Test-Path -LiteralPath $hywind) {
    $quarantineJar = Join-Path (Split-Path (Resolve-Path -LiteralPath $DeploymentResult) -Parent) ('rolled-back-Hywind-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '.jar')
    Move-Item -LiteralPath $hywind -Destination $quarantineJar
}
foreach($artifact in @($deployment.retiredArtifacts)){
    Copy-Item -LiteralPath (Join-Path $backup "mods\$($artifact.name)") -Destination (Join-Path $mods $artifact.name) -Force
}
[ordered]@{result='ARTIFACTS_RESTORED';target=$target;source=$backup} | ConvertTo-Json
