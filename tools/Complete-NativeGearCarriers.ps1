param(
    [string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip",
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../src/main/resources'),
    [switch]$VerifyOnly,
    [switch]$Promote
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $AssetsZip).Path)
try {
    function Stock([string]$path) {
        $entry=$zip.GetEntry($path);if($null -eq $entry){throw "Missing pinned stock asset $path"}
        $reader=[IO.StreamReader]::new($entry.Open())
        try { return ($reader.ReadToEnd() | ConvertFrom-Json -AsHashtable) } finally {$reader.Dispose()}
    }
    function Write-Asset([string]$relative,[object]$value) {
        $path=Join-Path $OutputRoot $relative
        $json=($value | ConvertTo-Json -Depth 100)+"`n"
        if($VerifyOnly) {
            if(-not (Test-Path -LiteralPath $path) -or
                    (Canonical (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable)) -ne (Canonical $value)){
                throw "Managed graph differs: $relative"
            }
        } else {
            New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
            [IO.File]::WriteAllText($path,$json,[Text.UTF8Encoding]::new($false))
        }
    }
    function Canonical($value) {
        if($value -is [System.Collections.IDictionary]) {
            return '{'+(($value.Keys | Sort-Object | ForEach-Object {($_ | ConvertTo-Json -Compress)+':'+(Canonical $value[$_])}) -join ',')+'}'
        }
        if($value -is [System.Collections.IEnumerable] -and $value -isnot [string]) {
            return '['+(($value | ForEach-Object {Canonical $_}) -join ',')+']'
        }
        return ($value | ConvertTo-Json -Compress)
    }
    $interaction='Server/Item/Interactions/RPG/Carriers/'
    $root='Server/Item/RootInteractions/RPG/Carriers/'
    $config='Server/ProjectileConfigs/RPG/Carriers/'
    $itemRoot='Server/Item/Items/RPG/Gear/'
    $focusDamage=[ordered]@{Type='RPG_CarrierDamage';DamageCalculator=@{BaseDamage=@{Physical=1}};RpgProcSelector='RPG_Carrier_Focus_Melee_Damage';RpgProcCoefficient=1.0}
    $focusProjectileDamage=[ordered]@{Type='RPG_CarrierDamage';DamageCalculator=@{BaseDamage=@{Projectile=1}};RpgProcSelector='RPG_Carrier_Focus_Projectile_Damage';RpgProcCoefficient=1.0}
    $bombDamage=[ordered]@{Type='RPG_CarrierDamage';DamageCalculator=@{BaseDamage=@{Projectile=1}};RpgProcSelector='RPG_Carrier_Bomb_Damage';RpgProcCoefficient=1.0}
    Write-Asset ($interaction+'RPG_Carrier_Focus_Melee_Damage.json') $focusDamage
    Write-Asset ($interaction+'RPG_Carrier_Focus_Projectile_Damage.json') $focusProjectileDamage
    Write-Asset ($interaction+'RPG_Carrier_Bomb_Damage.json') $bombDamage
    $shieldBash=Stock 'Server/Item/Interactions/Weapons/Shield/Secondary/Guard/Guard_Bash/Weapon_Shield_Secondary_Guard_Bash_Damage.json'
    $shieldBash.Remove('Parent');$shieldBash.Type='RPG_CarrierDamage'
    $shieldBash.RpgProcSelector='RPG_Carrier_Shield_Guard_Damage';$shieldBash.RpgProcCoefficient=1.0
    Write-Asset ($interaction+'RPG_Carrier_Shield_Guard_Damage.json') $shieldBash
    # Item.InteractionVars string values are native RootInteraction IDs. The
    # identically named Interaction leaf alone cannot satisfy Item.CODEC.
    foreach($leaf in @('RPG_Carrier_Focus_Melee_Damage','RPG_Carrier_Focus_Projectile_Damage',
            'RPG_Carrier_Bomb_Damage','RPG_Carrier_Shield_Guard_Damage')) {
        Write-Asset ($root+$leaf+'.json') ([ordered]@{Interactions=@($leaf)})
    }
    # The stock shield's unarmed primary contains an inline fixed native leaf.
    # Preserve its timings, selector and one-power hit using a private root.
    $unarmed=Stock 'Server/Item/Interactions/Weapons/Unarmed/Attacks/Swing_Left/Unarmed_Swing_Left.json'
    $unarmed.Next.Interactions[0].Interactions[0].HitEntity.Interactions[0].Type='RPG_CarrierDamage'
    $unarmed.Next.Interactions[0].Interactions[0].HitEntity.Interactions[0].RpgProcSelector='RPG_Carrier_Shield_Primary_Swing'
    $unarmed.Next.Interactions[0].Interactions[0].HitEntity.Interactions[0].RpgProcCoefficient=1.0
    Write-Asset ($interaction+'RPG_Carrier_Shield_Primary_Swing.json') $unarmed
    $unarmedAction=Stock 'Server/Item/Interactions/Weapons/Unarmed/Variants/Unarmed_Attack_Swing_Left.json'
    $unarmedAction.Next.Interactions[1]='RPG_Carrier_Shield_Primary_Swing'
    $unarmedAction.Failed='RPG_Carrier_Shield_Primary_Swing'
    Write-Asset ($interaction+'RPG_Carrier_Shield_Primary_Action.json') $unarmedAction
    $shieldPrimary=[ordered]@{Interactions=@('RPG_Carrier_Shield_Primary_Action');Tags=@{Attack=@('Melee')}}
    Write-Asset ($root+'RPG_Carrier_Shield_Primary.json') $shieldPrimary
    $focusModel='Skeleton_Mage_Corruption_Orb'
    foreach($kind in @('Staff','Wand','Book')) {
        $animation=switch($kind){'Staff'{'CastSummonCharged'}'Wand'{'CastLeftCharged'}'Book'{'CastHurlCharged'}}
        $launch=[ordered]@{Type='RPG_CarrierProjectile';RunTime=0.25;Lifetime=3.1;Effects=@{ItemAnimationId=$animation};Config="RPG_Carrier_${kind}_Projectile"}
        Write-Asset ($interaction+"RPG_Carrier_${kind}_Launch.json") $launch
        Write-Asset ($root+"RPG_Carrier_${kind}_Launch_Root.json") ([ordered]@{Interactions=@("RPG_Carrier_${kind}_Launch")})
        $flight=[ordered]@{
            Model=$focusModel;UseModelScale=$true
            Physics=@{Type='Standard';Gravity=0;TerminalVelocityAir=50;TerminalVelocityWater=50;RotationMode='Velocity';Bounciness=0}
            LaunchForce=30;SpawnOffset=@{X=-0.25;Y=0.1;Z=0}
            Interactions=@{
                ProjectileHit=@{Interactions=@('RPG_Carrier_Focus_Projectile_Damage','RPG_GearRoute_I_Common_Projectile_Despawn')}
                ProjectileMiss=@{Interactions=@('RPG_GearRoute_I_Common_Projectile_Despawn')}
            }
        }
        Write-Asset ($config+"RPG_Carrier_${kind}_Projectile.json") $flight
    }
    $stockBomb=Stock 'Server/Item/Items/Weapon/Bomb/Weapon_Bomb.json'
    $stockBombConfig=Stock 'Server/ProjectileConfigs/Weapons/Bombs/Projectile_Config_Bomb_Base.json'
    if($stockBomb.MaxStack -ne 10 -or $stockBombConfig.Model -ne 'Bomb'){throw 'Pinned stock bomb contract changed'}
    $explode=[ordered]@{
        Type='Selector';RunTime=0.05;Selector=@{Id='AOECircle';Range=2.5}
        HitEntity=@{Interactions=@('RPG_Carrier_Bomb_Damage')}
        Next='RPG_GearRoute_I_Common_Projectile_Despawn'
    }
    Write-Asset ($interaction+'RPG_Carrier_Bomb_Impact.json') $explode
    $bombConfig=[ordered]@{
        Model=$stockBombConfig.Model;SpawnRotationOffset=$stockBombConfig.SpawnRotationOffset
        Physics=$stockBombConfig.Physics;LaunchForce=$stockBombConfig.LaunchForce;SpawnOffset=$stockBombConfig.SpawnOffset
        Interactions=@{
            ProjectileSpawn=@{Cooldown=@{Cooldown=0};Interactions=@(@{Type='Simple';RunTime=0.5},'RPG_Carrier_Bomb_Impact')}
            ProjectileHit=@{Cooldown=@{Cooldown=0};Rules=@{Interrupting=@('ProjectileSpawn')};Interactions=@('RPG_Carrier_Bomb_Impact')}
        }
    }
    Write-Asset ($config+'RPG_Carrier_Bomb_Projectile.json') $bombConfig
    $throw=[ordered]@{Type='Serial';Interactions=@(
        @{Type='Simple';RunTime=0.25;Effects=@{ItemAnimationId='Throw'}},
        @{Type='ResetCooldown';Cooldown=@{Cooldown=1}},
        @{Type='RPG_CarrierProjectile';Config='RPG_Carrier_Bomb_Projectile';Next=@{Type='Simple';RunTime=0.2}}
    )}
    Write-Asset ($interaction+'RPG_Carrier_Bomb_Throw.json') $throw
    Write-Asset ($root+'RPG_Carrier_Bomb_Throw_Root.json') ([ordered]@{Interactions=@('RPG_Carrier_Bomb_Throw');Tags=@{Attack=@('Ranged')}})
    $bindingsPath=Join-Path $OutputRoot 'rpg/gear/native-bindings-v1.json'
    $bindings=Get-Content -LiteralPath $bindingsPath -Raw | ConvertFrom-Json -AsHashtable
    $bases=Get-Content -LiteralPath (Join-Path $OutputRoot 'rpg/gear/bases-v1.json') -Raw | ConvertFrom-Json -AsHashtable
    $baseById=@{};foreach($base in $bases.bases){$baseById[$base.id]=$base}
    $rarities=[ordered]@{''='RPG_Gear_Common';'_Uncommon'='RPG_Gear_Uncommon';'_Rare'='RPG_Gear_Rare';'_Epic'='RPG_Gear_Epic';'_Legendary'='RPG_Gear_Legendary'}
    $count=0
    foreach($binding in $bindings.bindings){
        $baseId=[string]$binding.baseId
        if($baseId -notmatch '^gm\.(shield|staff|wand|book|bomb)_.+\.(n|nm|h)$'){continue}
        $family=$Matches[1]
        $stem='RPG_Gear_'+($baseId.Substring(3) -replace '\.','_')
        foreach($suffix in $rarities.Keys){
            $relative=$itemRoot+$stem+$suffix+'.json'
            if($family -eq 'bomb'){
                $base=$baseById[$baseId]
                $carrier=[ordered]@{
                    TranslationProperties=@{Name='server.gear.base.'+$baseId}
                    Quality=$rarities[$suffix];MaxStack=1;MaxDurability=$base.durability
                    Model=$stockBomb.Model;Texture=$stockBomb.Texture;Icon=$stockBomb.Icon
                    PlayerAnimationsId=$stockBomb.PlayerAnimationsId;Tags=$stockBomb.Tags
                    Interactions=@{Primary='RPG_Carrier_Bomb_Throw_Root'};Weapon=@{}
                    Categories=$stockBomb.Categories;IconProperties=$stockBomb.IconProperties
                    Particles=$stockBomb.Particles;SoundEventId=$stockBomb.SoundEventId
                }
            } else {
                $carrier=Get-Content -LiteralPath (Join-Path $OutputRoot $relative) -Raw | ConvertFrom-Json -AsHashtable
                if($family -eq 'shield'){
                    $carrier.Interactions.Primary='RPG_Carrier_Shield_Primary'
                    $carrier.InteractionVars.Guard_Bash_Damage='RPG_Carrier_Shield_Guard_Damage'
                } else {
                    $variables=switch($family){
                        'staff'{@('Spear_Swing_Left_Damage','Spear_Swing_Right_Damage')}
                        'wand'{@('Sword_Swing_Left_Fast_Damage','Sword_Swing_Right_Fast_Damage')}
                        'book'{@('Block_Swing_Left_Damage','Block_Swing_Right_Damage','Block_Swing_Down_Damage')}
                    }
                    foreach($name in $variables){$carrier.InteractionVars[$name]='RPG_Carrier_Focus_Melee_Damage'}
                    switch($family){
                        'staff'{$carrier.InteractionVars.Staff_Cast_Summon_Launch='RPG_Carrier_Staff_Launch_Root'}
                        'wand'{$carrier.InteractionVars.Wand_Cast_Left_Launch='RPG_Carrier_Wand_Launch_Root'}
                        'book'{ 
                            $carrier.InteractionVars.Spellbook_Cast_Hurl_Launch='RPG_Carrier_Book_Launch_Root'
                            $cost=$carrier.InteractionVars.Spellbook_Cast_Hurl_Cost.Interactions[0]
                            if(($cost['Parent'] -ne 'Spellbook_Cast_Cost' -and $cost['Type'] -ne 'ChangeStat') -or
                                    $null -eq $cost.StatModifiers){
                                throw "Unexpected spellbook cost ancestry: $relative"
                            }
                            # The stock parent charges mana and removes one held spellbook.
                            # Keep the exact per-carrier mana debit, without consuming durable gear.
                            $cost.Remove('Parent')
                            $cost.Remove('Next')
                            $cost.Type='ChangeStat'
                        }
                    }
                }
            }
            Write-Asset $relative $carrier
            $count++
        }
        if(-not $VerifyOnly){
            if($family -eq 'bomb'){$binding.nativeItemId=if($Promote){'Weapon_Bomb'}else{$null}}
            $binding.disposition=if($Promote){'MAPPED'}else{'BLOCKED_WITH_REASON'}
            $binding.managedItemId=if($Promote){$stem}else{$null}
            $binding.reason=if($Promote){'Managed native action graph with valid-item local power and frozen native projectile impact.'}
                else{'Managed carrier graph and consumer compiled and offline tested; activation waits for main codec/system registration and native asset-loader tests.'}
            $binding.resolutionClass=if($Promote){'VALIDATED_NATIVE_REUSE'}else{'MISSING_ADAPTER'}
        } elseif($Promote){
            if($binding.disposition -ne 'MAPPED' -or $binding.managedItemId -ne $stem){throw "Binding not promoted: $baseId"}
        } elseif($binding.disposition -eq 'MAPPED' -or $null -ne $binding.managedItemId){throw "Binding activated before main tests: $baseId"}
    }
    if($count -ne 450){throw "Expected 90 bases x five rarities; got $count"}
    if(-not $VerifyOnly){[IO.File]::WriteAllText($bindingsPath,(($bindings | ConvertTo-Json -Depth 100)+"`n"),[Text.UTF8Encoding]::new($false))}
    Write-Output "Verified $count managed focus/shield/bomb carriers and native graphs; promoted=$Promote."
} finally {$zip.Dispose()}
