param(
    [string]$OfflineRoot = 'G:\My Drive\Inigmas Games\Orbis Offline Training',
    [string]$ActiveSaveRoot = "$env:APPDATA\Hytale\UserData\Saves\NPC"
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
& (Join-Path $projectRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Build failed before D5 freeze.' }
$java = Join-Path $projectRoot '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { $java = (Get-Command java -ErrorAction Stop).Source }
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$classpath = (Join-Path $projectRoot 'build\classes') + ';' + $serverJar
& $java -classpath $classpath `
    com.inigmasgames.persistentnpcs.training.cli.Block2Cli freeze-fixture `
    $projectRoot $OfflineRoot $ActiveSaveRoot
if ($LASTEXITCODE -ne 0) { throw 'D5 freeze gate failed.' }
