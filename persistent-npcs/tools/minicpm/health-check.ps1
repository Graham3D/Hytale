[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '_common.ps1')
$paths = Get-MiniCpmPaths
$status = [ordered]@{
    checkedAt = (Get-Date).ToUniversalTime().ToString('o')
    gateway = $null
    packagedGateway = $null
    worker = $null
    gpu = if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) { Get-MiniCpmGpuSnapshot } else { $null }
    healthy = $false
}
try { $status.worker = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:MiniCpmWorkerPort)/health" -TimeoutSec 5 -Proxy $null } catch { $status.worker = @{ error = $_.Exception.Message } }
try { $status.packagedGateway = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:MiniCpmPackagedGatewayPort)/health" -TimeoutSec 5 -Proxy $null } catch { $status.packagedGateway = @{ error = $_.Exception.Message } }
try { $status.gateway = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:MiniCpmGatewayPort)/health" -TimeoutSec 5 -Proxy $null } catch { $status.gateway = @{ error = $_.Exception.Message } }
$status.healthy = ($status.worker.error -eq $null -and $status.packagedGateway.error -eq $null -and $status.gateway.error -eq $null -and $status.worker.model_loaded -ne $false -and $status.gateway.healthy -ne $false)
$status | ConvertTo-Json -Depth 8
if (-not $status.healthy) { exit 1 }
