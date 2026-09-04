param(
    [string]$OfflineRoot = 'C:\HytaleTraining\Orbis',
    [string]$R127RunDirectory = 'G:\My Drive\Inigmas Games\Orbis Offline Training\runs\preflight-20260904T002822Z-7b4f4f954af6',
    [string]$R127BaseEvidenceDirectory = 'G:\My Drive\Inigmas Games\Orbis Offline Training\models\base\nvidia-nemotron-3-nano-4b-bf16-dfaf35de',
    [string]$AuthorizedBaselineCommit = 'eee1f2f2b05fbbd532d2df7a542c329ed1cec2fb'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '..'))
$resolvedRoot = [IO.Path]::GetFullPath($OfflineRoot)
if ($resolvedRoot -match '(?i)[\\/](My Drive|Google Drive|DriveFS)[\\/]') {
    throw "Offline root must not use synchronized Drive storage: $resolvedRoot"
}

$datasetId = 'ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86'
$datasetSource = Join-Path $repositoryRoot "orbis-offline-training\datasets\$datasetId"
$entries = [Collections.Generic.List[object]]::new()
$script:copiedBytes = [long]0

function Copy-VerifiedFile([string]$Source, [string]$RelativeTarget, [string]$Origin) {
    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
        throw "Required immutable source is missing: $Source"
    }
    $target = Join-Path $resolvedRoot $RelativeTarget
    $parent = Split-Path -Parent $target
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Source).Hash.ToLowerInvariant()
    if (Test-Path -LiteralPath $target) {
        $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
        if ($targetHash -ne $sourceHash) {
            throw "Existing target differs; refusing overwrite: $target"
        }
    } else {
        Copy-Item -LiteralPath $Source -Destination $target
    }
    $entries.Add([ordered]@{
        path = $RelativeTarget.Replace('\\', '/')
        bytes = (Get-Item -LiteralPath $target).Length
        sha256 = $sourceHash
        origin = $Origin
    })
    $script:copiedBytes += (Get-Item -LiteralPath $target).Length
}

New-Item -ItemType Directory -Force -Path $resolvedRoot | Out-Null
foreach ($directory in @('registry','datasets','runs','models','reports','quarantine','evidence','provenance')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $resolvedRoot $directory) | Out-Null
}

Get-ChildItem -LiteralPath $datasetSource -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($datasetSource.Length).TrimStart('\\')
    Copy-VerifiedFile $_.FullName "datasets\$datasetId\$relative" 'GITHUB_BASELINE'
}
Copy-VerifiedFile (Join-Path $repositoryRoot 'orbis-offline-training\registry\datasets.jsonl') 'registry\datasets.jsonl' 'GITHUB_BASELINE'

Copy-VerifiedFile (Join-Path $projectRoot 'training\configs\block3.json') 'provenance\block3.json' 'R128_CONTINUATION_CONFIGURATION'
foreach ($name in @('g0-training-base.json','g0-license-decision.json','d6-environment-lock.json','d6-readiness-blockers.json')) {
    Copy-VerifiedFile (Join-Path $projectRoot "training\configs\$name") "provenance\$name" 'AUTHORIZED_GITHUB_BASELINE'
}
Copy-VerifiedFile (Join-Path $projectRoot 'docs\R127_ORBIS_DISTILLATION_BLOCK3_G0_D6_D7_IMPLEMENTATION.md') 'provenance\R127_ORBIS_DISTILLATION_BLOCK3_G0_D6_D7_IMPLEMENTATION.md' 'GITHUB_BASELINE'

foreach ($name in @('environment.json','peft-preflight-report.json','run-manifest.json')) {
    Copy-VerifiedFile (Join-Path $R127RunDirectory $name) "evidence\r127\$name" 'R127_VERIFIED_DRIVE_EVIDENCE'
}
foreach ($name in @('huggingface-revision-api.json','NVIDIA-Nemotron-Open-Model-License-2025-12-15.pdf','official-license-page.html','trustworthy-ai-terms.html')) {
    Copy-VerifiedFile (Join-Path $R127BaseEvidenceDirectory $name) "provenance\base\$name" 'R127_VERIFIED_DRIVE_EVIDENCE'
}

$manifest = [ordered]@{
    schemaVersion = 1
    bootstrapId = 'r128-local-root-v1'
    offlineRoot = $resolvedRoot
    datasetId = $datasetId
    authorizedRepositoryBaseline = $AuthorizedBaselineCommit
    bootstrapSourceState = 'R128_WORKING_TREE_BEFORE_COMMIT'
    copiedFileCount = $entries.Count
    copiedBytes = $script:copiedBytes
    files = @($entries | Sort-Object path)
    exclusions = @(
        'old Python environments',
        'package and model caches',
        'downloaded toolchains',
        'model weights and tokenizer snapshot',
        'obsolete and temporary runs',
        'trainer scratch and checkpoints'
    )
}
$manifestPath = Join-Path $resolvedRoot 'bootstrap-manifest.json'
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
[ordered]@{
    decision = 'LOCAL_OFFLINE_ROOT_INITIALIZED'
    offlineRoot = $resolvedRoot
    manifest = $manifestPath
    manifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
    copiedFileCount = $entries.Count
    copiedBytes = $manifest.copiedBytes
} | ConvertTo-Json -Depth 5
