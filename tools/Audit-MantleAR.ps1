[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo=(Resolve-Path "$PSScriptRoot/..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-ar'
$game=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest'
$traceDir=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg'
$events=[Collections.Generic.List[object]]::new()
$files=@(Get-ChildItem (Join-Path $traceDir 'archive') -Filter '*.jsonl.gz' -File -Recurse)
$files+=Get-Item (Join-Path $traceDir 'skill-trace.jsonl')
foreach($file in $files){
    $stream=[IO.File]::OpenRead($file.FullName)
    if($file.Extension -eq '.gz'){$stream=[IO.Compression.GZipStream]::new($stream,[IO.Compression.CompressionMode]::Decompress)}
    $reader=[IO.StreamReader]::new($stream)
    try{while($null -ne ($line=$reader.ReadLine())){
        if($line -match 'R032-AQ' -and $line -match 'MANTLE_|WEAPON_FIRE_SOURCE_ROUTED'){$events.Add(($line|ConvertFrom-Json))}
    }}finally{$reader.Dispose()}
}
$events|ConvertTo-Json -Depth 15|Set-Content (Join-Path $out 'aq-connected-mantle-records.json') -Encoding utf8
$assetRoot=Join-Path $out 'native-assets';New-Item -ItemType Directory -Force $assetRoot|Out-Null
$z=[IO.Compression.ZipFile]::OpenRead((Join-Path $game 'Assets.zip'))
$assets=@()
try{
    foreach($entry in $z.Entries|Where-Object {$_.FullName -match '^Server/(Particles/Combat/(Impact/Misc/Fire/|Fire_Stick/Fire_Trap/Fire_AoE_CirclesFloor)|Entity/Effects/Status/Poison.json)'}){
        $dest=Join-Path $assetRoot $entry.FullName;New-Item -ItemType Directory -Force (Split-Path $dest)|Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$dest,$true)
        $assets+=@{path=$entry.FullName;sha256=(Get-FileHash $dest).Hash}
    }
}finally{$z.Dispose()}
$records=@($events|Group-Object eventType|Select-Object Name,Count)
$summary=@{recordCount=$events.Count;events=$records;serverSha256=(Get-FileHash (Join-Path $game 'Server/HytaleServer.jar')).Hash;assetsSha256=(Get-FileHash (Join-Path $game 'Assets.zip')).Hash;nativeAssets=$assets;video='C:/Users/Zemio/OneDrive/Desktop/Mantle1.mp4';videoSha256=(Get-FileHash 'C:/Users/Zemio/OneDrive/Desktop/Mantle1.mp4').Hash;connectedPolishVerified=$false}
$summary|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'before-audit.json') -Encoding utf8
$summary|ConvertTo-Json -Depth 3
