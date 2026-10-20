[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$gamePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $gamePackage 'Server\HytaleServer.jar'
$assetsZip = Join-Path $gamePackage 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$rpgJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.6.jar'
$runDirectory = Join-Path $projectRoot 'run\stage-04-smoke'
$modsDirectory = Join-Path $runDirectory 'mods'
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-04\R013'

New-Item -ItemType Directory -Force -Path $modsDirectory, $evidenceDirectory | Out-Null
Get-ChildItem -LiteralPath $modsDirectory -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $rpgJar -Destination (Join-Path $modsDirectory 'HytaleRPG-0.0.6.jar') -Force
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $modsDirectory -Force
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $modsDirectory -Force

Push-Location $runDirectory
try {
    $priorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & java -jar $serverJar --bare --auth-mode offline --allow-op --disable-sentry --assets=$assetsZip --boot-command=stop 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorPreference
}
finally { $ErrorActionPreference = 'Stop'; Pop-Location }

$plain = ($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$plain | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $exitCode
    targetVersion = '0.7.0-pre.1'
    serverVersionObserved = [bool]($plain -match 'Version: 0\.7\.0-pre\.1')
    rpgDiscovered = [bool]($plain -match 'InigmasGames:HytaleRPGPhase00Audit from path HytaleRPG-0\.0\.6\.jar')
    canvasUiDiscovered = [bool]($plain -match 'InigmasGames:CanvasUI from path CanvasUI-0\.1\.0\.jar')
    hytaleDevLibDiscovered = [bool]($plain -match 'HytaleDevLib:HytaleDevLib from path HYTALEDEVLIB-0\.5\.0\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R013 version=0\.0\.6 hytale=0\.7\.0-pre\.1 stage=04 combatEnabled=true')
    stageReady = [bool]($plain -match 'RPG_STAGE04_READY revision=R013 skills=87 passives=66 pilots=6 schema=2 balance=rpg\.combat-kernel\.r011')
    rpgEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    rpgFailureObserved = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|Exception while enabling.*HytaleRPG|HytaleRPGPhase00Audit.*(fatal|exception))')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.serverVersionObserved -and $summary.rpgDiscovered -and $summary.canvasUiDiscovered -and
          $summary.hytaleDevLibDiscovered -and $summary.rpgSetup -and $summary.stageReady -and $summary.rpgEnabled) -or
          $summary.rpgFailureObserved) { throw 'Stage 04 server smoke gate failed.' }
