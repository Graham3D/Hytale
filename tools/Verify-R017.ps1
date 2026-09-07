[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R017'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R017 Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.10.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath
    if ($LASTEXITCODE -ne 0) { throw 'R017 packaged CustomUI validation failed.' }

    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }
    $entries = @(& jar tf $jarPath)
    $required = @(
        'manifest.json', 'rpg-build.properties', 'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/RpgSkillTree.ui', 'Common/UI/Custom/RpgSkillTreeLibraryRow.ui',
        'Common/UI/Custom/RpgSkillTreeFilterRow.ui')
    $missing = @($required | Where-Object { $_ -notin $entries })
    $lateMacroArguments = @(Get-ChildItem -LiteralPath 'src\main\resources\Common\UI\Custom' -Filter '*.ui' -File |
        Select-String -Pattern '\b\w+\s*:[^;\r\n]*;[^\r\n]*@\w+\s*=' -AllMatches)
    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R017'; version = '0.0.10'
        stage = 'pre-Stage06 CustomUI load correction'; schema = 3; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        requiredJarEntriesPresent = $missing.Count -eq 0; missingJarEntries = $missing
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        customUiValidated = $true; lateImportedControlMacroArguments = $lateMacroArguments.Count
        canvasUiSourceChanged = [bool]((& git status --short -- canvas-ui canvas-ui-demo) -join '')
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($missing.Count -ne 0 -or $failures -ne 0 -or $errors -ne 0 -or
        $lateMacroArguments.Count -ne 0 -or $result.canvasUiSourceChanged) {
        throw 'R017 verification gate failed.'
    }
}
finally { Pop-Location }
