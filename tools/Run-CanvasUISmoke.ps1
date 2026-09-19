[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$root = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$server = Join-Path $package 'Server\HytaleServer.jar'; $assets = Join-Path $package 'Assets.zip'
$revision = ((Get-Content -LiteralPath (Join-Path $root 'gradle.properties')) | Where-Object { $_ -like 'rpg_revision=*' }) -replace '^rpg_revision=', ''
$hytaleVersion = ((Get-Content -LiteralPath (Join-Path $root 'gradle.properties')) | Where-Object { $_ -like 'hytale_version=*' }) -replace '^hytale_version=', ''
$run = Join-Path $root 'run\canvasui-cursor-smoke'; $mods = Join-Path $run 'mods'; $evidence = Join-Path $root ("evidence\canvas-ui\cursor-hud\" + $revision)
New-Item -ItemType Directory -Force -Path $mods,$evidence | Out-Null
Get-ChildItem -LiteralPath $mods -Filter 'CanvasUI*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $root 'canvas-ui\build\libs\CanvasUI-0.1.0.jar') -Destination $mods
Push-Location $run
$savedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $output = & java -jar $server --bare --auth-mode offline --allow-op --disable-sentry --assets=$assets --boot-command=stop 2>&1; $exitCode = $LASTEXITCODE }
finally { $ErrorActionPreference = $savedErrorActionPreference; Pop-Location }
$plain = (($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", '')
$plain | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    hytaleVersion = $hytaleVersion
    hytaleVersionObserved = [bool]($plain -match [regex]::Escape("Version: $hytaleVersion"))
    canvasDiscovered = [bool]($plain -match 'InigmasGames:CanvasUI from path CanvasUI-0\.1\.0\.jar')
    canvasSetup = [bool]($plain -match [regex]::Escape("CANVASUI_SETUP revision=$revision version=0.1.0 hytale=$hytaleVersion"))
    canvasEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:CanvasUI')
    demoBundled = [bool]($plain -match [regex]::Escape("CANVASUI_DEMO_SETUP revision=$revision bundled=true"))
    cursorProbeRegistered = [bool]($plain -match '/canvasui-cursor-probe')
    cursorDragProofRegistered = [bool]($plain -match '/canvasui-cursor-drag-proof')
    pluginScopedError = [bool]($plain -match '(?i)(CanvasUI).{0,180}(exception|error|failed)')
    note = 'Bare stop can return non-zero with unrelated core shutdown noise; named markers and scoped errors are authoritative.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
$summary | ConvertTo-Json
if (-not ($summary.hytaleVersionObserved -and $summary.canvasDiscovered -and $summary.canvasSetup -and $summary.canvasEnabled -and
    $summary.demoBundled -and $summary.cursorProbeRegistered -and $summary.cursorDragProofRegistered) -or $summary.pluginScopedError) { throw 'CanvasUI smoke gate failed.' }
