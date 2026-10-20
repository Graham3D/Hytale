[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $taskRoot 'evidence\stage-13\cohort-j'
$prior=Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'evidence\stage-13\cohort-i\before\checkpoint.json')|ConvertFrom-Json -AsHashtable
$priorLive=Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'evidence\stage-13\cohort-i\live-state-verification.json')|ConvertFrom-Json -AsHashtable
$liveMods=Join-Path $env:APPDATA 'Hytale\data\pre-release\Saves\RPG\mods'
$data=Join-Path $liveMods 'InigmasGames_HytaleRPGPhase00Audit'
$hashes=@{}
foreach($name in @('players','earned-rewards','encounters','diagnostics\native-rune-control')){
    $path=Join-Path $data $name
    if(Test-Path -LiteralPath $path){foreach($file in Get-ChildItem -LiteralPath $path -File -Recurse){
        $hashes['save\'+[IO.Path]::GetRelativePath($data,$file.FullName)]=(Get-FileHash -LiteralPath $file.FullName).Hash
    }}
}
if($hashes.Count -ne $prior.saveHashes.Count){throw 'Live owned-data inventory differs from historical before checkpoint; investigate without rewriting it'}
foreach($item in $prior.saveHashes.GetEnumerator()){if($hashes[$item.Key] -ne $item.Value){throw "Live owned-data changed: $($item.Key)"}}
$mods=@{}
foreach($file in Get-ChildItem -LiteralPath $liveMods -File -Filter '*.jar'){$mods[$file.Name]=(Get-FileHash -LiteralPath $file.FullName).Hash}
if($mods.Count -ne 3){throw 'Live mod inventory changed'}
foreach($item in $priorLive.liveMods.GetEnumerator()){if($mods[$item.Key] -ne $item.Value){throw "Live mod changed: $($item.Key)"}}
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');comparison='BYTE_HASH_AND_EXACT_FILE_INVENTORY';
    comparisonSource='evidence/stage-13/cohort-i/before/checkpoint.json and cohort-i/live-state-verification.json';
    ownedDataFiles=$hashes.Count;matchesBeforeCheckpoint=$true;liveMods=$mods;exactlyThreeLiveMods=$true;
    liveDeploymentPerformed=$false;liveDataModified=$false;connected='UNVERIFIED';
    scope='RPG-owned players, earned rewards, encounters, native-control recovery and installed mod JARs; not a full world-content comparison'
}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'live-state-verification.json') -Encoding utf8
Write-Output "Verified $($hashes.Count) historical owned-data files and exact three live JARs unchanged; no live writes."
