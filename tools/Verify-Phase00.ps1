[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidenceDir = Join-Path $projectRoot 'evidence\phase-00'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }

    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.2.jar'
    $entries = @(& jar tf $jarPath)
    $requiredEntries = @(
        'manifest.json',
        'rpg-build.properties',
        'Common/UI/Custom/Phase00Character.ui',
        'Common/UI/Custom/Phase00LinkCanvas.ui',
        'Common/UI/Custom/Phase00MouseProbe.ui',
        'Common/UI/Custom/Phase00Hud.ui',
        'Common/UI/Custom/Phase00RevisionHud.ui',
        'com/inigmasgames/hytalerpg/phase00/Phase00Plugin.class'
    )
    $missing = @($requiredEntries | Where-Object { $_ -notin $entries })

    $sourceText = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -Filter '*.java' |
        Get-Content -Raw
    $forbiddenPatterns = @('DamageSystems', 'setStatValue\s*\(', 'addStatValue\s*\(', 'subtractStatValue\s*\(')
    $forbiddenMatches = foreach ($pattern in $forbiddenPatterns) {
        if ($sourceText -match $pattern) { $pattern }
    }

    $manifest = & jar xf $jarPath manifest.json 2>$null
    $jarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
    $result = [pscustomobject]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        targetHytaleVersion = '0.7.0-pre.1'
        rpgRevision = 'R004'
        branch = (& git branch --show-current).Trim()
        commit = (& git rev-parse HEAD).Trim()
        workingTreeDirty = [bool](& git status --porcelain)
        buildSucceeded = $true
        jarPath = $jarPath
        jarSha256 = $jarHash
        requiredJarEntriesPresent = $missing.Count -eq 0
        missingJarEntries = $missing
        forbiddenGameplayMutationPatternsAbsent = @($forbiddenMatches).Count -eq 0
        forbiddenGameplayMutationPatternsFound = @($forbiddenMatches)
        sourceJavaFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -Filter '*.java').Count
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDir 'verification.json') -Encoding utf8
    $result | Format-List

    if (-not $result.requiredJarEntriesPresent -or -not $result.forbiddenGameplayMutationPatternsAbsent) {
        throw 'Phase 00 static verification failed.'
    }
}
finally {
    Remove-Item -LiteralPath (Join-Path $projectRoot 'manifest.json') -Force -ErrorAction SilentlyContinue
    Pop-Location
}
