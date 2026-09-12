[CmdletBinding()]
param([string]$Assets="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip")
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo=(Resolve-Path "$PSScriptRoot\..").Path
$out=Join-Path $repo 'src\main\resources\Server\Particles\RPG\Blizzard'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$zip=[IO.Compression.ZipFile]::OpenRead($Assets)
function Read-Asset([string]$path){
  $entry=$zip.GetEntry($path);if(!$entry){throw "Missing native asset: $path"}
  $reader=[IO.StreamReader]::new($entry.Open());try{return ($reader.ReadToEnd()|ConvertFrom-Json -AsHashtable)}finally{$reader.Dispose()}
}
function Save-Asset([string]$name,$value){
  [IO.File]::WriteAllText((Join-Path $out $name),($value|ConvertTo-Json -Depth 80)+"`n",[Text.UTF8Encoding]::new($false))
}
try{
  $common=Join-Path $repo 'src\main\resources\Common\VFX\RPG\Blizzard'
  New-Item -ItemType Directory -Force -Path $common | Out-Null
  foreach($file in @('Portal_Shard.blockymodel','Portal_Shard_Texture.png')){
    $entry=$zip.GetEntry("Common/Blocks/Miscellaneous/$file")
    if(!$entry){throw "Missing native model/texture: $file"}
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $common $file),$true)
  }
  $systems=@{
    RPG_Blizzard_Trail='Server/Particles/Projectile/Ice_Boulder/IceBoulderTrail.particlesystem'
    RPG_Blizzard_Impact='Server/Particles/Combat/Impact/Misc/Ice/Impact_Ice.particlesystem'
    RPG_Blizzard_Snow='Server/Particles/Weather/Snow/Snow_Heavy.particlesystem'
  }
  foreach($id in $systems.Keys){
    $system=Read-Asset $systems[$id]
    foreach($spawner in $system.Spawners){
      $nativeId=$spawner.SpawnerId
      $entry=@($zip.Entries|Where-Object {$_.Name -ceq "$nativeId.particlespawner"})
      if($entry.Count -ne 1){throw "Ambiguous spawner $nativeId"}
      $value=Read-Asset $entry[0].FullName
      $value.ParticleLifeSpan=@{Min=0.10;Max=0.20}
      $value.MaxConcurrentParticles=[Math]::Min(80,[int]$value.MaxConcurrentParticles)
      if($id -eq 'RPG_Blizzard_Snow'){
        # Unit footprint inscribed in a circle, scaled by compiled radius at emission.
        $value.EmitOffset=@{X=@{Min=-0.707;Max=0.707};Y=@{Min=0;Max=0.35};Z=@{Min=-0.707;Max=0.707}}
        $value.SpawnRate=@{Min=140;Max=140}
        $value.InitialVelocity=@{Speed=@{Min=0;Max=0}}
        $value.Attractors=@(@{LinearAcceleration=@{X=0;Y=-1;Z=0}})
        $value.Particle.InitialAnimationFrame.Scale=@{X=@{Min=0.05;Max=0.08};Y=@{Min=0.05;Max=0.08}}
        $spawner.PositionOffset=@{X=0;Y=0;Z=0}
      }
      $spawner.SpawnerId="${id}_$nativeId"
      Save-Asset "$($spawner.SpawnerId).particlespawner" $value
    }
    if($id -ne 'RPG_Blizzard_Trail'){$system.LifeSpan=0.05}
    Save-Asset "$id.particlesystem" $system
  }
}finally{$zip.Dispose()}
