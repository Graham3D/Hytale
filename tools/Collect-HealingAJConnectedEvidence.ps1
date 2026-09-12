[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$save=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG'
$traces=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg'
$archive=Join-Path $traces 'archive/skill-trace-20260912T214832Z-000002.jsonl.gz'
$manifest=Get-Content ($archive.Replace('.jsonl.gz','.manifest.json')) -Raw|ConvertFrom-Json
if($manifest.archiveState -ne 'VERIFIED' -or (Get-FileHash $archive).Hash -ne $manifest.compressedSha256){throw 'Archive integrity mismatch'}
$file=[IO.File]::OpenRead($archive)
$gzip=[IO.Compression.GZipStream]::new($file,[IO.Compression.CompressionMode]::Decompress)
$memory=[IO.MemoryStream]::new()
try{$gzip.CopyTo($memory);$bytes=$memory.ToArray()}finally{$gzip.Dispose();$file.Dispose();$memory.Dispose()}
$sha=[Security.Cryptography.SHA256]::Create()
try{$digest=([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','')}finally{$sha.Dispose()}
if($digest -ne $manifest.uncompressedSha256 -or $bytes.Length -ne $manifest.uncompressedBytes){throw 'Decompressed integrity mismatch'}
$lines=@([Text.Encoding]::UTF8.GetString($bytes) -split '\r?\n'|Where-Object {$_})
if($lines.Count -ne $manifest.eventCount){throw 'Archived record count mismatch'}
$active=Join-Path $traces 'skill-trace.jsonl'
$all=@($lines)+@(Get-Content $active)
$records=@($all|ForEach-Object {ConvertFrom-Json $_}|Where-Object {([DateTimeOffset]$_.timestamp) -ge [DateTimeOffset]'2026-09-12T21:48:00Z' -and ([DateTimeOffset]$_.timestamp) -le [DateTimeOffset]'2026-09-12T21:52:00Z'})
$probe=@($records|Where-Object {$_.details.probe})
$modes=@($probe|Group-Object {$_.details.mode}|ForEach-Object {
    $group=$_.Group
    [ordered]@{mode=$_.Name;roots=@($group.details.root|Sort-Object -Unique).Count;
        requested=@($group|Where-Object {$_.details.stage -eq 'REQUESTED'}).Count;
        stages=@($group.details.stage|Sort-Object -Unique);
        targetSelections=@($group.details.targetSelection|Where-Object {$_}|Sort-Object -Unique);
        failures=@($group|Where-Object {$_.details.stage -in @('FAILED','RELEASE_FAILED','OBSERVER_FAILED')}).Count}
})
$server=Join-Path $save 'logs/2026-09-12_17-48-00_server.log'
$client=Join-Path $env:APPDATA 'Hytale/data/pre-release/Logs/2026-09-12_17-47-50_client.log'
$text=Get-Content $server -Raw
$out=Join-Path $repo 'evidence/stage-13/cohort-ak/aj-connected-review.json'
[ordered]@{windowUtc='2026-09-12T21:48:00Z..21:52:00Z';sources=@(
    @{file=[IO.Path]::GetFileName($server);sha256=(Get-FileHash $server).Hash},
    @{file=[IO.Path]::GetFileName($client);sha256=(Get-FileHash $client).Hash},
    @{file=[IO.Path]::GetFileName($archive);sha256=(Get-FileHash $archive).Hash},
    @{file='skill-trace.jsonl';sha256=(Get-FileHash $active).Hash},
    @{file='ui-trace.jsonl';sha256=(Get-FileHash (Join-Path $traces 'ui-trace.jsonl')).Hash});
    startupMarker=($text -split '\r?\n'|Where-Object {$_ -match 'RPG_HEAL_PROBE revision='});
    records=$records.Count;probeRecords=$probe.Count;modes=$modes;
    ownerObservation='All eight controls visibly noticed, per owner message; not a material/motion or complete Gate A acceptance.';
    recipientStaffBackend='CLIENT_ONLY_NO_NATIVE_CONTROLLER_WRITE';nativeProductionChannelConnected='NOT_TESTED_IN_THIS_SESSION';
    eventCounts=@($records|Group-Object eventType|ForEach-Object {@{event=$_.Name;count=$_.Count}});
    archive=@{state=$manifest.archiveState;records=$manifest.eventCount;uncompressedBytes=$bytes.Length;compressedBytes=$manifest.compressedBytes;
        hashesIndependentlyVerified=$true;activeBytes=(Get-Item $active).Length};
    encounterErrorInServer=[bool]($text -match 'NoSuchElementException|ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED')
}|ConvertTo-Json -Depth 8|Set-Content $out -Encoding utf8
Get-Content $out
