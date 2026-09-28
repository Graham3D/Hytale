[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JarPath,
    [Parameter(Mandatory=$true)][string]$ExpectedVersion,
    [Parameter(Mandatory=$true)][string]$ExpectedRevision
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
        finally { $algorithm.Dispose() }
    } finally { $stream.Dispose() }
}
$resolved = (Resolve-Path -LiteralPath $JarPath).Path
$zip = [IO.Compression.ZipFile]::OpenRead($resolved)
try {
    $names = @($zip.Entries | ForEach-Object FullName)
    $manifestEntry = $zip.GetEntry('manifest.json')
    if ($null -eq $manifestEntry) { throw 'manifest.json is missing.' }
    $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }

    if ($manifest.Group -ne 'InigmasGames' -or $manifest.Name -ne 'HyARPG' -or
        $manifest.Version -ne $ExpectedVersion -or $manifest.Main -ne 'com.inigmasgames.hywind.HyArpgPlugin' -or
        $manifest.Metadata.RpgRevision -ne $ExpectedRevision -or $manifest.Metadata.Product -ne 'HyARPG' -or
        $manifest.Metadata.SeparationCheckpoint -ne 'A') {
        throw 'HyARPG manifest identity/version/bootstrap/revision mismatch.'
    }
    if ($manifest.Dependencies.PSObject.Properties.Name -contains 'InigmasGames:ImmersiveNPCs') {
        throw 'Manifest contains a hard ImmersiveNPCs dependency.'
    }

    $required = @(
        'com/inigmasgames/hywind/HyArpgPlugin.class',
        'com/inigmasgames/hywind/readypath/ReadyPathProbe.class',
        'com/inigmasgames/hytalerpg/input/NativeAbilityProjectionService.class',
        'com/inigmasgames/hytalerpg/ui/hud/RpgHud.class',
        'com/inigmasgames/hytalerpg/combat/RpgCombatKernel.class',
        'com/inigmasgames/canvasui/CanvasUI.class',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'rpg/gear/native-bindings-v1.json',
        'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/RpgSkillTree.ui',
        'rpg-build.properties'
    )
    foreach ($entry in $required) {
        if ($null -eq $zip.GetEntry($entry)) { throw "Missing required HyARPG entry: $entry" }
    }

    $forbiddenPatterns = @(
        '^com/inigmasgames/persistentnpcs/',
        '^com/inigmasgames/taverns/',
        '^Server/NPC/Roles/(ImmersiveNPCs|PersistentNPCs)/',
        '^Server/Item/Items/.*/Taverns?/',
        '^Server/Item/Items/Taverns?/',
        '^Common/UI/Custom/Pages/ImmersiveNpc',
        '(^|/)immersive_voice_worker\.py$',
        '(^|/)(ai-providers|llm-providers|orbis-resources)\.json$',
        '(?i)\.(onnx|safetensors|ckpt|pt|pth)$'
    )
    $forbidden = @($names | Where-Object {
        $name = $_
        @($forbiddenPatterns | Where-Object { $name -match $_ }).Count -gt 0
    })
    if ($forbidden.Count) { throw "ImmersiveNPCs/AI payload leaked into HyARPG: $($forbidden -join ', ')" }

    $ability4Assets = 0
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like 'Server/Item/Items/RPG/*.json' -or $_.FullName -like 'Server/Item/Items/RPG/*/*.json' }) {
        $entryReader = [IO.StreamReader]::new($entry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { if ($entryReader.ReadToEnd() -match '"Ability4"\s*:') { $ability4Assets++ } }
        finally { $entryReader.Dispose() }
    }
    if ($ability4Assets -lt 1) { throw 'No packaged RPG asset retains native Ability4 integration.' }

    $sha = Get-Sha256 $resolved
    [ordered]@{
        result = 'PASS'
        jar = $resolved
        sha256 = $sha
        bytes = (Get-Item -LiteralPath $resolved).Length
        entries = $zip.Entries.Count
        classes = @($names | Where-Object { $_ -like '*.class' }).Count
        ability4Assets = $ability4Assets
        manifest = "$($manifest.Group):$($manifest.Name)@$($manifest.Version)"
        revision = $manifest.Metadata.RpgRevision
        immersivePayloadEntries = 0
    } | ConvertTo-Json -Depth 4
} finally {
    $zip.Dispose()
}
