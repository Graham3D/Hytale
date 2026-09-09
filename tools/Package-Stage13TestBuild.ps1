[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence\stage-13\cohort-k'
$verification=Get-Content -Raw -LiteralPath (Join-Path $out 'verification.json')|ConvertFrom-Json
if($verification.tests -ne 2090 -or $verification.failures -or $verification.errors -or $verification.skipped -or -not $verification.normalThreeModSmoke){throw 'Final testing build requires the complete successful suite and smoke'}
$expected=[ordered]@{
    'HytaleRPG-0.0.25.jar'=$verification.jarSha256
    'CanvasUI-0.1.0.jar'='218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6'
    'HYTALEDEVLIB-0.5.0.jar'='DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230'
}
$files=@(foreach($item in $expected.GetEnumerator()){
    $path=Join-Path $out ('artifacts\'+$item.Key)
    if((Get-FileHash -LiteralPath $path).Hash -ne $item.Value){throw "Artifact hash mismatch: $($item.Key)"}
    $path
})
$zip=Join-Path $out 'Hytale-RPG-Stage13-K-test-build.zip'
if(Test-Path -LiteralPath $zip){throw 'Do not overwrite a published test archive'}
Compress-Archive -LiteralPath $files -DestinationPath $zip -CompressionLevel Optimal
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{
    if($archive.Entries.Count -ne 3){throw 'ZIP must contain exactly the three mod JARs'}
    foreach($entry in $archive.Entries){
        if(-not $expected.Contains($entry.FullName)){throw "Unexpected ZIP entry: $($entry.FullName)"}
        $stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
        if($hash -ne $expected[$entry.FullName]){throw "ZIP entry hash mismatch: $($entry.FullName)"}
    }
}finally{$archive.Dispose()}
[ordered]@{
    purpose='COMPLETE_CURRENT_ARPG_RPG_BUILD_FOR_TESTING';status='PACKAGED_FOR_TESTING';productionReleaseReady=$false
    revision='R032';version='0.0.25';cohort='k';startingCommit='eb42b1c8a437c2dbf0210b2b7b38be2e63d2ad28'
    fullValidationCommand='.\gradlew.bat :test :nativeControlTest :canvas-ui:test --rerun build --console=plain'
    fullValidationRuns=1;fullValidationExitCode=0;tests=2090;failures=0;errors=0;skipped=0
    threeModSmoke='PASS';archiveEntryHashes='PASS';jarHashes=$expected
    zipName=[IO.Path]::GetFileName($zip);zipSha256=(Get-FileHash -LiteralPath $zip).Hash
    connectedClientVerified=$false;nativeTickGate='NOT_MEASURED';liveDeploymentPerformed=$false;liveSaveDataModified=$false
    knownIssuesReport='docs/stage-13/stage-13-test-build-report.md';capturedAtUtc=[DateTime]::UtcNow.ToString('o')
}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'test-build.json') -Encoding utf8
Get-FileHash -LiteralPath $zip|Select-Object Path,Hash
