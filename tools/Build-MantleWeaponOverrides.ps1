param([string]$RepoRoot=(Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$package=Join-Path $env:APPDATA 'Hytale\install\pre-release\package\game\latest'
$native=Join-Path $package 'Assets.zip'
if((Get-FileHash -LiteralPath $native -Algorithm SHA256).Hash -ne '0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126') {throw 'Pinned asset archive changed; repeat the leaf audit.'}
$zip=[IO.Compression.ZipFile]::OpenRead($native)
try {
    $path='Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json'
    $reader=[IO.StreamReader]::new($zip.GetEntry($path).Open())
    try {$item=$reader.ReadToEnd() | ConvertFrom-Json} finally {$reader.Dispose()}
    $coverage=@()
    foreach($binding in $item.InteractionVars.PSObject.Properties) {
        foreach($leaf in $binding.Value.Interactions) {
            if($null -eq $leaf.DamageCalculator -or $null -eq $leaf.DamageCalculator.BaseDamage.Fire){continue}
            if($leaf.DamageCalculator.BaseDamage.PSObject.Properties.Count -gt 1){throw 'Mixed source definition needs an explicit audit'}
            $leaf | Add-Member -NotePropertyName Type -NotePropertyValue 'RPG_ManagedWeaponFire'
            $coverage+= [ordered]@{itemId='Weapon_Longsword_Flame';binding=$binding.Name;parent=$leaf.Parent;sourceFire=$leaf.DamageCalculator.BaseDamage.Fire;variance=$leaf.DamageCalculator.RandomPercentageModifier;contract='NORMALIZED_FIRE_V1';delivery='NATIVE_MELEE';connectedVerified=$false}
        }
    }
    if($coverage.Count -ne 4){throw 'Expected four Flame Longsword damage bindings'}
    $dest=Join-Path $RepoRoot ('src/main/resources/'+$path)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    [IO.File]::WriteAllText($dest,($item|ConvertTo-Json -Depth 60),[Text.UTF8Encoding]::new($false))
    $manifest=Join-Path $RepoRoot 'src/main/resources/rpg/runtime/managed-weapon-fire-v1.json'
    [IO.File]::WriteAllText($manifest,([ordered]@{schemaVersion=1;contract='NORMALIZED_FIRE_V1';coverage=$coverage}|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
    'Generated four opt-in native Flame Longsword leaves; all other item properties retained.'
} finally {$zip.Dispose()}
