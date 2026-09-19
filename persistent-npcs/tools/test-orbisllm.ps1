param(
    [string]$Manifest = "$PSScriptRoot\..\build\orbisllm\orbisllm-windows-x64-cuda.json",
    [int]$GpuLayers = 4,
    [switch]$Structured,
    [switch]$Reasoning,
    [switch]$CancelAfterAccepted
)

$ErrorActionPreference = 'Stop'
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$definition = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$pipeName = 'orbisllm-smoke-' + [Guid]::NewGuid().ToString('N')
$nonce = ([Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N'))
$instance = [Guid]::NewGuid().ToString('N')
$log = Join-Path (Split-Path -Parent $manifestPath) 'orbisllm-smoke.log'
$errorLog = Join-Path (Split-Path -Parent $manifestPath) 'orbisllm-smoke-error.log'
$arguments = @(
    '--pipe', $pipeName,
    '--nonce', $nonce,
    '--manifest-hash', $manifestHash,
    '--runtime-dir', $definition.runtimeDirectory,
    '--approved-model-path', $definition.model.path,
    '--approved-model-sha256', $definition.model.sha256,
    '--instance-id', $instance,
    '--parent-pid', $PID
)
$process = Start-Process -FilePath $definition.executablePath -ArgumentList $arguments `
    -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errorLog

$pipe = [IO.Pipes.NamedPipeClientStream]::new('.', $pipeName,
    [IO.Pipes.PipeDirection]::InOut, [IO.Pipes.PipeOptions]::Asynchronous)
$pipe.Connect(10000)
$reader = [IO.BinaryReader]::new($pipe, [Text.Encoding]::UTF8, $true)
$writer = [IO.BinaryWriter]::new($pipe, [Text.Encoding]::UTF8, $true)
$sequence = 0L

function Send-Frame([int]$type, [Guid]$requestId, [hashtable]$body) {
    $script:sequence++
    $bytes = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20 -Compress))
    $writer.Write([uint32]0x4c42524f)
    $writer.Write([uint16]1)
    $writer.Write([uint16]0)
    $writer.Write([uint16]$type)
    $writer.Write([uint16]0)
    $writer.Write([uint32]$bytes.Length)
    $writer.Write([uint64]$script:sequence)
    $writer.Write($requestId.ToByteArray())
    $writer.Write($bytes)
    $writer.Flush()
    Write-Host "SEND type=$type request=$requestId sequence=$script:sequence bytes=$($bytes.Length)"
}

function Read-Frame {
    $magic = $reader.ReadUInt32()
    if ($magic -ne 0x4c42524f) { throw 'Bad OrbisLLM frame magic' }
    $major = $reader.ReadUInt16(); $minor = $reader.ReadUInt16()
    $type = $reader.ReadUInt16(); $flags = $reader.ReadUInt16()
    $length = $reader.ReadUInt32(); $serverSequence = $reader.ReadUInt64()
    $request = [Guid]::new($reader.ReadBytes(16))
    $payload = $reader.ReadBytes($length)
    if ($payload.Length -ne $length) { throw 'Truncated OrbisLLM frame' }
    [pscustomobject]@{
        Type = $type
        RequestId = $request
        Sequence = $serverSequence
        Body = ([Text.Encoding]::UTF8.GetString($payload) | ConvertFrom-Json)
    }
}

function Read-Until([int[]]$types, [Guid]$requestId) {
    while ($true) {
        $frame = Read-Frame
        Write-Host ("EVENT type={0} request={1} state={2}" -f
            $frame.Type, $frame.RequestId, $frame.Body.state)
        if ($frame.Type -eq 22) { throw "OrbisLLM error: $($frame.Body.category): $($frame.Body.detail)" }
        if ($frame.RequestId -eq $requestId -and $types -contains $frame.Type) { return $frame }
    }
}

try {
    $runtime = [Guid]::NewGuid()
    Send-Frame 1 $runtime @{
        nonce = $nonce
        runtimeManifestHash = $manifestHash
        protocolMajor = 1
        protocolMinor = 0
    }
    $hello = Read-Until @(2) $runtime

    $load = [Guid]::NewGuid()
    Send-Frame 3 $load @{
        modelId = $definition.model.id
        modelPath = $definition.model.path
        modelSha256 = $definition.model.sha256
        gpuLayers = $GpuLayers
    }
    $loaded = Read-Until @(6) $load

    $context = [Guid]::NewGuid()
    Send-Frame 5 $context @{
        contextSize = 4096
        batchSize = 512
        microbatchSize = 128
        threads = 8
    }
    $ready = Read-Until @(6) $context

    $request = [Guid]::NewGuid()
    $contract = if ($Structured) { 'compact-choice-v1' } else { 'dialogue-text-v1' }
    $user = if ($Structured) {
        'Return exactly this decision as JSON: intent AMBIENT_RESPONSE, spokenText Hello there., emotion CALM, paralinguisticEvent NONE, actions empty, groundingEvidenceRefs empty.'
    } else {
        'Reply with one short friendly sentence introducing yourself as Mara.'
    }
    Send-Frame 7 $request @{
        requestId = $request.ToString()
        turnId = ([Guid]::NewGuid().ToString())
        responseId = ([Guid]::NewGuid().ToString())
        branchEpoch = 1
        messages = @(
            @{ role = 'system'; content = 'You are Mara, a curious Hytale NPC. Be concise.' },
            @{ role = 'user'; content = $user }
        )
        reasoningMode = $(if ($Reasoning) { 'ENABLED' } else { 'DISABLED' })
        outputContractId = $contract
        structured = [bool]$Structured
        maxTokens = $(if ($Structured) { 160 } else { 48 })
        temperature = $(if ($Structured) { 0.0 } else { 0.3 })
        topP = 1.0
        topK = 40
    }
    if ($CancelAfterAccepted) {
        Read-Until @(8) $request | Out-Null
        $cancelStarted = [Diagnostics.Stopwatch]::StartNew()
        Send-Frame 14 $request @{ requestId = $request.ToString(); reason = 'SMOKE_BARGE_IN' }
        $cancelled = Read-Until @(16) $request
        $cancelStarted.Stop()
        Write-Host "ORBISLLM_SMOKE_CANCEL_ACK_MS=$($cancelStarted.ElapsedMilliseconds)"
        Write-Host "ORBISLLM_SMOKE_CANCEL_STAGE=$($cancelled.Body.stage)"
    } else {
        $completed = Read-Until @(13) $request
        Write-Host "ORIBSLLM_SMOKE_TEXT=$($completed.Body.text)"
        Write-Host "ORBISLLM_SMOKE_TTFT_MS=$($completed.Body.ttftMillis)"
        Write-Host "ORBISLLM_SMOKE_COMPLETION_MS=$($completed.Body.completionMillis)"
    }

    $shutdown = [Guid]::NewGuid()
    Send-Frame 23 $shutdown @{}
    Read-Until @(24) $shutdown | Out-Null
} finally {
    $reader.Dispose(); $writer.Dispose(); $pipe.Dispose()
    if (-not $process.HasExited) { $process.Kill($true) }
    $process.Dispose()
}
