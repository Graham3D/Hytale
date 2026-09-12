[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$evidence=Join-Path $repo 'evidence\stage-13\cohort-ac'
$artifacts=Join-Path $evidence 'artifacts';$validation=Join-Path $evidence 'validation'
$candidate=Join-Path $repo 'build\candidate-ac\HytaleRPG-0.0.25.jar'
New-Item -ItemType Directory -Force -Path $artifacts,$validation | Out-Null
$smoke=Get-Content -Raw (Join-Path $evidence 'server-smoke-summary.json')|ConvertFrom-Json
if($smoke.jarSha256 -ne (Get-FileHash $candidate).Hash -or !$smoke.cleanShutdown -or !$smoke.supportTetherAssetsResolved -or $smoke.failure){throw 'Exact AC candidate native smoke required'}
$text=Get-Content -Raw (Join-Path $evidence 'server-smoke.txt')
if($text -notmatch 'RPG_SUPPORT_TETHER_ASSETS cohort=AC .* beam=RPG_Healing texture=Void_Green persistent=true connectedProof=false') {throw 'Native healing Beam asset audit missing'}
$names=@('HytaleRPG-0.0.25.jar','CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')
foreach($name in $names){$source=if($name -eq $names[0]){$candidate}else{Join-Path $repo "evidence\stage-13\cohort-ab\artifacts\$name"};Copy-Item -LiteralPath $source -Destination (Join-Path $artifacts $name) -Force}
if(@(Get-ChildItem $artifacts -Filter '*.jar').Count -ne 3){throw 'Exactly three distribution mods required'}
Copy-Item -LiteralPath (Join-Path $repo 'build\candidate-ac\differential.json') -Destination $evidence -Force
$counts=@()
foreach($suite in @('test','nativeControlTest','canvas-ui')){
    $source=if($suite -eq 'canvas-ui'){Join-Path $repo 'canvas-ui\build\test-results\test'}else{Join-Path $repo "build\test-results\$suite"}
    $dest=Join-Path $validation $suite;New-Item -ItemType Directory -Path $dest -Force|Out-Null
    $count=0;$fail=0;$skip=0;$errors=0
    foreach($file in Get-ChildItem $source -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw $file.FullName;$count += [int]$xml.testsuite.tests;$fail += [int]$xml.testsuite.failures;$skip += [int]$xml.testsuite.skipped;$errors += [int]$xml.testsuite.errors
        Copy-Item -LiteralPath $file.FullName -Destination $dest -Force
    }
    $counts += [ordered]@{suite=$suite;tests=$count;failures=$fail;errors=$errors;skipped=$skip}
}
$rootResult=$counts|Where-Object suite -eq 'test';$otherFailures=@($counts|Where-Object {$_.suite -ne 'test' -and ($_.failures -or $_.errors -or $_.skipped)})
$knownTraceFailure=($rootResult.failures -eq 1 -and $rootResult.errors -eq 0 -and $rootResult.skipped -eq 0 -and
    (Test-Path (Join-Path $repo 'build\test-results\test\TEST-com.inigmasgames.hytalerpg.diagnostics.TraceArchiveFixtureRoundTripTest.xml')) -and
    (Get-Content -Raw (Join-Path $repo 'build\test-results\test\TEST-com.inigmasgames.hytalerpg.diagnostics.TraceArchiveFixtureRoundTripTest.xml')) -match 'tests="1" skipped="0" failures="1" errors="0"')
if(!$knownTraceFailure -or $otherFailures.Count){throw 'Unexpected retained validation failure'}
$archive=Join-Path $evidence 'HytaleRPG-R032-AC-three-mods.zip'
Compress-Archive -LiteralPath @($names|ForEach-Object {Join-Path $artifacts $_}) -DestinationPath $archive -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{if($zip.Entries.Count -ne 3){throw 'Archive contains other than three mods'};foreach($entry in $zip.Entries){$stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()};if($hash -ne (Get-FileHash (Join-Path $artifacts $entry.FullName)).Hash){throw 'Archive byte mismatch'}}}finally{$zip.Dispose()}
$baseline=Join-Path $repo 'evidence\stage-13\cohort-ab\artifacts\HytaleRPG-0.0.25.jar';$roundTrip=Join-Path $repo 'build\candidate-ac\rollback-check.jar'
Copy-Item $baseline $roundTrip -Force;if((Get-FileHash $roundTrip).Hash -ne (Get-FileHash $baseline).Hash){throw 'Rollback baseline mismatch'}
Copy-Item $candidate $roundTrip -Force;if((Get-FileHash $roundTrip).Hash -ne (Get-FileHash $candidate).Hash){throw 'Roll-forward mismatch'}
Copy-Item $baseline $roundTrip -Force;if((Get-FileHash $roundTrip).Hash -ne (Get-FileHash $baseline).Hash){throw 'Rollback restoration mismatch'}
$live=Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar';$liveHash=(Get-FileHash $live).Hash
[ordered]@{capturedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');implemented=$true;packaged=$true;deployed=$false;connectedVerified=$false;suites=$counts;retainedGate='KNOWN FAILURE: TraceArchiveFixtureRoundTripTest unchanged <=15% fixture compression assertion';artifacts=@($names|ForEach-Object {@{name=$_;sha256=(Get-FileHash (Join-Path $artifacts $_)).Hash;bytes=(Get-Item (Join-Path $artifacts $_)).Length}});archiveSha256=(Get-FileHash $archive).Hash;archiveExactlyThreeMods=$true;archiveBytesMatch=$true;rollbackBinaryRoundTrip='PASS';saveMigration='NOT REQUIRED / NOT PERFORMED';liveJarSha256=$liveHash;liveUnchanged=($liveHash -eq (Get-FileHash $baseline).Hash)} |
  ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'package-validation.json') -Encoding utf8
Get-Content (Join-Path $evidence 'package-validation.json')
