[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/..").Path
$out=Join-Path $repo 'evidence/mantle-of-flame-boundary'
New-Item -ItemType Directory -Force $out|Out-Null
$install=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest'
$jar=Join-Path $install 'Server/HytaleServer.jar'
$assets=Join-Path $install 'Assets.zip'
$master='C:/Users/Zemio/Downloads/Hytale RPG Master Implementation Specification v1.3.docx'
$task='C:/Users/Zemio/.codex/attachments/ec0c8cc5-97df-4778-b297-6ceb4d80ef4c/pasted-text.txt'
$javap='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin/javap.exe'
$classes=@(
 'modules.interaction.interaction.config.server.DamageEntityInteraction',
 'modules.interaction.interaction.config.server.combat.DamageCalculator',
 'modules.entity.damage.Damage','modules.entity.damage.DamageCalculatorSystems',
 'modules.entity.damage.DamageCalculatorSystems$DamageSequence','modules.entity.damage.DamageCalculatorSystems$Sequence',
 'modules.projectile.component.ImpactModifiers','asset.type.entityeffect.config.ApplicationEffects',
 'entity.effect.EffectControllerComponent'
)
$api=Join-Path $out 'api';New-Item -ItemType Directory -Force $api|Out-Null
foreach($class in $classes){
 $result=@(& $javap -p -c -classpath $jar "com.hypixel.hytale.server.core.$class")
 if($LASTEXITCODE -ne 0){throw "Native API inspection failed: $class"}
 $result|Set-Content (Join-Path $api ($class.Split('.')[-1]+'.txt')) -Encoding utf8
}
function ReadEntry($entry){
 $r=[IO.StreamReader]::new($entry.Open())
 try{$r.ReadToEnd()}finally{$r.Dispose()}
}
function EntryHash($entry){
 $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
 try{([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
}
$zip=[IO.Compression.ZipFile]::OpenRead($master)
try{
 [xml]$xml=ReadEntry $zip.GetEntry('word/document.xml')
 $ns=[Xml.XmlNamespaceManager]::new($xml.NameTable);$ns.AddNamespace('w','http://schemas.openxmlformats.org/wordprocessingml/2006/main')
 $paragraphs=@($xml.SelectNodes('//w:body/w:p|//w:body/w:tbl//w:p',$ns)|ForEach-Object {($_.SelectNodes('.//w:t',$ns)|ForEach-Object InnerText)-join ''})
 $selected=@(0..284)+@(2488..2517)+@(3378..3392)+@(3624..3636)+@(3695..3720)
 @($selected|Sort-Object -Unique|ForEach-Object {('{0}: {1}' -f $_,$paragraphs[$_])})|Set-Content (Join-Path $out 'master-v1.3-relevant-paragraphs.txt') -Encoding utf8
}finally{$zip.Dispose()}
$zip=[IO.Compression.ZipFile]::OpenRead($assets)
try{
 $systems=@($zip.Entries|Where-Object {$_.FullName -match '/(Fire_AoE2|Impact_Fire)\.particlesystem$'})
 $rows=@()
 foreach($system in $systems){
  $json=(ReadEntry $system)|ConvertFrom-Json
  $children=@(foreach($binding in $json.Spawners){
   foreach($entry in $zip.Entries|Where-Object {$_.FullName -like "*/$($binding.SpawnerId).particlespawner"}){
    $child=(ReadEntry $entry)|ConvertFrom-Json
    [ordered]@{id=$binding.SpawnerId;path=$entry.FullName;sha256=(EntryHash $entry);binding=$binding;
     texture=$child.Particle.Texture;uvMotion=$child.UVMotion;attractors=$child.Attractors;initialVelocity=$child.InitialVelocity;
     lifespan=$child.ParticleLifeSpan;maxConcurrent=$child.MaxConcurrentParticles}
   }
  })
  $rows+=@{id=$system.Name;path=$system.FullName;sha256=(EntryHash $system);children=$children}
 }
 $rows|ConvertTo-Json -Depth 14|Set-Content (Join-Path $out 'native-vfx-inventory.json') -Encoding utf8
}finally{$zip.Dispose()}
$skills=Get-Content (Join-Path $repo 'src/main/resources/rpg/catalog/skills.json') -Raw|ConvertFrom-Json
$passives=Get-Content (Join-Path $repo 'src/main/resources/rpg/catalog/passives.json') -Raw|ConvertFrom-Json
$live=Join-Path $env:APPDATA 'Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar'
[ordered]@{
 capturedAtUtc=[DateTime]::UtcNow.ToString('o');branch=(& git -C $repo branch --show-current).Trim();head=(& git -C $repo rev-parse HEAD).Trim();
 serverJarSha256=(Get-FileHash $jar).Hash;assetsSha256=(Get-FileHash $assets).Hash;
 masterSha256=(Get-FileHash $master).Hash;taskSha256=(Get-FileHash $task).Hash;
 catalogSha256=(Get-FileHash (Join-Path $repo 'src/main/resources/rpg/catalog/skills.json')).Hash;
 currentSkills=$skills.Count;currentPassives=$passives.Count;currentAssessments=$skills.Count*$passives.Count;
 currentUnassigned=@($skills|Where-Object {$_.sourceAcquisition.validationState -eq 'UNASSIGNED'}).Count;
 proposedSkills=$skills.Count+1;proposedAssessments=($skills.Count+1)*$passives.Count;
 liveJarSha256=(Get-FileHash $live).Hash;liveModified=$false;deployed=$false;pushed=$false;
 status='BLOCKED';boundary='PRE_VICTIM_EXECUTION_WIDE_WEAPON_FIRE_COMPONENT_PROVENANCE';
 nativeScalarSuppressionAvailable=$true;productionWeaponFireEnvelopeAvailable=$false;connectedVerified=$false
}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'audit-summary.json') -Encoding utf8
Get-Content (Join-Path $out 'audit-summary.json')
