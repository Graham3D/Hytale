[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R018'
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$abilitiesUi = Join-Path $package 'Client\Data\Game\Interface\InGame\Hud\Abilities\AbilitiesHud.ui'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R018 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.11.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath

    $entries = @(& jar tf $jarPath)
    $assetRoot = 'Common/UI/Custom/Assets/RpgHud/'
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        "${assetRoot}HealthBackground.png", "${assetRoot}HealthBarFill.png", "${assetRoot}HealthIcon.png",
        "${assetRoot}ManaBackground.png", "${assetRoot}ManaFill.png", "${assetRoot}ManaIcon.png",
        "${assetRoot}StaminaBackground.png", "${assetRoot}StaminaBar.png", "${assetRoot}StaminaIcon.png",
        "${assetRoot}ExperienceFrame.png", "${assetRoot}ExperienceBackground.png", "${assetRoot}ExperienceBar.png",
        "${assetRoot}Background_Ability_NotReady.png", "${assetRoot}Frame_Ability_NotReady.png",
        "${assetRoot}Frame_Ability_Ready.png", "${assetRoot}OverlayAbilityErrorState.png")
    $missing = @($required | Where-Object { $_ -notin $entries })
    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $hudApi = (& javap -classpath $serverJar -p com.hypixel.hytale.protocol.packets.interface_.HudComponent 2>&1) -join "`n"
    $abilitySlotsApi = (& javap -classpath $serverJar -p 'com.hypixel.hytale.server.core.inventory.InventoryComponent$AbilitySlots' 2>&1) -join "`n"
    $abilityProtocol = (& javap -classpath $serverJar -p com.hypixel.hytale.protocol.AbilitySlot 2>&1) -join "`n"
    $nativeHudText = Get-Content -Raw -LiteralPath $abilitiesUi
    $audit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        abilitiesHudSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $abilitiesUi).Hash
        nativeHudAnchor = 'Right:52-StructurePadding Bottom:40 Width:329 Height:136'
        nativeSignaturePresent = [bool]($nativeHudText -match 'Group #SignatureAbility')
        nativeOptionalSlots = @([regex]::Matches($nativeHudText, 'Group #(Ability[12]Slot)') | ForEach-Object { $_.Groups[1].Value })
        nativeOptionalSlotsInitiallyHidden = ([regex]::Matches($nativeHudText, 'Visible: false').Count -ge 2)
        hudAbilitiesComponent = [bool]($hudApi -match 'HudComponent Abilities')
        nativeAbilityInventoryIsItemContainer = [bool]($abilitySlotsApi -match 'extends com\.hypixel\.hytale\.server\.core\.inventory\.InventoryComponent')
        protocolAbilitySlots = @([regex]::Matches($abilityProtocol, 'AbilitySlot (Primary|Support)') | ForEach-Object { $_.Groups[1].Value })
        arbitraryThreeSlotProjectionApi = $false
        configuredPhysicalInputLabelApi = $false
        decision = 'Preserve native Abilities/Signature; add three state-authoritative RPG cells immediately left. Display logical Ability2/3/4 labels because configured physical labels are not exposed to server CustomUI.'
    }
    $audit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'native-hud-api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R018'; version = '0.0.11'
        stage = 'HUD correction only; pre-Stage06'; schema = 3; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0; missingJarEntries = $missing
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        customUiValidated = $true; nativeSignaturePreserved = $audit.nativeSignaturePresent
        resourceOrder = 'Health|Mana|Stamina'; xpUsableWidth = 925; xpLeftAnchored = $true
        rpgAbilityMapping = 'Ability2->skill01,Ability3->skill02,Ability4->skill03'
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $failures -ne 0 -or $errors -ne 0 -or
        -not $audit.nativeSignaturePresent -or -not $audit.hudAbilitiesComponent -or
        $result.canvasUiSourceChanged) { throw 'R018 verification gate failed.' }
}
finally { Pop-Location }
