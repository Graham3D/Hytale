[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceSave = "$env:APPDATA\Hytale\UserData\Saves\RPG"
$targetRoot = "$env:APPDATA\Hytale\data\pre-release\Saves"
$targetSave = Join-Path $targetRoot 'RPG'

if (-not (Test-Path -LiteralPath $sourceSave -PathType Container)) { throw "Release RPG save not found: $sourceSave" }
New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null
if (Test-Path -LiteralPath $targetSave) {
    "Pre-release RPG save already exists; left unchanged: $targetSave"
    return
}

Copy-Item -LiteralPath $sourceSave -Destination $targetSave -Recurse
$locks = Get-ChildItem -LiteralPath $targetSave -Recurse -File -Filter '*.lock' -ErrorAction SilentlyContinue
foreach ($lock in $locks) { Remove-Item -LiteralPath $lock.FullName -Force }
$oldAuditJars = Get-ChildItem -LiteralPath (Join-Path $targetSave 'mods') -File -Filter 'HytaleRPG-Phase00-Audit-*.jar' -ErrorAction SilentlyContinue
foreach ($jar in $oldAuditJars) { Remove-Item -LiteralPath $jar.FullName -Force }
$metadata = [ordered]@{ CreatedWithPatchline = 'pre-release' }
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $targetSave 'client_metadata.json') -Encoding utf8
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $targetSave 'client_metadata.json.bak') -Encoding utf8
"Created isolated pre-release RPG save copy; release source remains unchanged: $targetSave"
