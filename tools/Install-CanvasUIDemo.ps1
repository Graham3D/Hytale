[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..").Path; $evidence = Join-Path $root 'evidence\canvas-ui\R002'
$library = Join-Path $root 'canvas-ui\build\libs\CanvasUI-0.1.0.jar'; $demo = Join-Path $root 'canvas-ui-demo\build\libs\CanvasUI-Demo-0.1.0.jar'
if (-not (Test-Path -LiteralPath $SaveModsDirectory -PathType Container)) { throw "Save mods directory missing: $SaveModsDirectory" }
if (-not (Test-Path -LiteralPath $library) -or -not (Test-Path -LiteralPath $demo)) { throw 'Build CanvasUI and the demo first.' }
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path @($library, $demo)
$libraryTarget = Join-Path $SaveModsDirectory 'CanvasUI-0.1.0.jar'; $demoTarget = Join-Path $SaveModsDirectory 'CanvasUI-Demo-0.1.0.jar'
Copy-Item -LiteralPath $library -Destination $libraryTarget -Force; Copy-Item -LiteralPath $demo -Destination $demoTarget -Force
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R002'; save = 'pre-release/RPG'
    commit = (& git -C $root rev-parse HEAD).Trim()
    library = [ordered]@{ path = $libraryTarget; sha256 = (Get-FileHash $libraryTarget -Algorithm SHA256).Hash }
    demo = [ordered]@{ path = $demoTarget; sha256 = (Get-FileHash $demoTarget -Algorithm SHA256).Hash }
    rollback = 'Run tools/Uninstall-CanvasUIDemo.ps1; it removes only these two exact jars.'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'installation.json') -Encoding utf8
$result | ConvertTo-Json -Depth 4
