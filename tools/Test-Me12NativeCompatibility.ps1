[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ServerJar,
    [Parameter(Mandatory=$true)][string]$AssetsZip,
    [Parameter(Mandatory=$true)][string]$Manifest,
    [Parameter(Mandatory=$true)][string]$SourcePins,
    [Parameter(Mandatory=$true)][string]$Output
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Sha([IO.Stream]$stream) {
    $digest=[Security.Cryptography.SHA256]::Create()
    try {return ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}
    finally {$digest.Dispose()}
}
function FileSha([string]$path) {
    $stream=[IO.File]::OpenRead($path)
    try {return Sha $stream} finally {$stream.Dispose()}
}
$source=(Resolve-Path -LiteralPath $Manifest).Path
$root=[IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $source) '../../../../..'))
$target=[IO.Path]::GetFullPath($Output)
if(-not $target.StartsWith((Join-Path $root 'build')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw 'Compatibility output must stay in this repository build directory.'
}
$binding=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
$pins=Get-Content -LiteralPath $SourcePins -Raw | ConvertFrom-Json
$serverHash=FileSha $ServerJar
$assetsHash=FileSha $AssetsZip
if($serverHash -ne $binding.serverJarSha256 -or $assetsHash -ne $binding.assetsSha256){
    throw "ME12 native identity mismatch: server=$serverHash assets=$assetsHash"
}
$checked=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
$zip=[IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {
    foreach($property in $pins.PSObject.Properties){
        $path=[string]$property.Name
        if(-not $path.StartsWith('Server/NPC/Roles/',[StringComparison]::Ordinal) -or -not $path.EndsWith('.json',[StringComparison]::Ordinal)){
            throw "Invalid native role pin path: $path"
        }
        $entry=$zip.GetEntry($path)
        if($null -eq $entry){throw "Missing native role: $path"}
        $stream=$entry.Open()
        try {$actual=Sha $stream} finally {$stream.Dispose()}
        if($actual -ne [string]$property.Value){throw "Changed native role: $path"}
        $checked.Add($path,$actual)
    }
    foreach($row in $binding.bindings){
        $path=[string]$row.sourceAssetPath
        $entry=$zip.GetEntry($path)
        if($null -eq $entry){throw "Missing certified binding role: $path"}
        $stream=$entry.Open()
        try {$actual=Sha $stream} finally {$stream.Dispose()}
        if($actual -ne [string]$row.sourceAssetSha256){throw "Changed certified binding role: $path"}
        if($checked.ContainsKey($path) -and $checked[$path] -ne $actual){throw "Conflicting native role pin: $path"}
        $checked[$path]=$actual
    }
    $required=@(
        'com/hypixel/hytale/server/spawning/world/system/WorldSpawningSystem.class',
        'com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/ChangeStatInteraction.class',
        'com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/ChangeStatWithModifierInteraction.class',
        'com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/ClearEntityEffectInteraction.class',
        'com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/DamageEntityInteraction.class',
        'com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/LaunchProjectileInteraction.class',
        'com/hypixel/hytale/server/core/modules/projectile/interaction/ProjectileInteraction.class'
    )
} finally {$zip.Dispose()}
$native=[IO.Compression.ZipFile]::OpenRead($ServerJar)
try {foreach($entry in $required){if($null -eq $native.GetEntry($entry)){throw "Missing native owner: $entry"}}}
finally {$native.Dispose()}
if((FileSha $ServerJar) -ne $serverHash -or (FileSha $AssetsZip) -ne $assetsHash){
    throw 'Native files changed during compatibility verification.'
}
$commit=(& git -C $root rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$'){throw 'Source commit unavailable.'}
$result=[ordered]@{
    schemaVersion=1; compatibility='Hytale 0.7.2 mod-side'; sourceCommit=$commit
    serverSha256=$serverHash; assetsSha256=$assetsHash
    bindingsSha256=(FileSha $Manifest); sourcePinsSha256=(FileSha $SourcePins)
    pinnedNativeRoles=$checked.Count; nativeOwnerClasses=$required.Count
    result='PASS'
}
$parent=Split-Path -Parent $target
[IO.Directory]::CreateDirectory($parent) | Out-Null
[IO.File]::WriteAllText($target,($result | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
Write-Output "ME12_NATIVE_072_COMPATIBILITY_PASS roles=$($checked.Count) owners=$($required.Count) server=$serverHash assets=$assetsHash"
