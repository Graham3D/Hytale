[CmdletBinding()]
param(
    [string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
)

$ErrorActionPreference = 'Stop'
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-0.0.2.jar'
if (Test-Path -LiteralPath $targetJar) {
    Remove-Item -LiteralPath $targetJar -Force
    "Removed temporary R005 Phase 00 probe: $targetJar"
} else {
    "No R005 Phase 00 probe was installed at: $targetJar"
}
