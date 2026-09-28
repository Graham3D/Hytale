param(
    [Parameter(Mandatory=$true)][string]$AssetsZip,
    [Parameter(Mandatory=$true)][string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath($ProjectRoot)
$assets = [System.IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($AssetsZip))
$failures = [Collections.Generic.List[string]]::new()
$findings = [Collections.Generic.List[string]]::new()
$findings.Add('NOT_ENABLED_IN_THIS_BUILD | stock ShopAsset GiveItemInteraction | 0 matching JSON assets')
$merchant = @(
    'Server/NPC/Roles/Intelligent/Faction/Kweebec/Kweebec_Merchant.json',
    'Server/NPC/Roles/Intelligent/Other/Klops/Klops_Merchant.json',
    'Server/NPC/Roles/Intelligent/Other/Klops/Klops_Merchant_Wandering.json'
)
$testObjectives = @(
    'Objective_Craft', 'Objective_Gather', 'Objective_Kill',
    'Objective_KillSpawnBeacon', 'Objective_KillSpawnMarker',
    'Objective_ReachLocation', 'Objective_UseBlock', 'Objective_UseEntity'
)
$unsafeType = '"Type"\s*:\s*"(GiveItem|GiveItemInteraction|GiveItems|OpenBarterShop|OpenShop)"'
try {
    foreach ($entry in $assets.Entries) {
        if (-not $entry.FullName.EndsWith('.json', [StringComparison]::OrdinalIgnoreCase)) { continue }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $matches = [regex]::Matches($body, $unsafeType)
        if ($body.Contains('GiveItemInteraction') -and $matches.Count -eq 0) {
            $failures.Add("Unclassified stock GiveItemInteraction asset: $($entry.FullName)")
        }
        foreach ($match in $matches) {
            $kind = $match.Groups[1].Value
            $path = $entry.FullName
            if ($kind -eq 'OpenBarterShop' -and $merchant -contains $path) {
                $override = Join-Path $root ('src/main/resources/' + $path)
                if (-not [IO.File]::Exists($override)) {
                    $failures.Add("Missing merchant override: $path")
                } else {
                    $replacement = [IO.File]::ReadAllText($override)
                    if ($replacement -match $unsafeType -or $replacement -notmatch '"Type"\s*:\s*"HywindOpenBarterShop"') {
                        $failures.Add("Merchant override still exposes native shop: $path")
                    } else {
                        $findings.Add("DISABLED_IN_SPATIAL_MODE | stock barter role | $path")
                    }
                }
            } elseif ($kind -eq 'GiveItems' -and $path -match '^Server/Objective/Objectives/(Objective_[^/]+)\.json$' -and $testObjectives -contains $Matches[1]) {
                $findings.Add("NOT_ENABLED_IN_THIS_BUILD | developer objective template | $path")
            } elseif ($kind -eq 'GiveItem' -and $path -eq 'Server/Prefabs/Testing/VolumeShowcase/Trigger_Volume_Showcase.prefab.json') {
                $override = Join-Path $root ('build/resources/main/' + $path)
                if (-not [IO.File]::Exists($override)) { $failures.Add("Missing generated trigger override: $path") }
                elseif ([IO.File]::ReadAllText($override) -match '"Type"\s*:\s*"GiveItem"' -or
                        [IO.File]::ReadAllText($override) -notmatch '"Type"\s*:\s*"HywindSpatialGrant"') {
                    $failures.Add("Trigger override still grants natively: $path")
                } else { $findings.Add("DISABLED_IN_SPATIAL_MODE | overridden testing trigger | $path") }
            } else {
                $failures.Add("Unsafe stock asset grant: $kind | $path")
            }
        }
        # A new non-test asset referencing developer objectives makes them reachable.
        if ($entry.FullName -notmatch '^Server/(Objective/(Objectives|ObjectiveLines|ObjectiveLocationMarkers)/|Prefabs/Testing/|Item/Items/MISC/Test_|NPC/Roles/_Core/Tests/)') {
            foreach ($id in $testObjectives) {
                if ($body.Contains('"' + $id + '"')) { $failures.Add("Developer objective referenced by enabled asset: $id | $($entry.FullName)") }
            }
            if ($body.Contains('Trigger_Volume_Showcase')) { $failures.Add("Testing grant prefab referenced by enabled asset: $($entry.FullName)") }
        }
    }
} finally { $assets.Dispose() }

foreach ($resources in @('src/main/resources/Server', 'canvas-ui/src/main/resources/Server', 'hytale-taverns/src/main/resources/Server')) {
    $projectAssets = Join-Path $root $resources
    if ([IO.Directory]::Exists($projectAssets)) {
        foreach ($file in [IO.Directory]::EnumerateFiles($projectAssets, '*.json', [IO.SearchOption]::AllDirectories)) {
            $body = [IO.File]::ReadAllText($file)
            if ($body -match $unsafeType -or $body.Contains('GiveItemInteraction')) {
                $failures.Add("Unsafe Hywind asset grant: $file")
            }
        }
    }
}

$qaWorlds = Join-Path ([Environment]::GetFolderPath('ApplicationData')) 'Hytale/data/pre-release/Saves/RPG-Inventory-QA-20260927/universe/worlds'
if ([IO.Directory]::Exists($qaWorlds)) {
    foreach ($file in [IO.Directory]::EnumerateFiles($qaWorlds, 'TriggerVolumeData.json', [IO.SearchOption]::AllDirectories)) {
        if ([IO.File]::ReadAllText($file) -match $unsafeType) { $failures.Add("Unsafe copied-save trigger grant: $file") }
    }
}

$sources = @(
    'src/main/java/com/inigmasgames/hytalerpg/commands/RpgGearCommand.java',
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleAmmoAdapter.java',
    'src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearLoot.java',
    'src/main/java/com/inigmasgames/hytalerpg/ui/inventory/SpatialInventoryTransferCoordinator.java',
    'hytale-taverns/src/main/java/com/inigmasgames/taverns/CoreModeManager.java',
    'hytale-taverns/src/main/java/com/inigmasgames/taverns/TableServingManager.java'
)
$requiredGuards = @{
    'src/main/java/com/inigmasgames/hytalerpg/commands/RpgGearCommand.java' = 'SpatialInventoryTransferCoordinator.grantGeneratedQaItem'
    'src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleAmmoAdapter.java' = 'spatialOwner'
    'src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearLoot.java' = 'SpatialBagComponent.OwnershipMode.NATIVE'
    'src/main/java/com/inigmasgames/hytalerpg/ui/inventory/SpatialInventoryTransferCoordinator.java' = 'OwnershipMode.NATIVE'
    'hytale-taverns/src/main/java/com/inigmasgames/taverns/CoreModeManager.java' = 'SpatialPlayerItemPolicy.nativeMode'
    'hytale-taverns/src/main/java/com/inigmasgames/taverns/TableServingManager.java' = 'SpatialPlayerItemPolicy.nativeMode'
}
$calls = '\.(addItemStack|giveItem|addOrDropItemStacks)\s*\('
foreach ($folder in @('src/main/java','canvas-ui/src/main/java','hytale-taverns/src/main/java')) {
    $dir = Join-Path $root $folder
    foreach ($file in [IO.Directory]::EnumerateFiles($dir, '*.java', [IO.SearchOption]::AllDirectories)) {
        if ([IO.File]::ReadAllText($file) -match $calls) {
            $relative = $file.Substring($root.Length + 1).Replace('\','/')
            if ($sources -notcontains $relative) { $failures.Add("Unreviewed project native grant call: $relative") }
        }
    }
}
foreach ($relative in $sources) {
    $source = [IO.File]::ReadAllText((Join-Path $root $relative))
    if (-not $source.Contains($requiredGuards[$relative])) {
        $failures.Add("Reviewed producer lost spatial owner guard: $relative")
    } else {
        $findings.Add("DISABLED_IN_SPATIAL_MODE or MANAGED_ADAPTER | reviewed Java producer | $relative")
    }
}
if (-not ([IO.File]::ReadAllText((Join-Path $root 'src/main/java/com/inigmasgames/hytalerpg/commands/RpgGearCommand.java')).Contains('OwnershipMode.NATIVE'))) {
    $failures.Add('Gear QA native grant route lost its spatial ownership branch')
}

$findings | Sort-Object -Unique | ForEach-Object { Write-Output $_ }
if ($failures.Count -gt 0) {
    $failures | Sort-Object -Unique | ForEach-Object { Write-Output "FAIL | $_" }
    throw "Spatial grant asset audit failed: $($failures.Count) unsafe or unreviewed routes"
}
Write-Output 'SPATIAL_GRANT_ASSET_AUDIT PASS: no enabled unsafe direct grant found in scanned assets or copied-save trigger data.'
