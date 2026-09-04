param([Parameter(Mandatory=$true)][string]$PreflightReport,[string]$OfflineRoot='G:\My Drive\Inigmas Games\Orbis Offline Training',[string]$DatasetId='ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86')
$ErrorActionPreference='Stop';$python=Join-Path $OfflineRoot 'envs\block3-py311\Scripts\python.exe';$gate=Join-Path $PSScriptRoot 'python\block3_gate.py';$manifest=Join-Path $OfflineRoot "datasets\$DatasetId\manifest.json"
& $python $gate smoke-readiness --preflight-report $PreflightReport --dataset-manifest $manifest
if($LASTEXITCODE -ne 0){Write-Error 'SFT-0 did not start. G2 and the 32-row smoke-data minimum are mandatory.';exit $LASTEXITCODE}
throw 'SFT-0 backend is intentionally unreachable until readiness returns an approved immutable run plan.'
