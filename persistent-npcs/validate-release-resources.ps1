param(
    [Parameter(Mandatory)][string]$ServerJar,
    [Parameter(Mandatory)][string]$ArtifactName
)

$ErrorActionPreference = 'Stop'
$manifest = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/manifest.json') | ConvertFrom-Json
$plugin = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java')
$revisionMatch = [regex]::Match($plugin, 'public static final String REVISION = "([^"]+)"')
$revision = $revisionMatch.Groups[1].Value
$expectedArtifact = 'ImmersiveNPCs-' + $manifest.Version.ToUpperInvariant() + '.jar'
if (-not $revisionMatch.Success -or $manifest.Version.ToUpperInvariant() -cne ('0.6.3-' + $revision) -or $ArtifactName -cne $expectedArtifact) {
    throw 'Release version drift: HUD REVISION, manifest Version, and output JAR must agree.'
}
$installer = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'install.ps1')
if (-not $installer.Contains("`$artifactName = '$ArtifactName'")) {
    throw 'Installer artifact does not match the release version.'
}

# Read the registry shipped with the exact server used for this build. No guessed IDs.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetsPath = Join-Path (Split-Path (Split-Path $ServerJar -Parent) -Parent) 'Assets.zip'
$archive = [IO.Compression.ZipFile]::OpenRead($assetsPath)
function Read-Registry([string]$name) {
    $entry = $archive.GetEntry("Cosmetics/CharacterCreator/$name.json")
    if ($null -eq $entry) { throw "Missing installed cosmetics registry: $name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
}
try {
    $neutral = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/defaults/profiles/neutral-appearance.json') | ConvertFrom-Json
    $registries = @{ bodyCharacteristic='BodyCharacteristics'; face='Faces'; ears='Ears'; eyes='Eyes'; mouth='Mouths'; underwear='Underwear' }
    $gradients = @(Read-Registry 'GradientSets')
    foreach ($field in $registries.Keys) {
        $encoded = $neutral.$field
        if ([string]::IsNullOrWhiteSpace($encoded)) { throw "Neutral appearance is missing $field" }
        $parts = $encoded.Split('.')
        $entries = @(Read-Registry $registries[$field])
        $selected = @($entries | Where-Object { $_.Id -ceq $parts[0] })
        if ($selected.Count -ne 1) { throw "Unknown neutral appearance ${field}: $encoded in $assetsPath" }
        if ($parts.Length -gt 1) {
            $gradient = @($gradients | Where-Object { $_.Id -ceq $selected[0].GradientSet })
            if ($gradient.Count -ne 1 -or $parts[1] -cnotin $gradient[0].Gradients.PSObject.Properties.Name) {
                throw "Unknown neutral appearance gradient ${field}: $encoded"
            }
        }
        foreach ($assetProperty in @('Model', 'GreyscaleTexture')) {
            $asset = $selected[0].$assetProperty
            if ($asset -and $null -eq $archive.GetEntry("Common/$asset")) {
                throw "Missing neutral appearance asset: $asset"
            }
        }
    }
    # R155: archived thumbnail experiments must never re-enter the production resource tree.
    $resourceRoot = Join-Path $PSScriptRoot 'src/main/resources'
    foreach ($forbidden in @('appearance-color-sources', 'Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails', 'Common/UI/Custom/Pages/ImmersiveNpcAppearanceThumbnails.ui')) {
        if (Test-Path -LiteralPath (Join-Path $resourceRoot $forbidden)) { throw "Unsafe appearance resource returned: $forbidden" }
    }
    # Checkpoint 2 permits exactly two immutable, source-hashed probe images.
    $probeRoot = Join-Path $resourceRoot 'Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe'
    $expectedProbeHashes = @{
        'UNDERTOP-FarmerTop.png' = '059DC8C47AFE08AAC235EF33EFF22F216751B046103CC208200CB3E3523CC219'
        'UNDERTOP-FlowerShirt.png' = 'FB59F44840BE4DE56C2BDFE82221889E18308669A409F54D3C009D27A02DF559'
    }
    $probeFiles = @(Get-ChildItem -LiteralPath $probeRoot -File -ErrorAction Stop)
    if ($probeFiles.Count -ne 2) { throw "Checkpoint 2 requires exactly two probe thumbnails; found $($probeFiles.Count)" }
    foreach ($probeFile in $probeFiles) {
        if (-not $expectedProbeHashes.ContainsKey($probeFile.Name)) {
            throw "Unexpected Checkpoint 2 probe asset: $($probeFile.Name)"
        }
        $actualProbeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $probeFile.FullName).Hash
        if ($actualProbeHash -cne $expectedProbeHashes[$probeFile.Name]) {
            throw "Checkpoint 2 probe hash mismatch: $($probeFile.Name)"
        }
    }
    $pageSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java')
    if ($pageSource -match 'AppearanceCardJobs|AppearanceColorCards|PrivateAppearanceCardAssets|queueAppearanceColorCards') {
        throw 'Unsafe runtime appearance image pipeline returned.'
    }
    Write-Host "Release resources validated: $revision; neutral skin, two immutable probe cards, and zero-runtime-image safety policy"
} finally { $archive.Dispose() }
