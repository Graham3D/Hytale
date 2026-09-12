[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-aj-live'
$candidate=Join-Path $repo 'build/libs/HyARPG.jar'
$baseline=Join-Path $repo 'evidence/stage-13/cohort-aj/artifacts/HyARPG.jar'
$liveMods=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods'
if((& git -C $repo branch --show-current).Trim() -ne 'RPG'){throw 'Expected RPG branch'}
$smoke=Get-Content (Join-Path $out 'server-smoke-summary.json') -Raw | ConvertFrom-Json
$nativeLog=Get-Content (Join-Path $out 'server-smoke.txt') -Raw
if($nativeLog -notmatch 'RPG_MANA_REPLICATION_NATIVE result=PASS .* oneCharge=true nativeViewerQueued=true connectedProof=false' -or $nativeLog -notmatch 'RPG_HEAL_STAFF_NATIVE result=PASS channelOnly=true removed=true'){throw 'AH native gates missing'}
if($nativeLog -notmatch 'RPG_HEAL_PROBE revision=R032-AJ enabled=true .*liveTest=true disposableWorldRequired=false connectedProof=false'){throw 'Live probe automatic registration missing'}
if($smoke.processExitCode -ne 0 -or $smoke.failure -or -not $smoke.exactlyThreeMods -or $smoke.jarSha256 -ne (Get-FileHash $candidate).Hash){throw 'Candidate must match successful smoke'}
$suites=@()
foreach($suite in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    $target=Join-Path $out ('validation/'+$suite.Replace('/','-'))
    $suiteSource=Join-Path $repo $suite
    $files=@(Get-ChildItem $suiteSource -Filter 'TEST-*.xml')
    if(-not $files.Count){throw "Missing suite $suite"}
    $tests=0;$failures=0;$errors=0;$skipped=0
    foreach($file in $files){$xml=[xml](Get-Content $file.FullName -Raw);$tests+=[int]$xml.testsuite.tests;$failures+=[int]$xml.testsuite.failures;$errors+=[int]$xml.testsuite.errors;$skipped+=[int]$xml.testsuite.skipped}
    if($failures -or $errors -or $skipped){throw "Retained suite not clean: $suite"}
    if(-not(Test-Path $target)){New-Item -ItemType Directory -Force $target|Out-Null;$files|Copy-Item -Destination $target}
    $suites+=@{suite=$suite;tests=$tests;failures=$failures;errors=$errors;skipped=$skipped}
}
Copy-Item (Join-Path $repo 'build/reports/trace-archive-fixtures.json') $out
function EntryHash($entry){$s=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($sha.ComputeHash($s))).Replace('-','')}finally{$s.Dispose();$sha.Dispose()}}
$old=[IO.Compression.ZipFile]::OpenRead($baseline);$new=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
    $oldEntries=@{};foreach($e in $old.Entries){if($e.Name){$oldEntries[$e.FullName]=EntryHash $e}}
    $diff=@(foreach($e in $new.Entries){if($e.Name){$h=EntryHash $e;if(-not $oldEntries.ContainsKey($e.FullName) -or $h -ne $oldEntries[$e.FullName]){
        if($e.FullName -notmatch '^(com/inigmasgames/hytalerpg/(execution/hytale/(HealingPresentationProbe|HealingProbePolicy)|commands/HealingProbeCommand|phase00/Phase00Plugin)[^/]*\.class|healing-probe-live-test\.txt)$'){throw "Unscoped JAR change: $($e.FullName)"}
        @{entry=$e.FullName;before=$oldEntries[$e.FullName];after=$h}
    };$oldEntries.Remove($e.FullName)}})
    if($oldEntries.Count){throw 'Unexpected removed JAR entries'}
    $marker=$new.GetEntry('healing-probe-live-test.txt');if(-not $marker){throw 'Live-test opt-in marker missing'}
    $mr=[IO.StreamReader]::new($marker.Open());try{if($mr.ReadToEnd().Trim() -ne 'R032-AJ-LIVE'){throw 'Invalid live-test marker'}}finally{$mr.Dispose()}
    $r=[IO.StreamReader]::new($new.GetEntry('manifest.json').Open());try{$manifest=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
    if($manifest.Name -ne 'HytaleRPGPhase00Audit' -or $manifest.Group -ne 'InigmasGames' -or $manifest.ServerVersion -ne '=0.7.0-pre.2'){throw 'Manifest identity/version mismatch'}
}finally{$old.Dispose();$new.Dispose()}
$artifacts=Join-Path $out 'artifacts';New-Item -ItemType Directory -Force $artifacts|Out-Null
foreach($source in @($candidate,(Join-Path $liveMods 'CanvasUI-0.1.0.jar'),(Join-Path $liveMods 'HYTALEDEVLIB-0.5.0.jar'))){
    $dest=Join-Path $artifacts ([IO.Path]::GetFileName($source))
    if((Test-Path $dest) -and (Get-FileHash $dest).Hash -ne (Get-FileHash $source).Hash){throw 'Do not replace archived candidate'}
    Copy-Item -LiteralPath $source -Destination $dest
}
$hashes=[ordered]@{};Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object {$hashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
if($hashes.Count -ne 3){throw 'Expected exactly three mods'}
$zip=Join-Path $out 'HyARPG-R032-AJ-LIVE-three-mods.zip'
if(-not(Test-Path $zip)){Compress-Archive -LiteralPath @(Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object FullName) -DestinationPath $zip -CompressionLevel Optimal}
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{if($archive.Entries.Count -ne 3){throw 'Invalid archive count'};foreach($e in $archive.Entries){if(-not $hashes.Contains($e.FullName) -or (EntryHash $e) -ne $hashes[$e.FullName]){throw 'Archive entry mismatch'}}}finally{$archive.Dispose()}
$rollback=Join-Path $out 'before/save/binary-rollback';New-Item -ItemType Directory -Force $rollback|Out-Null
$probe=Join-Path $rollback 'probe.jar'
foreach($source in @($baseline,$candidate,$baseline)){Copy-Item $source $probe -Force;if((Get-FileHash $probe).Hash -ne (Get-FileHash $source).Hash){throw 'Binary rollback validation failed'}}
[ordered]@{cohort='AJ-LIVE';startingHead=(& git -C $repo rev-parse HEAD).Trim();builtAtUtc=[DateTime]::UtcNow.ToString('o');
    suites=$suites;fullValidationRuns=1;fullValidationExitCode=0;initialFullRunFailures=0;
    isolatedNativeParticleLifecycle='PASS';visibilityGate='A_CONNECTED_UNVERIFIED';laterGates='B_C_D_E_DEFERRED';connectedVerified=$false;
    jarHashes=$hashes;archiveSha256=(Get-FileHash $zip).Hash;archiveEntriesVerified=$true;binaryRollback='AJ -> AJ-LIVE -> AJ PASS';
    unchangedEntriesPreserved=$true;entryDifferences=$diff;internalIdentityPreserved=$true;liveDeploymentPerformed=$false
}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'package-validation.json') -Encoding utf8
if(($suites|Measure-Object tests -Sum).Sum -ne 2258){throw 'Expected all 2258 retained-plus-new tests; do not package a partial suite'}
if((Get-FileHash (Join-Path $liveMods 'HyARPG.jar')).Hash -ne '272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692'){throw 'Live AH JAR changed during packaging-only task'}
$hashes;Get-FileHash $zip
