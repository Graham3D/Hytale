param(
    [ValidateRange(0, 180)][int]$RateTenths = 150,
    [ValidateRange(0, 50)][int]$ReachCentimetres = 40,
    [string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$OutputRoot = Join-Path $PSScriptRoot '../src/main/resources'
$expectedAssetsSha256='C02E69A0FA4088B965D0CA9D01C6A55610229EFA3F78F91FB45463108F07D022'
if((Get-FileHash -LiteralPath $AssetsZip -Algorithm SHA256).Hash -ne $expectedAssetsSha256){
    throw 'Installed Assets.zip changed; re-audit the sword native action graph before generation'
}

# One immutable native action family. Source IDs and animation names are pinned to the
# installed Sword primary graph; new upstream nodes fail generation rather than silently
# receiving stock attack timing. Reach is represented in centimetres, exactly on WA-014's grid.
$rateFactor = 1 + $RateTenths / 1000.0
$reach = $ReachCentimetres / 100.0
if ($RateTenths -ne 0 -and $RateTenths -lt 48) { throw 'WA-008 rate must be 0 or 48..180 tenths of a percent' }
if ($ReachCentimetres -ne 0 -and $ReachCentimetres -lt 12) { throw 'WA-014 reach must be 0 or 12..50 centimetres' }
if ($RateTenths -eq 0 -and $ReachCentimetres -eq 0) { throw 'Unaffixed sword uses the stock native root' }
$speedId = if ($RateTenths -eq 150) { 115 } else { 1000 + $RateTenths }
$profile = 'RPG_Action_Sword_S{0}_R{1:D2}' -f $speedId, $ReachCentimetres
$prefix = 'Weapon_Sword_Primary'
$sourcePrefix = 'Server/Item/Interactions/Weapons/Sword/Attacks/Primary/'
$attackAnimations = @('SwingLeft', 'SwingRight', 'SwingDownStrong', 'StabDashCharging', 'StabDashCharged')
$expectedSelectors = @('Weapon_Sword_Primary_Swing_Left_Selector',
    'Weapon_Sword_Primary_Swing_Right_Selector', 'Weapon_Sword_Primary_Swing_Down_Selector',
    'Weapon_Sword_Primary_Thrust_Selector')

function Scale-Graph($node) {
    if ($node -is [System.Collections.IDictionary]) {
        if ($node.Contains('RunTime')) { $node['RunTime'] = [math]::Round([double]$node['RunTime'] / $rateFactor, 6) }
        if ($node.Contains('ChainingAllowance')) { $node['ChainingAllowance'] = [math]::Round([double]$node['ChainingAllowance'] / $rateFactor, 6) }
        if ($node.Contains('Next') -and $node['Next'] -is [System.Collections.IDictionary]) {
            $keys = @($node['Next'].Keys)
            if ($keys.Count -gt 0 -and @($keys | Where-Object { $_ -notmatch '^\d+(\.\d+)?$' }).Count -eq 0) {
                $scaled = [ordered]@{}
                foreach ($key in $keys) {
                    $newKey = ([math]::Round([double]::Parse($key, [Globalization.CultureInfo]::InvariantCulture) / $rateFactor, 6)).ToString([Globalization.CultureInfo]::InvariantCulture)
                    if ($scaled.Contains($newKey)) { throw "Collision in scaled timeline $profile at $newKey" }
                    $scaled[$newKey] = $node['Next'][$key]
                }
                $node['Next'] = $scaled
            }
        }
        if ($node.Contains('Effects') -and $node['Effects'] -is [System.Collections.IDictionary]) {
            $animation = $node['Effects']['ItemAnimationId']
            if ($animation -in $attackAnimations) { $node['Effects']['ItemPlayerAnimationsId'] = "${profile}_Animations" }
        }
        foreach ($value in @($node.Values)) { Scale-Graph $value }
    } elseif ($node -is [System.Collections.IList]) {
        foreach ($value in $node) { Scale-Graph $value }
    }
}

function Write-JsonAsset([string]$relative, $json) {
    $target = Join-Path $OutputRoot $relative
    $directory = Split-Path -Parent $target
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $text = $json | ConvertTo-Json -Depth 100
    [IO.File]::WriteAllText($target, "$text`n", [Text.UTF8Encoding]::new($false))
}

Add-Type -AssemblyName System.IO.Compression
$zip = [IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {
    $sources = @($zip.Entries | Where-Object { $_.FullName.StartsWith($sourcePrefix) -and $_.Name -like '*.json' })
    if ($sources.Count -ne 16) { throw "Sword primary graph changed: expected 16 native interaction assets, found $($sources.Count)" }
    $foundSelectors = [Collections.Generic.HashSet[string]]::new()
    $foundAnimations = [Collections.Generic.HashSet[string]]::new()
    foreach ($entry in $sources) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $source = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $name = [IO.Path]::GetFileNameWithoutExtension($entry.Name)
        $json = $source | ConvertFrom-Json -AsHashtable
        if ($json.Contains('Selector')) {
            if ($name -notin $expectedSelectors -or $json['Selector']['Id'] -notin @('Horizontal', 'Stab') -or
                $json['Selector']['TestLineOfSight'] -ne $true) {
                throw "Unreviewed sword contact geometry: $name"
            }
            [void]$foundSelectors.Add($name)
            $json['Selector']['EndDistance'] = [math]::Round([double]$json['Selector']['EndDistance'] + $reach, 4)
            foreach ($trail in @($json['Effects']['Trails'])) {
                if ($null -ne $trail -and $trail.Contains('PositionOffset')) {
                    $trail['PositionOffset']['X'] = [math]::Round([double]$trail['PositionOffset']['X'] + $reach, 4)
                }
            }
        }
        if ($json.Contains('Effects') -and $json['Effects'].Contains('ItemAnimationId')) {
            $animation = $json['Effects']['ItemAnimationId']
            if ($animation -notin $attackAnimations) { throw "Unreviewed sword attack animation: $animation" }
            [void]$foundAnimations.Add($animation)
        }
        Scale-Graph $json
        # References to stock damage leaves still pass through existing managed item
        # InteractionVars. Clone names throughout so no profile node reads shared timing.
        $text = $json | ConvertTo-Json -Depth 100
        $text = $text.Replace($prefix, "${profile}_${prefix}")
        $relative = "Server/Item/Interactions/RPG/ActionProfiles/${profile}_${name}.json"
        $target = Join-Path $OutputRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        [IO.File]::WriteAllText($target, "$text`n", [Text.UTF8Encoding]::new($false))
    }
    if ($foundSelectors.Count -ne 4 -or $foundAnimations.Count -ne 5) { throw 'Sword contact or animation graph incomplete' }

    $rootEntry = $zip.GetEntry('Server/Item/RootInteractions/Weapons/Sword/Root_Weapon_Sword_Primary.json')
    if ($null -eq $rootEntry) { throw 'Native sword primary root missing' }
    $reader = [IO.StreamReader]::new($rootEntry.Open())
    try { $root = $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $reader.Dispose() }
    $root['Cooldown']['Cooldown'] = [math]::Round([double]$root['Cooldown']['Cooldown'] / $rateFactor, 6)
    $root['ClickQueuingTimeout'] = [math]::Round([double]$root['ClickQueuingTimeout'] / $rateFactor, 6)
    $root['Interactions'] = @("${profile}_${prefix}")
    Write-JsonAsset "Server/Item/RootInteractions/RPG/ActionProfiles/${profile}_Root.json" $root

    $animationEntry = $zip.GetEntry('Server/Item/Animations/Sword.json')
    if ($null -eq $animationEntry) { throw 'Native Sword animation profile missing' }
    $reader = [IO.StreamReader]::new($animationEntry.Open())
    try { $animations = $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $reader.Dispose() }
    $overrides = [ordered]@{}
    foreach ($name in $attackAnimations) {
        if (-not $animations['Animations'].Contains($name)) { throw "Missing native animation: $name" }
        $copy = $animations['Animations'][$name]
        $baseSpeed = if ($copy.Contains('Speed')) { [double]$copy['Speed'] } else { 1.0 }
        $copy['Speed'] = [math]::Round($baseSpeed * $rateFactor, 6)
        $overrides[$name] = $copy
    }
    Write-JsonAsset "Server/Item/Animations/${profile}_Animations.json" ([ordered]@{ Parent = 'Sword'; Animations = $overrides })
    Write-Output "Generated $profile from the installed native Sword primary graph (16 interactions, 1 root, 1 animation profile)."
} finally {
    $zip.Dispose()
}
