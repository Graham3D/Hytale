[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$sourceJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.4.jar'
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-0.0.4.jar'
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-02\R011'
$rollbackDirectory = Join-Path $evidenceDirectory 'rollback'

if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) { throw "Build Stage 02 first: $sourceJar" }
if (-not (Test-Path -LiteralPath $SaveModsDirectory -PathType Container)) { throw "RPG save mods directory not found: $SaveModsDirectory" }
New-Item -ItemType Directory -Force -Path $evidenceDirectory, $rollbackDirectory | Out-Null
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $sourceJar

$priorRpgJars = @(Get-ChildItem -LiteralPath $SaveModsDirectory -Filter 'HytaleRPG-*.jar' -File)
foreach ($prior in $priorRpgJars) {
    Copy-Item -LiteralPath $prior.FullName -Destination (Join-Path $rollbackDirectory $prior.Name) -Force
}
Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
foreach ($prior in $priorRpgJars) {
    if ($prior.FullName -ne $targetJar) { Remove-Item -LiteralPath $prior.FullName -Force }
}

$deployedJars = @(Get-ChildItem -LiteralPath $SaveModsDirectory -Filter '*.jar' -File | Sort-Object Name)
$expectedNames = @('CanvasUI-0.1.0.jar', 'HYTALEDEVLIB-0.5.0.jar', 'HytaleRPG-0.0.4.jar')
$actualNames = @($deployedJars.Name)
if ((Compare-Object $expectedNames $actualNames).Count -ne 0) {
    throw "Deployment must contain exactly the established three JARs. Found: $($actualNames -join ', ')"
}

$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o')
    patchline = 'pre-release'
    hytaleVersion = '0.7.0-pre.1'
    save = 'RPG'
    revision = 'R011'
    sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
    installed = $targetJar
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetJar).Hash
    deployedJars = $actualNames
    rollbackJars = @($priorRpgJars.Name)
    rollbackDirectory = $rollbackDirectory
    rollback = 'Stop the RPG world, remove HytaleRPG-0.0.4.jar, and restore the retained R010 HytaleRPG-0.0.3.jar into Saves/RPG/mods. Player schema remains v2.'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'installation.json') -Encoding utf8
[pscustomobject]$result | Format-List
