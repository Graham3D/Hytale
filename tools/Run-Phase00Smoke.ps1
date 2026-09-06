[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$serverJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
$assetsZip = "$env:APPDATA\Hytale\install\release\package\game\latest\Assets.zip"
$htDevLibJar = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar"
$probeJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.1.jar'
$runDirectory = Join-Path $projectRoot 'run\smoke'
$modsDirectory = Join-Path $runDirectory 'mods'
$evidenceDirectory = Join-Path $projectRoot 'evidence\phase-00'

New-Item -ItemType Directory -Force -Path $modsDirectory, $evidenceDirectory | Out-Null
Copy-Item -LiteralPath $probeJar -Destination (Join-Path $modsDirectory 'HytaleRPG-0.0.1.jar') -Force
Copy-Item -LiteralPath $htDevLibJar -Destination (Join-Path $modsDirectory 'HYTALEDEVLIB-0.5.0.jar') -Force

Push-Location $runDirectory
try {
    $output = & java -jar $serverJar --bare --auth-mode offline --allow-op --disable-sentry --assets=$assetsZip --boot-command=stop 2>&1
    $exitCode = $LASTEXITCODE
}
finally { Pop-Location }

$plain = ($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$plain | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke.log') -Encoding utf8

$summary = [pscustomobject]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $exitCode
    serverVersionObserved = [bool]($plain -match 'Version: 0\.6\.3, Revision: ff802bf5a538f7e4b1df43a575c72f9d2bebb504')
    htDevLibDiscovered = [bool]($plain -match 'HytaleDevLib:HytaleDevLib from path HYTALEDEVLIB-0\.5\.0\.jar')
    htDevLibSetup = [bool]($plain -match 'HytaleDevLib v0\.5\.0 loaded - Helper library ready!')
    htDevLibEnabled = [bool]($plain -match 'Enabled plugin HytaleDevLib:HytaleDevLib')
    probeDiscovered = [bool]($plain -match 'InigmasGames:HytaleRPGPhase00Audit from path HytaleRPG-0\.0\.1\.jar')
    probeSetup = [bool]($plain -match 'PHASE00 setup: temporary evidence probes only; no gameplay mutations')
    probeEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    note = 'Bare-mode shutdown emits core-plugin dependency/shutdown noise; evaluate the named plugin markers above, not unrelated core warnings.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke-summary.json') -Encoding utf8
$summary | Format-List

if (-not ($summary.serverVersionObserved -and $summary.htDevLibDiscovered -and $summary.htDevLibSetup -and
          $summary.htDevLibEnabled -and $summary.probeDiscovered -and $summary.probeSetup -and $summary.probeEnabled)) {
    throw 'One or more required server smoke markers were absent.'
}

