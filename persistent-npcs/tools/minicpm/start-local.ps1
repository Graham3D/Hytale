[CmdletBinding()]
param(
    [ValidateRange(0, 99)][int]$GpuLayers = 99,
    [ValidateRange(1024, 32768)][int]$ContextSize = 4096
)

. (Join-Path $PSScriptRoot '_common.ps1')
$paths = Get-MiniCpmPaths
Initialize-MiniCpmRuntimeDirectories $paths
Assert-MiniCpmRuntimeInstalled $paths

& (Join-Path $PSScriptRoot 'download-model.ps1') -VerifyOnly
if ($LASTEXITCODE -ne 0) { throw 'Model verification failed.' }

foreach ($port in @($script:MiniCpmGatewayPort, $script:MiniCpmPackagedGatewayPort, $script:MiniCpmWorkerPort, $script:MiniCpmCppPort)) {
    if (Test-MiniCpmPort $port) { throw "Port $port is already in use. Run stop-local.ps1 or identify the owning process." }
}

$config = [ordered]@{
    backend = 'cpp'
    model = [ordered]@{ model_path = 'unused-for-cpp-backend' }
    audio = [ordered]@{
        ref_audio_path = $paths.RefAudio
        playback_delay_ms = 120
        chat_vocoder = 'token2wav'
    }
    service = [ordered]@{
        gateway_port = $script:MiniCpmGatewayPort
        worker_base_port = $script:MiniCpmWorkerPort
        max_queue_size = 8
        request_timeout = 300.0
        compile = $false
        data_dir = (Join-Path $paths.RuntimeRoot 'service-data')
    }
    recording = [ordered]@{ enabled = $false; session_retention_days = 0; max_storage_gb = 0.0 }
    cpp_backend = [ordered]@{
        llamacpp_root = $paths.ComniRoot
        model_dir = $paths.ModelDir
        llm_model = 'MiniCPM-o-4_5-Q4_K_M.gguf'
        cpp_server_port = $script:MiniCpmCppPort
        ctx_size = $ContextSize
        n_gpu_layers = $GpuLayers
        vision_backend = 'auto'
    }
    duplex = [ordered]@{ pause_timeout = 60.0 }
}
$config | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $paths.ConfigPath -Encoding UTF8

$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$workerOut = Join-Path $paths.LogDir "worker-$stamp.out.log"
$workerErr = Join-Path $paths.LogDir "worker-$stamp.err.log"
$gatewayOut = Join-Path $paths.LogDir "gateway-$stamp.out.log"
$gatewayErr = Join-Path $paths.LogDir "gateway-$stamp.err.log"
$adapterOut = Join-Path $paths.LogDir "realtime-adapter-$stamp.out.log"
$adapterErr = Join-Path $paths.LogDir "realtime-adapter-$stamp.err.log"
$adapterScript = Join-Path $paths.RuntimeRoot 'realtime_gateway_adapter.py'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'realtime_gateway_adapter.py') -Destination $adapterScript -Force

$oldNoProxy = $env:NO_PROXY
$oldNoProxyLower = $env:no_proxy
$oldPythonPath = $env:PYTHONPATH
try {
    $env:NO_PROXY = '*'
    $env:no_proxy = '*'
    $env:PYTHONPATH = $paths.ServerDir
    $worker = Start-Process -FilePath $paths.Python -ArgumentList @(
        (Join-Path $paths.ServerDir 'worker.py'), '--host', '127.0.0.1', '--port',
        [string]$script:MiniCpmWorkerPort, '--gpu-id', '0', '--worker-index', '0'
    ) -WorkingDirectory $paths.ServerDir -WindowStyle Hidden -RedirectStandardOutput $workerOut -RedirectStandardError $workerErr -PassThru

    $deadline = (Get-Date).AddMinutes(7)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($worker.HasExited) { throw "MiniCPM worker exited during startup. See $workerErr" }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:MiniCpmWorkerPort)/health" -TimeoutSec 3 -Proxy $null
            if ($health.model_loaded -ne $false) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "MiniCPM worker did not become healthy before timeout. See $workerErr" }

    $gateway = Start-Process -FilePath $paths.Python -ArgumentList @(
        (Join-Path $paths.ServerDir 'gateway.py'), '--host', '127.0.0.1', '--port',
        [string]$script:MiniCpmPackagedGatewayPort, '--workers', "127.0.0.1:$($script:MiniCpmWorkerPort)", '--http'
    ) -WorkingDirectory $paths.ServerDir -WindowStyle Hidden -RedirectStandardOutput $gatewayOut -RedirectStandardError $gatewayErr -PassThru

    $adapter = Start-Process -FilePath $paths.Python -ArgumentList @(
        $adapterScript, '--host', '127.0.0.1', '--port',
        [string]$script:MiniCpmGatewayPort, '--packaged-gateway', "http://127.0.0.1:$($script:MiniCpmPackagedGatewayPort)"
    ) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput $adapterOut -RedirectStandardError $adapterErr -PassThru

    $state = [ordered]@{
        startedAt = (Get-Date).ToUniversalTime().ToString('o')
        runtimeRevision = $script:MiniCpmRuntimeRevision
        modelRevision = $script:MiniCpmModelRevision
        gpuLayers = $GpuLayers
        contextSize = $ContextSize
        workerPid = $worker.Id
        gatewayPid = $gateway.Id
        adapterPid = $adapter.Id
        workerOut = $workerOut
        workerErr = $workerErr
        gatewayOut = $gatewayOut
        gatewayErr = $gatewayErr
        adapterOut = $adapterOut
        adapterErr = $adapterErr
    }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $paths.PidFile -Encoding UTF8
} finally {
    $env:NO_PROXY = $oldNoProxy
    $env:no_proxy = $oldNoProxyLower
    $env:PYTHONPATH = $oldPythonPath
}

Start-Sleep -Seconds 2
& (Join-Path $PSScriptRoot 'health-check.ps1')
