param(
    [string]$ModelPath = "$env:USERPROFILE\.ollama\models\blobs\sha256-527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970",
    [string]$RuntimeDirectory = "$env:LOCALAPPDATA\OrbisLLM\dev\b10701\windows-x64-cuda-12.4",
    [string]$ToolchainDirectory = "$env:LOCALAPPDATA\OrbisLLM\dev\llvm-mingw-20260826\llvm-mingw-20260826-ucrt-x86_64",
    [string]$InstalledRuntimeDirectory = "$env:APPDATA\Hytale\UserData\OrbisLLM\runtimes\b10701\windows-x64-cuda-12.4"
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$llamaSource = Join-Path $projectRoot 'tools\orbisllm\upstream\llama.cpp-b10701'
$jsonSource = Join-Path $projectRoot 'tools\orbisllm\upstream\json-3.12.0'
$clang = Join-Path $ToolchainDirectory 'bin\x86_64-w64-mingw32-clang++.exe'
$output = Join-Path $projectRoot 'build\orbisllm'
$runtimeBundle = Join-Path $output 'runtime'
$executable = Join-Path $output 'OrbisLLM.exe'
$expectedModelHash = '527DB2CF6C705D8FABB95693D038D9C06B4A2B0B8B0A4BBDBD01212D37242970'

foreach ($required in @(
    $clang,
    (Join-Path $llamaSource 'include\llama.h'),
    (Join-Path $jsonSource 'single_include\nlohmann\json.hpp'),
    (Join-Path $RuntimeDirectory 'llama.dll'),
    $ModelPath
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required pinned OrbisLLM input is missing: $required"
    }
}
if ((Get-FileHash -LiteralPath $ModelPath -Algorithm SHA256).Hash -ne $expectedModelHash) {
    throw 'The configured Nemotron GGUF does not match the pinned R064 control artifact.'
}

New-Item -ItemType Directory -Force -Path $output, $runtimeBundle,
    $InstalledRuntimeDirectory | Out-Null
& $clang -std=c++20 -O2 -DNOMINMAX -DUNICODE -D_UNICODE `
    -I (Join-Path $llamaSource 'include') `
    -I (Join-Path $llamaSource 'ggml\include') `
    -I (Join-Path $jsonSource 'single_include') `
    (Join-Path $projectRoot 'native\orbisllm\src\main.cpp') `
    -o $executable -lbcrypt -lpsapi -ladvapi32
if ($LASTEXITCODE -ne 0) {
    throw "OrbisLLM native compilation failed with exit code $LASTEXITCODE"
}
Copy-Item -LiteralPath (Join-Path $ToolchainDirectory 'bin\libc++.dll') -Destination $output -Force
Copy-Item -LiteralPath (Join-Path $ToolchainDirectory 'bin\libunwind.dll') -Destination $output -Force

$runtimeFiles = @(
    'llama.dll', 'ggml.dll', 'ggml-base.dll',
    'ggml-cuda.dll', 'cudart64_12.dll', 'cublas64_12.dll', 'cublasLt64_12.dll', 'libomp.dll'
)
$runtimeFiles += Get-ChildItem -LiteralPath $RuntimeDirectory -File -Filter 'ggml-cpu-*.dll' |
    ForEach-Object Name
$runtimeFiles = @($runtimeFiles | Sort-Object -Unique)
$runtimeFiles | ForEach-Object {
    Copy-Item -LiteralPath (Join-Path $RuntimeDirectory $_) `
        -Destination (Join-Path $runtimeBundle $_) -Force
    Copy-Item -LiteralPath (Join-Path $RuntimeDirectory $_) `
        -Destination (Join-Path $InstalledRuntimeDirectory $_) -Force
}
$binaries = @(
    Get-Item -LiteralPath $executable,
    (Join-Path $output 'libc++.dll'),
    (Join-Path $output 'libunwind.dll')
)
$binaries += $runtimeFiles | ForEach-Object {
    Get-Item -LiteralPath (Join-Path $InstalledRuntimeDirectory $_)
}
$binaryManifest = @($binaries | ForEach-Object {
    [ordered]@{
        name = $_.Name
        path = $_.FullName
        bytes = $_.Length
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
})
$manifest = [ordered]@{
    manifestVersion = 1
    protocolMajor = 1
    protocolMinor = 0
    platform = 'windows-x64-cuda-12.4'
    runtimeBuild = 'orbisllm-phase1-b10701'
    llamaTag = 'b10701'
    llamaCommit = 'cc231cb0da565440cf6a3e5b55dfeba477972cb6'
    executablePath = $executable
    runtimeDirectory = (Resolve-Path -LiteralPath $InstalledRuntimeDirectory).Path
    model = [ordered]@{
        id = 'nvidia-nemotron-3-nano-4b-q4_k_m'
        path = (Resolve-Path -LiteralPath $ModelPath).Path
        sha256 = $expectedModelHash.ToLowerInvariant()
        bytes = (Get-Item -LiteralPath $ModelPath).Length
        quantization = 'Q4_K_M'
        parameterCount = 4000000000
    }
    template = [ordered]@{
        revision = 'nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16@dfaf35de3e30f1867dd8dbc38a7fc9fb52d3914f'
        sha256 = 'ab7813c3abdd9cb655905a410728b26c7884eca45ddfab8d9f931553485a7862'
        renderer = 'ORBIS_NEMOTRON_3'
    }
    profiles = [ordered]@{
        BALANCED = [ordered]@{ gpuLayers = 4; contextSize = 4096; batchSize = 512; microbatchSize = 128; threads = 8 }
        CPU_FIRST = [ordered]@{ gpuLayers = 0; contextSize = 4096; batchSize = 256; microbatchSize = 64; threads = 8 }
    }
    binaries = $binaryManifest
}
$manifestPath = Join-Path $output 'orbisllm-windows-x64-cuda.json'
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash

Write-Host "Built $executable"
Write-Host "Manifest $manifestPath"
Write-Host "Manifest SHA-256 $manifestHash"
