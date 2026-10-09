param(
    [string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip",
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../src/main/resources')
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $AssetsZip).Path)
try {
    $bindings=Get-Content (Join-Path $OutputRoot 'rpg/gear/native-bindings-v1.json') -Raw | ConvertFrom-Json -AsHashtable
    $rows=@($bindings.bindings | Where-Object {$_.baseId -match '^gm\.(staff|wand|book|shield|bomb)_.+\.(n|nm|h)$'})
    if($rows.Count -ne 90){throw "Expected 90 owned bases, got $($rows.Count)"}
    $count=0
    foreach($row in $rows){
        if($row.disposition -eq 'MAPPED' -or $null -ne $row.managedItemId -or
                $row.resolutionClass -ne 'MISSING_ADAPTER'){throw "Premature carrier activation $($row.baseId)"}
        $stem='RPG_Gear_'+($row.baseId.Substring(3) -replace '\.','_')
        $entry=$zip.GetEntry($row.sourceAsset)
        if($null -eq $entry){throw "Missing provenance source $($row.sourceAsset)"}
        $stream=$entry.Open();$memory=[IO.MemoryStream]::new()
        try{$stream.CopyTo($memory);$sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($memory.ToArray()))}
        finally{$stream.Dispose();$memory.Dispose()}
        if($sha -ne $row.sourceSha256){throw "Source hash drift $($row.baseId)"}
        foreach($suffix in @('','_Uncommon','_Rare','_Epic','_Legendary')){
            $asset=Join-Path $OutputRoot "Server/Item/Items/RPG/Gear/$stem$suffix.json"
            $item=Get-Content -LiteralPath $asset -Raw | ConvertFrom-Json -AsHashtable
            if($item.MaxStack -ne 1 -or $item.MaxDurability -le 0 -or $item.Weapon.Count -ne 0){throw "Invalid durable neutral carrier $asset"}
            foreach($section in @('Interactions','InteractionVars')) {
                if(-not $item.Contains($section)){continue}
                foreach($value in $item[$section].Values) {
                    if($value -is [string] -and $value.StartsWith('RPG_Carrier_')) {
                        $root=Join-Path $OutputRoot "Server/Item/RootInteractions/RPG/Carriers/$value.json"
                        if(-not (Test-Path -LiteralPath $root)){throw "Missing native RootInteraction $value for $asset"}
                    }
                }
            }
            $family=($row.baseId -split '[._]')[1]
            switch($family){
                'bomb'{if($item.Interactions.Primary -ne 'RPG_Carrier_Bomb_Throw_Root' -or $item.Contains('InteractionVars')){throw "Unsafe bomb $asset"}}
                'shield'{if($item.Interactions.Secondary -ne 'Root_Weapon_Shield_Secondary_Guard' -or
                        $item.InteractionVars.Guard_Bash_Damage -ne 'RPG_Carrier_Shield_Guard_Damage' -or
                        $item.Interactions.Primary -ne 'RPG_Carrier_Shield_Primary'){throw "Unsafe shield $asset"}}
                'staff'{if($item.InteractionVars.Staff_Cast_Summon_Launch -ne 'RPG_Carrier_Staff_Launch_Root' -or
                        $item.InteractionVars.Spear_Swing_Left_Damage -ne 'RPG_Carrier_Focus_Melee_Damage' -or
                        $item.InteractionVars.Spear_Swing_Right_Damage -ne 'RPG_Carrier_Focus_Melee_Damage'){throw "Unmanaged staff $asset"}}
                'wand'{if($item.InteractionVars.Wand_Cast_Left_Launch -ne 'RPG_Carrier_Wand_Launch_Root' -or
                        $item.InteractionVars.Sword_Swing_Left_Fast_Damage -ne 'RPG_Carrier_Focus_Melee_Damage' -or
                        $item.InteractionVars.Sword_Swing_Right_Fast_Damage -ne 'RPG_Carrier_Focus_Melee_Damage'){throw "Unmanaged wand $asset"}}
                'book'{$bad=@(@('Block_Swing_Left_Damage','Block_Swing_Right_Damage','Block_Swing_Down_Damage') |
                        Where-Object {$item.InteractionVars[$_] -ne 'RPG_Carrier_Focus_Melee_Damage'})
                    $cost=$item.InteractionVars.Spellbook_Cast_Hurl_Cost.Interactions[0]
                    if($item.InteractionVars.Spellbook_Cast_Hurl_Launch -ne 'RPG_Carrier_Book_Launch_Root' -or
                            $bad.Count -gt 0 -or $cost.Type -ne 'ChangeStat' -or
                            $cost.Contains('Parent') -or $cost.Contains('Next') -or $null -eq $cost.StatModifiers){
                        throw "Unmanaged or consumable book $asset"
                    }}
            }
            $count++
        }
    }
    foreach($leaf in @('RPG_Carrier_Focus_Melee_Damage','RPG_Carrier_Focus_Projectile_Damage',
            'RPG_Carrier_Bomb_Damage','RPG_Carrier_Shield_Guard_Damage')){
        $root=Join-Path $OutputRoot "Server/Item/RootInteractions/RPG/Carriers/$leaf.json"
        $definition=Get-Content -LiteralPath $root -Raw | ConvertFrom-Json -AsHashtable
        if(@($definition.Interactions).Count -ne 1 -or $definition.Interactions[0] -ne $leaf){
            throw "Managed damage root does not resolve its typed leaf: $leaf"
        }
    }
    foreach($folder in @('Server/Item/Interactions/RPG/Carriers','Server/Item/RootInteractions/RPG/Carriers',
            'Server/ProjectileConfigs/RPG/Carriers')){
        foreach($file in Get-ChildItem -LiteralPath (Join-Path $OutputRoot $folder) -Filter '*.json'){
            $json=Get-Content -LiteralPath $file.FullName -Raw
            $null=$json | ConvertFrom-Json -AsHashtable
            if($json -match '"Type"\s*:\s*"(?:DamageEntity|LaunchProjectile|Explode|ModifyInventory)"' -or
                    $json -match '"EntityDamage"\s*:'){throw "Unmanaged damaging/consuming route $($file.Name)"}
        }
    }
    $bomb=Get-Content (Join-Path $OutputRoot 'Server/ProjectileConfigs/RPG/Carriers/RPG_Carrier_Bomb_Projectile.json') -Raw | ConvertFrom-Json -AsHashtable
    if($bomb.Interactions.ProjectileHit.Interactions[0] -ne 'RPG_Carrier_Bomb_Impact' -or
            $bomb.Interactions.ProjectileSpawn.Interactions[1] -ne 'RPG_Carrier_Bomb_Impact'){throw 'Bomb impact path incomplete'}
    Write-Output "PASS: $count durable carriers, 90 exact source hashes, published custom roots, nonconsuming book cost and private managed impact graph. Run NativeAffixCarrierAssetQualificationTest for installed-stock reachability and native codec proof."
} finally {$zip.Dispose()}
