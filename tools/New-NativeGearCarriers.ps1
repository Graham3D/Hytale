param(
    [string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip",
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../src/main/resources'),
    [switch]$VerifyOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath (Join-Path $OutputRoot 'Server/Item/Interactions/RPG/Carriers/RPG_Carrier_Bomb_Throw.json')) {
    throw 'Native carrier stage-one generator is superseded by Complete-NativeGearCarriers.ps1; refusing to overwrite managed action graphs.'
}
Add-Type -AssemblyName System.IO.Compression

# Exact visual/action candidates from the installed public asset archive. A staged
# carrier is deliberately not a MAPPED binding: its native damage route still needs
# the actual snapshot consumer and a positive/control/negative native test.
$candidates = [ordered]@{
    shield_copper='Shield/Weapon_Shield_Copper'; shield_iron='Shield/Weapon_Shield_Iron'
    shield_thorium='Shield/Weapon_Shield_Thorium'; shield_cobalt='Shield/Weapon_Shield_Cobalt'
    shield_adamantite='Shield/Weapon_Shield_Adamantite'; shield_mithril='Shield/Weapon_Shield_Mithril'
    shield_wooden='Shield/Weapon_Shield_Wood'
    staff_flame='Staff/Weapon_Staff_Crystal_Red'; staff_ice='Staff/Weapon_Staff_Frost'
    staff_healing='Staff/Weapon_Staff_Wizard'; staff_apprentice='Staff/Weapon_Staff_Wood'
    staff_prismatic='Staff/Weapon_Staff_Crystal_Purple'; staff_mithril='Staff/Weapon_Staff_Mithril'
    staff_warden='Staff/Weapon_Staff_Iron'; staff_oracle='Staff/Weapon_Staff_Onyxium'
    wand_oak='Wand/Weapon_Wand_Wood'; wand_crystal='Wand/Weapon_Wand_Wood_Rotten'
    wand_bone='Wand/Weapon_Wand_Tribal'; wand_amber='Wand/Weapon_Wand_Wood'
    wand_mithril='Wand/Weapon_Wand_Tribal'
    book_apprentice='Spellbook/Weapon_Spellbook_Grimoire_Brown'
    book_binder='Spellbook/Weapon_Spellbook_Grimoire_Purple'
    book_prayer='Spellbook/Weapon_Spellbook_Grimoire_Brown'
    book_journeyman='Spellbook/Weapon_Spellbook_Fire'
    book_sage='Spellbook/Weapon_Spellbook_Frost'
    book_sanctum='Spellbook/Weapon_Spellbook_Demon'
    book_oracle='Spellbook/Weapon_Spellbook_Demon'
}
$bindingsPath = Join-Path $OutputRoot 'rpg/gear/native-bindings-v1.json'
$basesPath = Join-Path $OutputRoot 'rpg/gear/bases-v1.json'
$bindings = Get-Content -LiteralPath $bindingsPath -Raw | ConvertFrom-Json -AsHashtable
$bases = Get-Content -LiteralPath $basesPath -Raw | ConvertFrom-Json -AsHashtable
$baseById = @{}; foreach ($base in $bases.bases) { $baseById[$base.id] = $base }
$rarities = [ordered]@{ ''='RPG_Gear_Common'; '_Uncommon'='RPG_Gear_Uncommon'; '_Rare'='RPG_Gear_Rare'; '_Epic'='RPG_Gear_Epic'; '_Legendary'='RPG_Gear_Legendary' }
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $AssetsZip).Path)
try {
    $archivePaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $rootNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $zip.Entries) {
        [void]$archivePaths.Add($entry.FullName)
        if ($entry.FullName -like 'Server/Item/RootInteractions/*.json' -or
            $entry.FullName -like 'Server/Item/RootInteractions/*/*.json') {
            [void]$rootNames.Add([IO.Path]::GetFileNameWithoutExtension($entry.Name))
        }
    }
    function Read-Entry([string]$path) {
        $entry = $zip.GetEntry($path)
        if ($null -eq $entry) { throw "Missing installed native asset: $path" }
        $stream = $entry.Open(); $memory = [IO.MemoryStream]::new()
        try { $stream.CopyTo($memory); return $memory.ToArray() }
        finally { $memory.Dispose(); $stream.Dispose() }
    }
    function Sha256([byte[]]$bytes) {
        return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    }
    $template = [Text.Encoding]::UTF8.GetString((Read-Entry 'Server/Item/Items/Weapon/Shield/Template_Weapon_Shield.json')) | ConvertFrom-Json -AsHashtable
    $count = 0
    foreach ($binding in $bindings.bindings) {
        $baseId = [string]$binding.baseId
        if ($baseId -notmatch '^gm\.(shield|staff|wand|book)_(.+)\.(n|nm|h)$') { continue }
        $family = $baseId.Substring(3) -replace '\.(n|nm|h)$',''
        if (-not $candidates.Contains($family)) { throw "No reviewed candidate for $baseId" }
        if ($binding.disposition -eq 'MAPPED') { throw "Refusing to rewrite active binding $baseId" }
        $relative = 'Server/Item/Items/Weapon/' + $candidates[$family] + '.json'
        $sourceBytes = Read-Entry $relative
        $source = [Text.Encoding]::UTF8.GetString($sourceBytes) | ConvertFrom-Json -AsHashtable
        $nativeId = [IO.Path]::GetFileNameWithoutExtension($relative)
        $base = $baseById[$baseId]
        if ($null -eq $base -or $null -eq $base.durability) { throw "Missing authored base/durability: $baseId" }
        if ($source.Contains('Parent')) {
            if ($source.Parent -ne 'Template_Weapon_Shield') { throw "Unreviewed native Parent for $relative" }
            $merged = [ordered]@{}
            foreach ($key in $template.Keys) { $merged[$key] = $template[$key] }
            foreach ($key in $source.Keys) {
                if ($key -eq 'InteractionVars') {
                    $vars = [ordered]@{}
                    foreach ($name in $template.InteractionVars.Keys) { $vars[$name] = $template.InteractionVars[$name] }
                    foreach ($name in $source.InteractionVars.Keys) { $vars[$name] = $source.InteractionVars[$name] }
                    $merged.InteractionVars = $vars
                } elseif ($key -ne 'Parent') { $merged[$key] = $source[$key] }
            }
            $source = $merged
        }
        if ($null -eq $source.Model -or $null -eq $source.Texture -or $null -eq $source.Icon -or
            $null -eq $source.PlayerAnimationsId -or $null -eq $source.Interactions -or $null -eq $source.Tags) {
            throw "Incomplete native carrier source: $relative"
        }
        foreach ($path in @($source.Model,$source.Texture,$source.Icon)) {
            if (-not $archivePaths.Contains("Common/$path")) { throw "Missing stock visual $path for $relative" }
        }
        if (-not $archivePaths.Contains("Server/Item/Animations/$($source.PlayerAnimationsId).json")) {
            throw "Missing native animation $($source.PlayerAnimationsId) for $relative"
        }
        foreach ($action in $source.Interactions.Values) {
            if ($action -is [string] -and -not $rootNames.Contains($action)) {
                throw "Missing native root $action for $relative"
            }
        }
        if ($baseId -match '^gm\.(staff|wand|book)_') {
            $requiredRoot = switch ($Matches[1]) {
                'staff' { 'Staff_Primary' }; 'wand' { 'Wand_Primary' }; 'book' { 'Spellbook_Primary' }
            }
            if ($source.Interactions.Count -ne 2 -or $source.Interactions.Primary -ne $requiredRoot -or
                $source.Interactions.Secondary -ne $requiredRoot) {
                throw "Unreviewed focus action graph for $relative"
            }
        }
        # These are selected native graph and presentation fields only. Stock crafting,
        # fixed native Quality, intrinsic attributes and ammunition custody are excluded.
        $carrier = [ordered]@{
            TranslationProperties = @{ Name = 'server.gear.base.' + $baseId }
            Quality = 'RPG_Gear_Common'; MaxStack = 1; MaxDurability = $base.durability
            Model = $source.Model; Texture = $source.Texture; Icon = $source.Icon
            PlayerAnimationsId = $source.PlayerAnimationsId; Tags = $source.Tags
            Interactions = $source.Interactions
            Weapon = @{}
        }
        if ($source.Contains('InteractionVars')) { $carrier.InteractionVars = $source.InteractionVars }
        foreach ($field in @('Categories','Utility','Reticle','IconProperties','DroppedItemAnimation','ItemSoundSetId','DurabilityLossOnHit')) {
            if ($source.Contains($field)) { $carrier[$field] = $source[$field] }
        }
        $stem = 'RPG_Gear_' + ($baseId.Substring(3) -replace '\.','_')
        foreach ($suffix in $rarities.Keys) {
            $carrier.Quality = $rarities[$suffix]
            $target = Join-Path $OutputRoot ("Server/Item/Items/RPG/Gear/$stem$suffix.json")
            $json = ($carrier | ConvertTo-Json -Depth 100) + "`n"
            if ($VerifyOnly) {
                if (-not (Test-Path -LiteralPath $target) -or (Get-Content -LiteralPath $target -Raw) -ne $json) {
                    throw "Carrier differs from pinned source projection: $target"
                }
            } else {
                New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
                [IO.File]::WriteAllText($target,$json,[Text.UTF8Encoding]::new($false))
            }
            $count++
        }
        $sha = Sha256 $sourceBytes
        if ($VerifyOnly) {
            if ($binding.nativeItemId -ne $nativeId -or $binding.sourceAsset -ne $relative -or $binding.sourceSha256 -ne $sha -or
                $binding.disposition -eq 'MAPPED' -or $null -ne $binding.managedItemId) {
                throw "Staged provenance/binding changed: $baseId"
            }
        } else {
            $binding.nativeItemId = $nativeId
            $binding.sourceAsset = $relative
            $binding.sourceSha256 = $sha
            $binding.reason = 'Stock visual and action graph staged with exact source provenance. Native damage/guard/Smite path requires committed-snapshot adapter and native positive/control/negative tests before MAPPED.'
            $binding.resolutionClass = 'MISSING_ADAPTER'
        }
    }
    if ($count -ne 405) { throw "Expected 81 bases × five rarity carriers; generated $count" }
    # Stock Weapon_Bomb is stackable ammunition. Its throw removes Weapon_Bomb
    # after Projectile, and Explode_Generic applies a fixed 20 EntityDamage.
    # Record exact provenance but never assign an affixed managed carrier ID.
    $bombPath = 'Server/Item/Items/Weapon/Bomb/Weapon_Bomb.json'
    $bombBytes = Read-Entry $bombPath
    $bomb = [Text.Encoding]::UTF8.GetString($bombBytes) | ConvertFrom-Json -AsHashtable
    $throw = [Text.Encoding]::UTF8.GetString((Read-Entry 'Server/Item/Interactions/Weapons/Bomb/Bomb_Throw.json')) | ConvertFrom-Json -AsHashtable
    $explode = [Text.Encoding]::UTF8.GetString((Read-Entry 'Server/Item/Interactions/Explosions/Explode_Generic.json')) | ConvertFrom-Json -AsHashtable
    if ($bomb.MaxStack -ne 10 -or $throw.Interactions[2].Type -ne 'Projectile' -or
        $throw.Interactions[3].Type -ne 'ModifyInventory' -or
        $throw.Interactions[3].ItemToRemove.Id -ne 'Weapon_Bomb' -or
        $explode.Config.EntityDamage -ne 20) {
        throw 'Installed bomb ammunition/explosion contract changed; review durable design'
    }
    $bombRows = @($bindings.bindings | Where-Object { $_.baseId -match '^gm\.bomb_' })
    if ($bombRows.Count -ne 9) { throw "Expected nine bomb bases; found $($bombRows.Count)" }
    foreach ($binding in $bombRows) {
        if ($binding.disposition -eq 'MAPPED' -or $null -ne $binding.managedItemId) {
            throw "Consumable bomb bound as durable gear: $($binding.baseId)"
        }
        if ($VerifyOnly) {
            if ($binding.sourceAsset -ne $bombPath -or $binding.sourceSha256 -ne (Sha256 $bombBytes)) {
                throw "Bomb provenance changed: $($binding.baseId)"
            }
        } else {
            $binding.sourceAsset = $bombPath
            $binding.sourceSha256 = Sha256 $bombBytes
            $binding.reason = 'Installed Weapon_Bomb is stack-10 ammunition: Bomb_Throw launches before removing one ammo, while Explode_Generic deals fixed 20 native EntityDamage. A durable managed throw needs its own snapshot-owned projectile/impact route before MAPPED.'
            $binding.resolutionClass = 'MISSING_ADAPTER'
        }
    }
    if (-not $VerifyOnly) {
        [IO.File]::WriteAllText($bindingsPath,(($bindings | ConvertTo-Json -Depth 100) + "`n"),[Text.UTF8Encoding]::new($false))
    }
    Write-Output "Verified $count pinned stock-derived carrier assets and binding provenances. All remain gated."
} finally { $zip.Dispose() }
