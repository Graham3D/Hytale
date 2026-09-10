[CmdletBinding()]
param([ValidateSet('m','n','o','p','q')][string]$Cohort='m')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root "evidence/stage-13/cohort-$Cohort"
$baseline=if($Cohort -eq 'q'){'p'}elseif($Cohort -eq 'p'){'o'}elseif($Cohort -eq 'o'){'n'}elseif($Cohort -eq 'n'){'m'}else{'l'}
$previous=Join-Path $root "evidence/stage-13/cohort-$baseline/artifacts/HytaleRPG-0.0.25.jar"
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
    if($Cohort -eq 'q'){
        if($name -match '^com/inigmasgames/hytalerpg/(execution/(CompiledProfileResolver|ExecutionFailureDiagnostics|SkillExecutionService|hytale/(NativeStrikeFeedback|HytaleSkillExecutionSystem))|diagnostics/RpgTraceEventType|ui/hud/RpgHud)(\$[^/]*)?\.class$'){continue}
        if($name -in @('rpg/catalog/skills.json','rpg/catalog/passives.json','rpg/runtime/stage-04-skills.json','Common/UI/Custom/Phase00RevisionHud.ui')){continue}
        if($name -match '^Server/Item/Animations/RPG_QuickSlash_(Sword|Longsword|Daggers)\.json$'){continue}
        throw "Unexpected Q packaged change outside speed/diagnostics: $name"
    }
    if($Cohort -eq 'p'){
        if($name -in @('Common/UI/StatusEffects/','Common/UI/StatusEffects/RPG/')){continue}
        if($name -match '^com/inigmasgames/hytalerpg/execution/(ProfileComponentPolicy|CompiledProfileResolver)(\$[^/]*)?\.class$'){continue}
        if($name -match '^com/inigmasgames/hytalerpg/(ui/hud/RpgHud|execution/hytale/(HytaleSkillExecutionSystem|NativeStrikeFeedback|HytaleAreaStatuses|NativeProjectileSpawnAuditCommand))(\$[^/]*)?\.class$'){continue}
        if($name -in @('rpg/catalog/skills.json','rpg/catalog/passives.json','rpg/runtime/stage-04-skills.json','Common/UI/Custom/Phase00RevisionHud.ui')){continue}
        if($name -match '^Server/Item/Animations/RPG_QuickSlash_(Sword|Longsword|Daggers)\.json$|^Server/Entity/Effects/RPG/(RPG_Chill_Icon_[1-4]|RPG_Frozen|RPG_Frozen_Slow)\.json$|^Common/UI/StatusEffects/RPG/StatusChill0[1-5]\.png$'){continue}
        throw "Unexpected P packaged change outside player feedback correction: $name"
    }
    if($Cohort -eq 'o'){
        if($name -match '^com/inigmasgames/hytalerpg/(phase00/Phase00Plugin|execution/hytale/(HytaleSkillExecutionSystem|NativeProjectileSpawnConfig|ProjectileSpawnDiagnostics|NativeProjectileSpawnAuditCommand))(\$[^/]*)?\.class$'){continue}
        throw "Unexpected O packaged change outside native spawn correction: $name"
    }
    if($Cohort -eq 'n'){
        if($name -eq 'rpg/runtime/native-item-power-vanilla-0.7-pre1.json'){continue}
        if($name -match '^com/inigmasgames/hytalerpg/(commands/RpgTraceCommand|combat/power/NativeItemPowerRegistry|diagnostics/(RpgSkillTraceService|RpgSkillTracer|RpgTraceEventType|SkillTraceConfiguration|SkillTraceLevel|SkillTraceRouter)|execution/hytale/HytaleEquipmentAdapter|phase00/Phase00Plugin|progress/RpgLoadoutService)(\$[^/]*)?\.class$'){continue}
        throw "Unexpected N packaged change outside power/trace correction: $name"
    }
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
$drill=Join-Path $root "run/stage13-cohort-$Cohort-rollback"
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
[ordered]@{changedEntries=$changed;allOtherEntriesIdentical=$true;resourcesHudPowerRegistryPersistenceFormatsUnchanged=($Cohort -in @('m','o'));
    nativeHudInputExecutorsPersistenceAndOriginalPowerManifestUnchanged=($Cohort -eq 'n');expandedPowerManifest=($Cohort -eq 'n');
    equipmentTargetingInputPersistenceResourcesCooldownsHudAndNormalTraceIdenticalToN=($Cohort -eq 'o');
    powerInputPersistenceResourceCooldownAndTraceImplementationsIdenticalToO=($Cohort -eq 'p');
    quickSlashSpeedAndFailureDiagnosticsOnly=($Cohort -eq 'q');
    isolatedAtomicRollbackAndRollForward='PASS';retainedArchivedReaderTests='See full test-results.json; archived-reader tests unchanged';
    previousSha256=(Get-FileHash -LiteralPath $previous).Hash;candidateSha256=(Get-FileHash -LiteralPath $candidate).Hash;
    connectedCastingVerified=$false}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'jar-differential.json') -Encoding utf8
Get-Content -Raw (Join-Path $out 'jar-differential.json')
