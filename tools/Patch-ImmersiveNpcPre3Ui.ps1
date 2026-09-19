[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InputJar,
    [Parameter(Mandatory)][string]$OutputJar
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$source = (Resolve-Path -LiteralPath $InputJar).Path
$destination = [IO.Path]::GetFullPath($OutputJar)
$destinationDirectory = [IO.Path]::GetDirectoryName($destination)
New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
$temporary = "$destination.tmp"
if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }

$targets = @(
    'Common/UI/Custom/Pages/ProfileInventory/GridCommon.ui',
    'Common/UI/Custom/Pages/NativeInventoryProbe/GridCommon.ui',
    'Common/UI/Custom/Pages/ImmersiveNpcProfile.ui'
)
$patched = 0
$inputArchive = [IO.Compression.ZipFile]::OpenRead($source)
$outputArchive = [IO.Compression.ZipFile]::Open($temporary, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $inputArchive.Entries) {
        $replacement = $outputArchive.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
        $replacement.LastWriteTime = $entry.LastWriteTime
        if ($entry.FullName -in $targets) {
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $updated = $content.Replace('CursedIconPatch:', 'EphemeralIconPatch:').Replace('CursedIconAnchor:', 'EphemeralIconAnchor:')
            if ($updated -ne $content) { $patched++ }
            $writer = [IO.StreamWriter]::new($replacement.Open(), [Text.UTF8Encoding]::new($false))
            try { $writer.Write($updated) } finally { $writer.Dispose() }
        } else {
            $inputStream = $entry.Open()
            $outputStream = $replacement.Open()
            try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        }
    }
} finally {
    $outputArchive.Dispose()
    $inputArchive.Dispose()
}

if ($patched -lt 2) { throw "Expected at least two obsolete ItemGridStyle documents; patched $patched" }
Move-Item -LiteralPath $temporary -Destination $destination -Force
$archive = [IO.Compression.ZipFile]::OpenRead($destination)
try {
    foreach ($entry in $archive.Entries | Where-Object { $_.FullName -like '*.ui' }) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($content -match 'CursedIcon(?:Patch|Anchor):') {
            throw "Obsolete pre.2 ItemGridStyle field remains in $($entry.FullName)"
        }
    }
} finally {
    $archive.Dispose()
}

[pscustomobject]@{
    input = $source
    output = $destination
    patchedDocuments = $patched
    sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
}
