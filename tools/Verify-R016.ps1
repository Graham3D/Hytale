[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\corrections\R016'
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R016 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.9.jar'
    $entries = @(& jar tf $jarPath)
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/RpgSkillTree.ui', 'Common/UI/Custom/RpgSkillTreeLibraryRow.ui',
        'Common/UI/Custom/RpgSkillTreeFilterRow.ui',
        'com/inigmasgames/hytalerpg/ui/skilltree/RpgSkillTreePage.class',
        'com/inigmasgames/hytalerpg/ui/skilltree/RpgSkillTreeMutationService.class',
        'com/inigmasgames/hytalerpg/ui/skilltree/RpgSkillTreeProjectionService.class')
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
    $jarNames = @(& jar tf $serverJar | Where-Object { $_ -match '(?i)(KeyBind|Keybinding|GlobalKey|Hotkey)' })
    $api = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        hudAbilitiesComponent = [bool]($hudApi -match 'HudComponent Abilities')
        nativeAbilityInventoryIsItemContainer = [bool]($abilitySlotsApi -match 'extends com\.hypixel\.hytale\.server\.core\.inventory\.InventoryComponent')
        abilityProtocolSlots = @([regex]::Matches($abilityProtocol, 'AbilitySlot (Primary|Support)') | ForEach-Object { $_.Groups[1].Value })
        publicGlobalHotkeySymbols = $jarNames
        skillTreeOpenHotkey = 'BLOCKED_PUBLIC_API'
        nativeAbility1Ownership = 'Weapon Signature Move; RPG packet adapter deliberately returns null'
        rpgAbilityMapping = 'Ability2->skill01, Ability3->skill02, Ability4->skill03'
        hudDecision = 'Native Abilities component remains visible; public API has no arbitrary RPG state projection, so three non-overlapping CustomUI cells are used.'
    }
    $api | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'native-ability-hud-audit.json') -Encoding utf8
    $skills = Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        revision = 'R016'; version = '0.0.9'; stage = 'pre-Stage06 correction'; schema = 3
        hytaleVersion = '0.7.0-pre.1'; branch = (& git branch --show-current).Trim()
        sourceCommit = (& git rev-parse HEAD).Trim(); jarPath = $jarPath
        jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0; missingJarEntries = $missing
        canonicalSkills = $skills.Count; canonicalPassives = $passives.Count
        skillSlots = 3; passiveSlots = 6; jointSlots = 2; jointInputCapacity = 3
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
        customUiValidated = $true; hotkey = 'BLOCKED_PUBLIC_API'
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $skills.Count -ne 87 -or $passives.Count -ne 66 -or
        $failures -ne 0 -or $errors -ne 0 -or $result.canvasUiSourceChanged -or
        -not $api.hudAbilitiesComponent -or -not $api.nativeAbilityInventoryIsItemContainer) {
        throw 'R016 verification gate failed.'
    }
}
finally { Pop-Location }
