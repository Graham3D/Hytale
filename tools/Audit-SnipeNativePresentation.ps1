[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $root 'evidence/stage-13/cohort-u'
New-Item -ItemType Directory -Force -Path $out|Out-Null
$package='C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest'
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $package 'Assets.zip'))
$hashes=[ordered]@{}
function Read-Native([string]$name){
    $e=$zip.GetEntry($name);if(-not $e){throw "Missing native asset: $name"}
    $stream=$e.Open();try{$hashes[$name]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}
    $r=[IO.StreamReader]::new($e.Open());try{return ($r.ReadToEnd()|ConvertFrom-Json)}finally{$r.Dispose()}
}
try{
    $arrow=Read-Native 'Server/Models/Projectiles/Weapons/Arrow/Arrow_Crude.json'
    $charge=Read-Native 'Server/Item/Interactions/Weapons/Shortbow/Primary/Shoot/Weapon_Shortbow_Primary_Shoot_Charge.json'
    $animations=Read-Native 'Server/Item/Animations/Shortbow.json'
    $hold=Read-Native ('Common/'+$animations.Animations.ShootChargingHold.FirstPerson)
    $particles=Read-Native 'Server/Particles/Weapon/Bow/Bow_Charging.particlesystem'
    $circles=Read-Native 'Server/Particles/Weapon/Bow/Bow_Charging_Circles.particlespawner'
    $sparks=Read-Native 'Server/Particles/Weapon/Bow/Bow_Charging_Sparks.particlespawner'
    $config=Read-Native 'Server/ProjectileConfigs/Weapons/Shortbow/Projectile_Config_Arrow_Shortbow_Strength_4.json'
    if(-not $hold.nodeAnimations.'ARROW-PLACEHOLDER'.shapeVisible[0].delta){throw 'Native hold does not show arrow'}
    $ours=Get-Content -Raw (Join-Path $root 'src/main/resources/Server/Particles/RPG/RPG_Snipe_Ready.particlesystem')|ConvertFrom-Json
    foreach($e in $particles.Spawners){$e.StartDelay=0}
    if($particles.Spawners.Count -ne $ours.Spawners.Count){throw 'Ready emitter count changed'}
    for($i=0;$i -lt $particles.Spawners.Count;$i++){
        $a=$particles.Spawners[$i];$b=$ours.Spawners[$i]
        if(($a.PSObject.Properties.Name|Sort-Object) -join ',' -cne (($b.PSObject.Properties.Name|Sort-Object) -join ',')){throw 'Ready emitter fields changed'}
        foreach($name in $a.PSObject.Properties.Name){
            if(($a.$name|ConvertTo-Json -Depth 32 -Compress) -cne ($b.$name|ConvertTo-Json -Depth 32 -Compress)){throw "Ready emitter changed beyond delay: $name"}
        }
    }
    [ordered]@{
        installedServerSha256=(Get-FileHash -LiteralPath (Join-Path $package 'Server/HytaleServer.jar')).Hash
        nativeAssetHashes=$hashes;nativeArrowModel=$arrow;nativeHold=$animations.Animations.ShootChargingHold
        nativeHoldShowsArrowAtFrameZero=$true;nativeChargedRelease=$animations.Animations.ShootCharged
        nativeChargeEffects=$charge.Effects;nativeYellowRingColor=$circles.Particle.Animation.'0'.Color
        nativeSparksColor=$sparks.Particle.Animation.'0'.Color;nativeEmittersUnchanged=$true
        delayOnlyOverrideSeconds=0;nativeHeavyArrowSpeed=$config.LaunchForce;nativeHeavyArrowGravity=$config.Physics.Gravity
        ownerRequestedSnipeGravity=0;connectedRenderingVerified=$false
    }|ConvertTo-Json -Depth 32|Set-Content -LiteralPath (Join-Path $out 'native-presentation-sources.json') -Encoding utf8
}finally{$zip.Dispose()}
