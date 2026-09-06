[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
foreach ($name in @('CanvasUI-0.1.0.jar')) {
    $target = Join-Path $SaveModsDirectory $name
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force; "Removed $target" }
    else { "Not installed: $target" }
}
