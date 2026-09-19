param(
    [string]$Model = 'nemotron-3-nano:4b',
    [string]$OllamaEndpoint = 'http://127.0.0.1:11434/api/chat',
    [int]$Iterations = 3,
    [switch]$AllowWithoutHytale
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$hytale = @(Get-Process -ErrorAction SilentlyContinue | Where-Object {
    $_.ProcessName -match '^Hytale(Client|Server)?$'
})
$chatterbox = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match 'immersive_voice_worker' -and $_.CommandLine -match '(--role[ =]tts)'
})
if (-not $AllowWithoutHytale -and ($hytale.Count -eq 0 -or $chatterbox.Count -eq 0)) {
    throw 'Controlled benchmark requires active Hytale and Chatterbox. Start both, or use -AllowWithoutHytale for provider-only diagnostics.'
}

$strategies = @(
    [pscustomobject]@{ Policy = 'GPU_HEAVY'; NumGpu = -1 },
    [pscustomobject]@{ Policy = 'CPU_FIRST'; NumGpu = 0 },
    [pscustomobject]@{ Policy = 'PARTIAL_OFFLOAD'; NumGpu = 12 }
)
$prompt = 'Reply in one concise sentence as a grounded tavern NPC acknowledging that the player greeted you.'
$results = [System.Collections.Generic.List[object]]::new()

function Read-GpuSnapshot {
    try {
        $line = & nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.free,memory.total --format=csv,noheader,nounits 2>$null | Select-Object -First 1
        if (-not $line) { return $null }
        $parts = $line -split ',' | ForEach-Object Trim
        return [pscustomobject]@{
            Name = $parts[0]; UtilizationPercent = [int]$parts[1]
            VramUsedMiB = [long]$parts[2]; VramFreeMiB = [long]$parts[3]
            VramTotalMiB = [long]$parts[4]
        }
    } catch { return $null }
}

foreach ($strategy in $strategies) {
    for ($iteration = 1; $iteration -le [Math]::Max(1, $Iterations); $iteration++) {
        $beforeGpu = Read-GpuSnapshot
        $beforeCpu = @($hytale | ForEach-Object CPU | Measure-Object -Sum).Sum
        $started = [Diagnostics.Stopwatch]::StartNew()
        $body = @{
            model = $Model
            stream = $false
            think = $false
            messages = @(@{ role = 'user'; content = $prompt })
            options = @{ num_gpu = $strategy.NumGpu; temperature = 0.4; num_predict = 96 }
        } | ConvertTo-Json -Depth 8
        $response = Invoke-RestMethod -Method Post -Uri $OllamaEndpoint -ContentType 'application/json' -Body $body -TimeoutSec 120
        $started.Stop()
        $afterGpu = Read-GpuSnapshot
        $afterCpu = @($hytale | ForEach-Object { $_.Refresh(); $_.CPU } | Measure-Object -Sum).Sum
        $evalSeconds = [double]$response.eval_duration / 1000000000.0
        $results.Add([pscustomobject]@{
            Policy = $strategy.Policy
            NumGpuLayers = $strategy.NumGpu
            Iteration = $iteration
            WallMillis = $started.ElapsedMilliseconds
            LoadMillis = [math]::Round([double]$response.load_duration / 1000000.0, 2)
            PromptEvalMillis = [math]::Round([double]$response.prompt_eval_duration / 1000000.0, 2)
            GenerationMillis = [math]::Round([double]$response.eval_duration / 1000000.0, 2)
            TokensPerSecond = if ($evalSeconds -gt 0) { [math]::Round([double]$response.eval_count / $evalSeconds, 2) } else { 0 }
            HytaleCpuSecondsDelta = [math]::Round([double]$afterCpu - [double]$beforeCpu, 3)
            GpuBefore = $beforeGpu
            GpuAfter = $afterGpu
            HytalePresent = $hytale.Count -gt 0
            ChatterboxPresent = $chatterbox.Count -gt 0
        })
    }
}

$telemetry = Join-Path $projectRoot 'telemetry'
New-Item -ItemType Directory -Force -Path $telemetry | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$output = Join-Path $telemetry "orbis-resource-benchmark-$stamp.json"
[pscustomobject]@{
    schemaVersion = 1
    invokedAt = (Get-Date).ToUniversalTime().ToString('o')
    productionConfigurationChanged = $false
    model = $Model
    endpoint = $OllamaEndpoint
    results = $results
} | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $output -Encoding utf8
$results | Format-Table Policy, NumGpuLayers, Iteration, WallMillis, GenerationMillis, TokensPerSecond, HytaleCpuSecondsDelta
Write-Host "Benchmark written to $output"
