param(
    [string]$OfflineRoot = 'C:\HytaleTraining\Orbis',
    [string]$RequiredDistribution = 'Ubuntu-24.04',
    [string]$EvidencePath
)

$ErrorActionPreference = 'Stop'
$resolvedRoot = [IO.Path]::GetFullPath($OfflineRoot)
if ($resolvedRoot -match '(?i)[\\/](My Drive|Google Drive|DriveFS)[\\/]') {
    throw "Training root must not use synchronized Drive storage: $resolvedRoot"
}

$wslText = (& wsl.exe --status 2>&1 | Out-String).Replace([string][char]0, '').Trim()
$wslExit = $LASTEXITCODE
$distributionRows = @()
if ($wslExit -eq 0) {
    $distributionRows = @(& wsl.exe --list --verbose 2>&1 | ForEach-Object {
        ([string]$_).Replace([string][char]0, '')
    })
}
$requiredDistributionPresent = @($distributionRows | Where-Object {
    $_ -match [regex]::Escape($RequiredDistribution) -and $_ -match '\s2\s*$'
}).Count -gt 0

$gpuCsv = (& nvidia-smi `
    --query-gpu=name,driver_version,memory.total,memory.used,memory.free,temperature.gpu,power.draw `
    --format=csv,noheader,nounits 2>&1 | Out-String).Trim()
$gpuExit = $LASTEXITCODE
$hytale = @(Get-Process -Name Hytale,HytaleServer -ErrorAction SilentlyContinue)
$ollama = @(Get-Process -Name Ollama,'ollama app' -ErrorAction SilentlyContinue)
$decision = if ($wslExit -ne 0) {
    'HOST_SETUP_REQUIRED'
} elseif (-not $requiredDistributionPresent) {
    'UBUNTU_24_04_SETUP_REQUIRED'
} else {
    'LINUX_DEPENDENCY_PREFLIGHT_REQUIRED'
}

$result = [ordered]@{
    schemaVersion = 1
    decision = $decision
    offlineRoot = $resolvedRoot
    synchronizedDriveRejected = $true
    wslStatusExitCode = $wslExit
    wslStatus = $wslText
    requiredDistribution = $RequiredDistribution
    requiredDistributionWsl2 = $requiredDistributionPresent
    distributions = $distributionRows
    windowsGpuProbeExitCode = $gpuExit
    windowsGpuCsv = $gpuCsv
    hytaleRunning = $hytale.Count -gt 0
    ollamaRunning = $ollama.Count -gt 0
    mutatesHost = $false
}
$json = $result | ConvertTo-Json -Depth 6
$json
if (-not [string]::IsNullOrWhiteSpace($EvidencePath)) {
    $resolvedEvidencePath = [IO.Path]::GetFullPath($EvidencePath)
    $rootPrefix = $resolvedRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedEvidencePath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Evidence path must remain inside the offline root: $resolvedEvidencePath"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resolvedEvidencePath) | Out-Null
    $json | Set-Content -LiteralPath $resolvedEvidencePath -Encoding utf8
}

if ($decision -ne 'LINUX_DEPENDENCY_PREFLIGHT_REQUIRED') {
    exit 2
}
