[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$recoveryRoot = (Resolve-Path "$PSScriptRoot\..").Path
$recoveryOutput = Join-Path $recoveryRoot 'evidence\workspace-recovery-20260908'
New-Item -ItemType Directory -Force -Path $recoveryOutput | Out-Null
$recoveryFiles = @(Get-ChildItem -LiteralPath (Join-Path $recoveryRoot 'art') -Recurse -File | ForEach-Object {
    [ordered]@{path=[IO.Path]::GetRelativePath($recoveryRoot,$_.FullName);bytes=$_.Length;
        sha256=(Get-FileHash -LiteralPath $_.FullName).Hash;modifiedUtc=$_.LastWriteTimeUtc.ToString('o')}
})
$recoveryTests=@();$recoveryTotal=0
foreach($recoveryFile in Get-ChildItem -Path "$recoveryRoot\build\test-results\test","$recoveryRoot\build\test-results\nativeControlTest","$recoveryRoot\canvas-ui\build\test-results\test" -Filter 'TEST-*.xml') {
    [xml]$recoveryXml=Get-Content -Raw -LiteralPath $recoveryFile.FullName
    $recoverySuite=$recoveryXml.testsuite
    if([int]$recoverySuite.failures -or [int]$recoverySuite.errors -or [int]$recoverySuite.skipped){throw 'Recovery regression gate failed'}
    $recoveryTotal += [int]$recoverySuite.tests
    $recoveryTests += @{name=$recoverySuite.name;tests=[int]$recoverySuite.tests}
}
if($recoveryTotal -ne 516){throw 'Expected the unchanged Stage 09 regression inventory'}
$recoveryManifest=[ordered]@{
    capturedAtUtc=[DateTime]::UtcNow.ToString('o');workspace=$recoveryRoot
    sourceHead=(& git -C $recoveryRoot rev-parse HEAD).Trim();branch=(& git -C $recoveryRoot branch --show-current).Trim()
    preservedPreviousBranch='recovered-main-before-stage10-20260908';previousHead='9033cbf'
    oldDriveWritten=$false;liveDeploymentPerformed=$false;connectedEvidence='NOT_RUN'
    recoveredFiles=$recoveryFiles;retainedTests=$recoveryTotal;testSuites=$recoveryTests
    stage09RollbackSha256=(Get-FileHash -LiteralPath (Join-Path $recoveryRoot 'evidence\stage-09\cohort-f\artifacts\HytaleRPG-0.0.21.jar')).Hash
}
$recoveryManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $recoveryOutput 'verification.json') -Encoding utf8
[pscustomobject]@{retainedTests=$recoveryTotal;inventoriedFiles=$recoveryFiles.Count;workspace=$recoveryRoot}
