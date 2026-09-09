[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-m'
$previous=Join-Path $root 'evidence/stage-13/cohort-l/artifacts/HytaleRPG-0.0.25.jar'
$candidate=Join-Path $out 'artifacts/HytaleRPG-0.0.25.jar'
function EntryHashes([string]$path){
    $result=@{};$zip=[IO.Compression.ZipFile]::OpenRead($path)
    try{foreach($entry in $zip.Entries){$stream=$entry.Open();try{$result[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}}}
    finally{$zip.Dispose()}
    return $result
}
$before=EntryHashes $previous;$after=EntryHashes $candidate
$changed=@(foreach($name in @($before.Keys)+@($after.Keys)|Sort-Object -Unique){if($before[$name] -ne $after[$name]){$name}})
foreach($name in $changed){
    if($name -notmatch '^com/inigmasgames/hytalerpg/(diagnostics/RpgTraceEventType|execution/(SkillExecutionService|SkillExecutionPort|PreparationFailureDiagnostics|strike/StrikeCastPrerequisites|hytale/(HytaleEquipmentAdapter|HytaleSkillExecutionSystem)))(\$[^/]*)?\.class$'){
        throw "Unexpected packaged change outside bounded casting correction: $name"
    }
}
if(-not $changed.Count){throw 'No correction classes changed'}
Copy-Item -LiteralPath (Join-Path $root 'build/test-results/test/TEST-com.inigmasgames.hytalerpg.Stage13ConnectedCastingCorrectionTest.xml') -Destination $out
[xml]$benchmark=Get-Content -Raw (Join-Path $root 'build/test-results/test/TEST-com.inigmasgames.hytalerpg.Stage13DurabilityLoadTest.xml')
$line=($benchmark.testsuite.'system-out'.InnerText -split "`n"|Where-Object {$_ -like 'STAGE13_DURABILITY_LOAD *'}|Select-Object -First 1)
if(-not $line){throw 'Missing retained 64-update benchmark output'}
$metric=$line.Substring('STAGE13_DURABILITY_LOAD '.Length)|ConvertFrom-Json
if($metric.updatesPerSample -ne 64 -or $metric.samples -ne 60 -or -not $metric.restoredContributorCounts){throw 'Retained benchmark recovery gate failed'}
$metric|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $out 'durability-load.json') -Encoding utf8
$drill=Join-Path $root 'run/stage13-cohort-m-rollback'
New-Item -ItemType Directory -Force -Path $drill|Out-Null
$target=Join-Path $drill 'RPG.jar'
$staged=Join-Path $drill 'RPG.jar.pending'
Copy-Item -LiteralPath $candidate -Destination $target
Copy-Item -LiteralPath $previous -Destination $staged
[IO.File]::Replace($staged,$target,[NullString]::Value)
if((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $previous).Hash){throw 'Isolated rollback swap failed'}
Copy-Item -LiteralPath $candidate -Destination $staged
[IO.File]::Replace($staged,$target,[NullString]::Value)
if((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $candidate).Hash){throw 'Isolated roll-forward swap failed'}
[ordered]@{changedEntries=$changed;allOtherEntriesIdentical=$true;resourcesHudPowerRegistryPersistenceFormatsUnchanged=$true;
    isolatedAtomicRollbackAndRollForward='PASS';retainedArchivedReaderTests='See full test-results.json; no test changed';
    previousSha256=(Get-FileHash -LiteralPath $previous).Hash;candidateSha256=(Get-FileHash -LiteralPath $candidate).Hash;
    connectedCastingVerified=$false}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'jar-differential.json') -Encoding utf8
Get-Content -Raw (Join-Path $out 'jar-differential.json')
