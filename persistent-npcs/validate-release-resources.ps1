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
    $thumbnailRoot = Join-Path $PSScriptRoot 'src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails'
    $provenance = Get-Content -Raw -LiteralPath (Join-Path $thumbnailRoot 'provenance.json') | ConvertFrom-Json
    if (@($provenance.unavailable).Count -ne 0) { throw 'Pinned cosmetic thumbnail coverage is incomplete.' }
    if (($provenance.size -join ',') -ne '92,149' -or ($provenance.bakeSize -join ',') -ne '184,298') {
        throw 'Client thumbnail atlas budget or high-resolution bake contract drift.'
    }
    $rendererSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'tools/bake_appearance_thumbnails.py')).Replace("`r`n", "`n")
    $rendererHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($rendererSource)))
    if ($provenance.renderer -cne 'R152 fixed category rigs v2' -or $rendererHash -ine $provenance.rendererSha256) {
        throw 'Rebake thumbnails: category rig renderer provenance mismatch.'
    }
    $categoryHashes = @{}
    if (@($provenance.entryRigHashes.PSObject.Properties).Count -ne 590) { throw 'Incomplete category rig provenance.' }
    foreach ($entry in $provenance.entryRigHashes.PSObject.Properties) {
        $category = $entry.Name.Split(':')[0]
        if ($categoryHashes.ContainsKey($category) -and $categoryHashes[$category] -cne $entry.Value) {
            throw "Per-item camera drift in category $category"
        }
        $categoryHashes[$category] = $entry.Value
    }
    foreach ($source in $provenance.sourceHashes.PSObject.Properties) {
        $entry = $archive.GetEntry($source.Name)
        if ($null -eq $entry) { throw "Thumbnail source no longer exists: $($source.Name)" }
        $stream = $entry.Open()
        try { $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
        finally { $stream.Dispose() }
        if ($actual -ine $source.Value) { throw "Rebake cosmetic thumbnails against changed installed source: $($source.Name)" }
    }
    foreach ($line in Get-Content -LiteralPath (Join-Path $thumbnailRoot 'index.tsv')) {
        $fields = $line.Split("`t")
        if ($fields.Count -ne 3 -or $fields[1] -cnotmatch '^[a-f0-9]{24}\.png$') { throw 'Invalid thumbnail index entry.' }
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $thumbnailRoot $fields[1])).Hash -ine $fields[2]) {
            throw "Packaged thumbnail hash mismatch: $($fields[0])"
        }
        $thumbnailBytes = [IO.File]::ReadAllBytes((Join-Path $thumbnailRoot $fields[1]))
        $thumbnailWidth = $thumbnailBytes[16]*16777216 + $thumbnailBytes[17]*65536 + $thumbnailBytes[18]*256 + $thumbnailBytes[19]
        $thumbnailHeight = $thumbnailBytes[20]*16777216 + $thumbnailBytes[21]*65536 + $thumbnailBytes[22]*256 + $thumbnailBytes[23]
        if ($thumbnailWidth -ne 92 -or $thumbnailHeight -ne 149) {
            throw "Oversized client thumbnail: $($fields[0])"
        }
    }
    $materialRoot = Join-Path $PSScriptRoot 'src/main/resources/appearance-color-sources'
    $materials = Get-Content -Raw -LiteralPath (Join-Path $materialRoot 'index.json') | ConvertFrom-Json
    if ($materials.version -ne 1 -or $materials.sourceCount -ne 685 -or @($materials.entries.PSObject.Properties).Count -ne 590) {
        throw 'Incomplete private color material catalog.'
    }
    $bakerSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'tools/bake_appearance_color_sources.py')).Replace("`r`n", "`n")
    $bakerHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($bakerSource)))
    if ($bakerHash -ine $materials.bakerSha256 -or $rendererHash -ine $materials.geometrySha256) {
        throw 'Rebake private color materials: renderer provenance mismatch.'
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $materialRoot 'palettes.json')).Hash -ine $materials.paletteSha256) {
        throw 'Native palette hash mismatch.'
    }
    $materialCount = 0
    foreach ($cosmetic in $materials.entries.PSObject.Properties) {
        if ($cosmetic.Value.rigHash -cne $categoryHashes[$cosmetic.Name.Split(':')[0]]) { throw 'Color card category rig drift.' }
        foreach ($row in $cosmetic.Value.sources.PSObject.Properties) {
            $materialCount++
            foreach ($kind in @('base','mask')) {
                $file = $row.Value.$kind
                if ($file -cnotmatch '^[a-f0-9]{24}(-mask)?\.png$') { throw 'Unsafe material path.' }
                if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $materialRoot $file)).Hash -ine $row.Value.($kind+'Sha256')) {
                    throw "Private material hash mismatch: $file"
                }
            }
        }
    }
    if ($materialCount -ne 685) { throw 'Private material source count mismatch.' }
    foreach ($source in $materials.sourceHashes.PSObject.Properties) {
        $entry = $archive.GetEntry($source.Name)
        if ($null -eq $entry) { throw "Private card source missing: $($source.Name)" }
        $stream = $entry.Open()
        try { $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
        finally { $stream.Dispose() }
        if ($actual -ine $source.Value) { throw "Rebake private color materials against changed installed source: $($source.Name)" }
    }
    Write-Host "Release resources validated: $revision; neutral skin, reference cards and 685 color materials match $assetsPath"
} finally { $archive.Dispose() }
