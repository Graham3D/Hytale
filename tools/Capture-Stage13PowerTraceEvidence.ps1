param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-n'
$logs='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg'
$path=Join-Path $logs 'skill-trace.jsonl'
$counts=@{};$bytes=@{};$examples=@();$first=$null;$last=$null;$total=0L
$sessionCounts=@{};$sessionBytes=@{};$sessionTotal=0L;$sessionStart='2026-09-09T21:46:11'
foreach($line in [IO.File]::ReadLines($path)){
    if(-not $line){continue};$r=$line|ConvertFrom-Json
    if(-not $first){$first=$r.timestamp};$last=$r.timestamp
    $n=[Text.Encoding]::UTF8.GetByteCount($line)+2;$total+=$n;$event=[string]$r.eventType
    $counts[$event]++;$bytes[$event]+=$n
    if(([DateTimeOffset]$r.timestamp) -ge [DateTimeOffset]::Parse($sessionStart+'Z')){$sessionCounts[$event]++;$sessionBytes[$event]+=$n;$sessionTotal+=$n}
    if($event -eq 'SKILL_PREPARATION_FAILED' -and $r.details.itemId -in @('Weapon_Longsword_Flame','Weapon_Staff_Mithril')){
        $examples+= [ordered]@{timestamp=$r.timestamp;event=$event;correlationId=$r.correlationId;details=$r.details}
    }
}
$result=[ordered]@{scope='Entire retained skill-trace.jsonl before N; CRLF-inclusive UTF-8 byte counts';first=$first;last=$last;
    sha256=(Get-FileHash -LiteralPath $path).Hash;fileBytes=(Get-Item -LiteralPath $path).Length;countedBytes=$total;
    eventCounts=$counts;eventBytes=$bytes;nativeTickBytePercent=100*$bytes.NATIVE_RPG_TICK_SAMPLE/$total;
    latestSession=[ordered]@{fromUtc=$sessionStart;eventCounts=$sessionCounts;eventBytes=$sessionBytes;totalBytes=$sessionTotal;nativeTickBytePercent=100*$sessionBytes.NATIVE_RPG_TICK_SAMPLE/$sessionTotal};
    reportedItemFailures=$examples;uiTraceSha256=(Get-FileHash -LiteralPath (Join-Path $logs 'ui-trace.jsonl')).Hash;
    serverLogSha256=(Get-FileHash -LiteralPath 'C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\logs\2026-09-09_17-46-11_server.log').Hash}
$result|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $out 'connected-failure-evidence.json') -Encoding utf8
$result|ConvertTo-Json -Depth 15
