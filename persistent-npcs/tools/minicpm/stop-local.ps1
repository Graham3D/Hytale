[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '_common.ps1')
$paths = Get-MiniCpmPaths
$stopped = [System.Collections.Generic.List[int]]::new()

if (Test-Path -LiteralPath $paths.PidFile) {
    $state = Get-Content -LiteralPath $paths.PidFile -Raw | ConvertFrom-Json
    foreach ($processId in @([int]$state.adapterPid, [int]$state.gatewayPid, [int]$state.workerPid)) {
        if ($processId -le 0) { continue }
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process) { continue }
        $executable = $null
        try { $executable = $process.Path } catch { }
        if ($executable -and ([System.IO.Path]::GetFullPath($executable) -ne [System.IO.Path]::GetFullPath($paths.Python))) {
            throw "Refusing to stop PID $processId because it is not the recorded Comni Python runtime: $executable"
        }
        Stop-Process -Id $processId -Force
        $stopped.Add($processId)
    }
}

Start-Sleep -Seconds 2
foreach ($port in @($script:MiniCpmCppPort, $script:MiniCpmWorkerPort, $script:MiniCpmPackagedGatewayPort, $script:MiniCpmGatewayPort)) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if (-not $process) { continue }
        $path = $null
        try { $path = $process.Path } catch { }
        $isComni = $path -and ([System.IO.Path]::GetFullPath($path).StartsWith([System.IO.Path]::GetFullPath($paths.ComniRoot), [System.StringComparison]::OrdinalIgnoreCase))
        if (-not $isComni) { throw "Port $port remains owned by a non-Comni process (PID $($process.Id), $path); refusing to stop it." }
        Stop-Process -Id $process.Id -Force
        $stopped.Add($process.Id)
    }
}

if (Test-Path -LiteralPath $paths.PidFile) { Remove-Item -LiteralPath $paths.PidFile -Force }
[pscustomobject]@{ stoppedPids = @($stopped | Select-Object -Unique); portsReleased = -not (@($script:MiniCpmCppPort, $script:MiniCpmWorkerPort, $script:MiniCpmPackagedGatewayPort, $script:MiniCpmGatewayPort) | Where-Object { Test-MiniCpmPort $_ }) } | ConvertTo-Json
