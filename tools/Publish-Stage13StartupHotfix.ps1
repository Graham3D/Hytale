[CmdletBinding()]
param([switch]$Deploy)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence\stage-13\cohort-l'
$mods=(Resolve-Path 'C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods').Path
$oldHash='9083F89CB224BAC55B5D1B1CB9F5F5BA90C104D23A55B447A8E8029B4156B06F'
$names=@('HytaleRPG-0.0.25.jar','CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')
$suites=@(foreach($path in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $root $path) -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $s=$xml.testsuite
        [pscustomobject]@{name=$s.name;tests=[int]$s.tests;failures=[int]$s.failures;errors=[int]$s.errors;skipped=[int]$s.skipped;cases=@($s.testcase|ForEach-Object {$_.name})}
    }
})
if(($suites|Measure-Object tests -Sum).Sum -ne 2093 -or @($suites|Where-Object {$_.failures -or $_.errors -or $_.skipped}).Count){throw 'Expected all 2093 tests successful, none skipped'}
foreach($baseline in (Get-Content -Raw (Join-Path $root 'evidence/stage-13/cohort-k/test-results.json')|ConvertFrom-Json)){
    $current=@($suites|Where-Object name -eq $baseline.name)
    if($current.Count -ne 1){throw "Missing retained suite $($baseline.name)"}
    foreach($case in $baseline.cases){if($case -notin $current[0].cases){throw "Missing retained case $case"}}
}
$suites|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $out 'test-results.json') -Encoding utf8
$smoke=Get-Content -Raw (Join-Path $out 'server-smoke-summary.json')|ConvertFrom-Json
$jar=Join-Path $root 'build/libs/HytaleRPG-0.0.25.jar'
$hash=(Get-FileHash -LiteralPath $jar).Hash
if($smoke.jarSha256 -ne $hash -or $smoke.processExitCode -ne 0 -or -not $smoke.exactlyThreeMods -or -not $smoke.networkBooted -or -not $smoke.cleanShutdown -or $smoke.failure){throw 'Exact candidate smoke required'}
$artifacts=Join-Path $out 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts|Out-Null
$expected=[ordered]@{
    'HytaleRPG-0.0.25.jar'=$hash
    'CanvasUI-0.1.0.jar'='218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6'
    'HYTALEDEVLIB-0.5.0.jar'='DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230'
}
foreach($name in $names){
    $source=if($name -eq $names[0]){$jar}else{Join-Path $mods $name}
    if((Get-FileHash -LiteralPath $source).Hash -ne $expected[$name]){throw "Unexpected artifact $name"}
    $destination=Join-Path $artifacts $name
    if((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination).Hash -ne $expected[$name]){throw 'Do not overwrite different archived build'}
    Copy-Item -LiteralPath $source -Destination $destination
}
$zip=Join-Path $out 'Hytale-RPG-Stage13-L-startup-hotfix.zip'
if(-not (Test-Path -LiteralPath $zip)){Compress-Archive -LiteralPath @($names|ForEach-Object {Join-Path $artifacts $_}) -DestinationPath $zip -CompressionLevel Optimal}
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{
    if($archive.Entries.Count -ne 3){throw 'Archive must contain exactly three mods'}
    foreach($entry in $archive.Entries){
        if(-not $expected.Contains($entry.FullName)){throw 'Unexpected ZIP entry'}
        $stream=$entry.Open();try{$entryHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
        if($entryHash -ne $expected[$entry.FullName]){throw 'ZIP entry integrity failure'}
    }
}finally{$archive.Dispose()}
$result=[ordered]@{cohort='L';revision='R032';version='0.0.25';baselineCommit='84e29cb57175d9b6ade33654f38c77305ff5c91d';
    tests=2093;failures=0;errors=0;skipped=0;retainedKCaseIdentities='PASS';threeModSmoke='PASS';archiveHashes='PASS';
    jarHashes=$expected;zipSha256=(Get-FileHash -LiteralPath $zip).Hash;connectedClientVerified=$false;deploymentPerformed=$false}
if($Deploy){
    if(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^(java|javaw|HytaleServer).*' -and $_.CommandLine -match 'HytaleServer'}){throw 'Stop the Hytale server before deployment'}
    if(@(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File).Count -ne 3){throw 'Unexpected installed mod inventory'}
    $target=Join-Path $mods $names[0]
    $installedHash=(Get-FileHash -LiteralPath $target).Hash
    if($installedHash -notin @($oldHash,$hash)){throw 'Installed RPG changed since crash audit; inspect before overwrite'}
    $staged=Join-Path $mods 'HytaleRPG-0.0.25.jar.pending'
    $dataBefore=@(Get-ChildItem -LiteralPath $mods -Recurse -File|Where-Object {$_.Extension -ne '.jar' -and $_.FullName -ne $staged}|ForEach-Object {Get-FileHash -LiteralPath $_.FullName})
    $rollback=Join-Path $out 'rollback'
    New-Item -ItemType Directory -Force -Path $rollback|Out-Null
    $backup=Join-Path $rollback $names[0]
    if($installedHash -eq $oldHash){Copy-Item -LiteralPath $target -Destination $backup}
    if((Get-FileHash -LiteralPath $backup).Hash -ne $oldHash){throw 'Rollback integrity failure'}
    if($installedHash -eq $oldHash){
        if(Test-Path -LiteralPath $staged){
            if((Get-FileHash -LiteralPath $staged).Hash -ne $hash){throw 'Different pending deployment must be inspected'}
        }else{Copy-Item -LiteralPath $jar -Destination $staged}
        if((Get-FileHash -LiteralPath $staged).Hash -ne $hash){throw 'Staged integrity failure'}
        [IO.File]::Replace($staged,$target,[NullString]::Value)
    }
    foreach($name in $names){if((Get-FileHash -LiteralPath (Join-Path $mods $name)).Hash -ne $expected[$name]){throw "Deployed hash mismatch $name"}}
    $dataAfter=@(Get-ChildItem -LiteralPath $mods -Recurse -File|Where-Object Extension -ne '.jar'|ForEach-Object {Get-FileHash -LiteralPath $_.FullName})
    if(Compare-Object $dataBefore $dataAfter -Property Path,Hash){throw 'Live non-JAR data changed during deployment'}
    $result.deploymentPerformed=$true
    $result.deployedTo=$mods
    $result.rollbackSha256=$oldHash
    $result.liveModDataFilesVerifiedUnchanged=$dataAfter.Count
    $result.liveSaveDataModified=$false
}
$result.capturedAtUtc=[DateTime]::UtcNow.ToString('o')
$result|ConvertTo-Json -Depth 7|Set-Content -LiteralPath (Join-Path $out 'startup-hotfix.json') -Encoding utf8
$result|ConvertTo-Json -Depth 7
