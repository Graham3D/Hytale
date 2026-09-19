[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JarPath,
    [Parameter(Mandatory=$true)][string]$ExpectedVersion,
    [Parameter(Mandatory=$true)][string]$ExpectedRevision
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sha256 = {
    param([string]$Path)
    $stream = [IO.File]::OpenRead($Path)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-', '') }
    finally { $hash.Dispose(); $stream.Dispose() }
}
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$zip = [IO.Compression.ZipFile]::OpenRead($resolvedJar)
try {
    $names = @($zip.Entries | ForEach-Object FullName)
    $duplicates = @($names | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count) { throw "Duplicate JAR entries: $($duplicates.Name -join ', ')" }
    if (@($names | Where-Object { $_ -eq 'manifest.json' }).Count -ne 1) { throw 'Hywind must contain exactly one root manifest.json.' }

    $manifestEntry = $zip.GetEntry('manifest.json')
    $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'Hywind' -or
        $manifest.Version -ne $ExpectedVersion -or
        $manifest.Main -ne 'com.inigmasgames.hywind.HywindPlugin' -or
        $manifest.Metadata.RpgRevision -ne $ExpectedRevision) {
        throw 'Hywind manifest identity/version/bootstrap/revision mismatch.'
    }
    if ($manifest.ServerVersion -ne '=0.7.0-pre.3.1') { throw "Unexpected server pin: $($manifest.ServerVersion)" }
    if ($manifest.Dependencies.'Hytale:NPC' -ne '*') { throw 'Required Hytale:NPC dependency is absent.' }

    $required = @(
        'com/inigmasgames/hywind/HywindPlugin.class',
        'com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.class',
        'com/inigmasgames/canvasui/CanvasUI.class',
        'Server/Languages/en-US/server.lang',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'rpg/presentation/icon-index.json',
        'hywind-build.properties'
    )
    foreach ($requiredEntry in $required) {
        if ($null -eq $zip.GetEntry($requiredEntry)) { throw "Missing required merged entry: $requiredEntry" }
    }
    $forbidden = @(
        'com/inigmasgames/hytalerpg/phase00/Phase00Plugin.class',
        'com/inigmasgames/canvasui/CanvasUIPlugin.class'
    )
    foreach ($forbiddenEntry in $forbidden) {
        if ($null -ne $zip.GetEntry($forbiddenEntry)) { throw "Legacy lifecycle owner was packaged: $forbiddenEntry" }
    }

    foreach ($page in @('NativeInventoryProbe','ProfileInventory')) {
        $prefix = "Common/UI/Custom/Pages/$page/NpcSection"
        $sections = @($names | Where-Object { $_ -match ('^' + [regex]::Escape($prefix) + '(\d+)\.ui$') })
        if ($sections.Count -ne 1024) { throw "$page must contain checked-in sections 1..8 and generated sections 9..1024; found $($sections.Count)." }
        foreach ($id in 1..1024) {
            if ($null -eq $zip.GetEntry("$prefix$id.ui")) { throw "$page is missing NpcSection$id.ui." }
        }
    }

    $embeddedBinaries = @($names | Where-Object {
        $_ -match '(?i)(^|/)(HytaleServer\.jar|Assets\.zip)$' -or
        $_ -match '(?i)\.(onnx|safetensors|ckpt|pt|pth)$'
    })
    if ($embeddedBinaries.Count) { throw "Forbidden embedded runtime/model payload: $($embeddedBinaries -join ', ')" }

    $badUi = [Collections.Generic.List[string]]::new()
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like '*.ui' }) {
        $uiReader = [IO.StreamReader]::new($entry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { $body = $uiReader.ReadToEnd() } finally { $uiReader.Dispose() }
        if ($body -match 'CursedIcon(Patch|Anchor)') { $badUi.Add($entry.FullName) }
    }
    if ($badUi.Count) { throw "Pre-3 incompatible ItemGrid fields remain: $($badUi -join ', ')" }

    $languageEntry = $zip.GetEntry('Server/Languages/en-US/server.lang')
    $languageReader = [IO.StreamReader]::new($languageEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $language = $languageReader.ReadToEnd() } finally { $languageReader.Dispose() }
    foreach ($needle in @('items.RPG_Ability_', 'server.npcRoles.')) {
        if ($language -notmatch [regex]::Escape($needle)) { throw "Merged language file lacks $needle keys." }
    }

    $result = [ordered]@{
        result = 'PASS'
        jar = $resolvedJar
        sha256 = (& $sha256 $resolvedJar)
        bytes = (Get-Item -LiteralPath $resolvedJar).Length
        entries = $zip.Entries.Count
        classes = @($names | Where-Object { $_ -like '*.class' }).Count
        uiDocuments = @($names | Where-Object { $_ -like '*.ui' }).Count
        generatedNpcSections = 2032
        totalNpcSections = 2048
        manifest = "$($manifest.Group):$($manifest.Name)@$($manifest.Version)"
        revision = $manifest.Metadata.RpgRevision
    }
    $result | ConvertTo-Json -Depth 4
} finally {
    $zip.Dispose()
}
