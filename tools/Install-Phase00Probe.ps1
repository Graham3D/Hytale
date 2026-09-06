[CmdletBinding()]
param(
    [string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$sourceJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
$htDevLibSource = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar"
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-0.0.2.jar'
$htDevLibTarget = Join-Path $SaveModsDirectory 'HYTALEDEVLIB-0.5.0.jar'
$evidencePath = Join-Path $projectRoot 'evidence\phase-00\installation.json'

if (-not (Test-Path -LiteralPath $sourceJar)) { throw "Build the probe first: $sourceJar" }
if (-not (Test-Path -LiteralPath $SaveModsDirectory)) { throw "Pre-release RPG save mods directory not found: $SaveModsDirectory" }
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $sourceJar

Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
if (-not (Test-Path -LiteralPath $htDevLibTarget)) {
    Copy-Item -LiteralPath $htDevLibSource -Destination $htDevLibTarget
}

$result = [pscustomobject]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o')
    patchline = 'pre-release'
    save = 'RPG'
    revision = 'R003'
    sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
    installed = $targetJar
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetJar).Hash
    htDevLib = $htDevLibTarget
    htDevLibSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $htDevLibTarget).Hash
    rollback = 'Run tools/Uninstall-Phase00Probe.ps1; it removes only the current Phase 00 development jar.'
}
$result | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding utf8
$result | Format-List
