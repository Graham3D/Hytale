[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-02\R010'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build
    if ($LASTEXITCODE -ne 0) { throw 'Stage 02 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.3.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json', 'rpg-build.properties', 'rpg-skill-trace.properties',
        'rpg/balance/combat-kernel-v1.json', 'rpg/balance/item-power-registry-v1.json',
        'Server/Entity/Stats/Mana.json', 'Server/Entity/Stats/Stamina.json',
        'com/inigmasgames/hytalerpg/combat/RpgCombatKernel.class',
        'com/inigmasgames/hytalerpg/combat/attribute/EffectiveAttributeService.class',
        'com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.class',
        'com/inigmasgames/hytalerpg/combat/resource/ReservationService.class',
        'com/inigmasgames/hytalerpg/combat/cooldown/RpgCooldownService.class',
        'com/inigmasgames/hytalerpg/combat/damage/DamageCalculationService.class',
        'com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageAdapter.class',
        'com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageLifecycleSystems$Gather.class',
        'com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageLifecycleSystems$Filter.class',
        'com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageLifecycleSystems$Application.class',
        'com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageLifecycleSystems$Inspect.class',
        'com/inigmasgames/hytalerpg/combat/status/StatusService.class',
        'com/inigmasgames/hytalerpg/combat/snapshot/CombatSnapshot.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $balance = Get-Content -Raw 'src\main\resources\rpg\balance\combat-kernel-v1.json' | ConvertFrom-Json
    $registry = Get-Content -Raw 'src\main\resources\rpg\balance\item-power-registry-v1.json' | ConvertFrom-Json
    $passives = Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json
    $testSuites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }
    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R010'
        branch = (& git branch --show-current).Trim()
        sourceCommit = (& git rev-parse HEAD).Trim()
        buildSucceeded = $true
        jarPath = $jarPath
        jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0
        missingJarEntries = $missing
        balanceProfile = $balance.profileId
        balanceSchema = $balance.schemaVersion
        itemRegistry = $registry.registryId
        itemRegistrySchema = $registry.schemaVersion
        developmentItemPowerRecords = @($registry.items).Count
        canonicalPassiveCount = $passives.Count
        swiftRecoveryAddedToCatalog = @($passives | Where-Object id -eq 'swift_recovery').Count -gt 0
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        playerStateSchema = 2
        compiledPlanSchema = 2
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $balance.schemaVersion -ne 1 -or $registry.schemaVersion -ne 1 -or
        $passives.Count -ne 66 -or $result.swiftRecoveryAddedToCatalog -or $failures -ne 0 -or $errors -ne 0) {
        throw 'Stage 02 verification gate failed.'
    }
}
finally { Pop-Location }
