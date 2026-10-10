[CmdletBinding()]
param(
    [string]$Repository = '.',
    [switch]$AllowWarning,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$CodexArguments
)

$ErrorActionPreference = 'Stop'
$root = (& git -C $Repository rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Not a Git worktree.' }
$guard = Join-Path $PSScriptRoot 'Invoke-Guardian.ps1'
$result = @(& $guard -Mode Preflight -Repository $root)
if ($LASTEXITCODE -ne 0 -or $result.Count -eq 0 -or
    ($result[0] -ne 'GUARDIAN: PASS' -and -not ($AllowWarning -and $result[0].StartsWith('GUARDIAN: WARNING')))) {
    $result | Write-Output
    exit 2
}
& codex --cd $root @CodexArguments
exit $LASTEXITCODE
