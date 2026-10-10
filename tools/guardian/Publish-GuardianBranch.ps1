[CmdletBinding()]
param(
    [string]$Repository = '.',
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$ExpectedCommit,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$ReviewedBase,
    [Parameter(Mandatory)][string[]]$ReviewedPaths,
    [switch]$StandingAuthorization,
    [switch]$FixtureMode
)

$ErrorActionPreference = 'Stop'
if (-not $StandingAuthorization) { throw 'Task-branch publication requires explicit standing authorization.' }
$root = (& git -C $Repository rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Not a Git worktree.' }
$head = (& git -C $root rev-parse HEAD).Trim()
$branch = (& git -C $root symbolic-ref --quiet --short HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -notmatch '^codex/[A-Za-z0-9._/-]+$' -or $branch.Contains('..')) {
    throw 'Only a named codex/ task branch may be published by this helper.'
}
if ($head -ne $ExpectedCommit.ToLowerInvariant()) { throw 'HEAD changed after commit review.' }
$baseline = (& git -C $root rev-parse refs/remotes/origin/main).Trim()
if ($LASTEXITCODE -ne 0 -or $baseline -ne $ReviewedBase.ToLowerInvariant()) {
    throw 'Locally known main changed after history review.'
}
& git -C $root merge-base --is-ancestor $baseline $head
if ($LASTEXITCODE -ne 0) { throw 'Task branch does not descend from the reviewed main baseline.' }
$merges = @(& git -C $root rev-list --min-parents=2 "$baseline..$head")
if ($LASTEXITCODE -ne 0 -or $merges.Count -ne 0) { throw 'Task history contains merge commits; review and publish it manually.' }
if (@(& git -C $root status --porcelain=v1 --untracked-files=all).Count -ne 0) {
    throw 'Uncommitted or untracked files are present; review and preserve them before publication.'
}
$fetchUrl = (& git -C $root remote get-url origin).Trim()
$pushUrl = (& git -C $root remote get-url --push origin).Trim()
if (-not $FixtureMode) {
    $authorized = '^(https://github\.com/|git@github\.com:|ssh://git@github\.com/)(Graham3D/Hytale)(\.git)?/?$'
    if ($fetchUrl -notmatch $authorized -or $pushUrl -notmatch $authorized) {
        throw 'Fetch or push URL is not the authorized Graham3D/Hytale repository.'
    }
}
$historyPaths = @(& git -C $root log --format= --name-only --no-renames "$baseline..$head" |
    Where-Object { $_ -ne '' } | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0 -or $historyPaths.Count -eq 0) { throw 'No reviewed task history to publish.' }
$approved = @($ReviewedPaths | Sort-Object -Unique)
if (@($approved | Where-Object { $_ -match '[\r\n]' }).Count -gt 0) { throw 'Reviewed paths must each occupy one line.' }
if ($approved.Count -ne $historyPaths.Count -or
    @($historyPaths | Where-Object { $approved -cnotcontains $_ }).Count -ne 0) {
    throw ('Commit history paths differ from the reviewed inventory: ' + ($historyPaths -join ', '))
}
$guard = Join-Path $PSScriptRoot 'Invoke-Guardian.ps1'
$local = @(& $guard -Mode Completion -LocalOnly -Repository $root -FixtureMode:$FixtureMode)
if ($LASTEXITCODE -ne 1 -or $local.Count -ne 2 -or
    $local[0] -ne 'GUARDIAN: WARNING - GitHub synchronization was not checked; this is local verification only.' -or
    $local[1] -notmatch '^  state: local=VERIFIED GitHub=NOT_CHECKED') {
    throw ('Local completion gate did not pass: ' + ($local -join ' '))
}

# A deliberate, non-force push of this exact commit to its matching task branch only.
$priorErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $pushOutput = @(& git -C $root push --porcelain origin "${head}:refs/heads/$branch" 2>&1); $pushCode = $LASTEXITCODE }
finally { $ErrorActionPreference = $priorErrorAction }
if ($pushCode -ne 0) {
    Write-Output "GUARDIAN: BLOCKED - Non-force push to origin/$branch failed (Git exit $pushCode); GitHub synchronization is unverified."
    exit 2
}
$verified = @(& $guard -Mode Completion -Repository $root -FixtureMode:$FixtureMode)
$verificationCode = $LASTEXITCODE
$verified | Write-Output
if ($verificationCode -eq 2 -or
    @($verified | Where-Object { $_ -match 'local=VERIFIED GitHub=PUBLISHED ' }).Count -eq 0) {
    Write-Output 'GUARDIAN: BLOCKED - Push returned success, but authorized remote publication was not verified.'
    exit 2
}
if ($verificationCode -eq 1) { exit 1 }
