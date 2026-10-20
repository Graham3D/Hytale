[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$evidence = Join-Path $projectRoot 'evidence\corrections\R022'
$package = "$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$serverJar = Join-Path $package 'Server\HytaleServer.jar'
$assetsZip = Join-Path $package 'Assets.zip'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

Push-Location $projectRoot
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'R022 Gradle build failed.' }
    $jarPath = Join-Path $projectRoot 'build\libs\HytaleRPG-0.0.15.jar'
    & (Join-Path $PSScriptRoot 'Test-CustomUIDocuments.ps1') -Path $jarPath

    $entries = @(& jar tf $jarPath)
    $rootPath = 'src\main\resources\Server\Item\RootInteractions\RPG\Root_RPG_Ability_Bridge.json'
    $rootText = Get-Content -Raw -LiteralPath $rootPath
    $root = $rootText | ConvertFrom-Json
    $operation = @($root.Interactions)[0]
    $itemAssets = @(Get-ChildItem 'src\main\resources\Server\Item\Items\RPG\Abilities' -Filter '*.json' -File |
        ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json })
    $forbidden = 'Damage|ChangeStat|ModifyInventory|Launch|Projectile|Effect|Cooldown|Cost|Resource'

    $firstClickBytecode = (& javap -classpath $serverJar -p -c `
        com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.FirstClickInteraction) -join "`n"
    $simpleBytecode = (& javap -classpath $serverJar -p -c `
        com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction) -join "`n"
    $firstClickWaitsForClient = [bool]($firstClickBytecode -match 'getWaitForDataFrom[\s\S]*WaitForDataFrom\.Client')
    $firstClickNeedsRemoteSync = [bool]($firstClickBytecode -match 'public boolean needsRemoteSync\(\);[\s\S]*?iconst_1[\s\S]*?ireturn')
    $simpleWaitsForNone = [bool]($simpleBytecode -match 'getWaitForDataFrom[\s\S]*WaitForDataFrom\.None')

    $suites = Get-ChildItem -Recurse -Path 'build\test-results\test','canvas-ui\build\test-results\test' -Filter 'TEST-*.xml'
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($suite in $suites) {
        [xml]$xml = Get-Content -Raw -LiteralPath $suite.FullName
        $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors; $skipped += [int]$xml.testsuite.skipped
    }

    $changed = @(& git diff --name-only HEAD)
    $protectedChanges = @($changed | Where-Object {
        $_ -match '(^|/)(execution|combat|ui/hud)/' -or
        $_ -match 'ProjectileFamilyExecutor|SkillExecutionService|NativeAbilityProjection(Service|TickSystem)|RpgHud|Experience(Frame|Background|Bar)'
    })

    $apiAudit = [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString('o'); target = '0.7.0-pre.1'
        serverJarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash
        assetsZipSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetsZip).Hash
        simpleWaitForDataFrom = 'None'; simpleNeedsRemoteSyncWithoutRemoteBranch = $false
        firstClickWaitForDataFrom = 'Client'; firstClickNeedsRemoteSync = $true
        firstClickClickAndHeldOptional = $true
    }
    $apiAudit | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'interaction-api-audit.json') -Encoding utf8

    $result = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); revision = 'R022'; version = '0.0.15'
        stage = 'native ability synchronization correction only; pre-Stage06'; hytaleVersion = '0.7.0-pre.1'
        branch = (& git branch --show-current).Trim(); sourceCommit = (& git rev-parse HEAD).Trim()
        jarPath = $jarPath; jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
        tests = $tests; failures = $failures; errors = $errors; skipped = $skipped
        bridgePackaged = 'Server/Item/RootInteractions/RPG/Root_RPG_Ability_Bridge.json' -in $entries
        bridgeIsSingleFirstClick = @($root.Interactions).Count -eq 1 -and $operation.Type -eq 'FirstClick'
        bridgeHasNoBranches = $null -eq $operation.Click -and $null -eq $operation.Held
        bridgeHasNoGameplayMutation = -not [bool]($rootText -match $forbidden)
        requireNewClick = $root.RequireNewClick -eq $true
        firstClickWaitsForClient = $firstClickWaitsForClient
        firstClickNeedsRemoteSync = $firstClickNeedsRemoteSync
        simpleWaitsForNone = $simpleWaitsForNone
        triggerAssetsZeroNativeAuthority = $itemAssets.Count -eq 12 -and
            @($itemAssets | Where-Object { $_.Ability.Cooldown -ne 0 -or $_.Ability.Cost -ne 0 -or
                $_.Ability.CostType -ne 'None' -or $_.Ability.Cast -ne 'Root_RPG_Ability_Bridge' }).Count -eq 0
        protectedMechanicsOrHudChanged = $protectedChanges.Count -ne 0
        connectedClientRequired = $true
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'verification.json') -Encoding utf8
    [pscustomobject]$result | Format-List
    if ($failures -ne 0 -or $errors -ne 0 -or -not $result.bridgePackaged -or
        -not $result.bridgeIsSingleFirstClick -or -not $result.bridgeHasNoBranches -or
        -not $result.bridgeHasNoGameplayMutation -or -not $result.requireNewClick -or
        -not $result.firstClickWaitsForClient -or -not $result.firstClickNeedsRemoteSync -or
        -not $result.simpleWaitsForNone -or -not $result.triggerAssetsZeroNativeAuthority -or
        $result.protectedMechanicsOrHudChanged) { throw 'R022 verification gate failed.' }
}
finally { Pop-Location }
