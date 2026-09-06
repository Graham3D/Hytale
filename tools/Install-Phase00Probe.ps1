[CmdletBinding()]
param(
    [string]$SaveModsDirectory = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$sourceJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.1.jar'
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-Phase00-Audit-0.0.1.jar'

if (-not (Test-Path -LiteralPath $sourceJar)) {
    throw "Build the probe first: $sourceJar"
}
if (-not (Test-Path -LiteralPath $SaveModsDirectory)) {
    throw "RPG save mods directory not found: $SaveModsDirectory"
}

Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
[pscustomobject]@{
    installed = $targetJar
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetJar).Hash
    rollback = "Run tools/Uninstall-Phase00Probe.ps1 or remove only this exact jar."
} | Format-List

