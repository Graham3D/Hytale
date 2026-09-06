[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-05\R015'
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$assetsZip = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip"
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

function Read-ZipJson([string]$ArchivePath, [string]$EntryPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $entry = $archive.GetEntry($EntryPath)
        if ($null -eq $entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
        finally { $reader.Dispose() }
    }
    finally { $archive.Dispose() }
}

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Stage 05 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.8.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json', 'rpg-build.properties', 'rpg/runtime/stage-04-skills.json',
        'rpg/runtime/stage-05-projectiles.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fire_Bolt.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Frost_Bolt.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Arcane_Bolt.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Stone_Bolt.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Quick_Shot_Bow.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Quick_Shot_Crossbow.json',
        'Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Axe_Toss.json',
        'Server/Models/Projectiles/RPG_Fire_Bolt.json',
        'Server/Models/Projectiles/RPG_Frost_Bolt.json',
        'Server/Models/Projectiles/RPG_Arcane_Bolt.json',
        'Server/Models/Projectiles/RPG_Stone_Bolt.json',
        'Server/Models/Projectiles/RPG_Quick_Shot_Arrow.json',
        'Server/Models/Projectiles/RPG_Axe_Toss.json',
        'Server/Entity/Effects/RPG/RPG_Burn_Visual.json',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.class',
        'com/inigmasgames/hytalerpg/execution/hytale/HytaleAmmoAdapter.class',
        'com/inigmasgames/hytalerpg/execution/projectile/ProjectileFlight.class',
        'com/inigmasgames/hytalerpg/execution/projectile/ProjectileExecutionPlan.class',
        'com/inigmasgames/hytalerpg/execution/projectile/ProjectileLifecycleRegistry.class',
        'com/inigmasgames/hytalerpg/execution/projectile/RpgProjectileService.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $skills = Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $stage04 = (Get-Content -Raw 'src\main\resources\rpg\runtime\stage-04-skills.json' | ConvertFrom-Json).skills
    $stage05 = (Get-Content -Raw 'src\main\resources\rpg\runtime\stage-05-projectiles.json' | ConvertFrom-Json).skills
    $fire = $stage05 | Where-Object skillId -eq 'fire_bolt'
    $frost = $stage05 | Where-Object skillId -eq 'frost_bolt'
    $arcane = $stage05 | Where-Object skillId -eq 'arcane_bolt'
    $stone = $stage05 | Where-Object skillId -eq 'stone_bolt'
    $quick = $stage05 | Where-Object skillId -eq 'quick_shot'
    $axe = $stage05 | Where-Object skillId -eq 'axe_toss'
    $fireExact = $fire.resourceCost -eq 8 -and $fire.cooldownSeconds -eq 1.4 -and
        $fire.projectile.speed -eq 24 -and $fire.projectile.maxDistance -eq 24 -and
        $fire.projectile.radius -eq 0.30 -and $fire.projectile.coefficient -eq 0.95 -and
        $fire.projectile.periodicCoefficient -eq 0.10 -and $fire.projectile.periodicTicks -eq 4
    $frostExact = $frost.resourceCost -eq 8 -and $frost.cooldownSeconds -eq 1.5 -and
        $frost.projectile.speed -eq 22 -and $frost.projectile.maxDistance -eq 24 -and
        $frost.projectile.radius -eq 0.30 -and $frost.projectile.coefficient -eq 0.85 -and
        $frost.projectile.statusId -eq 'CHILL'
    $arcaneExact = $arcane.resourceCost -eq 7 -and $arcane.cooldownSeconds -eq 1.2 -and
        $arcane.projectile.speed -eq 26 -and $arcane.projectile.maxDistance -eq 26 -and
        $arcane.projectile.radius -eq 0.28 -and $arcane.projectile.coefficient -eq 0.90
    $stoneExact = $stone.resourceCost -eq 8 -and $stone.cooldownSeconds -eq 2.2 -and
        $stone.projectile.speed -eq 17 -and $stone.projectile.maxDistance -eq 20 -and
        $stone.projectile.radius -eq 0.45 -and $stone.projectile.coefficient -eq 1.20 -and
        $stone.projectile.knockbackDistance -eq 1.5
    $quickExact = $quick.resourceCost -eq 4 -and $quick.cooldownSeconds -eq 0.9 -and
        $quick.projectile.maxDistance -eq 28 -and $quick.projectile.radius -eq 0.075 -and
        $quick.projectile.coefficient -eq 0.80 -and
        $quick.projectile.ammoItemId -eq 'Weapon_Arrow_Crude' -and $quick.projectile.ammoQuantity -eq 1 -and
        $quick.projectile.speedsByWeaponKind.BOW -eq 30 -and $quick.projectile.speedsByWeaponKind.CROSSBOW -eq 40
    $axeExact = $axe.resourceCost -eq 8 -and $axe.cooldownSeconds -eq 5 -and
        $axe.projectile.speed -eq 18 -and $axe.projectile.maxDistance -eq 20 -and
        $axe.projectile.radius -eq 0.45 -and $axe.projectile.coefficient -eq 1.20 -and
        $axe.projectile.ammoQuantity -eq 0

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
    $nativeArrowBase = Read-ZipJson $assetsZip 'Server/ProjectileConfigs/Weapons/Arrows/Projectile_Config_Arrow_Base.json'
    $nativeCrossbow = Read-ZipJson $assetsZip 'Server/ProjectileConfigs/Weapons/Arrows/Projectile_Config_Arrow_Crossbow.json'
    $nativeArrowModel = Read-ZipJson $assetsZip 'Server/Models/Projectiles/Weapons/Arrow/Arrow_Crude.json'
    $skeletonScoutShot = Read-ZipJson $assetsZip 'Server/Item/Interactions/NPCs/Undead/Skeleton_Scout/Skeleton_Scout_Bow_Shoot.json'
    $skeletonScoutProjectile = Read-ZipJson $assetsZip 'Server/Projectiles/NPCs/Undead/Skeleton_Scout/Skeleton_Scout_Arrow.json'
    $api = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJar = $serverJar
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        nativeProjectileSpawn = [bool]($projectileModule -match 'spawnProjectile\(.+ProjectileConfig.+Vector3d.+Vector3d')
        nativeStandardPhysicsProvider = [bool]($projectileModule -match 'getStandardPhysicsProviderComponentType')
        nativeImpactCallback = [bool]($physics -match 'setImpactConsumer' -and $impact -match 'onImpact\(')
        authoritativeCombinedInventory = [bool]($inventory -match 'getCombined\(' -and $inventory -match 'HOTBAR_STORAGE_BACKPACK')
        atomicAmmoRemoval = [bool]($container -match 'removeItemStack\(' -and $container -match 'addItemStack\(')
        nativeBowLaunchForce30 = [bool]($nativeArrowBase.LaunchForce -eq 30)
        nativeCrossbowLaunchForce40 = [bool]($nativeCrossbow.LaunchForce -eq 40)
        nativeArrowHitboxRadius0075 = [bool]($nativeArrowModel.HitBox.Max.X -eq 0.075 -and $nativeArrowModel.HitBox.Min.X -eq -0.075)
        skeletonScoutSignatureSource = [bool]($skeletonScoutShot.Next.ProjectileId -eq 'Skeleton_Scout_Arrow' -and
            $skeletonScoutProjectile.Parent -eq 'Arrow_FullCharge')
        crudeArrowAsset = [bool]('Server/Item/Items/Weapon/Arrow/Weapon_Arrow_Crude.json' -in $assetEntries)
        fireProjectileParticle = [bool]('Server/Particles/Combat/Fire_Stick/Fire_Charged1.particlesystem' -in $assetEntries)
        burnVisualParticle = [bool]('Server/Particles/Status_Effect/Fire/Effect_Fire.particlesystem' -in $assetEntries)
        burnVisualModel = [bool]('Server/Entity/ModelVFX/Burn.json' -in $assetEntries)
        fireballModelSource = [bool]('Common/Items/Projectiles/Fireball.blockymodel' -in $assetEntries)
        arrowModelSource = [bool]('Common/Items/Projectiles/Projectile.blockymodel' -in $assetEntries)
        frostProjectileParticle = [bool]('Server/Particles/Block/Crystal/Block_Gem_Sparks.particlesystem' -in $assetEntries)
        dustProjectileParticle = [bool]('Server/Particles/Dust_Sparkles_Fine.particlesystem' -in $assetEntries)
        stoneProjectileParticle = [bool]('Server/Particles/Block/Stone/Block_Break_Stone.particlesystem' -in $assetEntries)
        quickShotParticle = [bool]('Server/Particles/Weapon/Bow/Bow_Signature_Projectile_Sparks.particlesystem' -in $assetEntries)
        axeImpactParticle = [bool]('Server/Particles/Combat/Sword/Basic/Impact_Blade_01.particlesystem' -in $assetEntries)
        iceBoltModelSource = [bool]('Common/Items/Projectiles/Ice_Bolt.blockymodel' -in $assetEntries)
        stoneModelSource = [bool]('Common/Items/Projectiles/Stone.blockymodel' -in $assetEntries)
        axeModelSource = [bool]('Common/NPC/Intelligent/Trork/Models/Weapons/Axe/Stone.blockymodel' -in $assetEntries)
    }
    $api | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R015'
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
        frostBoltExact = $frostExact
        arcaneBoltExact = $arcaneExact
        stoneBoltExact = $stoneExact
        quickShotExact = $quickExact
        axeTossExact = $axeExact
        snipeExecutable = [bool]($stage05.skillId -contains 'snipe')
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
        $stage04.Count -ne 6 -or $stage05.Count -ne 6 -or -not $fireExact -or -not $frostExact -or
        -not $arcaneExact -or -not $stoneExact -or -not $quickExact -or -not $axeExact -or
        $result.snipeExecutable -or
        $failures -ne 0 -or $errors -ne 0 -or $result.canvasUiSourceChanged -or $apiFailures.Count -ne 0) {
        throw 'Stage 05 verification gate failed.'
    }
}
finally { Pop-Location }
