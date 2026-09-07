[CmdletBinding()]
param([ValidateSet('a','b','c')][string]$Cohort = 'a')
$ErrorActionPreference = 'Stop'
$stage06Root = (Resolve-Path "$PSScriptRoot\..").Path
$stage06Evidence = Join-Path $stage06Root "evidence\stage-06\cohort-$Cohort"
$stage06Package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage06Server = Join-Path $stage06Package 'Server\HytaleServer.jar'
$stage06Assets = Join-Path $stage06Package 'Assets.zip'
$stage06Jar = Join-Path $stage06Root 'build\libs\HytaleRPG-0.0.18.jar'
New-Item -ItemType Directory -Force -Path $stage06Evidence, (Join-Path $stage06Evidence 'api'),
    (Join-Path $stage06Evidence 'artifacts'), (Join-Path $stage06Evidence 'rollback') | Out-Null
Push-Location $stage06Root
try {
    $stage06Classes = @(
        'com.hypixel.hytale.server.npc.role.support.WorldSupport',
        'com.hypixel.hytale.component.Store',
        'com.hypixel.hytale.server.core.modules.collision.CollisionModule',
        'com.hypixel.hytale.server.core.modules.collision.CollisionResult',
        'com.hypixel.hytale.server.core.modules.collision.BoxCollisionData',
        'com.hypixel.hytale.server.core.modules.debug.DebugUtils',
        'com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent',
        'com.hypixel.hytale.server.npc.entities.NPCEntity',
        'com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase',
        'com.hypixel.hytale.server.npc.systems.SteeringSystem',
        'com.hypixel.hytale.server.npc.systems.AvoidanceSystem',
        'com.hypixel.hytale.server.npc.corecomponents.combat.ActionAttack',
        'com.hypixel.hytale.server.npc.role.Role',
        'com.hypixel.hytale.server.core.entity.InteractionManager',
        'com.hypixel.hytale.server.core.modules.entity.damage.Damage',
        'com.hypixel.hytale.assetstore.AssetExtraInfo$Data',
        'com.hypixel.hytale.server.core.asset.type.item.config.Item',
        'com.hypixel.hytale.server.core.io.PacketHandler'
    )
    foreach ($stage06Class in $stage06Classes) {
        $stage06Bytecode = & javap -classpath $stage06Server -c -p $stage06Class
        if ($LASTEXITCODE -ne 0) { throw "Missing audited class: $stage06Class" }
        $stage06Bytecode | Set-Content -LiteralPath (Join-Path $stage06Evidence ('api\' + $stage06Class + '.txt')) -Encoding utf8
    }
    $stage06SourceAssets = @()
    $stage06Zip = [IO.Compression.ZipFile]::OpenRead($stage06Assets)
    try {
        foreach ($stage06AssetPath in @('Server/Entity/Effects/Status/Root.json', 'Server/Entity/Effects/Status/Slow.json',
                'Server/Entity/Effects/Status/Stun.json', 'Server/Entity/Effects/Status/Burn_Template.json',
                'Server/Entity/Damage/Earth.json', 'Server/Entity/Damage/Elemental.json',
                'Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Elite.json',
                'Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Elite_Phase_2.json',
                'Server/EncounterManager/Encounter_Skeleton_Elite.json', 'Server/EncounterManager/Example_Boss.json',
                'Server/Item/Items/Weapon/Bomb/Weapon_Bomb.json', 'Server/Item/Items/Weapon/Bomb/Weapon_Bomb_Fire.json',
                'Server/Item/Interactions/Weapons/Bomb/Bomb_Throw.json', 'Server/Item/Interactions/Weapons/Bomb/Bomb_Explode.json',
                'Server/ProjectileConfigs/Weapons/Bombs/Projectile_Config_Bomb_Base.json',
                'Server/Item/Interactions/Explosions/Explode_Generic_Entities.json', 'Server/Item/Interactions/Explosions/Explode_Generic.json')) {
            $stage06Entry = $stage06Zip.GetEntry($stage06AssetPath)
            if ($null -eq $stage06Entry) { throw "Missing native asset: $stage06AssetPath" }
            $stage06Reader = [IO.StreamReader]::new($stage06Entry.Open())
            try { $stage06SourceAssets += @{path=$stage06AssetPath; document=($stage06Reader.ReadToEnd() | ConvertFrom-Json)} }
            finally { $stage06Reader.Dispose() }
        }
    } finally { $stage06Zip.Dispose() }
    $stage06SourceAssets | ConvertTo-Json -Depth 32 | Set-Content -LiteralPath (Join-Path $stage06Evidence 'audited-native-assets.json') -Encoding utf8
    $stage06Tests = 0; $stage06Failed = 0; $stage06Errors = 0; $stage06Skipped = 0
    foreach ($stage06XmlFile in Get-ChildItem -Recurse -Path 'build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test' -Filter 'TEST-*.xml') {
        [xml]$stage06Xml = Get-Content -Raw -LiteralPath $stage06XmlFile.FullName
        $stage06Tests += [int]$stage06Xml.testsuite.tests; $stage06Failed += [int]$stage06Xml.testsuite.failures
        $stage06Errors += [int]$stage06Xml.testsuite.errors; $stage06Skipped += [int]$stage06Xml.testsuite.skipped
    }
    if ($stage06Failed -or $stage06Errors -or $stage06Skipped) { throw 'Regression result is not green.' }
    $stage06MinimumTests = switch ($Cohort) { 'a' { 171 } 'b' { 190 } 'c' { 199 } }
    if ($stage06Tests -lt $stage06MinimumTests) { throw "Incomplete retained regression suite: $stage06Tests < $stage06MinimumTests" }
    & "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $stage06Jar
    $stage06Protected = @(& git diff --name-only 5c5e55e -- 'src/main/java/com/inigmasgames/hytalerpg/ui' 'src/main/resources/Common/UI' 'canvas-ui/src' 'src/main/resources/rpg/runtime/stage-04-skills.json' 'src/main/resources/rpg/runtime/stage-05-projectiles.json' 'src/main/java/com/inigmasgames/hytalerpg/execution/projectile')
    if ($stage06Protected.Count) { throw "Protected HUD/earlier delivery changes: $stage06Protected" }
    Copy-Item -LiteralPath $stage06Jar -Destination (Join-Path $stage06Evidence 'artifacts\HytaleRPG-0.0.18.jar') -Force
    Copy-Item -LiteralPath 'evidence/corrections/R024/artifacts/HytaleRPG-0.0.17.jar' -Destination (Join-Path $stage06Evidence 'rollback\HytaleRPG-0.0.17.jar') -Force
    $stage06Summary = [ordered]@{
        capturedAtUtc=[DateTime]::UtcNow.ToString('o'); revision='R025'; version='0.0.18'; stage='06'
        branch=(& git branch --show-current).Trim(); sourceHead=(& git rev-parse HEAD).Trim(); worktreeDirty=[bool](& git status --porcelain)
        stageStatus='IMPLEMENTATION_IN_PROGRESS'; completeStageGate=$false; cohort=$Cohort
        cohortSkills=$(if ($Cohort -eq 'a') { @('ground_slam','frost_nova','root_snare') }
            elseif ($Cohort -eq 'b') { @('powder_mine','cold_wave','venom_spray','blizzard','wall_of_fire','poison_cloud') }
            else { @('vortex','earthquake','meteor','comet','avalanche','void_cataclysm') })
        tests=$stage06Tests; failures=$stage06Failed; errors=$stage06Errors; skipped=$stage06Skipped
        connectedVerification='UNVERIFIED'; nativeCastingFixed=$false; liveDeploymentPerformed=$false
        protectedPathsChanged=$stage06Protected
        jarSha256=(Get-FileHash -LiteralPath $stage06Jar).Hash; serverSha256=(Get-FileHash -LiteralPath $stage06Server).Hash
        assetsSha256=(Get-FileHash -LiteralPath $stage06Assets).Hash
        rollbackSha256=(Get-FileHash -LiteralPath (Join-Path $stage06Evidence 'rollback\HytaleRPG-0.0.17.jar')).Hash
    }
    $stage06Summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage06Evidence 'verification.json') -Encoding utf8
    [pscustomobject]$stage06Summary | Format-List
} finally { Pop-Location }
