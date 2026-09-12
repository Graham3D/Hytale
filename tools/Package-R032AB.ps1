[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$evidence=Join-Path $repo 'evidence\stage-13\cohort-ab'
$artifacts=Join-Path $evidence 'artifacts'
$validation=Join-Path $evidence 'validation'
New-Item -ItemType Directory -Force -Path $artifacts,$validation | Out-Null
$candidate=Join-Path $repo 'build\candidate-ab\HytaleRPG-0.0.25.jar'
$smoke=Get-Content -Raw (Join-Path $evidence 'server-smoke-summary.json') | ConvertFrom-Json
if($smoke.jarSha256 -ne (Get-FileHash $candidate).Hash -or !$smoke.cleanShutdown -or $smoke.failure){throw 'Exact candidate native smoke required'}
$text=Get-Content -Raw (Join-Path $evidence 'server-smoke.txt')
if($text -notmatch 'RPG_BLIZZARD_NATIVE_INTEGRATION result=PASS .*cleaned=true connectedProof=false' -or $text -match 'Invalid entity reference!'){throw 'Native Blizzard gate failed'}
$names=@('HytaleRPG-0.0.25.jar','CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')
foreach($name in $names){
    $source=if($name -eq $names[0]){$candidate}else{Join-Path $repo "evidence\stage-13\cohort-aa\artifacts\$name"}
    Copy-Item -LiteralPath $source -Destination (Join-Path $artifacts $name) -Force
}
if(@(Get-ChildItem $artifacts -Filter '*.jar').Count -ne 3){throw 'Exactly three distribution mods required'}
Copy-Item -LiteralPath (Join-Path $repo 'build\candidate-ab\differential.json') -Destination $evidence -Force
$counts=@()
foreach($suite in @('test','nativeControlTest','canvas-ui')){
    $source=if($suite -eq 'canvas-ui'){Join-Path $repo 'canvas-ui\build\test-results\test'}else{Join-Path $repo "build\test-results\$suite"}
    $dest=Join-Path $validation $suite
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    $count=0;$fail=0;$skip=0;$errors=0
    foreach($file in Get-ChildItem $source -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw $file.FullName
        $count += [int]$xml.testsuite.tests;$fail += [int]$xml.testsuite.failures
        $skip += [int]$xml.testsuite.skipped;$errors += [int]$xml.testsuite.errors
        Copy-Item -LiteralPath $file.FullName -Destination $dest -Force
    }
    $counts += [ordered]@{suite=$suite;tests=$count;failures=$fail;errors=$errors;skipped=$skip}
}
# Read-only fixture diagnostic. Do not rewrite live traces to improve a test ratio.
$ratios=@()
foreach($name in @('skill-trace.jsonl','ui-trace.jsonl')){
    $source=Join-Path $env:APPDATA "Hytale\data\pre-release\Saves\RPG\mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\$name"
    $bytes=[IO.File]::ReadAllBytes($source);$memory=[IO.MemoryStream]::new()
    $gzip=[IO.Compression.GZipStream]::new($memory,[IO.Compression.CompressionLevel]::Optimal,$true)
    $gzip.Write($bytes);$gzip.Dispose()
    $ratios += [ordered]@{file=$name;bytes=$bytes.Length;diagnosticGzipBytes=$memory.Length;fraction=$memory.Length/[double]$bytes.Length;engine='DotNet diagnostic, not a replacement Java assertion'}
    $memory.Dispose()
}
$archive=Join-Path $evidence 'HytaleRPG-R032-AB-three-mods.zip'
Compress-Archive -LiteralPath @($names|ForEach-Object {Join-Path $artifacts $_}) -DestinationPath $archive -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{
    if($zip.Entries.Count -ne 3){throw 'Archive contains other than three mods'}
    foreach($entry in $zip.Entries){
        if($entry.FullName -notin $names){throw 'Unexpected archive entry'}
        $stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
        if($hash -ne (Get-FileHash (Join-Path $artifacts $entry.FullName)).Hash){throw 'Archive byte mismatch'}
    }
}finally{$zip.Dispose()}
# Isolated binary rollback drill only; never touches a live save or mod path.
$rollback=Join-Path $repo 'build\candidate-ab\rollback-check.jar'
$baseline=Join-Path $repo 'evidence\stage-13\cohort-aa\artifacts\HytaleRPG-0.0.25.jar'
Copy-Item $baseline $rollback -Force
if((Get-FileHash $rollback).Hash -ne (Get-FileHash $baseline).Hash){throw 'Rollback baseline mismatch'}
Copy-Item $candidate $rollback -Force
if((Get-FileHash $rollback).Hash -ne (Get-FileHash $candidate).Hash){throw 'Candidate replacement mismatch'}
Copy-Item $baseline $rollback -Force
if((Get-FileHash $rollback).Hash -ne (Get-FileHash $baseline).Hash){throw 'Rollback restoration mismatch'}
$live=Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar'
if((Get-FileHash $live).Hash -ne (Get-FileHash $baseline).Hash){throw 'Live build changed; investigate rather than overwrite'}
[ordered]@{
    capturedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');implemented=$true;packaged=$true;deployed=$false;connectedVerified=$false
    suites=$counts;traceFixtureDiagnostic=$ratios;fullRetainedPass=(@($counts|Where-Object {$_.failures -or $_.errors}).Count -eq 0)
    artifacts=@($names|ForEach-Object {@{name=$_;sha256=(Get-FileHash (Join-Path $artifacts $_)).Hash}})
    archiveSha256=(Get-FileHash $archive).Hash;archiveExactlyThreeMods=$true;archiveBytesMatch=$true
    rollbackBinaryRoundTrip='PASS';rollbackSaveMigration='NOT REQUIRED / NO SAVE FORMAT CHANGE';liveJarSha256=(Get-FileHash $live).Hash;liveUnchanged=$true
    knownFailedGate='TraceArchiveFixtureRoundTripTest: unchanged <=15% compression assertion against current live fixtures'
} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'package-validation.json') -Encoding utf8
Get-Content (Join-Path $evidence 'package-validation.json')
