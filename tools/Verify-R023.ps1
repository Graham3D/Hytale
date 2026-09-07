[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R023'
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$server = Join-Path $package 'Server\HytaleServer.jar'
$assets = Join-Path $package 'Assets.zip'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R023 complete build/test gate failed.' }
    $jar = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.16.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jar
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($assets)
    try {
        $paths = @(
            'Server/Item/Items/Rune/Ability/Rune_Fireball.json',
            'Server/Item/RootInteractions/Abilities/Root_Ability_Fireball.json',
            'Server/Item/Interactions/Abilities/Fireball/Ability_Fireball_Cast.json'
        )
        $assetEvidence = @()
        foreach ($path in $paths) {
            $entry = $archive.GetEntry($path)
            if ($null -eq $entry) { throw "Shipped control asset missing: $path" }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $assetEvidence += [ordered]@{ path=$path; document=($text | ConvertFrom-Json) }
        }
        $ability = $assetEvidence[0].document.Ability
        if ($ability.Slot -ne 'Primary' -or $ability.Cast -ne 'Root_Ability_Fireball' -or
            $ability.Cost -ne 25 -or $ability.CostType -ne 'Mana' -or $ability.Cooldown -ne 12) {
            throw 'Shipped Fireball differs from audited baseline; do not run guessed control.'
        }
        $assetEvidence | ConvertTo-Json -Depth 32 | Set-Content (Join-Path $evidence 'shipped-control-assets.json') -Encoding utf8
    } finally { $archive.Dispose() }
    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','build\test-results\nativeControlTest','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }
    $protectedPaths = @('src/main/java/com/inigmasgames/hytalerpg/execution',
        'src/main/java/com/inigmasgames/hytalerpg/combat', 'src/main/java/com/inigmasgames/hytalerpg/ui',
        'src/main/resources', 'canvas-ui/src')
    $protected = @(& git diff --name-only f1e626fd1afd3e140777cdce37b6380ac7e3b607 -- @protectedPaths)
    $j = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $shippedOverrides = @($j.Entries | Where-Object { $_.FullName -match 'Rune_Fireball|Root_Ability_Fireball|Ability_Fireball_Cast' })
    } finally { $j.Dispose() }
    $result = [ordered]@{
        capturedAtUtc=[DateTime]::UtcNow.ToString('o'); revision='R023'; version='0.0.16'; branch=(& git branch --show-current).Trim()
        baseline='f1e626fd1afd3e140777cdce37b6380ac7e3b607'; sourceCommit=(& git rev-parse HEAD).Trim()
        sourceWorktreeDirty=[bool](& git status --porcelain)
        tests=$tests; failures=$failures; errors=$errors; skipped=$skipped
        jarSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash
        serverSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $server).Hash
        assetsSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $assets).Hash
        protectedMechanicsHudAssetsChanges=@($protected); shippedAssetOverrides=$shippedOverrides.Count
        connectedControlRequired=$true; castingFixed=$false; stage06Started=$false
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($failures -or $errors -or $skipped -or $protected.Count -or $shippedOverrides.Count) { throw 'R023 verification failed.' }
} finally { Pop-Location }
