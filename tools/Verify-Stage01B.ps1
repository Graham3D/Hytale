[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDirectory = Join-Path $projectRoot 'evidence\stage-01b\R009'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build
    if ($LASTEXITCODE -ne 0) { throw 'Stage 01B Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json',
        'rpg-build.properties',
        'rpg-skill-trace.properties',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'com/inigmasgames/hytalerpg/progress/RpgLoadoutService.class',
        'com/inigmasgames/hytalerpg/links/RpgLinkGraphService.class',
        'com/inigmasgames/hytalerpg/links/LinkCompiler.class',
        'com/inigmasgames/hytalerpg/commands/RpgCommand.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })
    $skills = @(Get-Content -Raw 'src\main\resources\rpg\catalog\skills.json' | ConvertFrom-Json)
    $passives = @(Get-Content -Raw 'src\main\resources\rpg\catalog\passives.json' | ConvertFrom-Json)
    $testSuites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }
    $sourceText = Get-ChildItem -LiteralPath 'src\main\java' -Recurse -Filter '*.java' | Get-Content -Raw
    $stage02Patterns = @('DamageSystems\.executeDamage', 'setStatValue\s*\(', 'addStatValue\s*\(', 'subtractStatValue\s*\(', 'spawnProjectile')
    $stage02Matches = @($stage02Patterns | Where-Object { $sourceText -match $_ })
    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R009'
        branch = (& git branch --show-current).Trim()
        startingCommit = (& git rev-parse HEAD).Trim()
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
        stage02PatternsAbsent = $stage02Matches.Count -eq 0
        stage02PatternsFound = $stage02Matches
        schemaVersion = 2
        edgeSchemaVersion = 1
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $skills.Count -ne 87 -or $passives.Count -ne 66 -or
        $failures -ne 0 -or $errors -ne 0 -or $stage02Matches.Count -ne 0) {
        throw 'Stage 01B verification gate failed.'
    }
}
finally { Pop-Location }
