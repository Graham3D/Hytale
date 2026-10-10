[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixtureRoot = Join-Path $repo ('build/guardian-fixtures/' + [guid]::NewGuid().ToString('N'))
if (-not $fixtureRoot.StartsWith((Join-Path $repo 'build/guardian-fixtures'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Fixture path escaped the repository.'
}
[IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
[IO.File]::WriteAllText((Join-Path $fixtureRoot '.guardian-fixture'), 'disposable fixture', [Text.UTF8Encoding]::new($false))
$remote = Join-Path $fixtureRoot 'remote.git'
$main = Join-Path $fixtureRoot 'main'
$old = Join-Path $fixtureRoot 'old'
$divergent = Join-Path $fixtureRoot 'divergent'
$feature = Join-Path $fixtureRoot 'feature'
$missingDocs = Join-Path $fixtureRoot 'missing-docs'
$missingAgents = Join-Path $fixtureRoot 'missing-agents'
$untrackedAgents = Join-Path $fixtureRoot 'untracked-agents'
$peer = Join-Path $fixtureRoot 'peer'
$guard = Join-Path $PSScriptRoot 'Invoke-Guardian.ps1'
$publisher = Join-Path $PSScriptRoot 'Publish-GuardianBranch.ps1'
$checks = 0

function Write-Fixture([string]$Relative, [string]$Value) {
    $path = Join-Path $main $Relative
    [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
    [IO.File]::WriteAllText($path, $Value, [Text.UTF8Encoding]::new($false))
}
function Invoke-FixtureGit([string]$Directory, [string[]]$Arguments) {
    $result = @(& git -C $Directory @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('Fixture Git failed: git ' + ($Arguments -join ' ') + ' ' + ($result -join ' ')) }
    return (($result | ForEach-Object { [string]$_ }) -join [Environment]::NewLine).Trim()
}
function Run-Guard([string]$Directory, [string[]]$Arguments = @()) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $guard -Repository $Directory -FixtureMode @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    $watch.Stop()
    return [pscustomobject]@{ ExitCode=$exitCode; Text=($output -join [Environment]::NewLine); Milliseconds=$watch.Elapsed.TotalMilliseconds }
}
function Assert-Case([bool]$Condition, [string]$Name, [string]$Details) {
    if (-not $Condition) { throw ("Guardian test failed: $Name. $Details") }
    $script:checks++
}

& git init --bare --initial-branch=main --quiet $remote | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize isolated fixture remote.' }
& git clone --quiet $remote $main 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot clone isolated fixture.' }
Invoke-FixtureGit $main @('config','user.name','Guardian Fixture') | Out-Null
Invoke-FixtureGit $main @('config','user.email','fixture@example.invalid') | Out-Null
Write-Fixture 'AGENTS.md' '# Fixture rules'
Write-Fixture 'gradle.properties' "version=0.2.0-R250-U7P5B`nrpg_revision=R250-U7P5B`n"
Write-Fixture 'src/main/resources/manifest.json' '{"Group":"InigmasGames","Name":"HyARPG","Main":"com.inigmasgames.hywind.HyArpgPlugin","Version":"${version}","Metadata":{"RpgRevision":"${rpgRevision}"},"Dependencies":{},"OptionalDependencies":{"InigmasGames:ImmersiveNPCs":"*"}}'
Write-Fixture 'README.md' 'Current gameplay baseline **R250-U7P5B**.'
Write-Fixture 'docs/DEVELOPMENT_BASELINE.md' "# R250-U7P5B baseline`nVerified source branch: main.`n"
Write-Fixture '.gitignore' "build/`n"
$stub = 'param($JarPath,$ExpectedVersion,$ExpectedRevision)' + [Environment]::NewLine + '[ordered]@{ result = ''PASS''; sha256 = (Get-FileHash -LiteralPath $JarPath).Hash } | ConvertTo-Json'
Write-Fixture 'tools/Test-HyArpgPackage.ps1' $stub
foreach ($name in @('a','b','c')) { Write-Fixture "src/data/$name.txt" $name }
Invoke-FixtureGit $main @('add','--','AGENTS.md','gradle.properties','src/main/resources/manifest.json','README.md','docs/DEVELOPMENT_BASELINE.md','.gitignore','tools/Test-HyArpgPackage.ps1','src/data') | Out-Null
Invoke-FixtureGit $main @('commit','--quiet','-m','Fixture baseline A') | Out-Null
$commitA = Invoke-FixtureGit $main @('rev-parse','HEAD')
Invoke-FixtureGit $main @('push','--quiet','origin','main') | Out-Null
Write-Fixture 'note.txt' 'approved baseline B'
Invoke-FixtureGit $main @('add','--','note.txt') | Out-Null
Invoke-FixtureGit $main @('commit','--quiet','-m','Fixture baseline B') | Out-Null
Invoke-FixtureGit $main @('push','--quiet','origin','main') | Out-Null

$current = Run-Guard $main @('-Mode','Preflight')
Assert-Case ($current.ExitCode -eq 0 -and $current.Text -eq 'GUARDIAN: PASS') 'current baseline passes' $current.Text
Assert-Case ([Text.Encoding]::UTF8.GetByteCount($current.Text) -eq 14 -and $current.Milliseconds -lt 5000) 'compact, fast success' $current.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','old',$old,$commitA) | Out-Null
$oldResult = Run-Guard $old @('-Mode','Preflight')
Assert-Case ($oldResult.ExitCode -eq 2 -and $oldResult.Text -match 'behind') 'outdated worktree blocked' $oldResult.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','divergent',$divergent,$commitA) | Out-Null
[IO.File]::WriteAllText((Join-Path $divergent 'diverge.txt'), 'diverged', [Text.UTF8Encoding]::new($false))
Invoke-FixtureGit $divergent @('add','--','diverge.txt') | Out-Null
Invoke-FixtureGit $divergent @('commit','--quiet','-m','Fixture divergent commit') | Out-Null
$diverged = Run-Guard $divergent @('-Mode','Preflight')
Assert-Case ($diverged.ExitCode -eq 2 -and $diverged.Text -match 'diverges') 'unexpected divergence blocked' $diverged.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','codex/feature',$feature,'refs/remotes/origin/main') | Out-Null
[IO.File]::WriteAllText((Join-Path $feature 'feature.txt'), 'feature work', [Text.UTF8Encoding]::new($false))
Invoke-FixtureGit $feature @('add','--','feature.txt') | Out-Null
Invoke-FixtureGit $feature @('commit','--quiet','-m','Fixture feature commit') | Out-Null
$featureResult = Run-Guard $feature @('-Mode','Preflight')
Assert-Case ($featureResult.ExitCode -eq 0 -and $featureResult.Text -eq 'GUARDIAN: PASS') 'descendant feature branch passes' $featureResult.Text
$localOnly = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($localOnly.ExitCode -eq 2 -and $localOnly.Text -match 'receipt is missing' -and
    $localOnly.Text -match 'GitHub=NOT_CHECKED') 'missing local evidence blocks completion' $localOnly.Text
# A synthetic package and four JUnit reports exercise the completion receipt reader.
$featureHead = Invoke-FixtureGit $feature @('rev-parse','HEAD')
$jarDir = Join-Path $feature 'build/libs'
$guardianDir = Join-Path $feature 'build/guardian'
[IO.Directory]::CreateDirectory($jarDir) | Out-Null
[IO.Directory]::CreateDirectory($guardianDir) | Out-Null
$jarPath = Join-Path $jarDir 'HyARPG.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($jarPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    $entry = $zip.CreateEntry('rpg-build.properties')
    $writer = New-Object IO.StreamWriter($entry.Open())
    try { $writer.Write("rpg.sourceCommit=$featureHead`nrpg.revision=R250-U7P5B`n") }
    finally { $writer.Dispose() }
} finally { $zip.Dispose() }
$logPath = Join-Path $guardianDir 'gradle-check.log'
[IO.File]::WriteAllText($logPath, 'BUILD SUCCESSFUL', [Text.UTF8Encoding]::new($false))
$fixtureReports = @()
foreach ($dir in @('build/test-results/test','build/test-results/nativeControlTest',
                   'canvas-ui/build/test-results/test','hytale-taverns/build/test-results/test')) {
    $reportDir = Join-Path $feature $dir
    [IO.Directory]::CreateDirectory($reportDir) | Out-Null
    $reportFile = Join-Path $reportDir 'TEST-Fixture.xml'
    [IO.File]::WriteAllText($reportFile, '<testsuite tests="1" failures="0" errors="0" skipped="0"/>', [Text.UTF8Encoding]::new($false))
    $fixtureReports += [ordered]@{ path=($dir + '/TEST-Fixture.xml'); sha256=(Get-FileHash $reportFile).Hash; tests=1 }
}
$fixtureReceipt = [ordered]@{
    sourceCommit=$featureHead; revision='R250-U7P5B'; version='0.2.0-R250-U7P5B'
    gradleCheck='PASS'; packageValidation='PASS'; checkLogPath='build/guardian/gradle-check.log'
    checkLogSha256=(Get-FileHash $logPath).Hash; jarPath='build/libs/HyARPG.jar'
    jarSha256=(Get-FileHash $jarPath).Hash; reports=$fixtureReports
}
[IO.File]::WriteAllText((Join-Path $guardianDir 'verification.json'),
    ($fixtureReceipt | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
$recordScript = Join-Path $PSScriptRoot 'Record-Verification.ps1'
$recordOutput = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $recordScript -Repository $feature -FixtureMode 2>&1)
$recordCode = $LASTEXITCODE
Assert-Case ($recordCode -eq 0 -and ($recordOutput -join ' ') -eq 'GUARDIAN: PASS') 'existing QA evidence recorded' ($recordOutput -join ' ')
$unpublished = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($unpublished.ExitCode -eq 0 -and $unpublished.Text -match 'GitHub=UNPUBLISHED' -and
    $unpublished.Text -match 'has not been published') 'local commit is not remote publication' $unpublished.Text
$baseForPublication = Invoke-FixtureGit $main @('rev-parse','refs/remotes/origin/main')
$firstPublishCommand = '& "' + $publisher + '" -Repository "' + $feature + '" -FixtureMode -StandingAuthorization -ExpectedCommit ' + $featureHead + ' -ReviewedBase ' + $baseForPublication + ' -ReviewedPaths @("feature.txt")'
$firstPublish = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $firstPublishCommand 2>&1)
Assert-Case ($LASTEXITCODE -eq 0 -and ($firstPublish -join ' ') -match 'GitHub=PUBLISHED main=PENDING') 'authorized reviewed task branch publishes and verifies' ($firstPublish -join ' ')
$complete = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($complete.ExitCode -eq 0 -and $complete.Text -match '^GUARDIAN: PASS - local=VERIFIED GitHub=PUBLISHED main=PENDING deployment=UNVERIFIED acceptance=UNVERIFIED') 'verified remote task branch passes' $complete.Text
$denied = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $publisher -Repository $feature -FixtureMode -ExpectedCommit $featureHead -ReviewedBase (Invoke-FixtureGit $main @('rev-parse','refs/remotes/origin/main')) -ReviewedPaths 'feature.txt' 2>&1)
Assert-Case ($LASTEXITCODE -ne 0 -and ($denied -join ' ') -match 'standing authorization') 'publication requires explicit authorization' ($denied -join ' ')

# A new local commit is not published merely because an earlier commit reached the branch.
[IO.File]::WriteAllText((Join-Path $feature 'second.txt'), 'second local change', [Text.UTF8Encoding]::new($false))
Invoke-FixtureGit $feature @('add','--','second.txt') | Out-Null
Invoke-FixtureGit $feature @('commit','--quiet','-m','Fixture unpublished second commit') | Out-Null
$featureHead = Invoke-FixtureGit $feature @('rev-parse','HEAD')
$zip = [IO.Compression.ZipFile]::Open($jarPath, [IO.Compression.ZipArchiveMode]::Update)
try {
    $zip.GetEntry('rpg-build.properties').Delete()
    $entry = $zip.CreateEntry('rpg-build.properties')
    $writer = New-Object IO.StreamWriter($entry.Open())
    try { $writer.Write("rpg.sourceCommit=$featureHead`nrpg.revision=R250-U7P5B`n") }
    finally { $writer.Dispose() }
} finally { $zip.Dispose() }
[IO.File]::WriteAllText($logPath, 'BUILD SUCCESSFUL', [Text.UTF8Encoding]::new($false))
$newReceipt = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $recordScript -Repository $feature -FixtureMode 2>&1)
Assert-Case ($LASTEXITCODE -eq 0 -and ($newReceipt -join ' ') -eq 'GUARDIAN: PASS') 'new local commit gets fresh evidence' ($newReceipt -join ' ')
$ahead = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($ahead.ExitCode -eq 0 -and $ahead.Text -match 'GitHub=UNPUBLISHED' -and
    $ahead.Text -match 'unpublished commits') 'remote branch behind HEAD is unpublished' $ahead.Text

# Another writer advances the fixture remote; publication must reject non-fast-forward history.
& git clone --quiet --branch codex/feature $remote $peer 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot clone fixture peer.' }
Invoke-FixtureGit $peer @('config','user.name','Guardian Fixture Peer') | Out-Null
Invoke-FixtureGit $peer @('config','user.email','peer@example.invalid') | Out-Null
[IO.File]::WriteAllText((Join-Path $peer 'peer.txt'), 'remote branch advance', [Text.UTF8Encoding]::new($false))
Invoke-FixtureGit $peer @('add','--','peer.txt') | Out-Null
Invoke-FixtureGit $peer @('commit','--quiet','-m','Fixture remote advance') | Out-Null
Invoke-FixtureGit $peer @('push','--quiet','origin','HEAD:refs/heads/codex/feature') | Out-Null
$unknown = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($unknown.ExitCode -eq 2 -and $unknown.Text -match 'GitHub=REMOTE_CHANGED') 'unfetched remote tip is not trusted' $unknown.Text
Invoke-FixtureGit $feature @('fetch','--quiet','origin','refs/heads/codex/feature:refs/remotes/origin/codex/feature') | Out-Null
$divergedRemote = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($divergedRemote.ExitCode -eq 2 -and $divergedRemote.Text -match 'GitHub=DIVERGED') 'remote divergence blocked' $divergedRemote.Text
$reviewedBase = Invoke-FixtureGit $main @('rev-parse','refs/remotes/origin/main')
$publishCommand = '& "' + $publisher + '" -Repository "' + $feature + '" -FixtureMode -StandingAuthorization -ExpectedCommit ' + $featureHead + ' -ReviewedBase ' + $reviewedBase + ' -ReviewedPaths @("feature.txt","second.txt")'
$failedPush = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $publishCommand 2>&1)
Assert-Case ($LASTEXITCODE -ne 0 -and ($failedPush -join ' ') -match 'Non-force push.*failed') 'failed push reported without force' ($failedPush -join ' ')

