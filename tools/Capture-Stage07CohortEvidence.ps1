[CmdletBinding()]
param([ValidateSet('a','b','c')][string]$Cohort='a')
$ErrorActionPreference='Stop'
$stage7Root=(Resolve-Path "$PSScriptRoot\..").Path
$stage7Evidence=Join-Path $stage7Root "evidence\stage-07\cohort-$Cohort"
$stage7Package="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$stage7Jar=Join-Path $stage7Root 'build\libs\HytaleRPG-0.0.19.jar'
$stage7Archived=Join-Path $stage7Evidence 'artifacts\HytaleRPG-0.0.19.jar'
if((Test-Path -LiteralPath $stage7Archived) -and (Get-FileHash -LiteralPath $stage7Archived).Hash -ne (Get-FileHash -LiteralPath $stage7Jar).Hash) {
    throw 'Refusing to overwrite an earlier cohort artifact. Preserve the cohort and select the next one.'
}
New-Item -ItemType Directory -Force -Path $stage7Evidence,(Join-Path $stage7Evidence 'artifacts'),(Join-Path $stage7Evidence 'rollback') | Out-Null
Push-Location $stage7Root
try {
    $stage7Results=@();$stage7Count=0;$stage7Failures=0;$stage7Errors=0;$stage7Skipped=0
    foreach($stage7File in Get-ChildItem -Recurse -Path 'build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test' -Filter 'TEST-*.xml') {
        [xml]$stage7Xml=Get-Content -Raw -LiteralPath $stage7File.FullName
        $stage7Suite=$stage7Xml.testsuite
        $stage7Count += [int]$stage7Suite.tests;$stage7Failures += [int]$stage7Suite.failures
        $stage7Errors += [int]$stage7Suite.errors;$stage7Skipped += [int]$stage7Suite.skipped
        $stage7Results += @{name=$stage7Suite.name;tests=[int]$stage7Suite.tests;failures=[int]$stage7Suite.failures;
            errors=[int]$stage7Suite.errors;skipped=[int]$stage7Suite.skipped;seconds=$stage7Suite.time;
            cases=@($stage7Suite.testcase | ForEach-Object {$_.name})}
    }
    $stage7Minimum=switch($Cohort){'a'{236}'b'{255}'c'{274}}
    if($stage7Count -lt $stage7Minimum -or $stage7Failures -or $stage7Errors -or $stage7Skipped){throw 'Incomplete or failing retained regression suite.'}
    & "$PSScriptRoot\Test-CustomUIDocuments.ps1" -Path $stage7Jar
    $stage7ProtectedPaths=@('src/main/java/com/inigmasgames/hytalerpg/ui','src/main/resources/Common/UI',
        'canvas-ui/src','src/main/resources/rpg/runtime/stage-04-skills.json','src/main/resources/rpg/runtime/stage-05-projectiles.json',
        'src/main/resources/Server/ProjectileConfigs','src/main/java/com/inigmasgames/hytalerpg/combat/resource',
        'src/main/java/com/inigmasgames/hytalerpg/combat/cooldown')
    $stage7Protected=@(& git diff --name-only 689d451 -- @stage7ProtectedPaths)
    if($stage7Protected.Count){throw "Protected HUD/numeric resource/profile changes: $stage7Protected"}
    $stage7Smoke=Get-Content -Raw -LiteralPath (Join-Path $stage7Evidence 'server-smoke-summary.json') | ConvertFrom-Json
    $stage7Hash=(Get-FileHash -LiteralPath $stage7Jar).Hash
    if(-not $stage7Smoke.networkBooted -or -not $stage7Smoke.cleanShutdown -or $stage7Smoke.failure -or
        $stage7Smoke.processExitCode -ne 0 -or $stage7Smoke.jarSha256 -ne $stage7Hash){throw 'Smoke does not establish normal boot/stop of this exact JAR.'}
    $stage7Zip=[IO.Compression.ZipFile]::OpenRead($stage7Jar)
    try {
        $stage7Projectiles=@($stage7Zip.Entries | Where-Object {$_.FullName -like 'Server/ProjectileConfigs/RPG/*.json'})
        if($stage7Projectiles.Count -ne 7){throw 'Unexpected projectile config inventory.'}
        foreach($stage7Entry in $stage7Projectiles) {
            $stage7Reader=[IO.StreamReader]::new($stage7Entry.Open())
            try {$stage7Asset=$stage7Reader.ReadToEnd() | ConvertFrom-Json}finally{$stage7Reader.Dispose()}
            if(@($stage7Asset.Interactions.PSObject.Properties).Count -ne 0){throw "Native gameplay interaction in carrier $($stage7Entry.FullName)"}
        }
    } finally {$stage7Zip.Dispose()}
    Copy-Item -LiteralPath $stage7Jar -Destination (Join-Path $stage7Evidence 'artifacts\HytaleRPG-0.0.19.jar') -Force
    Copy-Item -LiteralPath 'evidence/stage-06/cohort-d/artifacts/HytaleRPG-0.0.18.jar' -Destination (Join-Path $stage7Evidence 'rollback\HytaleRPG-0.0.18.jar') -Force
    $stage7Results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage7Evidence 'test-results.json') -Encoding utf8
    if($Cohort -eq 'c') {
        [xml]$stage7Load=Get-Content -Raw -LiteralPath 'build/test-results/test/TEST-com.inigmasgames.hytalerpg.Stage07LoadTest.xml'
        $stage7LoadMatch=[regex]::Match($stage7Load.testsuite.'system-out'.InnerText,'STAGE07_LOAD (\{[^\r\n]+\})')
        if(-not $stage7LoadMatch.Success){throw 'Missing Stage 07 local load result.'}
        $stage7Performance=$stage7LoadMatch.Groups[1].Value | ConvertFrom-Json
        if($stage7Performance.carriers -ne 512 -or $stage7Performance.remainingCarriers -ne 0 -or $stage7Performance.remainingRoots -ne 0){throw 'Incomplete Stage 07 load cleanup.'}
        $stage7Performance | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage7Evidence 'local-performance.json') -Encoding utf8
    }
    $stage7Summary=[ordered]@{
        capturedAtUtc=[DateTime]::UtcNow.ToString('o');revision='R026';version='0.0.19';stage='07';cohort=$Cohort
        branch=(& git branch --show-current).Trim();sourceHead=(& git rev-parse HEAD).Trim();worktreeDirty=[bool](& git status --porcelain)
        stageStatus=$(if($Cohort -eq 'c'){'IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION'}else{'IMPLEMENTATION_IN_PROGRESS'})
        completeStageGate=($Cohort -eq 'c');gateScope='LOCAL_ENGINEERING_ONLY'
        cohortPassives=$(switch($Cohort){'a'{@('piercing','fork','chain','return')}'b'{@('ricochet','volley','barrage','homing')}'c'{@('accelerant','ballistics','shrapnel','splinterburst')}})
        tests=$stage7Count;failures=$stage7Failures;errors=$stage7Errors;skipped=$stage7Skipped
        connectedVerification='UNVERIFIED';nativeCastingFixed=$false;nativeProjectileBehaviorVerified=$false;liveDeploymentPerformed=$false
        protectedPathsChanged=$stage7Protected;projectileConfigsWithNoNativeGameplay=7
        jarSha256=$stage7Hash;serverSha256=(Get-FileHash -LiteralPath (Join-Path $stage7Package 'Server\HytaleServer.jar')).Hash
        assetsSha256=(Get-FileHash -LiteralPath (Join-Path $stage7Package 'Assets.zip')).Hash
        rollbackSha256=(Get-FileHash -LiteralPath (Join-Path $stage7Evidence 'rollback\HytaleRPG-0.0.18.jar')).Hash
    }
    $stage7Summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage7Evidence 'verification.json') -Encoding utf8
    [pscustomobject]$stage7Summary | Format-List
} finally {Pop-Location}
