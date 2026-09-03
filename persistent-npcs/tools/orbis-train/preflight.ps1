param(
    [string]$OfflineRoot,
    [string]$ActiveSaveRoot = "$env:APPDATA\Hytale\UserData\Saves\NPC"
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$configPath = Join-Path $projectRoot 'training\configs\block1.json'
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json

if ($config.schemaVersion -ne 1 -or $config.mode -notin @('OFF', 'CORPUS_AUDIT')) {
    throw 'Unsupported Orbis Block-1 configuration.'
}
if ($config.trainingEnabled -or $config.packagingEnabled -or $config.promotionEnabled) {
    throw 'D0-D3 preflight refuses training, packaging, or promotion.'
}
if ([string]::IsNullOrWhiteSpace($OfflineRoot)) {
    $configured = [Environment]::GetEnvironmentVariable(
        [string]$config.offlineRootEnvironmentVariable)
    $OfflineRoot = if ([string]::IsNullOrWhiteSpace($configured)) {
        Join-Path $projectRoot ([string]$config.defaultRelativeRoot)
    } else { $configured }
}

$resolvedRoot = [IO.Path]::GetFullPath($OfflineRoot)
$resolvedSave = [IO.Path]::GetFullPath($ActiveSaveRoot)
$separator = [IO.Path]::DirectorySeparatorChar
$rootPrefix = $resolvedRoot.TrimEnd($separator) + $separator
$savePrefix = $resolvedSave.TrimEnd($separator) + $separator
if ($rootPrefix.StartsWith($savePrefix, [StringComparison]::OrdinalIgnoreCase) -or
        $savePrefix.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Offline root must not overlap active save: $resolvedRoot"
}

$directories = @('registry', 'candidates', 'teacher-runs', 'datasets', 'runs',
    'models', 'reports', 'quarantine')
New-Item -ItemType Directory -Force -Path $resolvedRoot | Out-Null
foreach ($directory in $directories) {
    New-Item -ItemType Directory -Force -Path (Join-Path $resolvedRoot $directory) | Out-Null
}

$serverJar = Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar'
$classes = Join-Path $projectRoot 'build\classes'
$java = Join-Path $projectRoot '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin\java.exe'
if ((Test-Path -LiteralPath $classes) -and (Test-Path -LiteralPath $serverJar) -and
        (Test-Path -LiteralPath $java)) {
    & $java -classpath "$classes;$serverJar" `
        com.inigmasgames.persistentnpcs.training.cli.Block1Bootstrap `
        $resolvedRoot $resolvedSave `
        (Join-Path $projectRoot 'training\configs\teacher-source-policies.json') `
        (Join-Path $projectRoot 'training\configs\production-model-identity.json') `
        (Join-Path $projectRoot 'training\configs\production-prompt-identity.json')
    if ($LASTEXITCODE -ne 0) { throw 'Block-1 registry bootstrap failed.' }
}

[pscustomobject]@{
    schemaVersion = 1
    mode = [string]$config.mode
    offlineRoot = $resolvedRoot
    activeSaveRoot = $resolvedSave
    modelMutationPermitted = $false
    teacherGenerationPermitted = $false
    readyForCorpusAudit = $true
} | ConvertTo-Json
