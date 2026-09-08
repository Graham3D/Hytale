[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$rewardRoot=(Resolve-Path "$PSScriptRoot\..").Path
$rewardOut=Join-Path $rewardRoot 'evidence\stage-12\cohort-e\api'
$rewardServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$rewardJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $rewardOut | Out-Null
foreach($rewardClass in @(
    'com.hypixel.hytale.server.spawning.world.system.WorldSpawnJobSystems',
    'com.hypixel.hytale.server.spawning.world.system.WorldSpawnTrackingSystem$1',
    'com.hypixel.hytale.server.spawning.SpawningContext',
    'com.hypixel.hytale.server.spawning.LoadedNPCEvent',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage',
    'com.hypixel.hytale.server.core.modules.entity.damage.Damage$ProjectileSource',
    'com.hypixel.hytale.server.core.modules.entity.damage.Damage$EntitySource',
    'com.hypixel.hytale.server.core.universe.world.World',
    'com.hypixel.hytale.server.core.universe.world.WorldConfig',
    'com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider',
    'com.hypixel.hytale.component.system.tick.TickingSystem',
    'com.hypixel.hytale.server.core.universe.world.storage.ChunkStore')){
    $rewardText=(& $rewardJavap -classpath $rewardServer -c -p $rewardClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Installed reward API audit failed: $rewardClass"}
    $rewardText | Set-Content -LiteralPath (Join-Path $rewardOut ($rewardClass+'.txt')) -Encoding utf8
}
[pscustomobject]@{serverSha256=(Get-FileHash -LiteralPath $rewardServer).Hash;connectedProof=$false;output=$rewardOut}
