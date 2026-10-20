[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z]$')][string]$Cohort)
$ErrorActionPreference='Stop'
$stateRoot=(Resolve-Path "$PSScriptRoot\..").Path
$stateEvidence=Join-Path $stateRoot "evidence\stage-13\cohort-$Cohort"
$before=Get-Content -Raw -LiteralPath (Join-Path $stateEvidence 'before\checkpoint.json')|ConvertFrom-Json -AsHashtable
$liveMods=Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods'
$liveData=Join-Path $liveMods 'InigmasGames_HytaleRPGPhase00Audit'
$actual=@{}
foreach($name in @('players','earned-rewards','encounters','diagnostics\native-rune-control')){
    $folder=Join-Path $liveData $name
    if(Test-Path -LiteralPath $folder){foreach($file in Get-ChildItem -LiteralPath $folder -File -Recurse){$actual['save\'+[IO.Path]::GetRelativePath($liveData,$file.FullName)]=(Get-FileHash -LiteralPath $file.FullName).Hash}}
}
if($actual.Count -ne $before.saveHashes.Count){throw 'Live data file inventory changed; investigate before claiming unchanged'}
foreach($key in $actual.Keys){if($actual[$key] -ne $before.saveHashes[$key]){throw "Live data differs: $key"}}
$expected=@{'HytaleRPG-0.0.16.jar'='D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42';
    'CanvasUI-0.1.0.jar'='218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6';
    'HYTALEDEVLIB-0.5.0.jar'='DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230'}
$jars=@(Get-ChildItem -LiteralPath $liveMods -File -Filter '*.jar')
if($jars.Count -ne 3){throw 'Live mod inventory differs'}
foreach($jar in $jars){if((Get-FileHash -LiteralPath $jar.FullName).Hash -ne $expected[$jar.Name]){throw "Live JAR differs: $($jar.Name)"}}
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');comparison='BYTE_HASH_AND_EXACT_FILE_INVENTORY';
    ownedDataFiles=$actual.Count;matchesBeforeCheckpoint=$true;liveMods=$expected;exactlyThreeLiveMods=$true;
    liveDeploymentPerformed=$false;liveDataModified=$false;connected='UNVERIFIED';
    scope='RPG-owned players, earned rewards, encounters, native-control recovery and installed mod JARs; not a full world-content comparison'}|
    ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $stateEvidence 'live-state-verification.json') -Encoding utf8
Write-Output "Verified $($actual.Count) live owned-data files and three original JARs unchanged; read-only check."
