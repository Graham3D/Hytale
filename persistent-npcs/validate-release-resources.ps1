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
    # Checkpoint 3 retains the two connected-proven probe files and expands the
    # same immutable canonical-image contract to the complete installed catalog.
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
    $catalogRoot = Join-Path $resourceRoot 'Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog'
    $catalogImageRoot = Join-Path $catalogRoot 'Thumbnails'
    $catalogIndex = Join-Path $catalogRoot 'index.tsv'
    $catalogLines = @(Get-Content -LiteralPath $catalogIndex | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($catalogLines.Count -ne 590) { throw "Checkpoint 3 requires 590 catalog references; found $($catalogLines.Count)" }
    $catalogFiles = @(Get-ChildItem -LiteralPath $catalogImageRoot -File -Filter '*.png' -ErrorAction Stop)
    if ($catalogFiles.Count -ne 588) { throw "Checkpoint 3 requires 588 added catalog images; found $($catalogFiles.Count)" }

    $registryByCategory = [ordered]@{
        BODY_CHARACTERISTIC='BodyCharacteristics'; CAPE='Capes'; EAR_ACCESSORY='EarAccessory'
        EARS='Ears'; EYEBROWS='Eyebrows'; EYES='Eyes'; FACE='Faces'
        FACE_ACCESSORY='FaceAccessory'; FACIAL_HAIR='FacialHair'; GLOVES='Gloves'
        HAIRCUT='Haircuts'; HEAD_ACCESSORY='HeadAccessory'; MOUTH='Mouths'
        OVERPANTS='Overpants'; OVERTOP='Overtops'; PANTS='Pants'; SHOES='Shoes'
        SKIN_FEATURE='SkinFeatures'; UNDERTOP='Undertops'; UNDERWEAR='Underwear'
    }
    $expectedKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($category in $registryByCategory.Keys) {
        foreach ($entry in @(Read-Registry $registryByCategory[$category])) {
            if (-not $expectedKeys.Add("${category}:$($entry.Id)")) {
                throw "Duplicate installed cosmetic ID: ${category}:$($entry.Id)"
            }
        }
    }
    $actualKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $referencedFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($line in $catalogLines) {
        $parts = $line.Split("`t")
        if ($parts.Count -ne 5) { throw "Malformed Checkpoint 3 index row: $line" }
        $key = "$($parts[0]):$($parts[1])"
        if (-not $actualKeys.Add($key)) { throw "Duplicate Checkpoint 3 reference: $key" }
        if (-not $expectedKeys.Contains($key)) { throw "Unknown Checkpoint 3 installed cosmetic: $key" }
        if ($parts[2] -notmatch '^UI/Custom/Pages/ImmersiveNpcAppearance/(Probe|Catalog/Thumbnails)/[A-Za-z0-9_.-]+\.png$') {
            throw "Unsafe Checkpoint 3 UI path: $($parts[2])"
        }
        if ($parts[3] -cne ('Common/' + $parts[2])) {
            throw "Checkpoint 3 UI/package path drift: $key"
        }
        $asset = Join-Path $resourceRoot $parts[3]
        if (-not (Test-Path -LiteralPath $asset -PathType Leaf)) { throw "Missing Checkpoint 3 image: $asset" }
        if (-not $referencedFiles.Add([IO.Path]::GetFullPath($asset))) { throw "Duplicate image path in Checkpoint 3 index: $asset" }
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $asset).Hash.ToLowerInvariant()
        if ($actualHash -cne $parts[4]) { throw "Checkpoint 3 image hash mismatch: $key" }
    }
    if ($actualKeys.Count -ne $expectedKeys.Count) {
        $missing = @($expectedKeys | Where-Object { -not $actualKeys.Contains($_) })
        throw "Checkpoint 3 catalog does not exactly cover installed registry; expected $($expectedKeys.Count), actual $($actualKeys.Count), missing $($missing -join ', ')"
    }
    $pageSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java')
    if ($pageSource -match 'AppearanceCardJobs|AppearanceColorCards|PrivateAppearanceCardAssets|queueAppearanceColorCards') {
        throw 'Unsafe runtime appearance image pipeline returned.'
    }
    $cardSource = Get-Content -Raw -LiteralPath (Join-Path $resourceRoot 'Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui')
    if (-not $cardSource.Contains('AssetImage #Thumbnail') -or
            -not $pageSource.Contains('#Thumbnail.AssetPath') -or
            $pageSource.Contains('appendInline(selector + " #ThumbnailHost"')) {
        throw 'R161 native AssetImage.AssetPath card binding contract is missing.'
    }
    Write-Host "Release resources validated: $revision; neutral skin, 590 immutable canonical cards, native AssetPath binding, exact installed-registry coverage, and zero-runtime-image safety policy"
} finally { $archive.Dispose() }
