[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$source = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.16.jar'
$evidence = Join-Path $projectRoot 'evidence\corrections\R023'
$rollback = Join-Path $evidence 'rollback'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Build R023 first: $source" }
$mods = (Resolve-Path -LiteralPath $SaveModsDirectory).Path
if (-not $mods.EndsWith('Hytale\data\pre-release\Saves\RPG\mods', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing deployment outside RPG save mods: $mods"
}
New-Item -ItemType Directory -Force -Path $evidence, $rollback | Out-Null
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $source
$target = Join-Path $mods 'HytaleRPG-0.0.16.jar'
$prior = @(Get-ChildItem -LiteralPath $mods -Filter 'HytaleRPG-*.jar' -File)
$before = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Select-Object -ExpandProperty Name)
if ((Compare-Object @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar','HytaleRPG-0.0.15.jar') $before).Count -ne 0) {
    throw 'Deployment requires the established three-mod R022 baseline; nothing changed.'
}
if (@(Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object CommandLine -Match 'HytaleServer\.jar').Count) {
    throw 'Stop Hytale servers before deployment; nothing changed.'
}
foreach ($jar in $prior) {
    $backup = Join-Path $rollback $jar.Name
    if (-not (Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $jar.FullName -Destination $backup }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $backup).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $jar.FullName).Hash) {
        throw 'Rollback hash mismatch; nothing deployed.'
    }
}
if (Test-Path -LiteralPath $target) { throw "R023 already deployed; preserve its backup before overwriting: $target" }
Copy-Item -LiteralPath $source -Destination $target
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash) { throw 'Deployment hash mismatch; prior build retained.' }
foreach ($jar in $prior) { if ($jar.FullName -ne $target) { Remove-Item -LiteralPath $jar.FullName -Force } }
$jars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Sort-Object Name)
$expected = @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar','HytaleRPG-0.0.16.jar')
if ((Compare-Object $expected @($jars.Name)).Count -ne 0) { throw "Expected exactly three mods; found $($jars.Name -join ', ')" }
$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R023'; version = '0.0.16'; schema = 3
    hytaleVersion = '0.7.0-pre.1'; sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
    installed = $target; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
    deployedJars = @($jars.Name); rollbackJars = @((Get-ChildItem -LiteralPath $rollback -Filter 'HytaleRPG-*.jar' -File).Name)
    deployedHashes = @($jars | ForEach-Object { @{name=$_.Name; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash} })
    rollback = 'Stop the RPG world, remove HytaleRPG-0.0.16.jar, and restore the retained HytaleRPG-0.0.15.jar.'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'installation.json') -Encoding utf8
[pscustomobject]$result | Format-List
