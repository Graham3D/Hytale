[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/..").Path
$evidence=Join-Path $root 'evidence/stage-13/cohort-y'
$previous=Join-Path $root 'evidence/stage-13/cohort-x/artifacts/HytaleRPG-0.0.25.jar'
$built=Join-Path $root 'build/libs/HytaleRPG-0.0.25.jar'
$artifacts=Join-Path $evidence 'artifacts'
$candidate=Join-Path $artifacts 'HytaleRPG-0.0.25.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Bytes($entry){$input=$entry.Open();$memory=[IO.MemoryStream]::new();try{$input.CopyTo($memory);return ,$memory.ToArray()}finally{$input.Dispose();$memory.Dispose()}}
function HashBytes([byte[]]$bytes){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))}
if(Test-Path -LiteralPath $candidate){throw 'Y artifact already exists; do not overwrite evidence'}
$totals=@{tests=0;failures=0;errors=0;skipped=0}
foreach($dir in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    $files=@(Get-ChildItem (Join-Path $root $dir) -Filter 'TEST-*.xml')
    if(-not $files.Count){throw "Missing validation: $dir"}
    foreach($file in $files){$suite=([xml](Get-Content $file.FullName -Raw)).testsuite;foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$suite.$key}}
}
if($totals.tests -ne 2215 -or $totals.failures -or $totals.errors -or $totals.skipped){throw ('Validation mismatch: '+($totals|ConvertTo-Json -Compress))}
New-Item -ItemType Directory -Force $artifacts|Out-Null
Copy-Item -LiteralPath $previous -Destination $candidate
$old=[IO.Compression.ZipFile]::OpenRead($previous);$fresh=[IO.Compression.ZipFile]::OpenRead($built);$out=[IO.Compression.ZipFile]::Open($candidate,[IO.Compression.ZipArchiveMode]::Update)
$changed=[Collections.Generic.List[string]]::new()
try{
    foreach($entry in $fresh.Entries){
        $name=$entry.FullName;$newBytes=Bytes $entry;$oldEntry=$old.GetEntry($name)
        if($oldEntry -and (HashBytes $newBytes) -eq (HashBytes (Bytes $oldEntry))){continue}
        $allowed=$name -match '^com/inigmasgames/hytalerpg/diagnostics/(BoundedTraceWriter|RpgSkillTraceService|SkillTraceConfiguration|TraceArchiveExport|TraceArchiveManager|TraceArchiveManifest|TraceArchiveReader|TraceInput|TraceSegmentWriter)(\$[^/]*)?\.class$' -or
            $name -eq 'com/inigmasgames/hytalerpg/phase00/Phase00Plugin.class' -or
            $name -match '^com/inigmasgames/hytalerpg/ui/trace/RpgUiTraceService(?:\$Record)?\.class$' -or
            $name -eq 'rpg-skill-trace.properties'
        if(-not $allowed){continue}
        if($out.GetEntry($name)){$out.GetEntry($name).Delete()}
        $replacement=$out.CreateEntry($name);$stream=$replacement.Open();try{$stream.Write($newBytes,0,$newBytes.Length)}finally{$stream.Dispose()}
        $changed.Add($name)
    }
}finally{$old.Dispose();$fresh.Dispose();$out.Dispose()}
if($changed.Count -lt 20){throw "Trace package differential unexpectedly small: $($changed.Count)"}
$old=[IO.Compression.ZipFile]::OpenRead($previous);$out=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
    foreach($entry in $old.Entries){
        if($changed.Contains($entry.FullName)){continue}
        $candidateEntry=$out.GetEntry($entry.FullName);if($null -eq $candidateEntry){throw "Missing retained entry: $($entry.FullName)"}
        if((HashBytes (Bytes $entry)) -ne (HashBytes (Bytes $candidateEntry))){throw "Unrelated entry changed: $($entry.FullName)"}
    }
}finally{$old.Dispose();$out.Dispose()}
foreach($name in @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')){Copy-Item -LiteralPath (Join-Path (Split-Path $previous) $name) -Destination $artifacts}
$archive=Join-Path $evidence 'Hytale-RPG-Stage13-Y-trace-storage.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($artifacts,$archive)
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{if($zip.Entries.Count -ne 3){throw 'Archive must contain exactly three RPG distribution mods'};foreach($entry in $zip.Entries){if((HashBytes (Bytes $entry)) -ne (Get-FileHash (Join-Path $artifacts $entry.FullName)).Hash){throw "Archive entry mismatch: $($entry.FullName)"}}}finally{$zip.Dispose()}
$drill=Join-Path $evidence 'rollback-drill';New-Item -ItemType Directory -Force $drill|Out-Null
$target=Join-Path $drill 'RPG.jar';$pending=Join-Path $drill 'pending.jar';Copy-Item $previous $target -Force;Copy-Item $candidate $pending -Force;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $candidate).Hash){throw 'Roll-forward failed'}
Copy-Item $previous $pending;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $previous).Hash){throw 'Rollback failed'}
$result=[ordered]@{cohort='R032-Y';tests=$totals;changedEntries=@($changed);allOtherXEntriesByteIdentical=$true;
    jarSha256=(Get-FileHash $candidate).Hash;archiveSha256=(Get-FileHash $archive).Hash;archiveEntries=3;rollback='PASS';deployed=$false;connectedClientVerified=$false}
$result|ConvertTo-Json -Depth 8|Set-Content (Join-Path $evidence 'package.json') -Encoding utf8
$result|ConvertTo-Json -Depth 8
