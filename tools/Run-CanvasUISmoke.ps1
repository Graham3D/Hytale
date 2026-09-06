[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$server = Join-Path $package 'Server\HytaleServer.jar'; $assets = Join-Path $package 'Assets.zip'
$run = Join-Path $root 'run\canvasui-smoke'; $mods = Join-Path $run 'mods'; $evidence = Join-Path $root 'evidence\canvas-ui\R004'
New-Item -ItemType Directory -Force -Path $mods,$evidence | Out-Null
Get-ChildItem -LiteralPath $mods -Filter 'CanvasUI*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $root 'canvas-ui\build\libs\CanvasUI-0.1.0.jar') -Destination $mods
Push-Location $run
try { $output = & java -jar $server --bare --auth-mode offline --allow-op --disable-sentry --assets=$assets --boot-command=stop 2>&1; $exitCode = $LASTEXITCODE }
finally { Pop-Location }
$plain = (($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", '')
$plain | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    hytale070pre1 = [bool]($plain -match 'Version: 0\.7\.0-pre\.1, Revision: e8b4d191fc98a977bf5546a951a7b25473d323e3')
    canvasDiscovered = [bool]($plain -match 'InigmasGames:CanvasUI from path CanvasUI-0\.1\.0\.jar')
    canvasSetup = [bool]($plain -match 'CANVASUI_SETUP revision=R004 version=0\.1\.0 hytale=0\.7\.0-pre\.1')
    canvasEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:CanvasUI')
    demoBundled = [bool]($plain -match 'CANVASUI_DEMO_SETUP revision=R004 bundled=true')
    pluginScopedError = [bool]($plain -match '(?i)(CanvasUI).{0,180}(exception|error|failed)')
    note = 'Bare stop can return non-zero with unrelated core shutdown noise; named markers and scoped errors are authoritative.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
$summary | ConvertTo-Json
if (-not ($summary.hytale070pre1 -and $summary.canvasDiscovered -and $summary.canvasSetup -and $summary.canvasEnabled -and
    $summary.demoBundled) -or $summary.pluginScopedError) { throw 'CanvasUI smoke gate failed.' }
