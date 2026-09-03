[CmdletBinding()]
param([switch]$VerifyOnly)

. (Join-Path $PSScriptRoot '_common.ps1')
$paths = Get-MiniCpmPaths
Initialize-MiniCpmRuntimeDirectories $paths
Assert-MiniCpmRuntimeInstalled $paths

$manifest = Join-Path $paths.StateDir 'model-manifest.json'
$arguments = @(
    (Join-Path $PSScriptRoot 'download_models.py'),
    '--repository', $script:MiniCpmModelRepository,
    '--revision', $script:MiniCpmModelRevision,
    '--model-dir', $paths.ModelDir,
    '--manifest', $manifest
)
if ($VerifyOnly) { $arguments += '--verify-only' }

& $paths.Python @arguments
if ($LASTEXITCODE -ne 0) { throw "MiniCPM model download/verification failed (exit $LASTEXITCODE)." }

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git is required to pin the official OpenBMB Gateway protocol source.' }
if (-not (Test-Path -LiteralPath $paths.GatewaySource)) {
    if ($VerifyOnly) { throw "Pinned Gateway source is missing: $($paths.GatewaySource)" }
    git clone --filter=blob:none --no-checkout https://github.com/OpenBMB/MiniCPM-o-Demo.git $paths.GatewaySource
    if ($LASTEXITCODE -ne 0) { throw 'Failed to clone OpenBMB MiniCPM-o-Demo.' }
}
$currentGatewayRevision = (git -C $paths.GatewaySource rev-parse HEAD 2>$null).Trim()
if ($currentGatewayRevision -ne $script:MiniCpmGatewayRevision) {
    if ($VerifyOnly) { throw "Gateway revision mismatch: expected $($script:MiniCpmGatewayRevision), found $currentGatewayRevision" }
    git -C $paths.GatewaySource fetch --depth=1 origin $script:MiniCpmGatewayRevision
    if ($LASTEXITCODE -ne 0) { throw 'Failed to fetch the pinned OpenBMB Gateway revision.' }
    git -C $paths.GatewaySource checkout --detach $script:MiniCpmGatewayRevision
    if ($LASTEXITCODE -ne 0) { throw 'Failed to check out the pinned OpenBMB Gateway revision.' }
}
