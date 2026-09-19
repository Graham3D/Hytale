param(
    [ValidateSet('standalone', 'co-resident')]
    [string]$Mode = 'standalone',
    [string]$ProfileLabel = 'default',
    [ValidateRange(1, 20)]
    [int]$Turns = 1,
    [ValidateRange(100, 2000)]
    [int]$GpuSampleMs = 200,
    [ValidateRange(20, 180)]
    [int]$TurnTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '_common.ps1')

$paths = Get-MiniCpmPaths
Initialize-MiniCpmRuntimeDirectories -Paths $paths
Assert-MiniCpmRuntimeInstalled -Paths $paths

$health = Invoke-RestMethod -Uri "http://127.0.0.1:$script:MiniCpmGatewayPort/health" -TimeoutSec 5
if (-not $health.healthy) {
    throw 'MiniCPM public realtime adapter is not healthy. Run start-local.ps1 first.'
}

$hytale = Get-Process -Name 'HytaleClient' -ErrorAction SilentlyContinue | Select-Object -First 1
$server = Get-Process -Name 'java' -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -like '*\Hytale\install\release\package\jre\*' } |
    Select-Object -First 1
if ($Mode -eq 'co-resident' -and (-not $hytale -or -not $server)) {
    throw 'Co-resident mode requires a running Hytale client and local Hytale Java server.'
}

$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$runDir = Join-Path $paths.BenchmarkDir "${stamp}_${Mode}_${ProfileLabel}"
[System.IO.Directory]::CreateDirectory($runDir) | Out-Null

$gpuCsv = Join-Path $runDir 'gpu.csv'
$gpuErr = Join-Path $runDir 'gpu.stderr.log'
$gpuQuery = 'timestamp,memory.total,memory.used,memory.free,utilization.gpu,temperature.gpu'
$gpuArgs = @(
    "--query-gpu=$gpuQuery",
    '--format=csv,noheader,nounits',
    "--loop-ms=$GpuSampleMs"
)
$gpuMonitor = Start-Process -FilePath 'nvidia-smi.exe' -ArgumentList $gpuArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $gpuCsv -RedirectStandardError $gpuErr

$probe = Join-Path $paths.GatewaySource 'examples\realtime\audio_probe.py'
$inputWav = Join-Path $paths.GatewaySource 'examples\realtime\assets\test.wav'
foreach ($required in @($probe, $inputWav)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required pinned benchmark asset is missing: $required" }
}

$hytaleBefore = if ($hytale) {
    [pscustomobject]@{ Id = $hytale.Id; CpuSeconds = $hytale.CPU; WorkingSetBytes = $hytale.WorkingSet64 }
} else { $null }
$serverBefore = if ($server) {
    [pscustomobject]@{ Id = $server.Id; CpuSeconds = $server.CPU; WorkingSetBytes = $server.WorkingSet64 }
} else { $null }
$startedAt = Get-Date
$turnResults = [System.Collections.Generic.List[object]]::new()

try {
    for ($turn = 1; $turn -le $Turns; $turn++) {
        $rawPath = Join-Path $runDir ("turn-{0:D2}.json" -f $turn)
        $rawErr = Join-Path $runDir ("turn-{0:D2}.stderr.log" -f $turn)
        $instructions = 'You are a concise, grounded English-speaking game character. Reply naturally in one short English sentence.'
        $arguments = @(
            $probe,
            '--url', "http://127.0.0.1:$script:MiniCpmGatewayPort",
            '--region', "local-${Mode}-${ProfileLabel}",
            '--input-wav', $inputWav,
            '--instructions', $instructions,
            '--chunk-ms', '1000',
            '--tail-silence-s', '3',
            '--max-session-s', $TurnTimeoutSeconds.ToString(),
            '--ping-count', '1',
            '--include-events',
            '--pretty-json'
        )
        $turnStarted = Get-Date
        & $paths.Python @arguments 1> $rawPath 2> $rawErr
        $probeExitCode = $LASTEXITCODE
        $turnEnded = Get-Date

        if ($probeExitCode -ne 0) {
            $errorText = if (Test-Path -LiteralPath $rawErr) { Get-Content -LiteralPath $rawErr -Raw } else { '' }
            $turnResults.Add([pscustomobject]@{
                Turn = $turn
                Success = $false
                ExitCode = $probeExitCode
                WallMs = [math]::Round(($turnEnded - $turnStarted).TotalMilliseconds, 1)
                Error = $errorText.Trim()
            })
            continue
        }

        $raw = Get-Content -LiteralPath $rawPath -Raw | ConvertFrom-Json
        $sender = $raw.events | Where-Object { $_.type -eq 'client.audio_sender_started' } | Select-Object -First 1
        $timedOut = $null -ne ($raw.events | Where-Object { $_.type -eq 'client.max_session_reached' } | Select-Object -First 1)
        $hasPlayableAudio = $null -ne $raw.first_audio_ms -and [int]$raw.output_audio_chunks -gt 0
        $probeSucceeded = [bool]$raw.success -and $hasPlayableAudio -and -not $timedOut
        $approxSpeechEndMs = if ($sender) { [double]$sender.t_ms + ([double]$sender.chunks * 1000.0) } else { $null }
        $speechEndToFirstAudioMs = if ($null -ne $approxSpeechEndMs -and $null -ne $raw.first_audio_ms) {
            [math]::Round(([double]$raw.first_audio_ms - $approxSpeechEndMs), 1)
        } else { $null }
        $turnResults.Add([pscustomobject]@{
            Turn = $turn
            Success = $probeSucceeded
            FailureReason = if ($timedOut) { 'session_timeout' } elseif (-not $hasPlayableAudio) { 'no_playable_audio' } else { $null }
            WallMs = [math]::Round(($turnEnded - $turnStarted).TotalMilliseconds, 1)
            SessionReadyMs = if ($sender) { [math]::Round([double]$sender.t_ms, 1) } else { $null }
            FirstTextMs = $raw.first_text_ms
            FirstAudioMs = $raw.first_audio_ms
            ApproxSpeechEndMs = $approxSpeechEndMs
            SpeechEndToFirstAudioMs = $speechEndToFirstAudioMs
            OutputAudioChunks = $raw.output_audio_chunks
            UnderrunCount = $raw.underrun_count
            Text = $raw.text
        })
    }
} finally {
    if ($gpuMonitor -and -not $gpuMonitor.HasExited) {
        Stop-Process -Id $gpuMonitor.Id -Force -ErrorAction SilentlyContinue
        $gpuMonitor.WaitForExit(5000) | Out-Null
    }
}

