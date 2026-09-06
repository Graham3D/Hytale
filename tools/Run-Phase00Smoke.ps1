[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$gamePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $gamePackage 'Server\HytaleServer.jar'
$assetsZip = Join-Path $gamePackage 'Assets.zip'
$htDevLibJar = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar"
$probeJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
$runDirectory = Join-Path $projectRoot 'run\smoke'
$modsDirectory = Join-Path $runDirectory 'mods'
$evidenceDirectory = Join-Path $projectRoot 'evidence\phase-00'

New-Item -ItemType Directory -Force -Path $modsDirectory, $evidenceDirectory | Out-Null
Get-ChildItem -LiteralPath $modsDirectory -Filter 'HytaleRPG*.jar' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force
Copy-Item -LiteralPath $probeJar -Destination (Join-Path $modsDirectory 'HytaleRPG-0.0.2.jar') -Force
Copy-Item -LiteralPath $htDevLibJar -Destination (Join-Path $modsDirectory 'HYTALEDEVLIB-0.5.0.jar') -Force

Push-Location $runDirectory
try {
    $output = & java -jar $serverJar --bare --auth-mode offline --allow-op --disable-sentry --assets=$assetsZip --boot-command=stop 2>&1
    $exitCode = $LASTEXITCODE
}
finally { Pop-Location }

$plain = ($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$plain | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke.txt') -Encoding utf8

$summary = [pscustomobject]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $exitCode
    targetVersion = '0.7.0-pre.1'
    targetRevision = 'e8b4d191fc98a977bf5546a951a7b25473d323e3'
    serverVersionObserved = [bool]($plain -match 'Version: 0\.7\.0-pre\.1, Revision: e8b4d191fc98a977bf5546a951a7b25473d323e3')
    htDevLibDiscovered = [bool]($plain -match 'HytaleDevLib:HytaleDevLib from path HYTALEDEVLIB-0\.5\.0\.jar')
    htDevLibSetup = [bool]($plain -match 'HytaleDevLib v0\.5\.0 loaded - Helper library ready!')
    htDevLibEnabled = [bool]($plain -match 'Enabled plugin HytaleDevLib:HytaleDevLib')
    probeDiscovered = [bool]($plain -match 'InigmasGames:HytaleRPGPhase00Audit from path HytaleRPG-0\.0\.2\.jar')
    probeSetup = [bool]($plain -match 'PHASE00_SETUP revision=R007 version=0\.0\.2 hytale=0\.7\.0-pre\.1 stage=00 observationOnly=true')
    probeEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    pluginExceptionObserved = [bool]($plain -match '(?i)(HytaleRPGPhase00Audit|PHASE00).{0,160}(exception|error|failed)')
    note = 'Bare shutdown may emit unrelated core transport/dependency noise. Gate on target/version/plugin markers and plugin-scoped exceptions.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke-summary.json') -Encoding utf8
$summary | Format-List

if (-not ($summary.serverVersionObserved -and $summary.htDevLibDiscovered -and $summary.htDevLibSetup -and
          $summary.htDevLibEnabled -and $summary.probeDiscovered -and $summary.probeSetup -and
          $summary.probeEnabled) -or $summary.pluginExceptionObserved) {
    throw 'One or more required 0.7.0-pre.1 server smoke markers were absent or a plugin-scoped error appeared.'
}
