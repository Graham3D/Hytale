param(
    [string]$SaveName,
    [string]$ModsDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$sourceJar = Join-Path $projectRoot 'dist\Taverns-0.1.0.jar'
if (-not (Test-Path -LiteralPath $sourceJar)) {
    & (Join-Path $projectRoot 'build.ps1')
}

if ([string]::IsNullOrWhiteSpace($ModsDirectory)) {
    if ([string]::IsNullOrWhiteSpace($SaveName)) {
        throw 'Pass -SaveName "Your Save Name" or -ModsDirectory "full path to mods folder".'
    }
    $ModsDirectory = Join-Path $env:APPDATA "Hytale\UserData\Saves\$SaveName\mods"
}

New-Item -ItemType Directory -Force -Path $ModsDirectory | Out-Null
$destination = Join-Path $ModsDirectory 'Taverns-0.1.0.jar'
Copy-Item -LiteralPath $sourceJar -Destination $destination -Force
Write-Host "Installed $destination"
Write-Host 'Restart the world/local server before testing Java changes.'
