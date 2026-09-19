[CmdletBinding()]
param([switch]$Deploy)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo=(Resolve-Path "$PSScriptRoot/..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-ar'
$candidate=Join-Path $repo 'build/libs/HyARPG.jar'
$baseline=Join-Path $repo 'evidence/stage-13/cohort-aq/artifacts/HyARPG.jar'
$save=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG'
$mods=Join-Path $save 'mods'
$live=Join-Path $mods 'HyARPG.jar'
function Hash($p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
function EntryHash($e){$s=$e.Open();$h=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($h.ComputeHash($s))).Replace('-','')}finally{$s.Dispose();$h.Dispose()}}
function AssertStopped {
    $running=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^Hytale(Client|Server)?(?:\.exe)?$' -or ($_.Name -match '^javaw?(?:\.exe)?$' -and $_.CommandLine -match 'HytaleServer')})
    if($running.Count){throw 'Close Hytale and its server completely before backup/deployment.'}
}
if((& git -C $repo branch --show-current).Trim() -ne 'RPG'){throw 'RPG branch required'}
if((Hash $baseline) -ne '43DA05C1031226EE34F0F8B34EAC2FEA255ACDFA34CCD072DEE703D854B9EC09'){throw 'AQ rollback artifact mismatch'}
$smoke=Get-Content (Join-Path $out 'server-smoke-summary.json') -Raw | ConvertFrom-Json
$log=Get-Content (Join-Path $out 'server-smoke.txt') -Raw
if($smoke.processExitCode -ne 0 -or $smoke.failure -or -not $smoke.exactlyThreeMods -or $smoke.jarSha256 -ne (Hash $candidate)){throw 'Exact candidate native smoke required'}
foreach($gate in @('HYTALE_RPG_SETUP revision=R032-AR','RPG_MANAGED_FIRE_BINDING result=PASS','RPG_MANTLE_NATIVE_EFFECTS result=PASS','RPG_MANTLE_AR_PRESENTATION result=PASS','RPG_NATIVE_SPAWN_INTEGRATION result=PASS','RPG_HEAL_POLISH_ASSETS revision=R032-AP result=PASS','RPG_HEAL_AUDIO_NATIVE revision=R032-AP result=PASS')){if(-not $log.Contains($gate)){throw "Missing native gate: $gate"}}
if((Get-Content (Join-Path $out 'full-validation-final.txt') -Raw) -notmatch 'BUILD SUCCESSFUL'){throw 'Final complete retained run required'}
$suites=@()
foreach($suite in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    $files=@(Get-ChildItem (Join-Path $repo $suite) -Filter 'TEST-*.xml')
    if(-not $files.Count){throw "Missing retained suite: $suite"}
    $tests=0;$failures=0;$errors=0;$skipped=0
    foreach($f in $files){$x=[xml](Get-Content $f.FullName -Raw);$tests+=[int]$x.testsuite.tests;$failures+=[int]$x.testsuite.failures;$errors+=[int]$x.testsuite.errors;$skipped+=[int]$x.testsuite.skipped}
    if($failures -or $errors -or $skipped){throw "Nonpassing retained suite: $suite"}
    $dest=Join-Path $out ('validation/'+$suite.Replace('/','-'));New-Item -ItemType Directory -Force $dest|Out-Null
    $files|Copy-Item -Destination $dest
    $suites+=@{suite=$suite;tests=$tests;failures=$failures;errors=$errors;skipped=$skipped}
}
$old=[IO.Compression.ZipFile]::OpenRead($baseline);$new=[IO.Compression.ZipFile]::OpenRead($candidate)
try{
    $oldEntries=@{};foreach($e in $old.Entries){if($e.Name){$oldEntries[$e.FullName]=EntryHash $e}}
    $newEntries=@{};foreach($e in $new.Entries){if($e.Name){if($newEntries.ContainsKey($e.FullName)){throw 'Duplicate JAR entry'};$newEntries[$e.FullName]=EntryHash $e}}
    $removed=@($oldEntries.Keys|Where-Object {-not $newEntries.ContainsKey($_)})
    if($removed.Count){throw "Unexpected removal from cumulative AQ build: $removed"}
    # These owners are explicitly out of scope; require identical packaged bytes, not merely source claims.
    foreach($path in $oldEntries.Keys){
        if($path -match '^com/inigmasgames/hytalerpg/(input/|execution/hytale/(Healing|SplineHealing)|execution/projectile/)' -or
           $path -match '^Server/(Particles/RPG/RPG_Mantle_(Aura|Pulse)\.particlesystem|Entity/Effects/RPG/RPG_Mantle_(Pulse|Flash)\.json|Item/Items/Weapon/|Languages/)' -or
           $path -match '^com/inigmasgames/hytalerpg/(combat/hytale/(NativeWeaponFireProducer|ManagedWeaponFireInteraction)|execution/support/|combat/(resource/|cooldown/))' -or
           $path -eq 'rpg/runtime/mantle-of-flame-v1.json' -or $path -eq 'rpg/runtime/managed-weapon-fire-v1.json' -or
           $path -match '^Server/Particles/RPG/HealingRed/' -or $path -match '^Common/UI/Custom/Phase00ResourceHud' -or
           $path -match '^com/inigmasgames/hytalerpg/progress/(FileEncounter|PersistentEncounter|NativePersistence|ShieldEscrow)'){
            if($oldEntries[$path] -ne $newEntries[$path]){throw "Unrelated owner changed: $path"}
        }
    }
    $diff=@($newEntries.Keys|Sort-Object|Where-Object {-not $oldEntries.ContainsKey($_) -or $oldEntries[$_] -ne $newEntries[$_]}|ForEach-Object {@{entry=$_;before=$oldEntries[$_];after=$newEntries[$_]}})
    $r=[IO.StreamReader]::new($new.GetEntry('rpg-build.properties').Open());try{if($r.ReadToEnd() -notmatch 'rpg.revision=R032-AR'){throw 'Candidate identity is not AR'}}finally{$r.Dispose()}
    $r=[IO.StreamReader]::new($new.GetEntry('manifest.json').Open());try{$manifest=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
    if($manifest.Name -ne 'HytaleRPGPhase00Audit' -or $manifest.Group -ne 'InigmasGames' -or $manifest.ServerVersion -ne '=0.7.0-pre.2'){throw 'Internal mod identity/pin changed'}
}finally{$old.Dispose();$new.Dispose()}
$artifacts=Join-Path $out 'artifacts';New-Item -ItemType Directory -Force $artifacts|Out-Null
foreach($source in @($candidate,(Join-Path $mods 'CanvasUI-0.1.0.jar'),(Join-Path $mods 'HYTALEDEVLIB-0.5.0.jar'))){
    $dest=Join-Path $artifacts ([IO.Path]::GetFileName($source))
    if((Test-Path $dest) -and (Hash $dest) -ne (Hash $source)){throw 'Never overwrite a different archived candidate'}
    Copy-Item -LiteralPath $source -Destination $dest
}
$hashes=[ordered]@{};Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object {$hashes[$_.Name]=Hash $_.FullName}
if($hashes.Count -ne 3){throw 'Exactly three archived mods required'}
$zip=Join-Path $out 'HyARPG-R032-AR-three-mods.zip'
if(-not (Test-Path $zip)){Compress-Archive -LiteralPath @(Get-ChildItem $artifacts -Filter '*.jar'|ForEach-Object FullName) -DestinationPath $zip -CompressionLevel Optimal}
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{if($archive.Entries.Count -ne 3){throw 'Archive count mismatch'};foreach($e in $archive.Entries){if(-not $hashes.Contains($e.FullName) -or (EntryHash $e) -ne $hashes[$e.FullName]){throw 'Archive entry hash mismatch'}}}finally{$archive.Dispose()}
$rollbackTest=Join-Path $out 'before/binary-rollback';New-Item -ItemType Directory -Force $rollbackTest|Out-Null
$probe=Join-Path $rollbackTest 'probe.jar'
foreach($source in @($baseline,$candidate,$baseline)){Copy-Item -LiteralPath $source -Destination $probe -Force;if((Hash $probe) -ne (Hash $source)){throw 'Binary rollback failed'}}
$record=[ordered]@{status='IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION';coverage='LIMITED_FLAME_LONGSWORD_PRIMARY';startingHead=(& git -C $repo rev-parse HEAD).Trim();revision='R032-AR';capturedAtUtc=[DateTime]::UtcNow.ToString('o');suites=$suites;totalTests=($suites|Measure-Object tests -Sum).Sum;jarHashes=$hashes;archiveSha256=(Hash $zip);rollbackArtifact=$baseline;rollbackSha256=(Hash $baseline);binaryRollback='AQ -> AR -> AQ PASS';entryDifferences=$diff;removedEntries=$removed;connectedVerified=$false;deployed=$false;pushed=$false}
if($Deploy){
    AssertStopped
    $active=@(Get-ChildItem $mods -Filter '*.jar'|Where-Object {$_.Name -match '^(HyARPG|HytaleRPG[^/]*|InigmasGames_HytaleRPGPhase00Audit[^/]*)\.jar$'})
    if($active.Count -ne 1 -or $active[0].FullName -ne $live){throw 'Expected exactly one active HyARPG.jar'}
    $beforeHash=Hash $live
    $otherHashes=@{};Get-ChildItem $mods -Filter '*.jar'|Where-Object FullName -ne $live|ForEach-Object {$otherHashes[$_.FullName]=Hash $_.FullName}
    $backupRoot=Join-Path $out ('before/save/'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    New-Item -ItemType Directory -Path $backupRoot|Out-Null
    Copy-Item -LiteralPath $save -Destination $backupRoot -Recurse
    $backup=Join-Path $backupRoot 'RPG'
    $inventory=@(Get-ChildItem $save -Recurse -File|ForEach-Object {
        $relative=[IO.Path]::GetRelativePath($save,$_.FullName);$hash=Hash $_.FullName
        if((Hash (Join-Path $backup $relative)) -ne $hash){throw "Backup mismatch: $relative"}
        @{path=$relative;sha256=$hash;bytes=$_.Length}
    })
    $inventory|ConvertTo-Json -Depth 5|Set-Content (Join-Path $backupRoot 'backup-inventory.json') -Encoding utf8
    AssertStopped
    if((Hash $live) -ne $beforeHash){throw 'Live binary changed during backup'}
    $pending=Join-Path $mods 'HyARPG.ar.pending'
    if(Test-Path $pending){throw 'Unexpected pending deployment; inspect before proceeding'}
    Copy-Item -LiteralPath $candidate -Destination $pending
    if((Hash $pending) -ne $hashes['HyARPG.jar']){throw 'Pending copy differs'}
    [IO.File]::Replace($pending,$live,(Join-Path $backupRoot 'replaced-live.jar'))
    if((Hash $live) -ne $hashes['HyARPG.jar']){throw 'DEPLOYED HASH MISMATCH'}
    foreach($path in $otherHashes.Keys){if((Hash $path) -ne $otherHashes[$path]){throw "Supporting mod changed: $path"}}
    $record.deployed=$true;$record['deploymentPath']=$live;$record['deployedSha256']=Hash $live
    $record['previousLiveSha256']=$beforeHash;$record['saveBackup']=$backup;$record['backupFileCount']=$inventory.Count
}
$record|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'package-validation.json') -Encoding utf8
[pscustomobject]$record|Select-Object revision,totalTests,deployed,deployedSha256,deploymentPath,saveBackup,archiveSha256