Invoke-FixtureGit $main @('merge','--quiet','--ff-only','codex/feature') | Out-Null
Invoke-FixtureGit $main @('push','--quiet','origin','main') | Out-Null
$integrated = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($integrated.ExitCode -eq 0 -and $integrated.Text -match 'GitHub=PUBLISHED main=INTEGRATED') 'authoritative main integration verified' $integrated.Text
Invoke-FixtureGit $main @('remote','set-url','origin','https://127.0.0.1:1/no-network.git') | Out-Null
$networkFailure = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($networkFailure.ExitCode -eq 0 -and $networkFailure.Text -match 'GitHub=UNKNOWN' -and
    $networkFailure.Text -match 'could not be checked') 'failed remote query cannot claim synchronization' $networkFailure.Text
Invoke-FixtureGit $main @('remote','set-url','origin',$remote) | Out-Null
$firstReport = Join-Path $feature 'build/test-results/test/TEST-Fixture.xml'
[IO.File]::AppendAllText($firstReport, 'changed')
$alteredReceipt = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($alteredReceipt.ExitCode -eq 2 -and $alteredReceipt.Text -match 'JUnit report missing or changed') 'changed test evidence blocked' $alteredReceipt.Text

[IO.File]::WriteAllText((Join-Path $feature 'scratch.txt'), 'preserve this', [Text.UTF8Encoding]::new($false))
$dirty = Run-Guard $feature @('-Mode','Preflight')
Assert-Case ($dirty.ExitCode -eq 0 -and $dirty.Text -match 'Uncommitted work' -and
    (Get-Content (Join-Path $feature 'scratch.txt') -Raw) -eq 'preserve this') 'uncommitted work reported and preserved' $dirty.Text
