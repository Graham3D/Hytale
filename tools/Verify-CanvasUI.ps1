[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $root 'evidence\canvas-ui\R004'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
Push-Location $root
try {
    & .\gradlew.bat :canvas-ui:clean :canvas-ui:test :canvas-ui:jar :canvas-ui:sourcesJar :canvas-ui:javadocJar
    if ($LASTEXITCODE -ne 0) { throw 'CanvasUI build/test failed.' }
    $library = Join-Path $root 'canvas-ui\build\libs\CanvasUI-0.1.0.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path @(
        (Join-Path $root 'src\main\resources'),
        $library
    )
    $libraryEntries = @(& jar tf $library)
    $requiredLibrary = @('manifest.json','canvasui-build.properties','Common/UI/Custom/CanvasUIPage.ui',
        'com/inigmasgames/canvasui/CanvasUI.class','com/inigmasgames/canvasui/runtime/CanvasService.class',
        'com/inigmasgames/canvasui/api/Canvas.class','com/inigmasgames/canvasui/api/CanvasSnapshotCodec.class',
        'com/inigmasgames/canvasui/demo/CanvasDemoCommand.class','com/inigmasgames/canvasui/demo/DemoDefinitions.class')
    $missingLibrary = @($requiredLibrary | Where-Object { $_ -notin $libraryEntries })
    $testSuites = @(Get-ChildItem -LiteralPath (Join-Path $root 'canvas-ui\build\test-results\test') -Filter '*.xml')
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $testSuites) {
        $xml = [xml](Get-Content -LiteralPath $suite.FullName -Raw)
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }
    $librarySource = Get-ChildItem -LiteralPath (Join-Path $root 'canvas-ui\src\main\java') -Recurse -Filter '*.java' | Get-Content -Raw
    $demoSource = Get-ChildItem -LiteralPath (Join-Path $root 'canvas-ui-demo\src\main\java') -Recurse -Filter '*.java' | Get-Content -Raw
    $forbiddenLibraryTerms = @('hytalerpg','skill rule','passive rule','progression rule')
    $forbiddenFound = @($forbiddenLibraryTerms | Where-Object { $librarySource -match [regex]::Escape($_) })
    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        revision = 'R004'; hytale = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); commit = (& git rev-parse HEAD).Trim()
        tests = [ordered]@{ total = $tests; failures = $failures; errors = $errors; skipped = $skipped; passed = ($tests -gt 0 -and $failures -eq 0 -and $errors -eq 0) }
        libraryJar = [ordered]@{ path = $library; bytes = (Get-Item $library).Length; sha256 = (Get-FileHash $library -Algorithm SHA256).Hash; missingEntries = $missingLibrary }
        demoBundledInLibraryJar = $true
        libraryForbiddenRpgTermsAbsent = $forbiddenFound.Count -eq 0
        forbiddenTermsFound = $forbiddenFound
        demoImportsInternalPackages = [bool]($demoSource -match 'com\.inigmasgames\.canvasui\.internal')
    }
    $result | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    $result | ConvertTo-Json -Depth 5
    if (-not $result.tests.passed -or $missingLibrary.Count -or
        -not $result.libraryForbiddenRpgTermsAbsent -or $result.demoImportsInternalPackages) { throw 'CanvasUI static gate failed.' }
}
finally { Pop-Location }
