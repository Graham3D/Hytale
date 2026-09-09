$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-p'
$save='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG'
$trace=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl'
$ui=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/ui-trace.jsonl'
$server=Join-Path $save 'logs/2026-09-09_19-00-11_server.log'
$rows=@(Get-Content -LiteralPath $trace|ForEach-Object {$_|ConvertFrom-Json}|Where-Object {[DateTimeOffset]$_.timestamp -ge [DateTimeOffset]'2026-09-09T23:00:11Z' -and [DateTimeOffset]$_.timestamp -lt [DateTimeOffset]'2026-09-09T23:06:10Z'})
$request=@($rows|Where-Object eventType -eq 'PROJECTILE_SPAWN_REQUEST')
if($request.Count -ne 1){throw 'Re-audit connected window'}
$pipeline=@($rows|Where-Object correlationId -eq $request[0].correlationId)
$cancel=@($pipeline|Where-Object eventType -eq 'PROJECTILE_CANCELLED')
if($cancel.Count -ne 1 -or $cancel[0].details.reason -ne 'NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT'){throw 'Unexpected connected boundary'}
$counts=[ordered]@{};foreach($g in ($rows|Group-Object eventType|Sort-Object Name)){$counts[$g.Name]=$g.Count}
$icons=@(1..5|ForEach-Object {
    $source=Join-Path $root "art/StatusChill0$_.png";$copy=Join-Path $root "src/main/resources/Common/UI/StatusEffects/RPG/StatusChill0$_.png"
    $hash=(Get-FileHash -LiteralPath $source).Hash
    if($hash -ne (Get-FileHash -LiteralPath $copy).Hash){throw 'Owner icon was altered'}
    [ordered]@{file="StatusChill0$_.png";sourceSha256=$hash;packagedSourceIdentical=$true}
})
[ordered]@{connectedBuild='O';window='2026-09-09 23:00:11 through 23:06:10 UTC exclusive';eventCounts=$counts;
    cancellationDelayMs=([DateTimeOffset]$cancel[0].timestamp-[DateTimeOffset]$request[0].timestamp).TotalMilliseconds;
    sourceHashes=@($trace,$ui,$server)|ForEach-Object {Get-FileHash -LiteralPath $_};pipeline=$pipeline;ownerIcons=$icons}|
    ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $out 'connected-feedback-evidence.json') -Encoding utf8
