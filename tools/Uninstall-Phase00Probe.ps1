[CmdletBinding()]
param(
    [string]$SaveModsDirectory = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods"
)

$ErrorActionPreference = 'Stop'
$targetJar = Join-Path $SaveModsDirectory 'HytaleRPG-Phase00-Audit-0.0.1.jar'
if (Test-Path -LiteralPath $targetJar) {
    Remove-Item -LiteralPath $targetJar -Force
    "Removed temporary Phase 00 probe: $targetJar"
} else {
    "No temporary Phase 00 probe was installed at: $targetJar"
}

