param([string]$LogDirectory = "$env:APPDATA\Hytale\UserData\Logs")
$ErrorActionPreference = 'Stop'
$latest = Get-ChildItem -LiteralPath $LogDirectory -File -Filter '*_client.log' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $latest) { throw "No Hytale client log found in $LogDirectory" }
$failures = Select-String -LiteralPath $latest.FullName -Pattern 'Texture atlas needs|dropping [0-9]+ of [0-9]+ images'
if ($failures) {
    Write-Error "Appearance client-resource sentinel: atlas failure in $($latest.FullName). Fully exit Hytale before retesting."
    $failures | ForEach-Object { Write-Host ("line {0}: {1}" -f $_.LineNumber, $_.Line.Trim()) }
    exit 2
}
Write-Host "No atlas-allocation failure found in $($latest.FullName). This is a local-development log check, not a remote-client signal."
