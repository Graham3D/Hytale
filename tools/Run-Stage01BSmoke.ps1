[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$gamePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $gamePackage 'Server\HytaleServer.jar'
$assetsZip = Join-Path $gamePackage 'Assets.zip'
$htDevLibJar = "$env:APPDATA\Hytale\UserData\Saves\RPG\mods\HYTALEDEVLIB-0.5.0.jar"
$rpgJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
$runDirectory = Join-Path $projectRoot 'run\stage-01b-smoke'
$modsDirectory = Join-Path $runDirectory 'mods'
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-01b\R009'

New-Item -ItemType Directory -Force -Path $modsDirectory, $evidenceDirectory | Out-Null
Get-ChildItem -LiteralPath $modsDirectory -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $rpgJar -Destination (Join-Path $modsDirectory 'HytaleRPG-0.0.2.jar') -Force
Copy-Item -LiteralPath $htDevLibJar -Destination (Join-Path $modsDirectory 'HYTALEDEVLIB-0.5.0.jar') -Force

Push-Location $runDirectory
try {
    $output = & java -jar $serverJar --bare --auth-mode offline --allow-op --disable-sentry --assets=$assetsZip --boot-command=stop 2>&1
    $exitCode = $LASTEXITCODE
}
finally { Pop-Location }

$plain = ($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$plain | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $exitCode
    targetVersion = '0.7.0-pre.1'
    serverVersionObserved = [bool]($plain -match 'Version: 0\.7\.0-pre\.1')
    rpgDiscovered = [bool]($plain -match 'InigmasGames:HytaleRPGPhase00Audit from path HytaleRPG-0\.0\.2\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R009 version=0\.0\.2 hytale=0\.7\.0-pre\.1 stage=01B combatEnabled=false')
    stageReady = [bool]($plain -match 'RPG_STAGE01B_READY revision=R009 skills=87 passives=66 schema=2')
    rpgEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    pluginExceptionObserved = [bool]($plain -match '(?i)(HytaleRPGPhase00Audit|HYTALE_RPG|RPG_STAGE01B).{0,200}(exception|error|failed)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.serverVersionObserved -and $summary.rpgDiscovered -and $summary.rpgSetup -and
          $summary.stageReady -and $summary.rpgEnabled) -or $summary.pluginExceptionObserved) {
    throw 'Stage 01B server smoke gate failed.'
}
