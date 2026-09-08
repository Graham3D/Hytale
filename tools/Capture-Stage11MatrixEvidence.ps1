[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$matrixRoot=(Resolve-Path "$PSScriptRoot\..").Path
$matrixSource=Join-Path $matrixRoot 'build\stage11-matrix'
$matrixOut=Join-Path $matrixRoot 'evidence\stage-11\cohort-z\matrix'
$matrixSingles=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'skill-passive-matrix.json')|ConvertFrom-Json
$matrixPairs=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'passive-pair-matrix.json')|ConvertFrom-Json
$matrixProperties=Get-Content -Raw -LiteralPath (Join-Path $matrixSource 'six-link-property-summary.json')|ConvertFrom-Json
if($matrixSingles.Count -ne 5742 -or $matrixPairs.Count -ne 2145 -or $matrixProperties.validGraphs -ne 1000 -or $matrixProperties.connectedEvidence){throw 'Incomplete matrix/property evidence'}
foreach($matrixGate in @('single-profile-gates.json','pair-profile-gates.json')){
    $matrixFailures=Get-Content -Raw -LiteralPath (Join-Path $matrixSource $matrixGate)|ConvertFrom-Json -AsHashtable
    if($matrixFailures.Count){throw 'Unresolved profile construction boundary'}
}
$matrixClasses=@(Get-ChildItem -LiteralPath (Join-Path $matrixRoot 'build\test-results\test') -Filter '*Stage11CompatibilityMatrixTest.xml')
if($matrixClasses.Count -ne 1){throw 'Missing matrix regression result'}
[xml]$matrixTest=Get-Content -Raw -LiteralPath $matrixClasses[0].FullName
if($matrixTest.testsuite.failures -ne '0' -or $matrixTest.testsuite.errors -ne '0' -or $matrixTest.testsuite.skipped -ne '0'){throw 'Matrix tests did not pass'}
New-Item -ItemType Directory -Force -Path $matrixOut | Out-Null
Get-ChildItem -LiteralPath $matrixSource -Filter '*.json' | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $matrixOut $_.Name) -Force}
$matrixInputs=@('src\main\resources\rpg\catalog\skills.json','src\main\resources\rpg\catalog\passives.json',
    'src\main\java\com\inigmasgames\hytalerpg\links\CompatibilityService.java',
    'src\main\java\com\inigmasgames\hytalerpg\links\LinkCompiler.java',
    'src\test\java\com\inigmasgames\hytalerpg\Stage11CompatibilityMatrixTest.java')
$matrixHashes=@{};foreach($matrixInput in $matrixInputs){$matrixHashes[$matrixInput]=(Get-FileHash -LiteralPath (Join-Path $matrixRoot $matrixInput)).Hash}
[ordered]@{stage=11;revision='R030';compiledPlanSchema=35;playerSchema=7;skillPassiveCells=5742;passivePairs=2145;pairSkillAssessments=186615;
    validSixLinkGraphs=1000;propertySeed=$matrixProperties.seed;profilesWithSixLinkFixtures=$matrixProperties.implementedProfilesCovered.Count;
    singlesByGate=@($matrixSingles|Group-Object gate|ForEach-Object {@{gate=$_.Name;count=$_.Count}});
    pairsByClassification=@($matrixPairs|Group-Object classification|ForEach-Object {@{classification=$_.Name;count=$_.Count}});
    testsSeconds=$matrixTest.testsuite.time;scope='Compiler and numeric profile resolution. Not execution, native capability availability, rendering or client input proof.';
    runtimeProfiles=60;remainingStage04And05Profiles=27;allCatalogRuntimeComplete=$false;connectedGate='UNVERIFIED';inputSha256=$matrixHashes;
    jarSha256=(Get-FileHash -LiteralPath (Join-Path $matrixRoot 'build\libs\HytaleRPG-0.0.23.jar')).Hash} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $matrixOut 'summary.json') -Encoding utf8
