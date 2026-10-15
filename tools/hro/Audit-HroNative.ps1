param([Parameter(Mandatory=$true)][string]$ServerJar,
      [Parameter(Mandatory=$true)][string]$AssetsZip,
      [Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$server=[IO.Compression.ZipFile]::OpenRead($ServerJar)
try {
    $required=@('com/hypixel/hytale/server/core/universe/PlayerRef.class',
        'com/hypixel/hytale/server/core/inventory/InventoryComponent$AbilitySlots.class',
        'com/hypixel/hytale/server/core/event/events/player/PlayerConnectEvent.class',
        'com/hypixel/hytale/server/core/entity/entities/player/hud/HudManager.class')
    foreach($entry in $required){if(-not $server.GetEntry($entry)){throw "Missing referenced native class: $entry"}}
    $reader=[IO.StreamReader]::new($server.GetEntry('META-INF/MANIFEST.MF').Open())
    try{$manifest=$reader.ReadToEnd()}finally{$reader.Dispose()}
    if($manifest -notmatch 'Implementation-Version: 0\.7\.2\s'){throw 'Unexpected release API'}
} finally {$server.Dispose()}
$assets=[IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {if($assets.Entries.Count -lt 1){throw 'Empty installed asset archive'};$assetCount=$assets.Entries.Count}
finally {$assets.Dispose()}
function Hash-File([string]$Path) {
    $sha=[Security.Cryptography.SHA256]::Create();$inputFile=[IO.File]::OpenRead($Path)
    try{return ([BitConverter]::ToString($sha.ComputeHash($inputFile))).Replace('-','')}
    finally{$inputFile.Dispose();$sha.Dispose()}
}
$serverHash=Hash-File $ServerJar
$assetHash=Hash-File $AssetsZip
$directory=Split-Path -Parent $Output
New-Item -ItemType Directory -Force -Path $directory | Out-Null
@("schemaVersion=1","serverVersion=0.7.2","serverSha256=$serverHash","assetsSha256=$assetHash",
  "assetEntries=$assetCount","nativeClassesVerified=$($required.Count)",
  'runtimeRegistration=REQUIRES_WITNESSES','runtimeAssets=NOT_INFERRED_FROM_OFFLINE_AUDIT') |
    Set-Content -LiteralPath $Output -Encoding ascii