$dirtyCompletion = Run-Guard $feature @('-Mode','Completion')
Assert-Case ($dirtyCompletion.ExitCode -eq 2 -and $dirtyCompletion.Text -match 'local=UNCOMMITTED' -and
    $dirtyCompletion.Text -match 'GitHub=NOT_CHECKED') 'uncommitted work cannot be synchronized' $dirtyCompletion.Text
$scope = Run-Guard $feature @('-Mode','Changes','-Scope','README.md')
Assert-Case ($scope.ExitCode -eq 2 -and $scope.Text -match 'outside task scope') 'out-of-scope path blocked' $scope.Text
Invoke-FixtureGit $feature @('add','--','scratch.txt') | Out-Null
$commitSafe = Run-Guard $feature @('-Mode','Commit')
Assert-Case ($commitSafe.ExitCode -eq 0 -and $commitSafe.Text -eq 'GUARDIAN: PASS') 'optional hook keeps safe success compact' $commitSafe.Text

foreach ($name in @('a','b','c')) { Remove-Item -LiteralPath (Join-Path $feature "src/data/$name.txt") }
$mass = Run-Guard $feature @('-Mode','Changes','-MassDeletionThreshold','3')
Assert-Case ($mass.ExitCode -eq 2 -and $mass.Text -match 'Suspicious mass deletion' -and
    -not (Test-Path (Join-Path $feature 'src/data/a.txt'))) 'mass deletion flagged without restoring files' $mass.Text
