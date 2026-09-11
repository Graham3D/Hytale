[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/..").Path
$evidence=Join-Path $root 'evidence/stage-13/cohort-x'
$previous=Join-Path $root 'evidence/stage-13/cohort-w/artifacts/HytaleRPG-0.0.25.jar'
$built=Join-Path $root 'build/libs/HytaleRPG-0.0.25.jar'
$candidate=Join-Path $evidence 'artifacts/HytaleRPG-0.0.25.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Bytes($entry){$inputStream=$entry.Open();$memory=[IO.MemoryStream]::new();try{$inputStream.CopyTo($memory);return ,$memory.ToArray()}finally{$inputStream.Dispose();$memory.Dispose()}}
function HashBytes([byte[]]$bytes){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))}
if(Test-Path $candidate){throw 'X artifact already exists; do not overwrite evidence'}
if((Get-FileHash $previous).Hash -ne '585E0A32B9682B5BE06FF8202EF39D9D88302E8FF178457A894440402E560EA3'){throw 'W rollback mismatch'}
$totals=@{tests=0;failures=0;errors=0;skipped=0}
foreach($dir in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    $files=@(Get-ChildItem (Join-Path $root $dir) -Filter 'TEST-*.xml')
    if(-not $files.Count){throw "Missing validation: $dir"}
    foreach($file in $files){$suite=([xml](Get-Content $file.FullName -Raw)).testsuite;foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$suite.$key}}
}
if($totals.tests -ne 2203 -or $totals.failures -or $totals.errors -or $totals.skipped){throw ('Validation mismatch: '+($totals|ConvertTo-Json -Compress))}
New-Item -ItemType Directory -Force (Split-Path $candidate)|Out-Null
# Preserve every already-deployed asset byte, including owner icons. Replace only verified compiled classes.
Copy-Item $previous $candidate
$old=[IO.Compression.ZipFile]::OpenRead($previous)
$fresh=[IO.Compression.ZipFile]::OpenRead($built)
$out=[IO.Compression.ZipFile]::Open($candidate,[IO.Compression.ZipArchiveMode]::Update)
$changed=[Collections.Generic.List[string]]::new()
try{
    $oldClasses=@($old.Entries|Where-Object FullName -like '*.class'|ForEach-Object FullName)
    $newClasses=@($fresh.Entries|Where-Object FullName -like '*.class'|ForEach-Object FullName)
    $added='com/inigmasgames/hytalerpg/execution/hytale/NativeNpcAttitudes.class'
    if(Compare-Object $oldClasses @($newClasses|Where-Object {$_ -ne $added})){throw 'Unexpected added/removed class'}
    if($added -notin $newClasses){throw 'Missing native cache helper'}
    foreach($name in $newClasses){
        $bytes=Bytes ($fresh.GetEntry($name))
        if($old.GetEntry($name) -and (HashBytes $bytes) -eq (HashBytes (Bytes ($old.GetEntry($name))))){continue}
        if($name -notmatch '^com/inigmasgames/hytalerpg/execution/(ExecutionFailureDiagnostics|SkillExecutionService(?:\$(?:1|ChannelCooldown|PendingCommit|Prepared|Rejection))?|hytale/(?:HytaleAreaQueries|HytaleConversionSystem|HytaleSupportSystem|NativeNpcAttitudes))\.class$'){throw "Unrelated class changed: $name"}
        if($name -match 'SkillExecutionService\$'){
            # Added source lines shift debug line tables in these unchanged nested systems.
            $class=$name.Replace('/','.').Replace('.class','')
            $javap='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin/javap.exe'
            $oldCode=(& $javap -classpath $previous -c -p $class)|Out-String
            if($LASTEXITCODE){throw 'Rollback bytecode audit failed'}
            $newCode=(& $javap -classpath $built -c -p $class)|Out-String
            if($LASTEXITCODE -or $oldCode -cne $newCode){throw "Nested system bytecode changed: $name"}
        }
        $compiled=Join-Path $root ('build/classes/java/main/'+$name)
        if((Get-FileHash $compiled).Hash -ne (HashBytes $bytes)){throw "Compiled mismatch: $name"}
        if($out.GetEntry($name)){$out.GetEntry($name).Delete()};$entry=$out.CreateEntry($name);$stream=$entry.Open()
        try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
        $changed.Add($name)
    }
    if($changed.Count -ne 11){throw "Expected six implementation/helper classes plus five line-table-only classes, got $($changed.Count)"}
}finally{$old.Dispose();$fresh.Dispose();$out.Dispose()}
$old=[IO.Compression.ZipFile]::OpenRead($previous);$out=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
    if($old.Entries.Count+1 -ne $out.Entries.Count){throw 'Unexpected entry inventory change'}
    foreach($entry in $old.Entries){if($changed.Contains($entry.FullName)){continue};if((HashBytes (Bytes $entry)) -ne (HashBytes (Bytes ($out.GetEntry($entry.FullName))))){throw "Asset/unrelated entry changed: $($entry.FullName)"}}
}finally{$old.Dispose();$out.Dispose()}
foreach($name in @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')){Copy-Item (Join-Path (Split-Path $previous) $name) (Split-Path $candidate)}
$archive=Join-Path $evidence 'Hytale-RPG-Stage13-X-native-attitude-cache.zip'
[IO.Compression.ZipFile]::CreateFromDirectory((Split-Path $candidate),$archive)
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{if($zip.Entries.Count -ne 3){throw 'Archive must contain three RPG distribution mods'};foreach($entry in $zip.Entries){if((HashBytes (Bytes $entry)) -ne (Get-FileHash (Join-Path (Split-Path $candidate) $entry.FullName)).Hash){throw 'Archive hash mismatch'}}}finally{$zip.Dispose()}
$drill=Join-Path $evidence 'rollback-drill';New-Item -ItemType Directory $drill|Out-Null
$target=Join-Path $drill 'RPG.jar';$pending=Join-Path $drill 'pending.jar'
Copy-Item $previous $target;Copy-Item $candidate $pending;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $candidate).Hash){throw 'Roll-forward failed'}
Copy-Item $previous $pending;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $previous).Hash){throw 'Rollback failed'}
$result=[ordered]@{cohort='R032-X';tests=$totals;changedEntries=@($changed);allOtherEntriesByteIdenticalToW=$true;jarSha256=(Get-FileHash $candidate).Hash;archiveSha256=(Get-FileHash $archive).Hash;rollback='PASS';connectedClientVerified=$false}
$result|ConvertTo-Json -Depth 6|Set-Content (Join-Path $evidence 'package.json') -Encoding utf8
$result|ConvertTo-Json -Depth 6
