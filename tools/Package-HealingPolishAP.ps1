[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-ap'
$candidate=Join-Path $repo 'build/libs/HyARPG.jar'
$baseline=Join-Path $repo 'evidence/stage-13/cohort-ao/artifacts/HyARPG.jar'
$liveMods=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods'
if((& git -C $repo branch --show-current).Trim() -ne 'RPG'){throw 'Expected RPG branch'}
$smoke=Get-Content (Join-Path $out 'server-smoke-summary.json') -Raw | ConvertFrom-Json
$nativeLog=Get-Content (Join-Path $out 'server-smoke.txt') -Raw
if($nativeLog -notmatch 'RPG_MANA_REPLICATION_NATIVE result=PASS .* oneCharge=true nativeViewerQueued=true connectedProof=false' -or $nativeLog -notmatch 'RPG_HEAL_STAFF_NATIVE result=PASS channelOnly=true removed=true'){throw 'AH native gates missing'}
if($nativeLog -notmatch 'RPG_HEAL_PROBE revision=R032-AP enabled=true .*liveTest=true disposableWorldRequired=false connectedProof=false'){throw 'Live probe automatic registration missing'}
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
        if($e.FullName -notmatch '^(com/inigmasgames/hytalerpg/(execution/hytale/(HealingPresentationProbe|HealingProbePolicy|HealingTetherPresentation|HealingWorldParticleFrame|SplineHealingParticleVisuals|HealingParticleVisuals|HealingChannelAudio|HealingHelix|HytaleSkillExecutionSystem)|ui/hud/RpgHud|input/(NativeHealingParticlePathAudit|NativeHealingPolishAudit|NativeSupportTetherAudit)|commands/HealingProbeCommand|phase00/Phase00Plugin)[^/]*\.class|healing-probe-live-test\.txt|Server/Particles/RPG/HealingRed/RPG_Heal_Red_[A-Za-z0-9_]+\.(?:particlespawner|particlesystem)|Server/Entity/Effects/RPG/RPG_Healing_(?:Recipient|Staff_(?:Block5|Knob|Origin_Projectile|TopPommel)|Audio_[0-7])\.json)$'){throw "Unscoped JAR change: $($e.FullName)"}
        @{entry=$e.FullName;before=$oldEntries[$e.FullName];after=$h}
    };$oldEntries.Remove($e.FullName)}})
    if($oldEntries.Count){throw 'No AO archive entries may be removed'}
    $marker=$new.GetEntry('healing-probe-live-test.txt');if(-not $marker){throw 'Live-test opt-in marker missing'}
    $mr=[IO.StreamReader]::new($marker.Open());try{if($mr.ReadToEnd().Trim() -ne 'R032-AP'){throw 'Invalid live-test marker'}}finally{$mr.Dispose()}
    $r=[IO.StreamReader]::new($new.GetEntry('manifest.json').Open());try{$manifest=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
    if($manifest.Name -ne 'HytaleRPGPhase00Audit' -or $manifest.Group -ne 'InigmasGames' -or $manifest.ServerVersion -ne '=0.7.0-pre.2'){throw 'Manifest identity/version mismatch'}
}finally{$old.Dispose();$new.Dispose()}
# Inlined presentation constants change this otherwise unchanged execution class.
$javap='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin/javap.exe'
$oldCode=@(& $javap -p -c -classpath $baseline 'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port$1') -join "`n"
$newCode=@(& $javap -p -c -classpath $candidate 'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port$1') -join "`n"
if($newCode.Replace('R032-AP','R032-AO').Replace('RPG_Heal_Red_Blips+RPG_Heal_Red_Pulse','RPG_Heal_World_Blips+RPG_Heal_World_Pulse') -ne $oldCode){throw 'Execution bytecode changed beyond inlined presentation constants'}
$artifacts=Join-Path $out 'artifacts';New-Item -ItemType Directory -Force $artifacts|Out-Null
foreach($source in @($candidate,(Join-Path $liveMods 'CanvasUI-0.1.0.jar'),(Join-Path $liveMods 'HYTALEDEVLIB-0.5.0.jar'))){
    $dest=Join-Path $artifacts ([IO.Path]::GetFileName($source))
    if((Test-Path $dest) -and (Get-FileHash $dest).Hash -ne (Get-FileHash $source).Hash){throw 'Do not replace archived candidate'}
    Copy-Item -LiteralPath $source -Destination $dest
}
$hashes=[ordered]@{};Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object {$hashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
if($hashes.Count -ne 3){throw 'Expected exactly three mods'}
$zip=Join-Path $out 'HyARPG-R032-AP-three-mods.zip'
if(-not(Test-Path $zip)){Compress-Archive -LiteralPath @(Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object FullName) -DestinationPath $zip -CompressionLevel Optimal}
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{if($archive.Entries.Count -ne 3){throw 'Invalid archive count'};foreach($e in $archive.Entries){if(-not $hashes.Contains($e.FullName) -or (EntryHash $e) -ne $hashes[$e.FullName]){throw 'Archive entry mismatch'}}}finally{$archive.Dispose()}
$rollback=Join-Path $out 'before/save/binary-rollback';New-Item -ItemType Directory -Force $rollback|Out-Null
$probe=Join-Path $rollback 'probe.jar'
foreach($source in @($baseline,$candidate,$baseline)){Copy-Item $source $probe -Force;if((Get-FileHash $probe).Hash -ne (Get-FileHash $source).Hash){throw 'Binary rollback validation failed'}}
[ordered]@{cohort='AP';startingHead=(& git -C $repo rev-parse HEAD).Trim();builtAtUtc=[DateTime]::UtcNow.ToString('o');
    suites=$suites;fullValidationRuns=1;fullValidationExitCode=1;initialFullRunFailures=1;
    correctedFocusedSuite='Stage13HealingProbeAITest: six tests PASS; old probe asset contract explicitly distinguished from production recolor';
    resultProvenance='Full retained XML except corrected six-test AI class replaced by its focused rerun; all originals retained in iterations/full-rpg-results';
    finalReviewCorrection='Red single-strand helix over unchanged world-particle spline; recipient Heal derivative and owned native loop';
    isolatedNativePacketLifecycle='PASS';visibilityGate='A_CONNECTED_UNVERIFIED';laterGates='CONNECTED_APPEARANCE_FLOW_CONTINUITY_CLEANUP_UNVERIFIED';connectedVerified=$false;
    jarHashes=$hashes;archiveSha256=(Get-FileHash $zip).Hash;archiveEntriesVerified=$true;binaryRollback='AO -> AP -> AO PASS';
    removedEntries=@();unchangedEntriesPreserved=$true;entryDifferences=$diff;internalIdentityPreserved=$true;liveDeploymentPerformed=$false
}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'package-validation.json') -Encoding utf8
if(($suites|Measure-Object tests -Sum).Sum -ne 2292){throw 'Expected all 2292 retained-plus-new tests; do not package a partial suite'}
if((Get-FileHash (Join-Path $liveMods 'HyARPG.jar')).Hash -ne '0586FB98DA3349584632FAF062C07644371C51C29E51B00EFA9E7E7E14457B36'){throw 'Live AO JAR changed during packaging-only task'}
$hashes;Get-FileHash $zip
if($nativeLog -notmatch 'RPG_HEAL_CHANNEL_OBSERVER result=PASS productionHooks=true nativeEffectsUnchanged=true observerStopDoesNotStopChannel=true nativeCleanup=true connectedProof=false'){throw 'Native production observer gate missing'}
if($nativeLog -notmatch 'RPG_HEAL_WORLD_PARTICLE_NATIVE revision=R032-AP result=PASS' -or $nativeLog -notmatch 'RPG_HEAL_PARTICLE_PATH_ASSETS revision=R032-AM result=PASS'){throw 'AM production particle derivative and lifecycle audit missing'}
if($nativeLog -notmatch 'RPG_HEAL_WORLD_PARTICLE_ASSETS revision=R032-AO result=PASS appearanceExact=true immediateBurst=true finiteSeconds=0.18 zeroVelocity=true connectedProof=false'){throw 'AO finite world particle gate missing'}


if($nativeLog -notmatch 'RPG_HEAL_POLISH_ASSETS revision=R032-AP result=PASS' -or $nativeLog -notmatch 'RPG_HEAL_AUDIO_NATIVE revision=R032-AP result=PASS'){throw 'AP native polish/audio gates missing'}
