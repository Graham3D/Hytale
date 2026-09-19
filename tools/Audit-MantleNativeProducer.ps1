[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/..").Path
$auditOut=Join-Path $repo 'evidence/mantle-of-flame-implementation'
New-Item -ItemType Directory -Force -Path "$auditOut/api" | Out-Null
$nativeInstall=Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest'
$nativeJar=Join-Path $nativeInstall 'Server/HytaleServer.jar'
$nativeJavap='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin/javap.exe'
$types=@(
 'modules.interaction.interaction.config.server.DamageEntityInteraction',
 'modules.interaction.interaction.config.server.combat.DamageCalculator',
 'modules.interaction.event.InteractionChainStartEvent',
 'modules.projectile.interaction.ProjectileInteraction',
 'modules.projectile.event.ProjectileLaunchEvent',
 'modules.projectile.component.ProjectileLaunchContext',
 'modules.projectile.component.ImpactModifiers',
 'modules.entity.damage.Damage',
 'modules.entity.damage.Damage$ProjectileSource',
 'entity.InteractionChain',
 'modules.interaction.interaction.config.RootInteraction'
)
foreach($type in $types){
 $disassembly=@(& $nativeJavap -p -c -classpath $nativeJar "com.hypixel.hytale.server.core.$type")
 if($LASTEXITCODE -ne 0){throw "Native producer inspection failed: $type"}
 $disassembly | Set-Content -LiteralPath (Join-Path "$auditOut/api" ($type.Split('.')[-1]+'.txt')) -Encoding utf8
}
$nativeAssets=Join-Path $nativeInstall 'Assets.zip'
$archive=[IO.Compression.ZipFile]::OpenRead($nativeAssets)
try {
 $path='Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json'
 $entry=$archive.GetEntry($path)
 $reader=[IO.StreamReader]::new($entry.Open());try{$json=$reader.ReadToEnd()}finally{$reader.Dispose()}
 $json | Set-Content -LiteralPath "$auditOut/Weapon_Longsword_Flame.installed.json" -Encoding utf8
 $item=$json | ConvertFrom-Json
 $fireCalculators=@($item.InteractionVars.PSObject.Properties | ForEach-Object {
   $bindingName=$_.Name
   $_.Value.Interactions | Where-Object {$_.DamageCalculator.BaseDamage.Fire} | ForEach-Object {
    [ordered]@{binding=$bindingName;parent=$_.Parent;fire=$_.DamageCalculator.BaseDamage.Fire;randomPercentage=$_.DamageCalculator.RandomPercentageModifier}
   }
 })
 [ordered]@{
  capturedAtUtc=[DateTime]::UtcNow.ToString('o');head=(& git -C $repo rev-parse HEAD).Trim()
  serverJarSha256=(Get-FileHash -LiteralPath $nativeJar).Hash
  assetsSha256=(Get-FileHash -LiteralPath $nativeAssets).Hash
  taskSha256=(Get-FileHash -LiteralPath 'C:/Users/Zemio/.codex/attachments/2ec41898-8c3a-432d-a10b-a8b0b19f585f/pasted-text.txt').Hash
  nativeItemPath=$path;nativeFireCalculators=$fireCalculators
  projectEnvelopeImplemented=$true;projectDecisionImplemented=$true;nativeWitnessTransportTested=$true
  nativeProducerRegistered=$false;connectedVerified=$false;deployed=$false;pushed=$false
 } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$auditOut/producer-audit.json" -Encoding utf8
}finally{$archive.Dispose()}
Get-Content -LiteralPath "$auditOut/producer-audit.json"
