$ErrorActionPreference = 'Stop'

$script:MiniCpmRuntimeRevision = 'tc-mb/llama.cpp-omni v1.0.22 (commit 61d8393fdddff58e8f831ccfdf8a80ff989ebf9c; packaged llama-server build 4d28373)'
$script:MiniCpmGatewayRevision = '50b0865c819c2f0ca24ec7994e05044e5f39d451'
$script:MiniCpmModelRepository = 'openbmb/MiniCPM-o-4_5-gguf'
$script:MiniCpmModelRevision = 'db25077c33951fe163b42986fba0132e279872a2'
$script:MiniCpmModelDirectoryName = 'MiniCPM-o-4_5-gguf'
$script:MiniCpmGatewayPort = 8006
$script:MiniCpmPackagedGatewayPort = 8005
$script:MiniCpmWorkerPort = 22700
$script:MiniCpmCppPort = 19060

function Get-MiniCpmPaths {
    $runtimeRoot = if ($env:IMMERSIVE_NPCS_MINICPM_HOME) {
        [System.IO.Path]::GetFullPath($env:IMMERSIVE_NPCS_MINICPM_HOME)
    } else {
        # Avoid packaged-app LocalAppData virtualization so Hytale, operator
        # PowerShell, and Codex all resolve the same runtime/model directory.
        Join-Path $env:USERPROFILE '.immersive-npcs\minicpm'
    }
    $comniRoot = if ($env:COMNI_INSTALL_ROOT) {
        [System.IO.Path]::GetFullPath($env:COMNI_INSTALL_ROOT)
    } else {
        Join-Path $env:LOCALAPPDATA 'Comni\_internal\resources'
    }
    [pscustomobject]@{
        RuntimeRoot = $runtimeRoot
        ModelRoot = Join-Path $runtimeRoot 'models'
        ModelDir = Join-Path (Join-Path $runtimeRoot 'models') $script:MiniCpmModelDirectoryName
        StateDir = Join-Path $runtimeRoot 'state'
        LogDir = Join-Path $runtimeRoot 'logs'
        BenchmarkDir = Join-Path $runtimeRoot 'benchmarks'
        ComniRoot = $comniRoot
        Python = Join-Path $comniRoot 'python-embed\python.exe'
        ServerDir = Join-Path $comniRoot 'apps\server'
        ConfigPath = Join-Path $comniRoot 'apps\server\config.json'
        LlamaServer = Join-Path $comniRoot 'build\bin\Release\llama-server.exe'
        RefAudio = Join-Path $comniRoot 'apps\assets\ref_audio\ref_minicpm_signature.wav'
        PidFile = Join-Path (Join-Path $runtimeRoot 'state') 'processes.json'
        GatewaySource = Join-Path $runtimeRoot 'gateway-source'
    }
}

function Initialize-MiniCpmRuntimeDirectories {
    param([Parameter(Mandatory)]$Paths)
    foreach ($path in @($Paths.RuntimeRoot, $Paths.ModelRoot, $Paths.StateDir, $Paths.LogDir, $Paths.BenchmarkDir)) {
        [System.IO.Directory]::CreateDirectory($path) | Out-Null
    }
}

function Test-MiniCpmPort {
    param([Parameter(Mandatory)][int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Get-MiniCpmGpuSnapshot {
    $query = 'name,driver_version,memory.total,memory.used,memory.free,utilization.gpu,temperature.gpu'
    $line = & nvidia-smi "--query-gpu=$query" '--format=csv,noheader,nounits' 2>$null | Select-Object -First 1
    if (-not $line) { return $null }
    $parts = $line -split ',\s*'
    if ($parts.Count -lt 7) { return $null }
    [pscustomobject]@{
        Name = $parts[0]
        DriverVersion = $parts[1]
        TotalMiB = [int]$parts[2]
        UsedMiB = [int]$parts[3]
        FreeMiB = [int]$parts[4]
        UtilizationPercent = [int]$parts[5]
        TemperatureC = [int]$parts[6]
    }
}

function Assert-MiniCpmRuntimeInstalled {
    param([Parameter(Mandatory)]$Paths)
    foreach ($required in @($Paths.Python, $Paths.LlamaServer, $Paths.ServerDir)) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Required official Comni runtime component is missing: $required"
        }
    }
}
