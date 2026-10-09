param([string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip")
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$zip = [IO.Compression.ZipFile]::OpenRead($AssetsZip)
$out = Join-Path $PSScriptRoot '../src/main/resources/rpg/gear/action-templates/twin-daggers'
New-Item -ItemType Directory -Path $out -Force | Out-Null
try {
    $prefix = 'Server/Item/Interactions/Weapons/Daggers/Primary/'
    $entries = @($zip.Entries | Where-Object { $_.FullName.StartsWith($prefix) -and $_.Name.EndsWith('.json') })
    if ($entries.Count -ne 25) { throw "Native dagger primary graph changed: $($entries.Count) nodes" }
    foreach ($entry in $entries) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $source = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $id = [IO.Path]::GetFileNameWithoutExtension($entry.Name)
        $json = $source | ConvertFrom-Json -AsHashtable
        if ($id -eq 'Weapon_Daggers_Primary_Swing_Left_Selector') {
            if ($json['Selector']['Id'] -ne 'Stab' -or $json['Selector']['EndDistance'] -ne 2 -or
                $json['HitEntity']['Interactions'][0]['Var'] -ne 'Swing_Left_Damage') { throw 'Unreviewed dagger first selector' }
            $json['Next'] = [ordered]@{ Type = 'RPG_TwinAssaultGate'; RunTime = 0.066;
                Effects = [ordered]@{ ItemAnimationId = 'SwingRight' };
                Next = 'RPG_Twin_Daggers_Offhand_Selector'; Failed = 'RPG_Twin_Daggers_Skip' }
        }
        $text = ($json | ConvertTo-Json -Depth 100).Replace('Weapon_Daggers_Primary', 'RPG_Twin_Daggers_Weapon_Daggers_Primary')
        [IO.File]::WriteAllText((Join-Path $out "RPG_Twin_Daggers_$id.json"), "$text`n", [Text.UTF8Encoding]::new($false))
    }
    $rootEntry = $zip.GetEntry('Server/Item/RootInteractions/Weapons/Daggers/Root_Weapon_Daggers_Primary.json')
    if ($null -eq $rootEntry) { throw 'Native dagger root missing' }
    $reader = [IO.StreamReader]::new($rootEntry.Open())
    try { $root = $reader.ReadToEnd().Replace('Weapon_Daggers_Primary', 'RPG_Twin_Daggers_Weapon_Daggers_Primary') }
    finally { $reader.Dispose() }
    [IO.File]::WriteAllText((Join-Path $out 'RPG_Twin_Daggers_Root.json'), "$root`n", [Text.UTF8Encoding]::new($false))
    $right = $entries | Where-Object { $_.Name -eq 'Weapon_Daggers_Primary_Swing_Right_Selector.json' }
    $reader = [IO.StreamReader]::new($right.Open())
    try { $offhand = $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $reader.Dispose() }
    if ($offhand['Effects']['Trails'][0]['TargetEntityPart'] -ne 'SecondaryItem') { throw 'Offhand trail target changed' }
    $offhand['Selector']['TestLineOfSight'] = $true
    $offhand['HitEntity'] = [ordered]@{ Interactions = @([ordered]@{
        Type = 'RPG_GearDamage'; Offhand = $true; Parent = 'Weapon_Daggers_Primary_Swing_Right_Damage';
        DamageCalculator = [ordered]@{ BaseDamage = [ordered]@{ Physical = 6 } };
        AngledDamage = @() }) }
    $offhand['Next'] = [ordered]@{ Type = 'Simple'; RunTime = 0.066 }
    [IO.File]::WriteAllText((Join-Path $out 'RPG_Twin_Daggers_Offhand_Selector.json'),
        "$(($offhand | ConvertTo-Json -Depth 100))`n", [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $out 'RPG_Twin_Daggers_Skip.json'),
        "{`"Type`":`"Simple`"}`n", [Text.UTF8Encoding]::new($false))
    Write-Output 'Generated 28 bounded native Twin Daggers assets.'
} finally { $zip.Dispose() }
