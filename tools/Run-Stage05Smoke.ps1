[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$gamePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $gamePackage 'Server\HytaleServer.jar'
$assetsZip = Join-Path $gamePackage 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$savePermissions = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\permissions.json"
$rpgJar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.8.jar'
$runDirectory = Join-Path $projectRoot 'run\stage-05-smoke'
$modsDirectory = Join-Path $runDirectory 'mods'
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-05\R015'

New-Item -ItemType Directory -Force -Path $modsDirectory, $evidenceDirectory | Out-Null
$resolvedMods = (Resolve-Path -LiteralPath $modsDirectory).Path
if (-not $resolvedMods.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean smoke directory outside repository: $resolvedMods"
}
Get-ChildItem -LiteralPath $resolvedMods -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $rpgJar -Destination (Join-Path $resolvedMods 'HytaleRPG-0.0.8.jar') -Force
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $resolvedMods -Force
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $resolvedMods -Force
Copy-Item -LiteralPath $savePermissions -Destination (Join-Path $runDirectory 'permissions.json') -Force

Push-Location $runDirectory
try {
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'java'
    $start.Arguments = "-jar `"$serverJar`" --bare --auth-mode offline --allow-op --disable-sentry --assets=`"$assetsZip`""
    $start.WorkingDirectory = $runDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start Stage 05 smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 25
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
    }
    if (-not $process.WaitForExit(30000)) {
        $process.Kill($true)
        throw 'Stage 05 smoke server did not stop within 30 seconds.'
    }
    $output = @($stdout.Result, $stderr.Result)
    $exitCode = $process.ExitCode
}
finally { Pop-Location }

$plain = ($output -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
$plain | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    processExitCode = $exitCode
    targetVersion = '0.7.0-pre.1'
    serverVersionObserved = [bool]($plain -match 'Version: 0\.7\.0-pre\.1')
    rpgDiscovered = [bool]($plain -match 'InigmasGames:HytaleRPGPhase00Audit from path HytaleRPG-0\.0\.8\.jar')
    canvasUiDiscovered = [bool]($plain -match 'InigmasGames:CanvasUI from path CanvasUI-0\.1\.0\.jar')
    hytaleDevLibDiscovered = [bool]($plain -match 'HytaleDevLib:HytaleDevLib from path HYTALEDEVLIB-0\.5\.0\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R015 version=0\.0\.8 hytale=0\.7\.0-pre\.1 stage=05 combatEnabled=true')
    stageReady = [bool]($plain -match 'RPG_STAGE05_READY revision=R015 skills=87 passives=66 pilots=12 projectiles=6 schema=2 balance=rpg\.combat-kernel\.r011')
    projectileAssetsRejected = [bool]($plain -match '(?i)(RPG_Fire_Bolt|RPG_Frost_Bolt|RPG_Arcane_Bolt|RPG_Stone_Bolt|RPG_Quick_Shot|RPG_Axe_Toss|Projectile_Config_RPG|RPG_Burn_Visual).{0,200}(error|failed|invalid|unknown)')
    rpgEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    pluginManagerStarted = [bool]($plain -match 'Plugin manager started!')
    cleanOperatorShutdown = [bool]($plain -match 'Shutting down\.\.\. 0\s')
    rpgFailureObserved = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|Exception while enabling.*HytaleRPG|HytaleRPGPhase00Audit.*(fatal|exception))')
    serverFailureObserved = [bool]($plain -match '(?i)(Failed to start Hytale:|shutdownReason\.pluginError|reason: mod_error)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.serverVersionObserved -and $summary.rpgDiscovered -and $summary.canvasUiDiscovered -and
          $summary.hytaleDevLibDiscovered -and $summary.rpgSetup -and $summary.stageReady -and $summary.rpgEnabled -and
          $summary.pluginManagerStarted -and $summary.cleanOperatorShutdown) -or
          $summary.projectileAssetsRejected -or $summary.rpgFailureObserved -or $summary.serverFailureObserved) {
    throw 'Stage 05 server smoke gate failed.'
}
