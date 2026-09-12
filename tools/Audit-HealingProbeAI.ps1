[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'evidence/stage-13/cohort-ai'
New-Item -ItemType Directory -Force -Path $out|Out-Null
$game=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest'
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $game 'Assets.zip'))
$items=[Collections.Generic.List[object]]::new()
function ReadEntry($entry){$r=[IO.StreamReader]::new($entry.Open());try{$r.ReadToEnd()}finally{$r.Dispose()}}
function RecordEntry($entry){
    $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
    try{$hash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
    $items.Add(@{path=$entry.FullName;bytes=$entry.Length;sha256=$hash})
}
$systems=@('Beam_Heal_Green2','Effect_Health_Pack','Staff_Bronze')
$resolved=@()
try{
    foreach($system in $systems){
        $e=@($zip.Entries|Where-Object {$_.Name -ceq "$system.particlesystem"})
        if($e.Count -ne 1){throw "System does not resolve uniquely: $system"}
        RecordEntry $e[0];$json=ReadEntry $e[0]|ConvertFrom-Json
        $spawners=@()
        foreach($reference in $json.Spawners){
            $id=$reference.SpawnerId
            $spawner=@($zip.Entries|Where-Object {$_.Name -ceq "$id.particlespawner"})
            if($spawner.Count -ne 1){throw "Spawner does not resolve uniquely: $id"}
            RecordEntry $spawner[0];$definition=ReadEntry $spawner[0]|ConvertFrom-Json
            $spawners+=@{id=$id;reference=$reference;definition=$definition}
            # Resolve the native sprite texture dependency where specified.
            if($definition.Particle.Texture){$texture=$zip.GetEntry('Common/'+$definition.Particle.Texture);if(-not $texture){throw 'Missing particle texture'};RecordEntry $texture}
        }
        $resolved+=@{system=$system;definition=$json;spawners=$spawners}
    }
    foreach($path in @('Common/NPC/MISC/Empty.blockymodel','Common/Items/Projectiles/Projectile_default.png','Common/NPC/MISC/Mannequin/Models/Model.blockymodel','Common/NPC/MISC/Mannequin/Models/Model_Default.png','Common/Trails/Void_Green.png')){
        $e=$zip.GetEntry($path);if(-not $e){throw "Missing native dependency: $path"};RecordEntry $e
    }
}finally{$zip.Dispose()}
$client=Join-Path $env:APPDATA 'Hytale/data/pre-release/Logs/2026-09-12_15-12-06_client.log'
$clientLines=Get-Content -LiteralPath $client
$warnings=@($clientLines|Select-String -Pattern '(?i)(missing.*texture|failed.*texture|Empty\.png|Beam_Heal_Green2|RPG_Healing_Stream)')
$native=Join-Path $game 'Server/HytaleServer.jar'
$api=Join-Path $out 'native-api';New-Item -ItemType Directory -Force -Path $api|Out-Null
foreach($class in @('com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent','com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems$EffectControllerSystem','com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems$EntityModel','com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems$EntityUpdate','com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems$SendPackets','com.hypixel.hytale.server.core.io.PacketHandler')){
    & javap -classpath $native -c -p $class | Set-Content (Join-Path $api ($class.Split('.')[-1]+'.txt')) -Encoding utf8
    if($LASTEXITCODE){throw "javap failed: $class"}
}
$live=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar'
[ordered]@{baseline='f5aeb31cf9dea6ede1db1f63f6b19080c381320c';cohort='AI';gate='A_UNVERIFIED_CONNECTED';
    serverSha256=(Get-FileHash $native).Hash;assetsSha256=(Get-FileHash (Join-Path $game 'Assets.zip')).Hash;
    liveJarSha256=(Get-FileHash $live).Hash;liveModified=$false;
    clientLog=@{file=[IO.Path]::GetFileName($client);sha256=(Get-FileHash $client).Hash;lines=$clientLines.Count;matchedTextureOrBeamLines=@($warnings|ForEach-Object {@{line=$_.LineNumber;text=$_.Line}})};
    systems=$resolved;nativeDependencies=$items;
    limitation='No attached connected viewer or packet delivery acknowledgement in the isolated audit. No renderer success inferred.'
}|ConvertTo-Json -Depth 30|Set-Content (Join-Path $out 'visibility-boundary-audit.json') -Encoding utf8
Write-Host 'Native dependencies, API disassembly and AH client-log checks recorded. No deployment or live-save writes.'
