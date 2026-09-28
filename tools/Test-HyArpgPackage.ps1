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
        $manifest.Metadata.SeparationCheckpoint -ne 'C') {
        throw 'HyARPG manifest identity/version/bootstrap/revision mismatch.'
    }
    if ($manifest.Dependencies.PSObject.Properties.Name -contains 'InigmasGames:ImmersiveNPCs') {
        throw 'Manifest contains a hard ImmersiveNPCs dependency.'
    }
    if (-not ($manifest.OptionalDependencies.PSObject.Properties.Name -contains 'InigmasGames:ImmersiveNPCs')) {
        throw 'Manifest is missing the optional ImmersiveNPCs bridge declaration.'
    }

    $required = @(
        'com/inigmasgames/hywind/HyArpgPlugin.class',
        'com/inigmasgames/hywind/readypath/ReadyPathProbe.class',
        'com/inigmasgames/hytalerpg/input/NativeAbilityProjectionService.class',
        'com/inigmasgames/hytalerpg/ui/hud/RpgHud.class',
        'com/inigmasgames/hytalerpg/combat/RpgCombatKernel.class',
        'com/inigmasgames/canvasui/CanvasUI.class',
        'com/inigmasgames/taverns/TavernsPlugin.class',
        'com/inigmasgames/taverns/CoreModeManager.class',
        'com/inigmasgames/taverns/TavernPatronManager.class',
        'com/inigmasgames/taverns/api/SpatialPlayerItemPolicy.class',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'rpg/gear/native-bindings-v1.json',
        'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/RpgSkillTree.ui',
        'Common/UI/Custom/RpgInventory/GridCommon.ui',
        'Common/UI/Custom/RpgInventory/Slot@2x.png',
        'Common/UI/Custom/RpgInventory/QuantityPopupSlotOverlay@2x.png',
        'Common/UI/Custom/RpgInventory/SlotItemBrokenCracksOverlay@2x.png',
        'Common/UI/Custom/RpgInventory/SlotItemBrokenIconOverlay@2x.png',
        'Common/UI/Custom/RpgInventory/UnknownItemIcon@2x.png',
        'Common/UI/Custom/RpgInventory/DurabilityBar@2x.png',
        'Common/UI/Custom/RpgInventory/DurabilityBarBackground@2x.png',
        'Common/UI/Custom/RpgInventory/CursedSpiral.png',
        'Common/UI/Custom/Hud/TavernsRevision.ui',
        'comfort_registry.json',
        'prepared_foods.json',
        'Server/Item/Items/Core/Core_Tavern.json',
        'rpg-build.properties'
    )
    foreach ($entry in $required) {
        if ($null -eq $zip.GetEntry($entry)) { throw "Missing required HyARPG entry: $entry" }
    }

    $forbiddenPatterns = @(
        '^com/inigmasgames/persistentnpcs/',
        '^com/inigmasgames/compat/immersivenpcs/',
        '^Server/NPC/Roles/(ImmersiveNPCs|PersistentNPCs)/',
        '^Common/UI/Custom/Pages/ImmersiveNpc',
        '^Common/UI/Custom/Pages/ProfileInventory/',
        '(^|/)immersive_voice_worker\.py$',
        '(^|/)(ai-providers|llm-providers|orbis-resources)\.json$',
        '(?i)\.(onnx|safetensors|ckpt|pt|pth)$'
    )
    $forbidden = @($names | Where-Object {
        $name = $_
        @($forbiddenPatterns | Where-Object { $name -match $_ }).Count -gt 0
    })
    if ($forbidden.Count) { throw "ImmersiveNPCs/AI payload leaked into HyARPG: $($forbidden -join ', ')" }

    $tavernHudEntry = $zip.GetEntry('Common/UI/Custom/Hud/TavernsRevision.ui')
    $tavernHudReader = [IO.StreamReader]::new($tavernHudEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $tavernHud = $tavernHudReader.ReadToEnd() } finally { $tavernHudReader.Dispose() }
    if ($tavernHud -match '#RevisionLabel' -or $tavernHud -match 'TAVERNS\s+R\d+') {
        throw 'Tavern composite HUD still renders a second revision badge.'
    }

    foreach ($section in @(
            'Common/UI/Custom/InventoryNativeAlias/Section1.ui',
            'Common/UI/Custom/InventoryNativeWorkspace/Section702.ui')) {
        $sectionEntry = $zip.GetEntry($section)
        if ($null -eq $sectionEntry) { throw "Missing generated native inventory document: $section" }
        $sectionReader = [IO.StreamReader]::new($sectionEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { $sectionText = $sectionReader.ReadToEnd() } finally { $sectionReader.Dispose() }
        if ($sectionText -notmatch '\.\./RpgInventory/GridCommon\.ui' -or
                $sectionText -match 'ProfileInventory') {
            throw "Native inventory document has an orphan or ImmersiveNPC-owned base import: $section"
        }
    }
    foreach ($uiEntry in $zip.Entries | Where-Object { $_.FullName -like '*.ui' }) {
        $uiReader = [IO.StreamReader]::new($uiEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { $uiText = $uiReader.ReadToEnd() } finally { $uiReader.Dispose() }
        if ($uiText -match 'ProfileInventory') {
            throw "Packaged HyARPG UI still references the ImmersiveNPC ProfileInventory resource: $($uiEntry.FullName)"
        }
    }

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
        tavernClasses = @($names | Where-Object { $_ -like 'com/inigmasgames/taverns/*.class' -or $_ -like 'com/inigmasgames/taverns/*/*.class' }).Count
        ability4Assets = $ability4Assets
        manifest = "$($manifest.Group):$($manifest.Name)@$($manifest.Version)"
        revision = $manifest.Metadata.RpgRevision
        immersivePayloadEntries = 0
    } | ConvertTo-Json -Depth 4
} finally {
    $zip.Dispose()
}
