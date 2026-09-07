$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R023'
$save = "$env:APPDATA\Hytale\data\pre-release\Saves\RPG"
$log = Join-Path $save 'logs\2026-09-07_13-12-52_server.log'
$trace = Join-Path $save 'mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl'
$rows = @(Get-Content -LiteralPath $trace | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object rpgRevision -eq 'R022')
$counts = [ordered]@{}
foreach ($name in @('NATIVE_ABILITY_SLOT_PROJECTED','NATIVE_ABILITY_SLOT_CONFLICT','NATIVE_ABILITY_INPUT_OBSERVED',
    'SKILL_ACTIVATION_REQUEST','SKILL_VALIDATION_PASS','SKILL_COMMITTED','EXECUTOR_DISPATCH',
    'STRIKE_QUERY','PROJECTILE_SPAWN_REQUEST','PROJECTILE_SPAWNED')) {
    $counts[$name] = @($rows | Where-Object eventType -eq $name).Count
}
$lines = @(Get-Content -LiteralPath $log | Where-Object { $_ -match 'RPG_NATIVE_BRIDGE_AUDIT revision=R022|RPG_STAGE05_READY revision=R022' })
$result = [ordered]@{
    capturedAtUtc=[DateTime]::UtcNow.ToString('o'); revision='R022'; serverLog=(Split-Path $log -Leaf)
    serverLogSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $log).Hash
    traceSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $trace).Hash
    firstR022Timestamp=$rows[0].timestamp; lastR022Timestamp=$rows[-1].timestamp
    eventCounts=$counts; setupAndBridgeEvidence=$lines
    conclusion='Connected RPG casting FAILED. Zero accepted initial ability observations, not a raw packet capture. Bridge versus watcher remains unresolved.'
}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $evidence 'r022-connected-failure.json') -Encoding utf8
[pscustomobject]$result | Format-List
