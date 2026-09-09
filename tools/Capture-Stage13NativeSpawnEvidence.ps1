[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-o'
$save='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG'
$trace=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl'
$ui=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/ui-trace.jsonl'
$server=Join-Path $save 'logs/2026-09-09_18-20-29_server.log'
$start=[DateTimeOffset]'2026-09-09T22:20:29Z';$end=[DateTimeOffset]'2026-09-09T22:22:18Z'
$rows=@(Get-Content -LiteralPath $trace|ForEach-Object {$_|ConvertFrom-Json}|Where-Object {[DateTimeOffset]$_.timestamp -ge $start -and [DateTimeOffset]$_.timestamp -lt $end})
$failures=@($rows|Where-Object {$_.eventType -eq 'PROJECTILE_SPAWN_REJECTED' -and $_.details.reason -eq 'ATOMIC_BATCH_ROLLBACK'})
if($failures.Count -ne 3 -or @($failures|Where-Object {$_.details.error -ne 'IllegalArgumentException'}).Count){throw 'Connected failure evidence differs; re-audit'}
$ids=@($failures.correlationId)
$pipelines=@($rows|Where-Object {$_.correlationId -in $ids})
$counts=[ordered]@{};foreach($g in ($rows|Group-Object eventType|Sort-Object Name)){$counts[$g.Name]=$g.Count}
$uiRows=@(Get-Content -LiteralPath $ui|ForEach-Object {$_|ConvertFrom-Json}|Where-Object {[DateTimeOffset]$_.timestamp -ge $start -and [DateTimeOffset]$_.timestamp -lt $end})
[ordered]@{windowStartUtc=$start.ToString('o');windowEndExclusiveUtc=$end.ToString('o');sourceFiles=@($trace,$ui,$server)|ForEach-Object {Get-FileHash -LiteralPath $_};
    eventCounts=$counts;uiRecordsInWindow=$uiRows.Count;fireBoltAtomicAllocationFailures=$failures.Count;fireBoltPipelines=$pipelines;
    note='Connected N session, not corrected O client proof. Later idle/rejoin sessions are excluded. No raw server authentication log copied.'}|
    ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $out 'connected-failure-evidence.json') -Encoding utf8
