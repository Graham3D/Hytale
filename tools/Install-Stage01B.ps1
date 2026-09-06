[CmdletBinding()]
param(
    [string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$sourceJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-0.0.2.jar'
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-01b\R009'
$rollbackDirectory = Join-Path $evidenceDirectory 'rollback'

if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) { throw "Build Stage 01B first: $sourceJar" }
if (-not (Test-Path -LiteralPath $SaveModsDirectory -PathType Container)) { throw "RPG save mods directory not found: $SaveModsDirectory" }
New-Item -ItemType Directory -Force -Path $evidenceDirectory, $rollbackDirectory | Out-Null
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $sourceJar
if (Test-Path -LiteralPath $targetJar -PathType Leaf) {
    Copy-Item -LiteralPath $targetJar -Destination (Join-Path $rollbackDirectory 'HytaleRPG-0.0.2-R008.jar') -Force
}
Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force

$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o')
    patchline = 'pre-release'
    hytaleVersion = '0.7.0-pre.1'
    save = 'RPG'
    revision = 'R009'
    sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
    installed = $targetJar
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetJar).Hash
    rollbackJar = (Join-Path $rollbackDirectory 'HytaleRPG-0.0.2-R008.jar')
    rollback = 'Stop the RPG world, then restore rollback/HytaleRPG-0.0.2-R008.jar as Saves/RPG/mods/HytaleRPG-0.0.2.jar. Never downgrade player schema files in place.'
}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'installation.json') -Encoding utf8
[pscustomobject]$result | Format-List
