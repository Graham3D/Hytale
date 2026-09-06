[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-05\R014'
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$assetsZip = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip"
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Stage 05 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.7.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json', 'rpg-build.properties', 'rpg/runtime/stage-04-skills.json',
        'rpg/runtime/stage-05-projectiles.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fire_Bolt.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Snipe.json',
        'Server/Models/Projectiles/RPG_Fire_Bolt.json',
        'Server/Models/Projectiles/RPG_Snipe_Arrow.json',
        'Server/Entity/Effects/RPG/RPG_Burn_Visual.json',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.class',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleAmmoAdapter.class',
        'com/inigmasgames/hytalerpg/execution/projectile/ProjectileFlight.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $skills = Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $stage04 = (Get-Content -Raw 'src\main\resources\rpg\runtime\stage-04-skills.json' | ConvertFrom-Json).skills
    $stage05 = (Get-Content -Raw 'src\main\resources\rpg\runtime\stage-05-projectiles.json' | ConvertFrom-Json).skills
    $fire = $stage05 | Where-Object skillId -eq 'fire_bolt'
    $snipe = $stage05 | Where-Object skillId -eq 'snipe'
    $fireExact = $fire.resourceCost -eq 8 -and $fire.cooldownSeconds -eq 1.4 -and
        $fire.projectile.speed -eq 24 -and $fire.projectile.maxDistance -eq 24 -and
        $fire.projectile.radius -eq 0.30 -and $fire.projectile.coefficient -eq 0.95 -and
        $fire.projectile.periodicCoefficient -eq 0.10 -and $fire.projectile.periodicTicks -eq 4
    $snipeExact = $snipe.resourceCost -eq 12 -and $snipe.cooldownSeconds -eq 10 -and
        $snipe.projectile.speed -eq 45 -and $snipe.projectile.maxDistance -eq 48 -and
        $snipe.projectile.radius -eq 0.10 -and $snipe.projectile.coefficient -eq 2.00 -and
        $snipe.projectile.ammoItemId -eq 'Weapon_Arrow_Crude' -and
        $snipe.projectile.ammoQuantity -eq 1 -and $snipe.projectile.fullyCharged

    $testSuites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $projectileModule = (& javap -classpath $serverJar com.hypixel.hytale.server.core.modules.projectile.ProjectileModule 2>&1) -join "`n"
    $physics = (& javap -classpath $serverJar com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider 2>&1) -join "`n"
    $impact = (& javap -classpath $serverJar com.hypixel.hytale.server.core.modules.projectile.config.ImpactConsumer 2>&1) -join "`n"
    $inventory = (& javap -classpath $serverJar com.hypixel.hytale.server.core.inventory.InventoryComponent 2>&1) -join "`n"
    $container = (& javap -classpath $serverJar com.hypixel.hytale.server.core.inventory.container.ItemContainer 2>&1) -join "`n"
    $assetEntries = @(& jar tf $assetsZip)
    $api = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJar = $serverJar
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        nativeProjectileSpawn = [bool]($projectileModule -match 'spawnProjectile\(.+ProjectileConfig.+Vector3d.+Vector3d')
        nativeStandardPhysicsProvider = [bool]($projectileModule -match 'getStandardPhysicsProviderComponentType')
        nativeImpactCallback = [bool]($physics -match 'setImpactConsumer' -and $impact -match 'onImpact\(')
        authoritativeCombinedInventory = [bool]($inventory -match 'getCombined\(' -and $inventory -match 'HOTBAR_STORAGE_BACKPACK')
        atomicAmmoRemoval = [bool]($container -match 'removeItemStack\(' -and $container -match 'addItemStack\(')
        crudeArrowAsset = [bool]('Server/Item/Items/Weapon/Arrow/Weapon_Arrow_Crude.json' -in $assetEntries)
        fireProjectileParticle = [bool]('Server/Particles/_Test/Fire/Fire_Projectile.particlesystem' -in $assetEntries)
        burnVisualParticle = [bool]('Server/Particles/Status_Effect/Fire/Effect_Fire.particlesystem' -in $assetEntries)
        burnVisualModel = [bool]('Server/Entity/ModelVFX/Burn.json' -in $assetEntries)
        fireballModelSource = [bool]('Common/Items/Projectiles/Fireball.blockymodel' -in $assetEntries)
        arrowModelSource = [bool]('Common/Items/Projectiles/Projectile.blockymodel' -in $assetEntries)
        snipeProjectileParticle = [bool]('Server/Particles/Weapon/Bow/Bow_Signature_Projectile_Sparks.particlesystem' -in $assetEntries)
    }
    $api | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R014'
        branch = (& git branch --show-current).Trim()
        sourceCommit = (& git rev-parse HEAD).Trim()
        buildSucceeded = $true
        jarPath = $jarPath
        jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0
        missingJarEntries = $missing
        canonicalSkillCount = $skills.Count
        canonicalPassiveCount = $passives.Count
        retainedStage04PilotCount = $stage04.Count
        stage05PilotCount = $stage05.Count
        fireBoltExact = $fireExact
        snipeExact = $snipeExact
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
    if ($missing.Count -ne 0 -or $skills.Count -ne 87 -or $passives.Count -ne 66 -or
        $stage04.Count -ne 6 -or $stage05.Count -ne 2 -or -not $fireExact -or -not $snipeExact -or
        $failures -ne 0 -or $errors -ne 0 -or $result.canvasUiSourceChanged -or $apiFailures.Count -ne 0) {
        throw 'Stage 05 verification gate failed.'
    }
}
finally { Pop-Location }
