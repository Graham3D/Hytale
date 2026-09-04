param([string]$OfflineRoot = 'G:\My Drive\Inigmas Games\Orbis Offline Training',[switch]$Install)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$lockPath = Join-Path $projectRoot 'training\configs\d6-environment-lock.json'
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
$python = Join-Path $OfflineRoot 'envs\block3-py311\Scripts\python.exe'
$uv = Join-Path $OfflineRoot 'toolchains\uv-0.12.9\uv.exe'
if ($lock.schemaVersion -ne 1) { throw 'Unsupported D6 environment lock.' }
if (-not (Test-Path -LiteralPath $python)) { throw "Pinned Python environment is missing: $python" }
if (-not (Test-Path -LiteralPath $uv)) { throw "Pinned uv executable is missing: $uv" }
$actualPython = (& $python --version 2>&1).ToString().Trim().Replace('Python ', '')
if ($actualPython -ne [string]$lock.python) { throw "Python mismatch: expected $($lock.python), got $actualPython" }
$blockedKernels = @($lock.requiredNativeKernels | Where-Object { -not $_.officialWindowsWheelAvailable })
[ordered]@{schemaVersion=1;python=$actualPython;uv=(& $uv --version).ToString().Trim();lockSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $lockPath).Hash.ToLowerInvariant();platform=[Environment]::OSVersion.VersionString;installRequested=[bool]$Install;blockedKernels=@($blockedKernels|ForEach-Object{"$($_.package)==$($_.version)"});decision=if($blockedKernels.Count){'REMOTE_REQUIRED'}else{'READY_TO_INSTALL'}} | ConvertTo-Json -Depth 6
if ($blockedKernels.Count) { Write-Error 'D6 is fail-closed: required official Mamba/CausalConv Windows wheels do not exist. No packages were installed.'; exit 2 }
if (-not $Install) { exit 0 }
throw 'Installation is unreachable until the selected platform passes the native-kernel gate.'
