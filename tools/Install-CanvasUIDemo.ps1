[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..").Path; $evidence = Join-Path $root 'evidence\canvas-ui\R007'
$library = Join-Path $root 'canvas-ui\build\libs\CanvasUI-0.1.0.jar'
if (-not (Test-Path -LiteralPath $SaveModsDirectory -PathType Container)) { throw "Save mods directory missing: $SaveModsDirectory" }
if (-not (Test-Path -LiteralPath $library)) { throw 'Build CanvasUI first.' }
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $library
$libraryTarget = Join-Path $SaveModsDirectory 'CanvasUI-0.1.0.jar'
Copy-Item -LiteralPath $library -Destination $libraryTarget -Force
$legacyDemoTarget = Join-Path $SaveModsDirectory 'CanvasUI-Demo-0.1.0.jar'
if (Test-Path -LiteralPath $legacyDemoTarget) { Remove-Item -LiteralPath $legacyDemoTarget -Force }
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R007'; save = 'pre-release/RPG'
    commit = (& git -C $root rev-parse HEAD).Trim()
    library = [ordered]@{ path = $libraryTarget; sha256 = (Get-FileHash $libraryTarget -Algorithm SHA256).Hash }
    demoBundledInLibraryJar = $true
    rollback = 'Run tools/Uninstall-CanvasUIDemo.ps1; it removes only CanvasUI-0.1.0.jar.'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'installation.json') -Encoding utf8
$result | ConvertTo-Json -Depth 4