$endedAt = Get-Date
$gpuRows = @()
if (Test-Path -LiteralPath $gpuCsv) {
    $gpuRows = Get-Content -LiteralPath $gpuCsv |
        Where-Object { $_ -and $_ -notmatch '^timestamp' } |
        ForEach-Object {
            $p = $_ -split ',\s*'
            if ($p.Count -ge 6) {
                [pscustomobject]@{
                    Timestamp = $p[0]
                    TotalMiB = [int]$p[1]
                    UsedMiB = [int]$p[2]
                    FreeMiB = [int]$p[3]
                    UtilizationPercent = [int]$p[4]
                    TemperatureC = [int]$p[5]
                }
            }
        }
}

$hytaleAfter = if ($hytaleBefore) { Get-Process -Id $hytaleBefore.Id -ErrorAction SilentlyContinue } else { $null }
$serverAfter = if ($serverBefore) { Get-Process -Id $serverBefore.Id -ErrorAction SilentlyContinue } else { $null }
$os = Get-CimInstance Win32_OperatingSystem
$summary = [ordered]@{
    SchemaVersion = 1
    StartedAt = $startedAt.ToString('o')
    EndedAt = $endedAt.ToString('o')
    Mode = $Mode
    ProfileLabel = $ProfileLabel
    RuntimeRevision = $script:MiniCpmRuntimeRevision
    GatewayRevision = $script:MiniCpmGatewayRevision
    ModelRepository = $script:MiniCpmModelRepository
    ModelRevision = $script:MiniCpmModelRevision
    TurnsRequested = $Turns
    TurnsSucceeded = @($turnResults | Where-Object Success).Count
    Hytale = [ordered]@{
        Required = $Mode -eq 'co-resident'
        ClientPid = if ($hytaleBefore) { $hytaleBefore.Id } else { $null }
        ServerPid = if ($serverBefore) { $serverBefore.Id } else { $null }
        ClientAliveAfter = $null -ne $hytaleAfter
        ServerAliveAfter = $null -ne $serverAfter
        ClientCpuDeltaSeconds = if ($hytaleAfter) { [math]::Round($hytaleAfter.CPU - $hytaleBefore.CpuSeconds, 3) } else { $null }
        ServerCpuDeltaSeconds = if ($serverAfter) { [math]::Round($serverAfter.CPU - $serverBefore.CpuSeconds, 3) } else { $null }
        ClientWorkingSetMiB = if ($hytaleAfter) { [math]::Round($hytaleAfter.WorkingSet64 / 1MB, 1) } else { $null }
        ServerWorkingSetMiB = if ($serverAfter) { [math]::Round($serverAfter.WorkingSet64 / 1MB, 1) } else { $null }
        FrameMetrics = 'unavailable_without_elevated_presentmon_or_native_frame_telemetry'
    }
    Gpu = [ordered]@{
        Samples = @($gpuRows).Count
        MinFreeMiB = if ($gpuRows) { ($gpuRows | Measure-Object FreeMiB -Minimum).Minimum } else { $null }
        MaxUsedMiB = if ($gpuRows) { ($gpuRows | Measure-Object UsedMiB -Maximum).Maximum } else { $null }
        MaxUtilizationPercent = if ($gpuRows) { ($gpuRows | Measure-Object UtilizationPercent -Maximum).Maximum } else { $null }
        MaxTemperatureC = if ($gpuRows) { ($gpuRows | Measure-Object TemperatureC -Maximum).Maximum } else { $null }
    }
    System = [ordered]@{
        FreePhysicalMemoryMiB = [math]::Round(([double]$os.FreePhysicalMemory / 1024.0), 1)
        TotalVisibleMemoryMiB = [math]::Round(([double]$os.TotalVisibleMemorySize / 1024.0), 1)
    }
    Turns = @($turnResults)
}

$summaryPath = Join-Path $runDir 'summary.json'
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 10
Write-Host "Benchmark artifacts: $runDir"

if ($summary.TurnsSucceeded -ne $Turns) { exit 1 }
if ($Mode -eq 'co-resident' -and (-not $summary.Hytale.ClientAliveAfter -or -not $summary.Hytale.ServerAliveAfter)) { exit 2 }
