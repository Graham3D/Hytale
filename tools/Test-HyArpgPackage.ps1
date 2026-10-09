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
        'com/inigmasgames/hytalerpg/ui/inventory/AdvancedStatsViewModel.class',
        'com/inigmasgames/hytalerpg/ui/inventory/RpgRingEquipment.class',
        'com/inigmasgames/hytalerpg/ui/inventory/WeaponArtCatalog.class',
        'com/inigmasgames/hytalerpg/ui/trace/UiInteractionTrace.class',
        'com/inigmasgames/hytalerpg/gear/GearQaTrace.class',
        'com/inigmasgames/hytalerpg/gear/GearAffixQaSuite.class',
        'com/inigmasgames/hytalerpg/commands/RpgGearTraceCommand.class',
        'com/inigmasgames/hytalerpg/commands/RpgUiTraceCommand.class',
        'com/inigmasgames/canvasui/api/CanvasInteractionObserver.class',
        'com/inigmasgames/canvasui/CanvasUI.class',
        'com/inigmasgames/taverns/TavernsPlugin.class',
        'com/inigmasgames/taverns/CoreModeManager.class',
        'com/inigmasgames/taverns/TavernPatronManager.class',
        'com/inigmasgames/taverns/api/SpatialPlayerItemPolicy.class',
        'rpg/catalog/skills.json',
        'rpg/catalog/passives.json',
        'rpg/gear/native-bindings-v1.json',
        'rpg/gear/affix-qa-fixtures-v1.json',
        'rpg/gear/affix-qa-coverage-v1.json',
        'rpg/gear/tooltip-families-v1.json',
        'rpg/inventory/weapon-art-v1.json',
        'Common/UI/Custom/RpgHud.ui',
        'Common/UI/Custom/RpgSkillTree.ui',
        'Common/UI/Custom/RpgAdvancedStatRow.ui',
        'Common/UI/Custom/RpgAdvancedStatSection.ui',
        'Common/UI/Custom/RpgInventoryProbe.ui',
        'Common/UI/Custom/InventoryDropTargets/OutsideSection1.ui',
        'Common/UI/Custom/InventoryDropTargets/OutsideSection702.ui',
        'Common/UI/Custom/InventoryDropTargets/TransparentSlot.png',
        'Common/UI/Custom/Icons/Hytale/SlotDefault@2x.png',
        'Common/UI/Custom/Icons/Hytale/ContainerHeader@2x.png',
        'Common/UI/Custom/Icons/Hytale/ContainerHeaderNoRunes@2x.png',
        'Common/UI/Custom/Icons/Hytale/ContainerDecorationTop@2x.png',
        'Common/UI/Custom/Icons/Hytale/NavigationItemSelected@2x.png',
        'Common/UI/Custom/Icons/Hytale/NavigationItemHovered@2x.png',
        'Common/UI/Custom/Icons/Hytale/TopBarBackground@2x.png',
        'Common/UI/Custom/Icons/Hytale/ItemTooltipDefault@2x.png',
        'Common/UI/Custom/Icons/Hytale/CharacterBackground@2x.png',
        'Common/UI/Custom/Icons/Hytale/RingSlotIconLeft@2x.png',
        'Common/UI/Custom/Icons/Hytale/RingSlotIconRight@2x.png',
        'Common/UI/Custom/Icons/Hytale/AutoSortIcon@2x.png',
        'Common/UI/Custom/Icons/Hytale/ArmorVisibilityOn@2x.png',
        'Common/UI/Custom/Icons/Hytale/ArmorVisibilityOff@2x.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-1x1.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-1x2.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-1x3.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-1x4.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-2x2.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-2x3.png',
        'Common/UI/Custom/Icons/RPG/GridRarity-2x4.png',
        'Common/UI/Custom/Icons/RPG/ResistEarth.png',
        'Common/UI/Custom/Icons/RPG/ResistFire.png',
        'Common/UI/Custom/Icons/RPG/ResistLightning.png',
        'Common/UI/Custom/Icons/RPG/ResistVoid.png',
        'Common/UI/Custom/Icons/RPG/ResistWater.png',
        'Common/UI/Custom/Icons/RPG/ResistWind.png',
        'Common/UI/Custom/Icons/RPG/RingSlotIconLeft@2x.png',
        'Common/UI/Custom/Icons/RPG/RingSlotIconRight@2x.png',
        'Common/Icons/Items/RPG/RingCopper.png',
        'Server/Item/Items/RPG/Rings/RPG_Ring_Copper.json',
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
    foreach ($band in @('Common', 'Magic', 'Rare', 'Legendary', 'Uncommon', 'Epic')) {
        $required += "Common/UI/ItemQualities/Tooltips/Hywind/ItemTooltip${band}@2x.png"
        $required += "Common/UI/ItemQualities/Tooltips/Hywind/ItemTooltip${band}Arrow@2x.png"
    }
    foreach ($entry in $required) {
        if ($null -eq $zip.GetEntry($entry)) { throw "Missing required HyARPG entry: $entry" }
    }

    $coverageReader = [IO.StreamReader]::new($zip.GetEntry('rpg/gear/affix-qa-coverage-v1.json').Open())
    try { $coverage = $coverageReader.ReadToEnd() | ConvertFrom-Json } finally { $coverageReader.Dispose() }
    $coverageIds = @($coverage | ForEach-Object affixId)
    if ($coverage.Count -ne 160 -or @($coverageIds | Select-Object -Unique).Count -ne 160 -or
        @($coverage | Where-Object result -eq 'FUNCTIONAL').Count -ne 159 -or
        @($coverage | Where-Object { $_.result -eq 'GATED' -and $_.affixId -eq 'WA-155' -and -not $_.productionEnabled }).Count -ne 1 -or
        @($coverage | Where-Object { $_.result -eq 'FUNCTIONAL' -and (@($_.proofHoles).Count -ne 0 -or -not $_.productionEnabled) }).Count -ne 0) {
        throw 'Packaged affix QA coverage is incomplete or duplicated.'
    }

    $weaponManifestEntry = $zip.GetEntry('rpg/inventory/weapon-art-v1.json')
    $weaponReader = [IO.StreamReader]::new($weaponManifestEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $weaponArt = $weaponReader.ReadToEnd() | ConvertFrom-Json } finally { $weaponReader.Dispose() }
    $footprintEntry = $zip.GetEntry('rpg/inventory/footprints-v1.json')
    $footprintReader = [IO.StreamReader]::new($footprintEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
    try { $footprints = $footprintReader.ReadToEnd() | ConvertFrom-Json } finally { $footprintReader.Dispose() }
    if ($footprints.catalogRevision -ne 3) { throw 'Spatial footprint catalog revision mismatch.' }
    if ($weaponArt.schemaVersion -ne 2 -or @($weaponArt.bindings.PSObject.Properties).Count -ne 133) {
        throw 'Weapon art manifest version/count mismatch.'
    }
    $revisedNativeFootprints = 0
    foreach ($binding in $weaponArt.bindings.PSObject.Properties) {
        $itemId = $binding.Name
        $value = $binding.Value
        $path = 'Common/UI/Custom/' + $value.texture
        if ($value.texture -ne "Icons/RPG/WeaponArt/$itemId.png" -or
            $value.canvasWidth -lt 1 -or $value.canvasHeight -lt 1 -or
            @($value.alphaBounds).Count -ne 4) {
            throw "Invalid authored weapon art metadata: $itemId"
        }
        $size = $footprints.bindings.$itemId
        if ($null -eq $size) { throw "No spatial footprint for authored weapon: $itemId" }
        if ($null -ne $value.authoredFootprint) {
            if ($size.width -ne $value.authoredFootprint.width -or
                $size.height -ne $value.authoredFootprint.height) {
                throw "Artist spatial suffix does not match catalog: $itemId"
            }
            if ($null -ne $size.previousWidth) { $revisedNativeFootprints++ }
        }
        $imageEntry = $zip.GetEntry($path)
        if ($null -eq $imageEntry) { throw "Missing authored weapon art: $path" }
        $imageStream = $imageEntry.Open()
        try {
            $algorithm = [Security.Cryptography.SHA256]::Create()
            try { $imageHash = ([BitConverter]::ToString($algorithm.ComputeHash($imageStream))).Replace('-', '') }
            finally { $algorithm.Dispose() }
        } finally { $imageStream.Dispose() }
        if ($imageHash -ne $value.sha256) { throw "Authored weapon PNG changed in package: $itemId" }
    }
    if ($revisedNativeFootprints -ne 74) { throw "Unexpected native weapon footprint changes: $revisedNativeFootprints" }

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
            'Common/UI/Custom/InventoryNativeWorkspace/Section702.ui',
            'Common/UI/Custom/InventoryDropTargets/OutsideSection702.ui')) {
        $sectionEntry = $zip.GetEntry($section)
        if ($null -eq $sectionEntry) { throw "Missing generated native inventory document: $section" }
        $sectionReader = [IO.StreamReader]::new($sectionEntry.Open(), [Text.UTF8Encoding]::new($false), $true)
        try { $sectionText = $sectionReader.ReadToEnd() } finally { $sectionReader.Dispose() }
        if ($sectionText -notmatch '\.\./RpgInventory/GridCommon\.ui' -or
                $sectionText -match 'ProfileInventory') {
            throw "Native inventory document has an orphan or ImmersiveNPC-owned base import: $section"
        }
        if ($section -like '*InventoryDropTargets*' -and $sectionText -notmatch 'InventorySectionId: 702;') {
            throw "Outside drop target section mismatch: $section"
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
