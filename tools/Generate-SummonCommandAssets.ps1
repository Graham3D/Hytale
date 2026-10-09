param(
    [string]$AssetsZip = (Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest/Assets.zip')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$archive = [System.IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {
    function Read-Entry([string]$name) {
        $entry = $archive.GetEntry($name)
        if ($null -eq $entry) { throw "Missing native template: $name" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable }
        finally { $reader.Dispose() }
    }
    $nativeAction = Read-Entry 'Server/Item/Interactions/NPCs/Undead/Skeleton_Archer/Skeleton_Archer_Bow_Shoot.json'
    $nativeRoot = Read-Entry 'Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Undead/Skeleton_Archer/Skeleton_Archer_Bow_Shoot.json'
    $nativeBow = Read-Entry 'Server/Item/Animations/Bow.json'
    if ($nativeAction.RunTime -ne 1 -or $nativeAction.Next.RunTime -ne .2 -or
        $nativeAction.Next.Type -ne 'LaunchProjectile' -or $nativeAction.Next.ProjectileId -ne 'Skeleton_Archer_Arrow' -or
        $nativeRoot.Interactions.Count -ne 1 -or $nativeBow.Animations.Shoot.Speed -ne 1) {
        throw 'Installed native archer timeline differs from reviewed template'
    }
    $root = Split-Path $PSScriptRoot -Parent
    $roleTemplate = Get-Content (Join-Path $root 'src/main/resources/Server/NPC/Roles/RPG/RPG_Summon_Skeleton_Archer.json') -Raw
    function Write-Asset([string]$relative,[object]$value) {
        $path = Join-Path $root "src/main/resources/$relative"
        $parent = Split-Path $path -Parent
        [System.IO.Directory]::CreateDirectory($parent) | Out-Null
        [System.IO.File]::WriteAllText($path, (($value | ConvertTo-Json -Depth 100) + "`n"), [System.Text.UTF8Encoding]::new($false))
    }
    # Two legal weapon slots can contribute one WA-117 each; the frozen sum reaches 24%.
    for ($tenths = 32; $tenths -le 240; $tenths++) {
        $suffix = '{0:D3}' -f $tenths
        $factor = 1 + $tenths / 1000
        $actionId = "RPG_Summon_Archer_Command_$suffix"
        $animationId = "${actionId}_Bow"
        $roleId = "RPG_Summon_Skeleton_Archer_Command_$suffix"
        $action = $nativeAction | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
        $action.RunTime = [math]::Round(1 / $factor, 6)
        $action.Next.RunTime = [math]::Round(.2 / $factor, 6)
        $action.Effects.ItemPlayerAnimationsId = $animationId
        $rootAction = $nativeRoot | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
        $rootAction.Interactions = @($actionId)
        $shoot = $nativeBow.Animations.Shoot | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
        $shoot.Speed = [math]::Round($factor, 6)
        $animations = @{ Parent = 'Skeleton_Bow'; Animations = @{ Shoot = $shoot } }
        $role = $roleTemplate | ConvertFrom-Json -AsHashtable
        $role.Modify.Attack = $actionId
        Write-Asset "Server/Item/Interactions/RPG/Summon/$actionId.json" $action
        Write-Asset "Server/Item/RootInteractions/RPG/Summon/$actionId.json" $rootAction
        Write-Asset "Server/Item/Animations/RPG/Summon/$animationId.json" $animations
        Write-Asset "Server/NPC/Roles/RPG/$roleId.json" $role
    }
}
finally { $archive.Dispose() }
