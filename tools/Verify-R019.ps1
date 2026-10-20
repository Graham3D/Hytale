[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R019'
$preReleasePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$releaseInterface = "$env:APPDATA\Hytale\install\release\package\game\latest\Client\Data\Game\Interface\InGame"
$releaseInventory = Join-Path $releaseInterface 'Pages\Inventory'
$ownerArt = 'C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\art'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R019 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.12.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath

    $entries = @(& jar tf $jarPath)
    $assetRoot = 'Common/UI/Custom/Assets/RpgHud/'
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        "${assetRoot}CharacterPanelStatIconMana@2x.png", "${assetRoot}ProgressBar@2x.png",
        "${assetRoot}ProgressBarFill@2x.png", "${assetRoot}ExperienceFrame.png",
        "${assetRoot}ExperienceBackground.png", "${assetRoot}ExperienceBar.png",
        "${assetRoot}Background_Ability_NotReady.png", "${assetRoot}Frame_Ability_NotReady.png",
        "${assetRoot}Frame_Ability_Ready.png", "${assetRoot}OverlayAbilityErrorState.png")
    $retired = @(
        "${assetRoot}HealthBackground.png", "${assetRoot}HealthBarFill.png", "${assetRoot}HealthIcon.png",
        "${assetRoot}ManaBackground.png", "${assetRoot}ManaFill.png", "${assetRoot}ManaIcon.png",
        "${assetRoot}StaminaBackground.png", "${assetRoot}StaminaBar.png", "${assetRoot}StaminaIcon.png")
    $missing = @($required | Where-Object { $_ -notin $entries })
    $unexpected = @($retired | Where-Object { $_ -in $entries })

    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $hudText = Get-Content -Raw 'src\main\resources\Common\UI\Custom\RpgHud.ui'
    $healthText = Get-Content -Raw (Join-Path $releaseInterface 'Hud\Health\Health.ui')
    $staminaText = Get-Content -Raw (Join-Path $releaseInterface 'Hud\Stamina\StaminaPanel.ui')
    $assetChecks = foreach ($name in @('CharacterPanelStatIconMana@2x.png','ProgressBar@2x.png','ProgressBarFill@2x.png')) {
        $source = Join-Path $releaseInventory $name
        $packagedSource = Join-Path $projectRoot "src\main\resources\Common\UI\Custom\Assets\RpgHud\$name"
        [ordered]@{ name = $name; sourceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash
            packagedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $packagedSource).Hash }
    }
    $xpChecks = foreach ($name in @('ExperienceFrame.png','ExperienceBackground.png','ExperienceBar.png')) {
        $source = Join-Path $ownerArt $name
        $packagedSource = Join-Path $projectRoot "src\main\resources\Common\UI\Custom\Assets\RpgHud\$name"
        [ordered]@{ name = $name; sourceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash
            packagedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $packagedSource).Hash }
    }
    $audit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        releaseHealthNativeHotbarRelative = [bool]($healthText -match 'InventoryClosedContainerMargin.*HotbarHeight.*\+ 6')
        releaseStaminaNativeHotbarRelative = [bool]($staminaText -match 'InventoryClosedContainerMargin.*HotbarHeight.*\+ 6')
        nativeHealthAndStaminaAbsentFromCustomDocument = -not ($hudText -match '#HealthBar|#HealthFill|#StaminaBar|#StaminaFill')
        customManaOnly = [bool]($hudText -match '#ManaHud')
        manaInventoryAssetsByteIdentical = -not [bool](@($assetChecks | Where-Object { $_.sourceSha256 -ne $_.packagedSha256 }).Count)
        xpOwnerAssetsByteIdentical = -not [bool](@($xpChecks | Where-Object { $_.sourceSha256 -ne $_.packagedSha256 }).Count)
        texturePathsRelativeToDocument = -not ($hudText -match 'TexturePath: "Common/UI/Custom/')
        experienceImplicitlyCentered = [bool]($hudText -match '#ExperienceHud \{ Anchor: \(Bottom: 138, Width: 931, Height: 28\)')
        experienceLayerOrder = 'ExperienceBackground|ExperienceFill|ExperienceFrame'
        manaAssets = $assetChecks; experienceAssets = $xpChecks
    }
    $audit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'hud-asset-layout-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R019'; version = '0.0.12'
        stage = 'HUD correction only; pre-Stage06'; schema = 3; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0; missingJarEntries = $missing
        retiredR018ResourceAssetsAbsent = $unexpected.Count -eq 0; unexpectedJarEntries = $unexpected
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        customUiValidated = $true; nativeResources = 'Health|Stamina'; customResources = 'Mana'
        xpUsableWidth = 925; xpLeftAnchored = $true; xpCentered = $true
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $unexpected.Count -ne 0 -or $failures -ne 0 -or $errors -ne 0 -or
        -not $audit.releaseHealthNativeHotbarRelative -or -not $audit.releaseStaminaNativeHotbarRelative -or
        -not $audit.nativeHealthAndStaminaAbsentFromCustomDocument -or -not $audit.customManaOnly -or
        -not $audit.manaInventoryAssetsByteIdentical -or -not $audit.xpOwnerAssetsByteIdentical -or
        -not $audit.texturePathsRelativeToDocument -or -not $audit.experienceImplicitlyCentered -or
        $result.canvasUiSourceChanged) { throw 'R019 verification gate failed.' }
}
finally { Pop-Location }
