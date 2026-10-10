[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Changes', 'Commit', 'Completion')][string]$Mode = 'Preflight',
    [string]$Repository = '.',
    [string]$BaselineRef = 'refs/remotes/origin/main',
    [string[]]$Scope = @(),
    [int]$MassDeletionThreshold = 20,
    [switch]$AllowMassDeletion,
    [string]$Receipt = 'build/guardian/verification.json',
    [int]$MaxBaselineAgeHours = 24,
    [switch]$Details,
    [switch]$LocalOnly,
    [switch]$FixtureMode
)

$ErrorActionPreference = 'Stop'
$blocked = New-Object 'System.Collections.Generic.List[string]'
$warnings = New-Object 'System.Collections.Generic.List[string]'
$script:LocalState = 'UNVERIFIED'
$script:GitHubState = 'NOT_CHECKED'
$script:MainState = 'NOT_CHECKED'

function Add-Blocked([string]$Message) { $script:blocked.Add($Message) }
function Add-Warning([string]$Message) { $script:warnings.Add($Message) }
function Run-Git([string[]]$Arguments) {
    $priorErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $output = @(& git -C $script:Root @Arguments 2>&1); $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $priorErrorAction }
    return [pscustomobject]@{
        Code = $code
        Text = (($output | ForEach-Object { [string]$_ }) -join [Environment]::NewLine).TrimEnd()
    }
}
function Repo-Path([string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[/\\])\.\.([/\\]|$)') {
        throw 'Path must remain inside the repository.'
    }
    $full = [IO.Path]::GetFullPath((Join-Path $script:Root $Relative))
    $prefix = $script:Root.TrimEnd([char[]]@('/', '\')) + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Path escapes the repository.'
    }
    $current = $script:Root
    foreach ($part in ($Relative -split '[/\\]' | Where-Object { $_ })) {
        $current = Join-Path $current $part
        if (Test-Path -LiteralPath $current) {
            if (((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Refusing a repository path through a symlink or junction.'
            }
        }
    }
    return $full
}
function Matches-Scope([string]$Path) {
    if ($Scope.Count -eq 0) { return $true }
    $path = $Path.Replace('\', '/')
    foreach ($entry in $Scope) {
        $item = $entry.Replace('\', '/')
        if ($item -match '(^|/)\.\.(/|$)' -or $item.StartsWith('/')) { continue }
        if ($item.EndsWith('/')) {
            if ($path.StartsWith($item, [StringComparison]::OrdinalIgnoreCase)) { return $true }
        } elseif ($path.Equals($item, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}
function Finish {
    $state = "local=$script:LocalState GitHub=$script:GitHubState main=$script:MainState deployment=UNVERIFIED acceptance=UNVERIFIED"
    if ($blocked.Count -gt 0) {
        Write-Output ('GUARDIAN: BLOCKED - ' + $blocked[0])
        if ($Mode -eq 'Completion') { Write-Output ('  state: ' + $state) }
        if ($Details -and $script:Context) { Write-Output ('  ' + $script:Context) }
        foreach ($issue in @($blocked | Select-Object -Skip 1) + @($warnings)) { Write-Output ('  - ' + $issue) }
        exit 2
    }
    if ($warnings.Count -gt 0) {
        Write-Output ('GUARDIAN: WARNING - ' + $warnings[0])
        if ($Mode -eq 'Completion') { Write-Output ('  state: ' + $state) }
        if ($Details -and $script:Context) { Write-Output ('  ' + $script:Context) }
        foreach ($issue in @($warnings | Select-Object -Skip 1)) { Write-Output ('  - ' + $issue) }
        exit 0
    }
    if ($Mode -eq 'Completion') { Write-Output ('GUARDIAN: PASS - ' + $state) }
    else { Write-Output 'GUARDIAN: PASS' }
    if ($Details -and $script:Context) { Write-Output ('  ' + $script:Context) }
    exit 0
}

try {
    $resolved = @(& git -C $Repository rev-parse --show-toplevel 2>&1)
    if ($LASTEXITCODE -ne 0 -or $resolved.Count -eq 0) { throw 'Not a Git worktree.' }
    $script:Root = [IO.Path]::GetFullPath([string]$resolved[-1])
    if ($FixtureMode) {
        $fixtureBase = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../build/guardian-fixtures'))
        $fixturePrefix = $fixtureBase.TrimEnd([char[]]@('/', '\')) + [IO.Path]::DirectorySeparatorChar
        if (-not $script:Root.StartsWith($fixturePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Fixture mode is restricted to this repository build/guardian-fixtures.'
        }
        $fixtureId = ($script:Root.Substring($fixturePrefix.Length) -split '[/\\]')[0]
        if (-not (Test-Path -LiteralPath (Join-Path (Join-Path $fixtureBase $fixtureId) '.guardian-fixture') -PathType Leaf)) {
            throw 'Fixture marker is missing.'
        }
    }
    if ($BaselineRef -notmatch '^refs/remotes/[A-Za-z0-9._/-]+$' -or $BaselineRef.Contains('..')) {
        throw 'Baseline must be a remote-tracking Git ref.'
    }
    if ($MassDeletionThreshold -lt 1 -or $MaxBaselineAgeHours -lt 1) {
        throw 'Thresholds must be positive.'
    }
    if ($LocalOnly -and $Mode -ne 'Completion') { throw '-LocalOnly applies only to Completion.' }

    $remote = Run-Git @('config', '--get', 'remote.origin.url')
    if (-not $FixtureMode -and ($remote.Code -ne 0 -or
        $remote.Text -notmatch '^(https://github\.com/|git@github\.com:|ssh://git@github\.com/)(Graham3D/Hytale)(\.git)?/?$')) {
        Add-Blocked 'This checkout is not the authorized Graham3D/Hytale repository.'
    }
    $headResult = Run-Git @('rev-parse', '--verify', 'HEAD^{commit}')
    $baselineResult = Run-Git @('rev-parse', '--verify', "$BaselineRef^{commit}")
    $branchResult = Run-Git @('symbolic-ref', '--quiet', '--short', 'HEAD')
    if ($headResult.Code -ne 0) { Add-Blocked 'HEAD is not a commit.' }
    if ($baselineResult.Code -ne 0) { Add-Blocked 'Local origin/main baseline is missing; fetch and review it.' }
    if ($branchResult.Code -ne 0) { Add-Blocked 'Detached HEAD cannot be used for an engineering task.' }
    if ($blocked.Count -gt 0) { Finish }
    $head = $headResult.Text.Trim()
    $baseline = $baselineResult.Text.Trim()
    $branch = $branchResult.Text.Trim()
    $script:Context = ('worktree=' + $script:Root + ' branch=' + $branch + ' HEAD=' + $head + ' baseline=' + $BaselineRef + ':' + $baseline)

    $containsBaseline = Run-Git @('merge-base', '--is-ancestor', $baseline, $head)
    if ($containsBaseline.Code -ne 0) {
        $behind = Run-Git @('merge-base', '--is-ancestor', $head, $baseline)
        if ($behind.Code -eq 0) { Add-Blocked "Checkout $branch is behind the locally known approved baseline." }
        else { Add-Blocked "Checkout $branch diverges from the locally known approved baseline." }
    }
    $baselineTime = Run-Git @('reflog', '-1', '--format=%ct', $BaselineRef)
    $epoch = [long]0
    if ($baselineTime.Code -ne 0 -or -not [long]::TryParse($baselineTime.Text.Trim(), [ref]$epoch)) {
        Add-Warning 'Local baseline refresh time is unknown; verify origin/main before relying on this result.'
    } else {
        $ageHours = ([DateTimeOffset]::UtcNow - [DateTimeOffset]::FromUnixTimeSeconds($epoch)).TotalHours
        if ($ageHours -gt $MaxBaselineAgeHours) {
            Add-Warning ("Local origin/main was last observed {0:N0} hours ago; fetch and review before new work." -f $ageHours)
        }
    }

    $agents = Run-Git @('ls-files', '--error-unmatch', '--', 'AGENTS.md')
    if ($agents.Code -ne 0 -or -not (Test-Path -LiteralPath (Repo-Path 'AGENTS.md') -PathType Leaf)) {
        Add-Blocked 'Root AGENTS.md is missing or untracked.'
    }
    $propertiesPath = Repo-Path 'gradle.properties'
    $manifestPath = Repo-Path 'src/main/resources/manifest.json'
    $revision = ''
    $version = ''
    if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
        Add-Blocked 'gradle.properties is missing.'
    } else {
        $properties = Get-Content -LiteralPath $propertiesPath -Raw
        if ($properties -match '(?m)^rpg_revision=([^\r\n]+)') { $revision = $Matches[1].Trim() }
        if ($properties -match '(?m)^version=([^\r\n]+)') { $version = $Matches[1].Trim() }
        if (-not $revision -or -not $version -or -not $version.EndsWith('-' + $revision, [StringComparison]::Ordinal)) {
            Add-Blocked 'Gradle version and rpg_revision metadata disagree.'
        }
        $metadataDiff = Run-Git @('diff', '--quiet', 'HEAD', '--', 'gradle.properties')
        if ($metadataDiff.Code -ne 0) { Add-Warning 'Revision metadata differs from the committed HEAD.' }
    }
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        Add-Blocked 'HyARPG manifest is missing.'
    } else {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'HyARPG' -or
            $manifest.Main -ne 'com.inigmasgames.hywind.HyArpgPlugin' -or
            $manifest.Version -ne '${version}' -or $manifest.Metadata.RpgRevision -ne '${rpgRevision}') {
            Add-Blocked 'HyARPG identity, entrypoint, or build placeholders changed; review migration separately.'
        }
        if ($manifest.Dependencies.PSObject.Properties.Name -contains 'InigmasGames:ImmersiveNPCs') {
            Add-Blocked 'ImmersiveNPCs became a required HyARPG dependency.'
        }
    }

    $statusResult = Run-Git @('-c', 'core.quotepath=false', 'status', '--porcelain=v1', '--untracked-files=all')
    if ($statusResult.Code -ne 0) { Add-Blocked 'Git status could not be inspected.'; Finish }
    $statusLines = @($statusResult.Text -split '\r?\n' | Where-Object { $_.Length -ge 3 })
    if ($statusLines.Count -gt 0) {
        if ($Mode -eq 'Completion') {
            $script:LocalState = 'UNCOMMITTED'
            Add-Blocked 'Uncommitted or untracked work is present; completion requires a clean committed checkout.'
            Finish
        }
        elseif ($Mode -ne 'Commit') { Add-Warning ("Uncommitted work is present ({0} path(s)); nothing was changed." -f $statusLines.Count) }
    }
    if ($Mode -eq 'Changes' -or $Mode -eq 'Commit') {
        if ($Mode -eq 'Changes' -and $Scope.Count -eq 0) { Add-Warning 'No task scope supplied; outside-scope changes cannot be classified.' }
        $deleted = 0
        $outside = New-Object 'System.Collections.Generic.List[string]'
        $protected = New-Object 'System.Collections.Generic.List[string]'
        foreach ($line in $statusLines) {
            $xy = $line.Substring(0, 2)
            if ($Mode -eq 'Commit' -and ($xy[0] -eq ' ' -or $xy -eq '??')) { continue }
            $path = $line.Substring(3).Trim('"')
            if ($path.Contains(' -> ')) { $path = ($path -split ' -> ')[-1] }
            $path = $path.Replace('\', '/')
            if ($xy.Contains('D')) { $deleted++ }
            if ($Mode -eq 'Changes' -and -not (Matches-Scope $path)) { $outside.Add($path) }
            if ($path -match '^(AGENTS\.md|gradle\.properties|build\.gradle|settings\.gradle|\.gitattributes|src/main/resources/(manifest\.json|rpg-build\.properties)|docs/(DEVELOPMENT_BASELINE|DEPLOYMENT_REVISIONS)\.md|persistent-npcs/|tools/guardian/)') {
                $protected.Add($path)
            }
        }
        if ($deleted -ge $MassDeletionThreshold) {
            if ($AllowMassDeletion) { Add-Warning ("Approved mass deletion override: $deleted tracked paths; review the exact diff.") }
            else { Add-Blocked ("Suspicious mass deletion: $deleted tracked paths (threshold $MassDeletionThreshold).") }
        }
        if ($outside.Count -gt 0) {
            Add-Blocked ("Changes outside task scope: " + (($outside | Select-Object -First 5) -join ', ') + $(if ($outside.Count -gt 5) { ', ...' } else { '' }))
        }
        if ($protected.Count -gt 0) {
            Add-Warning ("Protected architecture/build paths changed: " + (($protected | Select-Object -First 5) -join ', ') + '. Review the diff.')
        }
    }

    if ($Mode -eq 'Completion') {
        $readme = Repo-Path 'README.md'
        $baselineDoc = Repo-Path 'docs/DEVELOPMENT_BASELINE.md'
        if (-not (Test-Path -LiteralPath $readme) -or -not (Test-Path -LiteralPath $baselineDoc)) {
            Add-Blocked 'Current baseline documentation is missing.'
        } else {
            $readmeStart = (Get-Content -LiteralPath $readme -TotalCount 18) -join ' '
            $docStart = (Get-Content -LiteralPath $baselineDoc -TotalCount 32) -join ' '
            if ($readmeStart -notmatch [regex]::Escape($revision) -or $docStart -notmatch [regex]::Escape($revision)) {
                Add-Blocked 'Current revision is not documented at the top of README and the baseline record.'
            }
        }
        $receiptPath = Repo-Path $Receipt
        if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
            Add-Blocked 'Verification receipt is missing; run the explicit offline validation command.'
        } else {
            $proof = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
            if ($proof.sourceCommit -ne $head -or $proof.revision -ne $revision -or
                $proof.version -ne $version -or $proof.gradleCheck -ne 'PASS' -or
                $proof.packageValidation -ne 'PASS') {
                Add-Blocked 'Verification receipt does not match this committed revision or lacks passing checks.'
            } else {
                $jarPath = Repo-Path ([string]$proof.jarPath)
                $logPath = Repo-Path ([string]$proof.checkLogPath)
                if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf) -or
                    -not (Test-Path -LiteralPath $logPath -PathType Leaf)) {
                    Add-Blocked 'Verified JAR or Gradle check log is missing.'
                } else {
                    $jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
                    $logHash = (Get-FileHash -LiteralPath $logPath -Algorithm SHA256).Hash
                    if ($jarHash -ne $proof.jarSha256 -or $logHash -ne $proof.checkLogSha256 -or
                        ((Get-Content -LiteralPath $logPath -Tail 30) -join ' ') -notmatch 'BUILD SUCCESSFUL') {
                        Add-Blocked 'Verified JAR or Gradle check log changed.'
                    }
                    Add-Type -AssemblyName System.IO.Compression.FileSystem
                    $zip = [IO.Compression.ZipFile]::OpenRead($jarPath)
                    try {
                        $entry = $zip.GetEntry('rpg-build.properties')
                        if ($null -eq $entry) { Add-Blocked 'JAR lacks rpg-build.properties.' }
                        else {
                            $reader = New-Object IO.StreamReader($entry.Open())
                            try { $buildProperties = $reader.ReadToEnd() } finally { $reader.Dispose() }
                            if ($buildProperties -notmatch ('(?m)^rpg\.sourceCommit=' + [regex]::Escape($head) + '\r?$') -or
                                $buildProperties -notmatch ('(?m)^rpg\.revision=' + [regex]::Escape($revision) + '\r?$')) {
                                Add-Blocked 'JAR source commit or revision differs from HEAD.'
                            }
                        }
                    } finally { $zip.Dispose() }
                    $validator = Repo-Path 'tools/Test-HyArpgPackage.ps1'
                    if (-not (Test-Path -LiteralPath $validator)) { Add-Blocked 'Package validator is missing.' }
                    else {
                        try {
                            $package = & $validator -JarPath $jarPath -ExpectedVersion $version -ExpectedRevision $revision | ConvertFrom-Json
                            if ($package.result -ne 'PASS' -or $package.sha256 -ne $jarHash) {
                                Add-Blocked 'Existing package validator did not confirm this JAR.'
                            }
                        } catch { Add-Blocked ('Existing package validator failed: ' + $_.Exception.Message) }
                    }
                }
                $reports = @($proof.reports)
                if ($reports.Count -lt 4) { Add-Blocked 'JUnit receipt lacks reports from all required test tasks.' }
                $modules = @('build/test-results/test/', 'build/test-results/nativeControlTest/',
                             'canvas-ui/build/test-results/test/', 'hytale-taverns/build/test-results/test/')
                foreach ($module in $modules) {
                    if (@($reports | Where-Object { ([string]$_.path).Replace('\', '/').StartsWith($module) }).Count -eq 0) {
                        Add-Blocked ("JUnit receipt lacks $module")
                    }
                }
                foreach ($report in $reports) {
                    $reportPath = Repo-Path ([string]$report.path)
                    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf) -or
                        (Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash -ne $report.sha256) {
                        Add-Blocked ('JUnit report missing or changed: ' + $report.path)
                    } else {
                        [xml]$xml = Get-Content -LiteralPath $reportPath -Raw
                        $suite = $xml.DocumentElement
                        if ($suite.LocalName -ne 'testsuite' -or [int]$suite.GetAttribute('tests') -lt 1 -or
                            [int]$suite.GetAttribute('failures') -gt 0 -or [int]$suite.GetAttribute('errors') -gt 0 -or
                            [int]$suite.GetAttribute('skipped') -gt 0) {
                            Add-Blocked ('JUnit report contains no tests or has failures/errors/skips: ' + $report.path)
                        }
                    }
                }
            }
        }
        if ($blocked.Count -eq 0) {
            $script:LocalState = 'VERIFIED'
            if ($LocalOnly) {
                Add-Warning 'GitHub synchronization was not checked; this is local verification only.'
            } else {
                # One narrow, read-only remote query. Never update refs or infer publication from a stale tracking ref.
                $oldPrompt = $env:GIT_TERMINAL_PROMPT
                $oldGcm = $env:GCM_INTERACTIVE
                $oldSsh = $env:GIT_SSH_COMMAND
                $env:GIT_TERMINAL_PROMPT = '0'
                $env:GCM_INTERACTIVE = 'never'
                $env:GIT_SSH_COMMAND = 'ssh -o BatchMode=yes -o ConnectTimeout=15'
                try {
                    $remoteHeads = Run-Git @('-c', 'http.lowSpeedLimit=1', '-c', 'http.lowSpeedTime=15',
                        'ls-remote', '--heads', 'origin', 'refs/heads/main', "refs/heads/$branch")
                } finally {
                    if ($null -eq $oldPrompt) { Remove-Item Env:GIT_TERMINAL_PROMPT -ErrorAction SilentlyContinue }
                    else { $env:GIT_TERMINAL_PROMPT = $oldPrompt }
                    if ($null -eq $oldGcm) { Remove-Item Env:GCM_INTERACTIVE -ErrorAction SilentlyContinue }
                    else { $env:GCM_INTERACTIVE = $oldGcm }
                    if ($null -eq $oldSsh) { Remove-Item Env:GIT_SSH_COMMAND -ErrorAction SilentlyContinue }
                    else { $env:GIT_SSH_COMMAND = $oldSsh }
                }
                if ($remoteHeads.Code -ne 0) {
                    $script:GitHubState = 'UNKNOWN'
                    $script:MainState = 'UNKNOWN'
                    Add-Warning 'Authorized GitHub remote could not be checked; a failed push or network error cannot be ruled out.'
                } else {
                    $heads = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
                    foreach ($line in ($remoteHeads.Text -split '\r?\n')) {
                        if ($line -match '^([0-9a-fA-F]{40})\s+(refs/heads/\S+)$') {
                            $heads[$Matches[2]] = $Matches[1].ToLowerInvariant()
                        }
                    }
                    if (-not $heads.ContainsKey('refs/heads/main')) {
                        Add-Blocked 'Authorized remote main is missing; GitHub integration cannot be verified.'
                    } else {
                        $liveMain = $heads['refs/heads/main']
                        $mainKnown = $liveMain -eq $head -or
                            (Run-Git @('cat-file', '-e', "$liveMain^{commit}")).Code -eq 0
                        if ($liveMain -eq $head -or ($mainKnown -and
                            (Run-Git @('merge-base', '--is-ancestor', $head, $liveMain)).Code -eq 0)) {
                            $script:MainState = 'INTEGRATED'
                            $script:GitHubState = 'PUBLISHED'
                        } elseif ($liveMain -eq $baseline) {
                            $script:MainState = 'PENDING'
                        } elseif (-not $mainKnown) {
                            $script:MainState = 'UNVERIFIED_NEW_MAIN'
                            Add-Warning 'GitHub main moved beyond locally known objects; fetch and review before claiming integration.'
                        } elseif ((Run-Git @('merge-base', '--is-ancestor', $baseline, $liveMain)).Code -ne 0) {
                            $script:MainState = 'DIVERGED'
                            Add-Blocked 'GitHub main diverged from the locally known authoritative baseline.'
                        } else {
                            $script:MainState = 'PENDING_NEW_MAIN'
                            Add-Warning 'GitHub main advanced since the local baseline; review the new commits.'
                        }
                        $branchRef = "refs/heads/$branch"
                        if ($heads.ContainsKey($branchRef)) {
                            $liveBranch = $heads[$branchRef]
                            if ($liveBranch -eq $head) {
                                $script:GitHubState = 'PUBLISHED'
                            } elseif ($script:MainState -ne 'INTEGRATED') {
                                $branchKnown = (Run-Git @('cat-file', '-e', "$liveBranch^{commit}")).Code -eq 0
                                if (-not $branchKnown) {
                                    $script:GitHubState = 'REMOTE_CHANGED'
                                    Add-Blocked "GitHub $branch differs from HEAD; fetch and review the remote branch."
                                } elseif ((Run-Git @('merge-base', '--is-ancestor', $liveBranch, $head)).Code -eq 0) {
                                    $script:GitHubState = 'UNPUBLISHED'
                                    Add-Warning "HEAD has unpublished commits on GitHub $branch; a push may have failed."
                                } elseif ((Run-Git @('merge-base', '--is-ancestor', $head, $liveBranch)).Code -eq 0) {
                                    $script:GitHubState = 'REMOTE_AHEAD'
                                    Add-Blocked "GitHub $branch advanced beyond this checkout; fetch and review."
                                } else {
                                    $script:GitHubState = 'DIVERGED'
                                    Add-Blocked "GitHub $branch diverged from this checkout; do not force-push."
                                }
                            }
                        } elseif ($script:MainState -ne 'INTEGRATED') {
                            $script:GitHubState = 'UNPUBLISHED'
                            Add-Warning "HEAD is not on GitHub $branch; the task branch has not been published."
                        }
                    }
                }
            }
        }
    }
} catch {
    Add-Blocked $_.Exception.Message
}
Finish
