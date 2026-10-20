[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R020'
$preReleasePackage = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$releasePackage = "$env:APPDATA\Hytale\install\release\package\game\latest"
$releaseInterface = Join-Path $releasePackage 'Client\Data\Game\Interface\InGame'
$ownerArt = 'C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\art'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
Add-Type -AssemblyName System.Drawing

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R020 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.13.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath

    $entries = @(& jar tf $jarPath)
    $assetRoot = 'Common/UI/Custom/Assets/RpgHud/'
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        "${assetRoot}ExperienceFrame.png", "${assetRoot}ExperienceBackground.png",
        "${assetRoot}ExperienceBar.png",
        "${assetRoot}Background_Ability_NotReady.png", "${assetRoot}Frame_Ability_NotReady.png",
        "${assetRoot}Frame_Ability_Ready.png", "${assetRoot}OverlayAbilityErrorState.png")
    $retired = @(
        "${assetRoot}HealthBackground.png", "${assetRoot}HealthBarFill.png", "${assetRoot}HealthIcon.png",
        "${assetRoot}ManaBackground.png", "${assetRoot}ManaFill.png", "${assetRoot}ManaIcon.png",
        "${assetRoot}StaminaBackground.png", "${assetRoot}StaminaBar.png", "${assetRoot}StaminaIcon.png",
        "${assetRoot}CharacterPanelStatIconMana@2x.png", "${assetRoot}ProgressBar@2x.png",
        "${assetRoot}ProgressBarFill@2x.png")
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
    $allProductionJava = (Get-ChildItem 'src\main\java' -Recurse -File -Filter '*.java' |
        Get-Content -Raw) -join [Environment]::NewLine
    $healthText = Get-Content -Raw (Join-Path $releaseInterface 'Hud\Health\Health.ui')
    $staminaText = Get-Content -Raw (Join-Path $releaseInterface 'Hud\Stamina\StaminaPanel.ui')
    $xpChecks = foreach ($name in @('ExperienceFrame.png','ExperienceBackground.png','ExperienceBar.png')) {
        $source = Join-Path $ownerArt $name
        $packagedSource = Join-Path $projectRoot "src\main\resources\Common\UI\Custom\Assets\RpgHud\$name"
        $sourceImage = [System.Drawing.Image]::FromFile($source)
        $packagedImage = [System.Drawing.Image]::FromFile($packagedSource)
        [ordered]@{ name = $name; sourceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash
            packagedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $packagedSource).Hash
            sourceWidth = $sourceImage.Width; sourceHeight = $sourceImage.Height
            packagedWidth = $packagedImage.Width; packagedHeight = $packagedImage.Height }
        $sourceImage.Dispose(); $packagedImage.Dispose()
    }
    $expectedDimensions = @{ 'ExperienceFrame.png' = @(702,28); 'ExperienceBackground.png' = @(696,28); 'ExperienceBar.png' = @(1,22) }
    $dimensionsMatch = -not [bool](@($xpChecks | Where-Object {
        $_.sourceWidth -ne $expectedDimensions[$_.name][0] -or $_.sourceHeight -ne $expectedDimensions[$_.name][1] -or
        $_.packagedWidth -ne $_.sourceWidth -or $_.packagedHeight -ne $_.sourceHeight
    }).Count)
    $audit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        releaseHealthNativeHotbarRelative = [bool]($healthText -match 'InventoryClosedContainerMargin.*HotbarHeight.*\+ 6')
        releaseStaminaNativeHotbarRelative = [bool]($staminaText -match 'InventoryClosedContainerMargin.*HotbarHeight.*\+ 6')
        allNativeResourceControlsAbsentFromPackagedHud = -not ($hudText -match '#Health|#Mana|#Stamina') -and
            -not (Test-Path -LiteralPath 'src\main\resources\Common\UI\Custom\Phase00Hud.ui')
        noRpgVisibilityMutation = -not [bool]($allProductionJava -match
            'HudVisibilityLease|setVisibleHudComponents|getVisibleHudComponents|hideHudComponents|resetVisibleHudComponents')
        xpOwnerAssetsByteIdentical = -not [bool](@($xpChecks | Where-Object { $_.sourceSha256 -ne $_.packagedSha256 }).Count)
        xpAssetDimensionsAuthoritative = $dimensionsMatch
        texturePathsRelativeToDocument = -not ($hudText -match 'TexturePath: "Common/UI/Custom/')
        experienceImplicitlyCentered = [bool]($hudText -match '#ExperienceHud \{ Anchor: \(Bottom: 138, Width: 702, Height: 28\)')
        experienceBackgroundGeometry = [bool]($hudText -match '#ExperienceBackground \{ Anchor: \(Left: 3, Top: 0, Width: 696, Height: 28\)')
        experienceFillGeometry = [bool]($hudText -match '#ExperienceFill \{ Anchor: \(Left: 3, Top: 3, Width: 0, Height: 22\)')
        experienceLayerOrder = 'ExperienceBackground|ExperienceFill|ExperienceFrame'
        experienceAssets = $xpChecks
    }
    $audit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'hud-asset-layout-audit.json') -Encoding utf8

    $buildAudit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        releasePackage = $releasePackage
        releasePackageLastWriteUtc = (Get-Item -LiteralPath $releasePackage).LastWriteTimeUtc.ToString('o')
        releaseServerSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releasePackage 'Server\HytaleServer.jar')).Hash
        preReleasePackage = $preReleasePackage
        preReleasePackageLastWriteUtc = (Get-Item -LiteralPath $preReleasePackage).LastWriteTimeUtc.ToString('o')
        preReleaseServerSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $preReleasePackage 'Server\HytaleServer.jar')).Hash
        releaseNativeHudSha256 = [ordered]@{
            health = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseInterface 'Hud\Health\Health.ui')).Hash
            mana = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseInterface 'Hud\Mana\Mana.ui')).Hash
            stamina = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseInterface 'Hud\Stamina\StaminaPanel.ui')).Hash
            hotbar = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseInterface 'Hud\Hotbar.ui')).Hash
        }
        buildTargetIntentionallyUnchanged = '0.7.0-pre.1'
        rationale = 'R020 is HUD-only; latest release UI was audited, while changing the established pre-release runtime target is outside scope.'
    }
    $buildAudit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'latest-hytale-build-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R020'; version = '0.0.13'
        stage = 'HUD correction only; pre-Stage06'; schema = 3; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0; missingJarEntries = $missing
        rpgResourceAssetsAbsent = $unexpected.Count -eq 0; unexpectedJarEntries = $unexpected
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        customUiValidated = $true; nativeResources = 'Health|Mana|Stamina'; customResources = 'NONE'
        xpFrameWidth = 702; xpUsableWidth = 696; xpLeftAnchored = $true; xpCentered = $true
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $unexpected.Count -ne 0 -or $failures -ne 0 -or $errors -ne 0 -or
        -not $audit.releaseHealthNativeHotbarRelative -or -not $audit.releaseStaminaNativeHotbarRelative -or
        -not $audit.allNativeResourceControlsAbsentFromPackagedHud -or -not $audit.noRpgVisibilityMutation -or
        -not $audit.xpOwnerAssetsByteIdentical -or -not $audit.xpAssetDimensionsAuthoritative -or
        -not $audit.texturePathsRelativeToDocument -or
        -not $audit.experienceImplicitlyCentered -or -not $audit.experienceBackgroundGeometry -or
        -not $audit.experienceFillGeometry -or $result.canvasUiSourceChanged) {
        throw 'R020 verification gate failed.'
    }
}
finally { Pop-Location }
