[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R021'
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assetsZip = Join-Path $package 'Assets.zip'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R021 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.14.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath

    $entries = @(& jar tf $jarPath)
    $nativeItems = @($entries | Where-Object { $_ -match '^Server/Item/Items/RPG/Abilities/RPG_Ability_.+\.json$' })
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/Assets/RpgHud/ExperienceFrame.png',
        'Common/UI/Custom/Assets/RpgHud/ExperienceBackground.png',
        'Common/UI/Custom/Assets/RpgHud/ExperienceBar.png',
        'Server/Item/RootInteractions/RPG/Root_RPG_Ability_Bridge.json',
        'Server/Languages/en-US/server.lang')
    $retired = @(
        'Common/UI/Custom/Assets/RpgHud/Background_Ability_NotReady.png',
        'Common/UI/Custom/Assets/RpgHud/Frame_Ability_NotReady.png',
        'Common/UI/Custom/Assets/RpgHud/Frame_Ability_Ready.png',
        'Common/UI/Custom/Assets/RpgHud/OverlayAbilityErrorState.png')
    $hud = Get-Content -Raw 'src\main\resources\Common\UI\Custom\RpgHud.ui'
    $hudRuntime = Get-Content -Raw 'src\main\java\com\inigmasgames\hytalerpg\ui\hud\RpgHud.java'
    $hudCoordinator = Get-Content -Raw 'src\main\java\com\inigmasgames\hytalerpg\ui\hud\RpgHudCoordinator.java'
    $plugin = Get-Content -Raw 'src\main\java\com\inigmasgames\hytalerpg\phase00\Phase00Plugin.java'
    $itemBodies = @(Get-ChildItem 'src\main\resources\Server\Item\Items\RPG\Abilities' -Filter '*.json' -File |
        ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName })
    $itemAssets = @($itemBodies | ForEach-Object { $_ | ConvertFrom-Json })
    $root = Get-Content -Raw 'src\main\resources\Server\Item\RootInteractions\RPG\Root_RPG_Ability_Bridge.json'

    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $apiAudit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        target = '0.7.0-pre.1'
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        assetsZipSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetsZip).Hash
        abilityLines = 2; abilityLineWidth = 3; primaryIndices = @(0,3)
        nativeHudItemAbilitySlots = @('Ability2','Ability3')
        ability4Result = 'NATIVE_ABILITY4_UNAVAILABLE'
        officialDocs = @(
            'https://hytale.com/news/2026/9/pre-release-patch-notes-update-7',
            'https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/ItemAbility',
            'https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/inventory/InventoryComponent.AbilitySlots',
            'https://pre-release.docs.hytale.com/api/com/hypixel/hytale/builtin/abilities/window/AbilityBenchWindow',
            'https://pre-release.docs.hytale.com/api/com/hypixel/hytale/builtin/abilities/AbilitiesPlugin')
    }
    $apiAudit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'native-ability-api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R021'; version = '0.0.14'
        stage = 'native ability correction only; pre-Stage06'; schema = 3; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        requiredEntriesPresent = (@($required | Where-Object { $_ -notin $entries }).Count -eq 0)
        nativeItemAssetCount = $nativeItems.Count
        retiredAbilityArtAbsent = (@($retired | Where-Object { $_ -in $entries }).Count -eq 0)
        customAbilityControlsAbsent = -not [bool]($hud -match '#RpgAbility|#Skill[123](Action|Icon|Cooldown|Unavailable|ReadyFrame|NotReadyFrame|Name|State)')
        customAbilityUpdateCodeAbsent = -not [bool](($hudRuntime + $hudCoordinator) -match 'writeSkills|ABILITY_HUD_REFRESH|ABILITY_SLOT_CHANGED')
        singleAuthoritativeNativeObserver = [bool]($plugin -match 'new HytaleAbilitySkillInputAdapter\(nativeAbilities::observeInput\)') -and
            -not [bool]($plugin -match 'AbilityInputObserver\.observe|new AbilityInputsProbeCommand')
        triggerAssetsZeroNativeAuthority = ($itemAssets.Count -eq 12) -and
            (@($itemAssets | Where-Object { $_.Ability.Cooldown -ne 0 -or $_.Ability.Cost -ne 0 -or
                $_.Ability.CostType -ne 'None' -or $_.Ability.Cast -ne 'Root_RPG_Ability_Bridge' }).Count -eq 0)
        rootInteractionIsEffectFree = [bool]($root -match '"Type"\s*:\s*"Simple"') -and -not [bool]($root -match 'Damage|ChangeStat|TriggerCooldown|Launch')
        xpGeometryPreserved = [bool]($hud -match '#ExperienceHud \{ Anchor: \(Bottom: 138, Width: 702, Height: 28\)') -and
            [bool]($hud -match '#ExperienceBackground \{ Anchor: \(Left: 3, Top: 0, Width: 696, Height: 28\)') -and
            [bool]($hud -match '#ExperienceFill \{ Anchor: \(Left: 3, Top: 3, Width: 0, Height: 22\)')
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
        connectedClientRequired = $true
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($failures -ne 0 -or $errors -ne 0 -or -not $result.requiredEntriesPresent -or
        $result.nativeItemAssetCount -ne 12 -or -not $result.retiredAbilityArtAbsent -or
        -not $result.customAbilityControlsAbsent -or -not $result.customAbilityUpdateCodeAbsent -or
        -not $result.singleAuthoritativeNativeObserver -or -not $result.triggerAssetsZeroNativeAuthority -or
        -not $result.rootInteractionIsEffectFree -or -not $result.xpGeometryPreserved -or
        $result.canvasUiSourceChanged) { throw 'R021 verification gate failed.' }
}
finally { Pop-Location }
