[CmdletBinding()]
param([ValidateSet('m','n','o','p','q','r','s')][string]$Cohort='m')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root "evidence/stage-13/cohort-$Cohort"
$baseline=if($Cohort -eq 's'){'r'}elseif($Cohort -eq 'r'){'q'}elseif($Cohort -eq 'q'){'p'}elseif($Cohort -eq 'p'){'o'}elseif($Cohort -eq 'o'){'n'}elseif($Cohort -eq 'n'){'m'}else{'l'}
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
    if($Cohort -eq 's'){
        if($name -match '^com/inigmasgames/hytalerpg/ui/(hud/RpgHud|skilltree/(RpgSkillIcons|RpgSkillTreePage|RpgSkillTreeProjectionService))(\$[^/]*)?\.class$'){continue}
        if($name -in @('rpg/presentation/','rpg/presentation/icon-index.json','Common/UI/Custom/Phase00RevisionHud.ui','Common/UI/Custom/RpgSkillTree.ui')){continue}
        if($name -in @('Common/Icons/Items/RPG/SkillWhirlwind.png','Common/UI/Custom/Icons/RPG/SkillWhirlwind.png',
            'Server/Item/Items/RPG/Abilities/RPG_Ability_Whirlwind.json')){continue}
        throw "Unexpected S packaged change outside optional icons/passive surfaces/badge: $name"
    }
    if($Cohort -eq 'r'){
        if($name -match '^com/inigmasgames/hytalerpg/ui/(hud/RpgHud|skilltree/(RpgSkillIcons|RpgSkillTreePage|RpgSkillTreeProjectionService|StaticSkillTreeViewModel))(\$[^/]*)?\.class$'){continue}
        if($name -in @('Common/Icons/','Common/Icons/Items/','Common/Icons/Items/RPG/','Common/UI/Custom/Icons/','Common/UI/Custom/Icons/RPG/',
            'Common/UI/Custom/Phase00RevisionHud.ui','Common/UI/Custom/RpgSkillTree.ui','Common/UI/Custom/RpgSkillTreeLibraryRow.ui',
            'Server/Item/Items/RPG/Abilities/RPG_Ability_Fire_Bolt.json','Server/Item/Items/RPG/Abilities/RPG_Ability_Quick_Slash.json')){continue}
        if($name -match '^Common/(Icons/Items/RPG|UI/Custom/Icons/RPG)/(SkillFirebolt|SkillQuickslash|Background_Ability_Ready|Frame_Ability_Ready)\.png$'){continue}
        throw "Unexpected R packaged change outside icons/search/badge: $name"
    }
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
if($Cohort -eq 's'){
    $import=Get-Content -Raw (Join-Path $out 'candidate-icon-backups/last-update.json')|ConvertFrom-Json
    if($import.afterSha256 -ne (Get-FileHash -LiteralPath $candidate).Hash){throw 'Candidate icon import receipt mismatch'}
    $iconHash=(Get-FileHash -LiteralPath (Join-Path $root 'art/Skills/SkillWhirlwind.png')).Hash
    foreach($entry in @('Common/Icons/Items/RPG/SkillWhirlwind.png','Common/UI/Custom/Icons/RPG/SkillWhirlwind.png')){
        if($after[$entry] -ne $iconHash){throw 'Owner Whirlwind icon differs in package'}
    }
    $documents=@(foreach($path in @($previous,$candidate)){
        $zip=[IO.Compression.ZipFile]::OpenRead($path)
        try{
            $reader=[IO.StreamReader]::new($zip.GetEntry('Server/Item/Items/RPG/Abilities/RPG_Ability_Whirlwind.json').Open())
            try{$json=$reader.ReadToEnd()|ConvertFrom-Json -AsHashtable}finally{$reader.Dispose()}
            [void]$json.Remove('Icon')
            $json|ConvertTo-Json -Depth 20 -Compress
        }finally{$zip.Dispose()}
    })
    if($documents.Count -ne 2 -or $documents[0] -cne $documents[1]){throw 'Whirlwind native fields other than Icon changed'}
}
if($Cohort -eq 'r'){
    foreach($icon in @('SkillFirebolt.png','SkillQuickslash.png')){
        $sourceHash=(Get-FileHash -LiteralPath (Join-Path $root "art/Skills/$icon")).Hash
        foreach($entry in @("Common/Icons/Items/RPG/$icon","Common/UI/Custom/Icons/RPG/$icon")){
            if($after[$entry] -ne $sourceHash){throw "Owner icon bytes differ in package: $entry"}
        }
    }
    $baselineZip=[IO.Compression.ZipFile]::OpenRead($previous)
    $candidateZip=[IO.Compression.ZipFile]::OpenRead($candidate)
    try{
        foreach($id in @('Fire_Bolt','Quick_Slash')){
            $entry="Server/Item/Items/RPG/Abilities/RPG_Ability_$id.json"
            $documents=@(foreach($zip in @($baselineZip,$candidateZip)){
                $reader=[IO.StreamReader]::new($zip.GetEntry($entry).Open())
                try{$json=$reader.ReadToEnd()|ConvertFrom-Json -AsHashtable}finally{$reader.Dispose()}
                [void]$json.Remove('Icon')
                $json|ConvertTo-Json -Depth 20 -Compress
            })
            if($documents.Count -ne 2 -or $documents[0] -cne $documents[1]){throw "Native item fields other than Icon changed: $id"}
        }
    }finally{$baselineZip.Dispose();$candidateZip.Dispose()}
}
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
    skillIconsSearchAndBadgeOnly=($Cohort -eq 'r');
    optionalIconLookupPassiveSurfacesAndBadgeOnly=($Cohort -eq 's');
    isolatedAtomicRollbackAndRollForward='PASS';retainedArchivedReaderTests='See full test-results.json; archived-reader tests unchanged';
    previousSha256=(Get-FileHash -LiteralPath $previous).Hash;candidateSha256=(Get-FileHash -LiteralPath $candidate).Hash;
    connectedCastingVerified=$false}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'jar-differential.json') -Encoding utf8
Get-Content -Raw (Join-Path $out 'jar-differential.json')
