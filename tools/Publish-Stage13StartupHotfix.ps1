[CmdletBinding()]
param([switch]$Deploy,[ValidateSet('l','m','n','o','p','q','r','s')][string]$Cohort='l')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root "evidence\stage-13\cohort-$Cohort"
$mods=(Resolve-Path 'C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods').Path
$oldHash='9083F89CB224BAC55B5D1B1CB9F5F5BA90C104D23A55B447A8E8029B4156B06F'
$expectedTests=2093
$baselineCohort='k'
$baselineCommit='84e29cb57175d9b6ade33654f38c77305ff5c91d'
$archiveName='Hytale-RPG-Stage13-L-startup-hotfix.zip'
$manifestName='startup-hotfix.json'
if($Cohort -eq 'm'){
    $oldHash='1E4A5CAA1344CE71C8701EBCFBFCB3883BD288DC90BE9732066338A5F6A5B0F6'
    $expectedTests=2106
    $baselineCohort='l'
    $baselineCommit='24efcdf'
    $archiveName='Hytale-RPG-Stage13-M-casting-correction.zip'
    $manifestName='casting-correction.json'
}
if($Cohort -eq 'n'){
    $oldHash='AABF71F03C31014A092C471C45965013DBD3FE491D50E17AB817148ACBA1D410'
    $expectedTests=2119
    $baselineCohort='m'
    $baselineCommit='e6ef501772464ae867d18ee92a8146d87a780689'
    $archiveName='Hytale-RPG-Stage13-N-power-trace-correction.zip'
    $manifestName='power-trace-correction.json'
}
$names=@('HytaleRPG-0.0.25.jar','CanvasUI-0.1.0.jar','HYTALEDEVLIB-0.5.0.jar')
if($Cohort -eq 'o'){
    $oldHash='669B230DAC394A2CE1246125028C51DF446BBC5024FCBEED83059F65F8BDDF33'
    $expectedTests=2128
    $baselineCohort='n'
    $baselineCommit='183601dbe46f3633c45d3f822852c7ea8738a4d2'
    $archiveName='Hytale-RPG-Stage13-O-native-spawn-correction.zip'
    $manifestName='native-spawn-correction.json'
}
if($Cohort -eq 'p'){
    $oldHash='B8BCCF7DCAA4E7949A32961F261504A0604E7A5671F8E32FBF1BEBBD0CEF0400'
    $expectedTests=2135
    $baselineCohort='o'
    $baselineCommit='790121472246b160224de584ed699d6d5af624d0'
    $archiveName='Hytale-RPG-Stage13-P-player-feedback-correction.zip'
    $manifestName='player-feedback-correction.json'
}
if($Cohort -eq 'q'){
    $oldHash='1C02F192F50F9D699372EDB6D74A0F3FDAE0A0A85C512780C3B280BA0EE7FD21'
    $expectedTests=2137
    $baselineCohort='p'
    $baselineCommit='064bc4242780bfafd96323930285da9eb2948664'
    $archiveName='Hytale-RPG-Stage13-Q-quick-slash-speed.zip'
    $manifestName='quick-slash-speed.json'
}
if($Cohort -eq 'r'){
    $oldHash='D811E68C7D8E2DE1421EC1E893F597B5DE0B979D5DEDE4470DB430768D0DED73'
    $expectedTests=2145
    $baselineCohort='q'
    $baselineCommit='86e82dd8b767b5d192f698f8364e17bcaea8d2bf'
    $archiveName='Hytale-RPG-Stage13-R-skill-icons-search.zip'
    $manifestName='skill-icons-search.json'
}
if($Cohort -eq 's'){
    $oldHash='8D0A2B286839B433D38DF436C0ADE0B307E1FB38B2653F251D387928BF1488F0'
    $expectedTests=2157
    $baselineCohort='r'
    $baselineCommit='a4335e3578d2ddd399509dc0941a4a1d391b9a1f'
    $archiveName='Hytale-RPG-Stage13-S-owner-icon-workflow.zip'
    $manifestName='owner-icon-workflow.json'
}
$suites=@(foreach($path in @('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $root $path) -Filter 'TEST-*.xml'){
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $s=$xml.testsuite
        [pscustomobject]@{name=$s.name;tests=[int]$s.tests;failures=[int]$s.failures;errors=[int]$s.errors;skipped=[int]$s.skipped;cases=@($s.testcase|ForEach-Object {$_.name})}
    }
})
if(($suites|Measure-Object tests -Sum).Sum -ne $expectedTests -or @($suites|Where-Object {$_.failures -or $_.errors -or $_.skipped}).Count){throw "Expected all $expectedTests tests successful, none skipped"}
foreach($baseline in (Get-Content -Raw (Join-Path $root "evidence/stage-13/cohort-$baselineCohort/test-results.json")|ConvertFrom-Json)){
    $current=@($suites|Where-Object name -eq $baseline.name)
    if($current.Count -ne 1){throw "Missing retained suite $($baseline.name)"}
    foreach($case in $baseline.cases){
        # Owner explicitly changes Mithril from unsupported to supported. The same missing-power
        # safety assertions remain on an unaudited fixture; a new positive test covers real Mithril.
        if($Cohort -eq 'n' -and $baseline.name -eq 'com.inigmasgames.hytalerpg.Stage13ConnectedCastingCorrectionTest' -and $case -eq 'nativeMithrilReproducesExactMissingMagicPowerThenRejectsBeforeCommit()'){
            $case='unauditedStaffReproducesExactMissingMagicPowerThenRejectsBeforeCommit()'
        }
        if($case -notin $current[0].cases){throw "Missing retained case $case"}
    }
}
$suites|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $out 'test-results.json') -Encoding utf8
$smoke=Get-Content -Raw (Join-Path $out 'server-smoke-summary.json')|ConvertFrom-Json
$jar=Join-Path $root 'build/libs/HytaleRPG-0.0.25.jar'
$hash=(Get-FileHash -LiteralPath $jar).Hash
if($smoke.jarSha256 -ne $hash -or $smoke.processExitCode -ne 0 -or -not $smoke.exactlyThreeMods -or -not $smoke.networkBooted -or -not $smoke.cleanShutdown -or $smoke.failure){throw 'Exact candidate smoke required'}
if($Cohort -in @('o','p','q','r','s')){
    $native=Get-Content -Raw (Join-Path $out 'native-spawn-integration.json')|ConvertFrom-Json
    if($native.result -ne 'PASS' -or $native.jarSha256 -ne $hash -or $native.spawned -ne 1 -or $native.requests -ne 2 -or $native.pendingRollback -ne 'PASS' -or -not $native.nativeRefValidAfterQueue){throw 'Exact candidate native spawn proof required before packaging/deploy'}
    if($Cohort -in @('p','q','r','s') -and -not $native.sameTickAdvanceAndRestingExpiry){throw 'Retained P same-tick/expiry proof required'}
}
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
$zip=Join-Path $out $archiveName
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
$result=[ordered]@{cohort=$Cohort.ToUpperInvariant();revision='R032';version='0.0.25';baselineCommit=$baselineCommit;
    tests=$expectedTests;failures=0;errors=0;skipped=0;retainedBaselineCaseIdentities='PASS';retainedBaselineCohort=$baselineCohort;threeModSmoke='PASS';archiveHashes='PASS';
    jarHashes=$expected;zipSha256=(Get-FileHash -LiteralPath $zip).Hash;connectedClientVerified=$false;deploymentPerformed=$false}
if($Cohort -eq 'n'){$result.baselineCaseMigration='Mithril missing-power negative fixture moved to Unaudited_Staff_Test with all safety assertions retained; actual Mithril positive regression added per owner request.'}
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
$result|ConvertTo-Json -Depth 7|Set-Content -LiteralPath (Join-Path $out $manifestName) -Encoding utf8
$result|ConvertTo-Json -Depth 7
