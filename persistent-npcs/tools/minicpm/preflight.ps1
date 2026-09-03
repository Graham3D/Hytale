[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '_common.ps1')
$paths = Get-MiniCpmPaths
Initialize-MiniCpmRuntimeDirectories $paths

$failures = [System.Collections.Generic.List[string]]::new()
if (-not (Get-Command nvidia-smi -ErrorAction SilentlyContinue)) {
    $failures.Add('nvidia-smi is unavailable; an NVIDIA driver with supported telemetry is required.')
}
if (-not (Test-Path -LiteralPath $paths.Python)) {
    $failures.Add("Official Comni embedded Python is missing: $($paths.Python)")
}
if (-not (Test-Path -LiteralPath $paths.LlamaServer)) {
    $failures.Add("Official Comni llama.cpp-omni server is missing: $($paths.LlamaServer)")
}

$gpu = if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) { Get-MiniCpmGpuSnapshot } else { $null }
$os = Get-CimInstance Win32_OperatingSystem
$drive = Get-PSDrive -Name ([System.IO.Path]::GetPathRoot($paths.RuntimeRoot).Substring(0, 1))
$memoryFreeGiB = [math]::Round($os.FreePhysicalMemory * 1KB / 1GB, 2)
$memoryTotalGiB = [math]::Round($os.TotalVisibleMemorySize * 1KB / 1GB, 2)

$result = [ordered]@{
    checkedAt = (Get-Date).ToUniversalTime().ToString('o')
    runtimeRevision = $script:MiniCpmRuntimeRevision
    gatewayProtocolRevision = $script:MiniCpmGatewayRevision
    modelRepository = $script:MiniCpmModelRepository
    modelRevision = $script:MiniCpmModelRevision
    runtimeRoot = $paths.RuntimeRoot
    modelDirectory = $paths.ModelDir
    os = $os.Caption
    systemRamGiB = $memoryTotalGiB
    freeSystemRamGiB = $memoryFreeGiB
    freeDiskGiB = [math]::Round($drive.Free / 1GB, 2)
    gpu = $gpu
    ports = [ordered]@{
        gateway8006Busy = Test-MiniCpmPort $script:MiniCpmGatewayPort
        packagedGateway8005Busy = Test-MiniCpmPort $script:MiniCpmPackagedGatewayPort
        worker22700Busy = Test-MiniCpmPort $script:MiniCpmWorkerPort
        cpp19060Busy = Test-MiniCpmPort $script:MiniCpmCppPort
    }
    pass = ($failures.Count -eq 0)
    failures = $failures
}

$output = Join-Path $paths.StateDir 'preflight.json'
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $output -Encoding UTF8
$result | ConvertTo-Json -Depth 6
if ($failures.Count -gt 0) { exit 1 }
