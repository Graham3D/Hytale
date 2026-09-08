[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$encRoot=(Resolve-Path "$PSScriptRoot\..").Path
$encOut=Join-Path $encRoot 'evidence\stage-12\cohort-c\api'
$encPackage="$env:APPDATA\Hytale\install\pre-release\package\game\latest"
$encServer=Join-Path $encPackage 'Server\HytaleServer.jar'
$encAssets=Join-Path $encPackage 'Assets.zip'
$encJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $encOut | Out-Null
foreach($encClass in @(
    'com.hypixel.hytale.component.AddReason',
    'com.hypixel.hytale.server.npc.entities.NPCEntity',
    'com.hypixel.hytale.server.spawning.world.system.WorldSpawnTrackingSystem',
    'com.hypixel.hytale.server.spawning.world.system.WorldSpawnJobSystems$Ticking',
    'com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems$OnDeathSystem',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent',
    'com.hypixel.hytale.server.worldgen.BiomeDataSystem',
    'com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator',
    'com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult',
    'com.hypixel.hytale.server.worldgen.loader.context.FileLoadingContext',
    'com.hypixel.hytale.server.worldgen.loader.context.FileContextLoader',
    'com.hypixel.hytale.server.worldgen.loader.context.ZoneFileContext',
    'com.hypixel.hytale.server.worldgen.loader.context.BiomeFileContext',
    'com.hypixel.hytale.server.worldgen.loader.context.FileContext',
    'com.hypixel.hytale.server.worldgen.loader.biome.TileBiomeJsonLoader',
    'com.hypixel.hytale.server.worldgen.loader.biome.CustomBiomeJsonLoader',
    'com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen',
    'com.hypixel.hytale.procedurallib.file.FileIO',
    'com.hypixel.hytale.procedurallib.file.FileIOSystem',
    'com.hypixel.hytale.server.npc.NPCPlugin')){
    $encText=(& $encJavap -classpath $encServer -c -p $encClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Installed encounter API audit failed: $encClass"}
    $encText | Set-Content -LiteralPath (Join-Path $encOut ($encClass+'.txt')) -Encoding utf8
}
$encRoles=@();$encBiomes=@();$encZones=@()
$encZip=[IO.Compression.ZipFile]::OpenRead($encAssets)
try{
    foreach($encEntry in $encZip.Entries){
        $encPath=$encEntry.FullName
        if($encPath -notmatch '^Server/NPC/Roles/.+\.json$|^Server/World/Default/Zones/[^/]+/(Tile\.[^/]+|Custom\.[^/]+|Zone)\.json$'){continue}
        $encMemory=[IO.MemoryStream]::new();$encStream=$encEntry.Open()
        try{$encStream.CopyTo($encMemory);$encBytes=$encMemory.ToArray()}finally{$encStream.Dispose();$encMemory.Dispose()}
        $encHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($encBytes))
        $encJson=[Text.Encoding]::UTF8.GetString($encBytes).TrimStart([char]0xFEFF)|ConvertFrom-Json -AsHashtable
        if($encPath -match '^Server/NPC/Roles/'){
            $encRoles+=@{assetId=[IO.Path]::GetFileNameWithoutExtension($encPath);path=$encPath;type=$encJson['Type'];reference=$encJson['Reference'];sha256=$encHash;
                classification='UNCLASSIFIED_XP_DISABLED';sourceValidation='NOT_CONNECTED_VERIFIED'}
        }elseif($encPath -match '/([^/]+)/Zone\.json$'){
            $encZones+=@{zoneId=$Matches[1];path=$encPath;discovery=$encJson['Discovery'];sha256=$encHash}
        }elseif($encPath -match '/([^/]+)/(Tile|Custom)\.([^/]+)\.json$'){
            $encBiomes+=@{zoneId=$Matches[1];kind=$Matches[2];fileNameStem=$Matches[3];path=$encPath;sha256=$encHash;
                qualification='Native runtime name still checked against installed FileLoadingContext; these are asset identifiers, not native numeric IDs or vanilla levels'}
        }
    }
}finally{$encZip.Dispose()}
$encSkills=Get-Content -Raw -LiteralPath (Join-Path $encRoot 'src\main\resources\rpg\catalog\skills.json') | ConvertFrom-Json
$encRegistry=Get-Content -Raw -LiteralPath (Join-Path $encRoot 'src\main\resources\rpg\progression\enemy-registry.json')|ConvertFrom-Json
foreach($encRole in $encRoles){
    $encAssignment=$encRegistry.roles|Where-Object {$_.roleId -ceq $encRole.assetId}
    if($encAssignment){$encRole.classification='RPG_PILOT_CLASSIFIED_ASSET_ONLY';$encRole.combatIdentity=$encAssignment.combatIdentity;$encRole.rpgRank=$encAssignment.rank}
}
$encSources=@($encSkills|ForEach-Object {@{skillId=$_.id;proposedSource=$_.sourceAcquisition.signatureEnemyId;validationState=$_.sourceAcquisition.validationState;
    publicAcquisitionEnabled=$false;exactNativeRoleAssignment=$null}})
[ordered]@{capturedAtUtc=[DateTime]::UtcNow.ToString('o');serverSha256=(Get-FileHash -LiteralPath $encServer).Hash;assetsSha256=(Get-FileHash -LiteralPath $encAssets).Hash;
    scope='Installed named NPC role assets, including variants/templates/components. Not a count of current combat enemies.';
    roleAssetCount=$encRoles.Count;classifiedPilotRoles=$encRegistry.roles.Count;unclassifiedNamedRoleAssets=@($encRoles|Where-Object {$_.classification -eq 'UNCLASSIFIED_XP_DISABLED'}).Count;
    roleAssets=$encRoles;legacyBiomeAssetCount=$encBiomes.Count;legacyBiomes=$encBiomes;legacyZones=$encZones;
    sourceAssignments=$encSources;connectedEvidence='UNVERIFIED';vanillaEnemyLevelsInvented=$false} |
    ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $encOut 'installed-enemy-registry-audit.json') -Encoding utf8
[pscustomobject]@{roleAssets=$encRoles.Count;legacyBiomeAssets=$encBiomes.Count;zones=$encZones.Count;sourceRows=$encSources.Count;connected='UNVERIFIED'}
