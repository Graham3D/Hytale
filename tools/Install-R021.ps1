[CmdletBinding()]
param([string]$SaveModsDirectory = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG\mods")

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$source = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.14.jar'
$evidence = Join-Path $projectRoot 'evidence\corrections\R021'
$rollback = Join-Path $evidence 'rollback'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Build R021 first: $source" }
$mods = (Resolve-Path -LiteralPath $SaveModsDirectory).Path
if (-not $mods.EndsWith('Hytale\data\pre-release\Saves\RPG\mods', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing deployment outside RPG save mods: $mods"
}
New-Item -ItemType Directory -Force -Path $evidence, $rollback | Out-Null
& (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $source
$target = Join-Path $mods 'HytaleRPG-0.0.14.jar'
$prior = @(Get-ChildItem -LiteralPath $mods -Filter 'HytaleRPG-*.jar' -File)
foreach ($jar in $prior) {
    if ($jar.FullName -ne $target) { Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $rollback $jar.Name) -Force }
}
Copy-Item -LiteralPath $source -Destination $target -Force
foreach ($jar in $prior) { if ($jar.FullName -ne $target) { Remove-Item -LiteralPath $jar.FullName -Force } }
$jars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Sort-Object Name)
$expected = @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar','HytaleRPG-0.0.14.jar')
if ((Compare-Object $expected @($jars.Name)).Count -ne 0) { throw "Expected exactly three mods; found $($jars.Name -join ', ')" }
$result = [ordered]@{
    installedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R021'; version = '0.0.14'; schema = 3
    hytaleVersion = '0.7.0-pre.1'; sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
    installed = $target; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
    deployedJars = @($jars.Name); rollbackJars = @((Get-ChildItem -LiteralPath $rollback -Filter 'HytaleRPG-*.jar' -File).Name)
    rollback = 'Stop the RPG world, remove HytaleRPG-0.0.14.jar, and restore HytaleRPG-0.0.13.jar from this rollback directory.'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'installation.json') -Encoding utf8
[pscustomobject]$result | Format-List
