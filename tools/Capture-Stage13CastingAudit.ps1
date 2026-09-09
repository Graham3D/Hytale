[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-m'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$save='C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG'
$logs=Join-Path $save 'mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg'
$skill=Join-Path $logs 'skill-trace.jsonl'
$ui=Join-Path $logs 'ui-trace.jsonl'
$server=Get-ChildItem -LiteralPath (Join-Path $save 'logs') -Filter '*_server.log' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
$player=Join-Path $save 'universe/players/73f9b698-2494-480d-8406-2943e4a7505b.json'
$sources=@(foreach($path in @($skill,$ui,$server.FullName,$player)){
    $file=Get-Item -LiteralPath $path
    [ordered]@{name=$file.Name;sha256=(Get-FileHash -LiteralPath $path).Hash;bytes=$file.Length;lastWriteUtc=$file.LastWriteTimeUtc.ToString('o')}
})
$records=@(Get-Content -LiteralPath $skill | ForEach-Object {$_|ConvertFrom-Json} | Where-Object {$_.timestamp -like '2026-09-09*'})
$failures=@($records | Where-Object {$_.details.failureCode -in @('NO_VALID_TARGET','COMMIT_PREPARATION_FAILED_IllegalArgumentException')})
$ids=@($failures.correlationId | Sort-Object -Unique)
$records | Where-Object {$_.correlationId -in $ids} | ForEach-Object {$_|ConvertTo-Json -Depth 12 -Compress} | Set-Content -LiteralPath (Join-Path $out 'connected-casting-excerpt.jsonl') -Encoding utf8
$uiRecords=@(Get-Content -LiteralPath $ui | ForEach-Object {$_|ConvertFrom-Json} | Where-Object {$_.timestamp -like '2026-09-09*'})
$playerData=Get-Content -Raw -LiteralPath $player | ConvertFrom-Json
$hotbar=$playerData.Components.HotbarInventory
$heldId=$hotbar.Inventory.Items.([string]$hotbar.ActiveSlot).Id
$installed='C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest'
$assets=Join-Path $installed 'Assets.zip'
$registry=Get-Content -Raw (Join-Path $root 'src/main/resources/rpg/runtime/native-item-power-r032.json') | ConvertFrom-Json
$zip=[IO.Compression.ZipFile]::OpenRead($assets)
try{
    $items=@(foreach($id in @('Weapon_Staff_Mithril','Weapon_Staff_Crystal_Flame','Weapon_Staff_Crystal_Ice','Weapon_Wand_Wood')){
        $entry=@($registry.items | Where-Object itemId -eq $id)
        $asset=if($id -eq 'Weapon_Staff_Mithril'){'Server/Item/Items/Weapon/Staff/Weapon_Staff_Mithril.json'}else{$entry[0].sourceAsset}
        $stream=$zip.GetEntry($asset).Open();$reader=[IO.StreamReader]::new($stream)
        try{$native=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
        [ordered]@{itemId=$id;nativeAsset=$asset;rawFamily=@($native.Tags.Family);rawType=@($native.Tags.Type);registryEntry=if($entry.Count){$entry[0]}else{$null}}
    })
}finally{$zip.Dispose()}
$result=[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');sources=$sources;
    qaDay='2026-09-09';nativeInputs=@($records|Where-Object eventType -eq 'NATIVE_ABILITY_INPUT_OBSERVED').Count;
    failureCounts=@($failures|Group-Object {$_.details.failureCode}|ForEach-Object {[ordered]@{code=$_.Name;records=$_.Count}});
    uiRecords=$uiRecords.Count;uiEventCounts=@($uiRecords|Group-Object eventType|ForEach-Object {[ordered]@{event=$_.Name;records=$_.Count}});
    sessionEndActiveSlot=$hotbar.ActiveSlot;sessionEndItemId=$heldId;
    historicalExceptionMessageAvailable=$false;causality='Saved session-end item plus deterministic production resolver reproduction; old trace did not capture item-at-cast or exception message.';
    installedServerSha256=(Get-FileHash -LiteralPath (Join-Path $installed 'Server/HytaleServer.jar')).Hash;
    installedAssetsSha256=(Get-FileHash -LiteralPath $assets).Hash;nativeItemAudit=$items}
$result|ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $out 'connected-source-audit.json') -Encoding utf8
$profiles=@(foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'src/main/resources/rpg/runtime') -Filter '*.json'){
    $data=Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json
    foreach($p in $data.skills){
        $contract='SPATIAL_EMPTY_ALLOWED';$requirements='Actor/equipment/resource/cooldown/capability and bounded native geometry; entities resolved at execution'
        if($p.conversion){$contract='ENTITY_REQUIRED';$requirements='Eligible native dominatable entity; range/aim/LOS/rank restrictions'}
        elseif($p.summonAction){$contract='CONSUMABLE_REQUIRED';$requirements='Specific corpse or owned eligible minion; durable consumption retained'}
        elseif($p.summon){if($p.summon.corpseRequired){$contract='CORPSE_REQUIRED';$requirements='Eligible corpse and durable claim'}else{$contract='PLACEMENT_NO_ENEMY_REQUIRED';$requirements='Loaded safe placement and summon caps/assets'}}
        elseif($p.support){
            if($p.support.kind -in @('TAUNT','WEAKEN','MARK','FEAR')){$contract='ENTITY_REQUIRED';$requirements='Explicit hostile target eligibility/range/LOS'}
            elseif($p.support.kind -in @('HEAL','SHIELD')){$contract='RECIPIENT_REQUIRED';$requirements='Explicit valid ally recipient; native selector permits authored self fallback'}
            else{$contract='SELF_OR_RECIPIENT_SET';$requirements='Self/recipient-set support; native support gates, reservations and escrow unchanged'}
        }
        elseif($p.connection -and $p.connection.kind -in @('TETHER','CHAIN','DRAIN')){$contract='ENTITY_REQUIRED';$requirements='ConnectionProfile.requiresTarget; first aimed eligible entity'}
        elseif($p.movement){
            if($p.movement.details.groundTarget){$requirements='Loaded legal terrain/ground path; no enemy required'}
            elseif($p.movement.kind -eq 'LEAP'){$contract='ENTITY_REQUIRED';$requirements='Pounce authored target-directed leap; valid aimed entity retained'}
            else{$requirements='Movement direction and bounded native collision path; no enemy required'}
        }
        elseif($p.reaction){$contract='SELF_ARMED';$requirements='Reaction state/assets/capability; triggers acquire attackers after activation'}
        elseif($p.area){$requirements='Ground/roof/aim/placement and bounded query gates as authored; zero enemies allowed'}
        elseif($p.projectile){$requirements='Aim, config, launch capacity, ammunition if authored, muzzle/path gates; no enemy required'}
        $gate=''
        if($p.nativeStance){$gate='NATIVE_PER_ACTOR_BASIC_ATTACK_CADENCE_UNVERIFIED';$contract='SELF_STANCE_GATED'}
        elseif($p.cage){$gate='BONE_CAGE_NATIVE_ENEMY_ONLY_COLLISION_UNVERIFIED';$contract='SELECTIVE_COLLISION_GATED'}
        elseif($p.reaction.nativeHeld){$gate='NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED'}
        elseif($p.projectile.details.nativeCapabilityGate){$gate=$p.projectile.details.nativeCapabilityGate}
        [pscustomobject]@{skillId=$p.skillId;family=$p.family;sourceFile=$file.Name;targetContract=$contract;requirements=$requirements;activationGate=$gate;connectedRetest='NOT_RUN'}
    }
})
if($profiles.Count -ne 87 -or @($profiles.skillId|Sort-Object -Unique).Count -ne 87){throw 'Expected exactly 87 distinct audited profiles'}
$profiles|Sort-Object skillId|Export-Csv -LiteralPath (Join-Path $out 'target-contract-87-profiles.csv') -NoTypeInformation -Encoding utf8
$result|ConvertTo-Json -Depth 3
