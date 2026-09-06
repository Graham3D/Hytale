[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-04\R013'
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$assetsZip = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip"
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Stage 04 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.6.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json', 'rpg-build.properties', 'rpg/runtime/stage-04-skills.json',
        'Common/UI/Custom/RpgHud.ui', 'Common/UI/Custom/RpgCharacter.ui',
        'com/inigmasgames/hytalerpg/execution/SkillExecutionService.class',
        'com/inigmasgames/hytalerpg/execution/SkillExecutionContext.class',
        'com/inigmasgames/hytalerpg/execution/SkillExecutorRegistry.class',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.class',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleEquipmentAdapter.class',
        'com/inigmasgames/hytalerpg/execution/strike/StrikeGeometryService.class',
        'com/inigmasgames/hytalerpg/execution/strike/StrikeRepeatSchedule.class',
        'com/inigmasgames/hytalerpg/execution/movement/MovementPlanner.class',
        'com/inigmasgames/hytalerpg/execution/reaction/ReactionWindowService.class',
        'com/inigmasgames/hytalerpg/vfx/LinkTreeVfxService.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $skills = Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $pilots = (Get-Content -Raw 'src\main\resources\rpg\runtime\stage-04-skills.json' | ConvertFrom-Json).skills
    $testSuites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $damage = (& javap -classpath $serverJar com.hypixel.hytale.server.core.modules.entity.damage.Damage 2>&1) -join "`n"
    $collision = (& javap -classpath $serverJar com.hypixel.hytale.server.core.modules.collision.CollisionModule 2>&1) -join "`n"
    $player = (& javap -classpath $serverJar com.hypixel.hytale.server.core.entity.entities.Player 2>&1) -join "`n"
    $movement = (& javap -classpath $serverJar com.hypixel.hytale.protocol.packets.player.ClientMovement 2>&1) -join "`n"
    $effects = (& javap -classpath $serverJar com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent 2>&1) -join "`n"
    $effectAsset = (& javap -classpath $serverJar com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect 2>&1) -join "`n"
    $utility = (& javap -classpath $serverJar com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent 2>&1) -join "`n"
    $npc = (& javap -classpath $serverJar com.hypixel.hytale.server.npc.role.Role 2>&1) -join "`n"
    $assetEntries = @(& jar tf $assetsZip)
    $stunPath = 'Server/Entity/Effects/Status/Stun.json'
    $stunJson = if ($stunPath -in $assetEntries) { (& tar -xOf $assetsZip $stunPath 2>&1) -join "`n" } else { '' }
    $api = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJar = $serverJar
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        damageBlockedMetadata = [bool]($damage -match 'MetaKey<java\.lang\.Boolean> BLOCKED')
        sweptCollision = [bool]($collision -match 'findCollisions')
        authoritativeMoveTo = [bool]($player -match '\bmoveTo\(')
        clientWishMovement = [bool]($movement -match '\bwishMovement;')
        durationOverrideEntityEffect = [bool]($effects -match 'addEffect\(.+float.+OverlapBehavior')
        entityEffectAssetMap = [bool]($effectAsset -match '\bgetAssetMap\(')
        authoritativeOffhand = [bool]($utility -match '\bgetActiveItem\(')
        npcProtectionFlag = [bool]($npc -match '\bisInvulnerable\(')
        verifiedStunAsset = [bool]($stunJson -match '"DisableAll"\s*:\s*true')
        stunAssetPath = $stunPath
        bossClassificationBoundary = 'No explicit public per-NPC boss flag found; no role-name heuristic is used. Stage 02 boss policy remains enforced when an authoritative caller supplies boss=true.'
    }
    $api | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R013'
        branch = (& git branch --show-current).Trim()
        sourceCommit = (& git rev-parse HEAD).Trim()
        buildSucceeded = $true
        jarPath = $jarPath
        jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0
        missingJarEntries = $missing
        canonicalSkillCount = $skills.Count
        canonicalPassiveCount = $passives.Count
        pilotSkillCount = $pilots.Count
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        playerStateSchema = 2
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    $apiFailures = @($api.GetEnumerator() | Where-Object { $_.Value -is [bool] -and -not $_.Value })
    if ($missing.Count -ne 0 -or $skills.Count -ne 87 -or $passives.Count -ne 66 -or $pilots.Count -ne 6 -or
        $failures -ne 0 -or $errors -ne 0 -or $result.canvasUiSourceChanged -or $apiFailures.Count -ne 0) {
        throw 'Stage 04 verification gate failed.'
    }
}
finally { Pop-Location }
