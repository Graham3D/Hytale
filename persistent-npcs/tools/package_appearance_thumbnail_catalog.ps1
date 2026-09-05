param(
    [string]$SourceRoot = (Join-Path $PSScriptRoot 'retired-appearance-r154\Thumbnails'),
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\src\main\resources')
)

$ErrorActionPreference = 'Stop'
$sourceRootPath = [IO.Path]::GetFullPath($SourceRoot)
$resourceRootPath = [IO.Path]::GetFullPath($ResourceRoot)
$catalogRoot = [IO.Path]::GetFullPath((Join-Path $resourceRootPath 'Common\UI\Custom\Pages\ImmersiveNpcAppearance\Catalog'))
$thumbnailRoot = Join-Path $catalogRoot 'Thumbnails'
$indexSource = Join-Path $sourceRootPath 'index.tsv'
$indexTarget = Join-Path $catalogRoot 'index.tsv'

if (-not $catalogRoot.StartsWith($resourceRootPath + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe catalog output path: $catalogRoot"
}
if (-not (Test-Path -LiteralPath $indexSource)) {
    throw "Missing offline thumbnail index: $indexSource"
}

if (Test-Path -LiteralPath $catalogRoot) {
    Remove-Item -LiteralPath $catalogRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $thumbnailRoot | Out-Null

$probePaths = @{
    'UNDERTOP:FarmerTop' = 'ImmersiveNpcAppearance/Probe/UNDERTOP-FarmerTop.png'
    'UNDERTOP:FlowerShirt' = 'ImmersiveNpcAppearance/Probe/UNDERTOP-FlowerShirt.png'
}
$records = [Collections.Generic.List[string]]::new()
$keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)

foreach ($line in Get-Content -LiteralPath $indexSource) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line -split "`t"
    if ($parts.Count -ne 3) { throw "Malformed thumbnail index row: $line" }
    $key, $sourceFile, $hash = $parts
    if (-not $keys.Add($key)) { throw "Duplicate thumbnail key: $key" }
    if ($hash -notmatch '^[0-9a-f]{64}$') { throw "Malformed thumbnail hash: $key" }
    $source = Join-Path $sourceRootPath $sourceFile
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing thumbnail source: $source" }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash.ToLowerInvariant()
    if ($actualHash -cne $hash) { throw "Thumbnail source hash mismatch: $key" }

    if ($probePaths.ContainsKey($key)) {
        $uiPath = $probePaths[$key]
        $packagedPath = 'Common/UI/Custom/Pages/' + $uiPath
    } else {
        Copy-Item -LiteralPath $source -Destination (Join-Path $thumbnailRoot $sourceFile)
        $uiPath = "ImmersiveNpcAppearance/Catalog/Thumbnails/$sourceFile"
        $packagedPath = "Common/UI/Custom/Pages/$uiPath"
    }
    $category, $cosmeticId = $key -split ':', 2
    $records.Add("$category`t$cosmeticId`t$uiPath`t$packagedPath`t$hash")
}

if ($records.Count -ne 590) { throw "Expected 590 canonical thumbnails; found $($records.Count)" }
[IO.File]::WriteAllLines($indexTarget, $records, [Text.UTF8Encoding]::new($false))
Write-Host "Packaged 590 immutable canonical thumbnail references (588 catalog + 2 retained R159 probe assets)."
