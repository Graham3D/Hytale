[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-03\R012'
$serverJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build
    if ($LASTEXITCODE -ne 0) { throw 'Stage 03 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.5.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json', 'rpg-build.properties',
        'Common/UI/Custom/RpgHud.ui', 'Common/UI/Custom/RpgCharacter.ui',
        'com/inigmasgames/hytalerpg/ui/RpgUiProjectionService.class',
        'com/inigmasgames/hytalerpg/ui/model/CharacterSheetViewModel.class',
        'com/inigmasgames/hytalerpg/ui/model/RpgHudViewModel.class',
        'com/inigmasgames/hytalerpg/ui/character/RpgCharacterPage.class',
        'com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.class',
        'com/inigmasgames/hytalerpg/ui/hud/HudVisibilityLease.class',
        'com/inigmasgames/hytalerpg/input/HytaleAbilitySkillInputAdapter.class',
        'com/inigmasgames/hytalerpg/input/RpgSkillActivationService.class',
        'com/inigmasgames/hytalerpg/progress/AttributeAllocationService.class',
        'com/inigmasgames/hytalerpg/ui/trace/RpgUiTraceService.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $skills = Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $testSuites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $interaction = (& javap -classpath $serverJar com.hypixel.hytale.protocol.InteractionType 2>&1) -join "`n"
    $hud = (& javap -classpath $serverJar com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager 2>&1) -join "`n"
    $customHud = (& javap -classpath $serverJar com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud 2>&1) -join "`n"
    $page = (& javap -classpath $serverJar com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage 2>&1) -join "`n"
    $api = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        serverJar = $serverJar
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        ability1To4 = @('Ability1','Ability2','Ability3','Ability4' | ForEach-Object { [bool]($interaction -match "\b$_\b") })
        selectiveHudVisibility = [bool]($hud -match 'getVisibleHudComponents' -and $hud -match 'setVisibleHudComponents' -and $hud -match 'hideHudComponents')
        customHudLifecycle = [bool]($customHud -match 'abstract void build' -and $customHud -match 'void update')
        interactiveCustomPage = [bool]($page -match 'handleDataEvent')
        globalCharacterLinkOpenBinding = $false
        globalOpenFallback = '/rpg character; Link Tree frontend deferred'
    }
    $api | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R012'
        branch = (& git branch --show-current).Trim()
        sourceCommit = (& git rev-parse HEAD).Trim()
        buildSucceeded = $true
        jarPath = $jarPath
        jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0
        missingJarEntries = $missing
        canonicalSkillCount = $skills.Count
        canonicalPassiveCount = $passives.Count
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        playerStateSchema = 2
        uiTracePath = 'logs/rpg/ui-trace.jsonl'
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $skills.Count -ne 87 -or $passives.Count -ne 66 -or
        $failures -ne 0 -or $errors -ne 0 -or $result.canvasUiSourceChanged -or
        $api.ability1To4 -contains $false -or -not $api.selectiveHudVisibility -or
        -not $api.customHudLifecycle -or -not $api.interactiveCustomPage) {
        throw 'Stage 03 verification gate failed.'
    }
}
finally { Pop-Location }
