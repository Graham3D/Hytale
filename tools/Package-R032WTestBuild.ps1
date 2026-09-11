[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/..").Path
$evidence=Join-Path $root 'evidence/stage-13/cohort-w'
$previous=Join-Path $root 'evidence/stage-13/cohort-v/artifacts/HytaleRPG-0.0.25.jar'
$built=Join-Path $root 'build/libs/HytaleRPG-0.0.25.jar'
$candidate=Join-Path $evidence 'artifacts/HytaleRPG-0.0.25.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Bytes($entry){$inputStream=$entry.Open();$memory=[IO.MemoryStream]::new();try{$inputStream.CopyTo($memory);return ,$memory.ToArray()}finally{$inputStream.Dispose();$memory.Dispose()}}
function HashBytes([byte[]]$bytes){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))}
if(Test-Path $candidate){throw 'W artifact already exists; do not overwrite evidence'}
if((Get-FileHash $previous).Hash -ne '1103268939C69FA6B3D9E58DB2AE10F66A2412776AC00A43A9C13C64D24889FF'){throw 'V rollback mismatch'}
$totals=@{tests=0;failures=0;errors=0;skipped=0}
foreach($dir in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    $files=@(Get-ChildItem (Join-Path $root $dir) -Filter 'TEST-*.xml')
    if(-not $files.Count){throw "Missing validation: $dir"}
    foreach($file in $files){$suite=([xml](Get-Content $file.FullName -Raw)).testsuite;foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$suite.$key}}
}
if($totals.tests -ne 2197 -or $totals.failures -or $totals.errors -or $totals.skipped){throw ('Validation mismatch: '+($totals|ConvertTo-Json -Compress))}
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
    if(Compare-Object $oldClasses $newClasses){throw 'Unexpected added/removed class'}
    foreach($name in $newClasses){
        $bytes=Bytes ($fresh.GetEntry($name))
        if((HashBytes $bytes) -eq (HashBytes (Bytes ($old.GetEntry($name))))){continue}
        if($name -notmatch '^com/inigmasgames/hytalerpg/execution/hytale/(HytaleSupportSystem(?:\$(?:Absorb|Removal))?|HytaleSkillExecutionSystem\$Port\$1)\.class$'){throw "Unrelated class changed: $name"}
        if($name -match 'HytaleSupportSystem\$'){
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
        $out.GetEntry($name).Delete();$entry=$out.CreateEntry($name);$stream=$entry.Open()
        try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
        $changed.Add($name)
    }
    if($changed.Count -ne 4){throw "Expected two implementation classes plus two line-table-only classes, got $($changed.Count)"}
}finally{$old.Dispose();$fresh.Dispose();$out.Dispose()}
$old=[IO.Compression.ZipFile]::OpenRead($previous);$out=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
    if($old.Entries.Count -ne $out.Entries.Count){throw 'Entry inventory changed'}
    foreach($entry in $old.Entries){if($changed.Contains($entry.FullName)){continue};if((HashBytes (Bytes $entry)) -ne (HashBytes (Bytes ($out.GetEntry($entry.FullName))))){throw "Asset/unrelated entry changed: $($entry.FullName)"}}
}finally{$old.Dispose();$out.Dispose()}
foreach($name in @('CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')){Copy-Item (Join-Path (Split-Path $previous) $name) (Split-Path $candidate)}
$archive=Join-Path $evidence 'Hytale-RPG-Stage13-W-healing-npc.zip'
[IO.Compression.ZipFile]::CreateFromDirectory((Split-Path $candidate),$archive)
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{if($zip.Entries.Count -ne 3){throw 'Archive must contain three RPG distribution mods'};foreach($entry in $zip.Entries){if((HashBytes (Bytes $entry)) -ne (Get-FileHash (Join-Path (Split-Path $candidate) $entry.FullName)).Hash){throw 'Archive hash mismatch'}}}finally{$zip.Dispose()}
$drill=Join-Path $evidence 'rollback-drill';New-Item -ItemType Directory $drill|Out-Null
$target=Join-Path $drill 'RPG.jar';$pending=Join-Path $drill 'pending.jar'
Copy-Item $previous $target;Copy-Item $candidate $pending;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $candidate).Hash){throw 'Roll-forward failed'}
Copy-Item $previous $pending;[IO.File]::Replace($pending,$target,[NullString]::Value)
if((Get-FileHash $target).Hash -ne (Get-FileHash $previous).Hash){throw 'Rollback failed'}
$result=[ordered]@{cohort='R032-W';tests=$totals;changedEntries=@($changed);allOtherEntriesByteIdenticalToV=$true;jarSha256=(Get-FileHash $candidate).Hash;archiveSha256=(Get-FileHash $archive).Hash;rollback='PASS';connectedClientVerified=$false}
$result|ConvertTo-Json -Depth 6|Set-Content (Join-Path $evidence 'package.json') -Encoding utf8
$result|ConvertTo-Json -Depth 6
