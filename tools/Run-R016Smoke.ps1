[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'; $assets = Join-Path $package 'Assets.zip'
$saveMods = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods"
$savePermissions = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\permissions.json"
$runDirectory = Join-Path $projectRoot 'run\r016-smoke'; $mods = Join-Path $runDirectory 'mods'
$evidence = Join-Path $projectRoot 'evidence\corrections\R016'
New-Item -ItemType Directory -Force -Path $mods, $evidence | Out-Null
$resolved = (Resolve-Path -LiteralPath $mods).Path
if (-not $resolved.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe smoke path: $resolved" }
Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.9.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'CanvasUI-0.1.0.jar') -Destination $resolved
Copy-Item -LiteralPath (Join-Path $saveMods 'HYTALEDEVLIB-0.5.0.jar') -Destination $resolved
Copy-Item -LiteralPath $savePermissions -Destination (Join-Path $runDirectory 'permissions.json') -Force

Push-Location $runDirectory
try {
    $start = [Diagnostics.ProcessStartInfo]::new('java', "-jar `"$serverJar`" --bare --auth-mode offline --allow-op --disable-sentry --assets=`"$assets`"")
    $start.WorkingDirectory = $runDirectory; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start R016 smoke server.' }
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Seconds 25
    if (-not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(30000)) { $process.Kill($true); throw 'R016 smoke server timeout.' }
    $plain = (($stdout.Result, $stderr.Result) -join [Environment]::NewLine) -replace "`e\[[0-9;]*[A-Za-z]", ''
    $exitCode = $process.ExitCode
}
finally { Pop-Location }
$plain | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke.txt') -Encoding utf8
$summary = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o'); processExitCode = $exitCode
    serverVersionObserved = [bool]($plain -match 'Version: 0\.7\.0-pre\.1')
    exactlyThreeMods = (@(Get-ChildItem -LiteralPath $resolved -Filter '*.jar' -File).Count -eq 3)
    rpgDiscovered = [bool]($plain -match 'HytaleRPG-0\.0\.9\.jar')
    rpgSetup = [bool]($plain -match 'HYTALE_RPG_SETUP revision=R016 version=0\.0\.9 hytale=0\.7\.0-pre\.1 stage=05')
    ready = [bool]($plain -match 'RPG_STAGE05_READY revision=R016 skills=87 passives=66 pilots=12 projectiles=6 schema=3')
    mapping = [bool]($plain -match 'abilityInput=Ability2->skill01,Ability3->skill02,Ability4->skill03 nativeAbility1=SIGNATURE_UNTOUCHED')
    pluginEnabled = [bool]($plain -match 'Enabled plugin InigmasGames:HytaleRPGPhase00Audit')
    managerStarted = [bool]($plain -match 'Plugin manager started!')
    cleanShutdown = [bool]($plain -match 'Shutting down\.\.\. 0\s')
    failure = [bool]($plain -match '(?i)(Failed to setup plugin InigmasGames:HytaleRPGPhase00Audit|shutdownReason\.pluginError|reason: mod_error)')
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'server-smoke-summary.json') -Encoding utf8
[pscustomobject]$summary | Format-List
if (-not ($summary.serverVersionObserved -and $summary.exactlyThreeMods -and $summary.rpgDiscovered -and
    $summary.rpgSetup -and $summary.ready -and $summary.mapping -and $summary.pluginEnabled -and
    $summary.managerStarted -and $summary.cleanShutdown) -or $summary.failure) { throw 'R016 smoke gate failed.' }
