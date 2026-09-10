$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-q'
$save='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG'
$logs=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg'
$sources=@(Get-ChildItem -LiteralPath $logs -Filter 'skill-trace.jsonl*' -File)
$rows=@($sources|ForEach-Object {Get-Content -LiteralPath $_.FullName|ForEach-Object {$_|ConvertFrom-Json}}|
    Where-Object {[DateTimeOffset]$_.timestamp -ge [DateTimeOffset]'2026-09-09T23:45:01Z' -and [DateTimeOffset]$_.timestamp -lt [DateTimeOffset]'2026-09-09T23:52:50Z'}|
    Sort-Object {[DateTimeOffset]$_.timestamp})
$casts=@($rows|Where-Object eventType -eq 'SKILL_COMMITTED'|ForEach-Object {
    $cast=$_;$events=@($rows|Where-Object correlationId -eq $cast.correlationId)
    $damage=@($events|Where-Object eventType -eq 'DAMAGE_INSPECTED')
    [ordered]@{timestamp=$cast.timestamp;skill=$cast.details.skillId;correlationId=$cast.correlationId;
        queries=@($events|Where-Object eventType -eq 'STRIKE_QUERY'|ForEach-Object {$_.details});
        damageReceipts=@($damage|ForEach-Object {$_.details});actualHealthLoss=($damage|ForEach-Object {$_.details.actualHealthLoss}|Measure-Object -Sum).Sum;
        terminalReasons=@($events|Where-Object eventType -eq 'SKILL_TERMINATED'|ForEach-Object {$_.details.reason})}
})
$server=Join-Path $save 'logs/2026-09-09_19-45-01_server.log'
$ui=Join-Path $logs 'ui-trace.jsonl'
$counts=[ordered]@{};foreach($g in ($rows|Group-Object eventType|Sort-Object Name)){$counts[$g.Name]=$g.Count}
[ordered]@{connectedBuild='P';windowUtc='2026-09-09 23:45:01 through 23:52:50 exclusive';rotationsIncluded=$sources.Name;
    sourceHashes=@($sources.FullName)+@($server,$ui)|ForEach-Object {Get-FileHash -LiteralPath $_};eventCounts=$counts;casts=$casts;
    serverFailureLines=@(Select-String -LiteralPath $server -Pattern 'RPG_ENCOUNTER_FAILURE|ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED'|ForEach-Object {$_.Line});
    quickSlashDamageEvents=@($rows|Where-Object {$_.eventType -in @('DAMAGE_INSPECTED','STRIKE_HIT') -and $_.correlationId -in @($casts|Where-Object skill -eq 'quick_slash'|ForEach-Object {$_.correlationId})});
    connectedNewSpeedVerified=$false}|ConvertTo-Json -Depth 14|Set-Content -LiteralPath (Join-Path $out 'connected-log-review.json') -Encoding utf8
$casts|Where-Object skill -eq 'quick_slash'|ForEach-Object {[pscustomobject]@{timestamp=$_.timestamp;loss=$_.actualHealthLoss;terminal=$_.terminalReasons -join ','}}
