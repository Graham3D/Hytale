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
    }
    Write-Host "Release resources validated: $revision; neutral skin and hashed cosmetic thumbnails match $assetsPath"
} finally { $archive.Dispose() }