Invoke-FixtureGit $feature @('add','-u','--','src/data') | Out-Null
$commitMass = Run-Guard $feature @('-Mode','Commit','-MassDeletionThreshold','3')
Assert-Case ($commitMass.ExitCode -eq 2 -and $commitMass.Text -match 'Suspicious mass deletion') 'optional hook blocks staged mass deletion' $commitMass.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','missing-docs',$missingDocs,'refs/remotes/origin/main') | Out-Null
Invoke-FixtureGit $missingDocs @('rm','--quiet','--','docs/DEVELOPMENT_BASELINE.md') | Out-Null
Invoke-FixtureGit $missingDocs @('commit','--quiet','-m','Fixture missing revision record') | Out-Null
$docsResult = Run-Guard $missingDocs @('-Mode','Completion')
Assert-Case ($docsResult.ExitCode -eq 2 -and $docsResult.Text -match 'documentation is missing') 'missing revision record blocked' $docsResult.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','missing-agents',$missingAgents,'refs/remotes/origin/main') | Out-Null
Remove-Item -LiteralPath (Join-Path $missingAgents 'AGENTS.md')
$agentsResult = Run-Guard $missingAgents @('-Mode','Preflight')
Assert-Case ($agentsResult.ExitCode -eq 2 -and $agentsResult.Text -match 'AGENTS.md is missing or untracked') 'missing AGENTS blocked' $agentsResult.Text

Invoke-FixtureGit $main @('worktree','add','--quiet','-b','untracked-agents',$untrackedAgents,'refs/remotes/origin/main') | Out-Null
Invoke-FixtureGit $untrackedAgents @('rm','--quiet','--cached','--','AGENTS.md') | Out-Null
$untrackedResult = Run-Guard $untrackedAgents @('-Mode','Preflight')
Assert-Case ($untrackedResult.ExitCode -eq 2 -and $untrackedResult.Text -match 'AGENTS.md is missing or untracked') 'untracked AGENTS blocked' $untrackedResult.Text

Invoke-FixtureGit $main @('remote','set-url','origin','https://127.0.0.1:1/no-network.git') | Out-Null
$offline = Run-Guard $main @('-Mode','Preflight')
Assert-Case ($offline.ExitCode -eq 0 -and $offline.Text -eq 'GUARDIAN: PASS') 'local verification requires no network' $offline.Text

Write-Output ("GUARDIAN TESTS: PASS ($checks cases; clean preflight {0:N0} ms, 14 output bytes)" -f $current.Milliseconds)
