[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$conditionalRoot=(Resolve-Path "$PSScriptRoot\..").Path
$conditionalOut=Join-Path $conditionalRoot 'evidence\stage-11\cohort-e\api'
$conditionalJar="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$conditionalJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $conditionalOut | Out-Null
foreach($conditionalClass in @('com.hypixel.hytale.server.core.modules.entity.damage.DamageModule$OrderGatherFilter',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ScaleOutgoingDamageFromEntityEffects',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ArmorDamageReduction',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage',
    'com.hypixel.hytale.server.core.modules.entity.damage.Damage','com.hypixel.hytale.server.core.meta.MetaRegistry')){
    & $conditionalJavap -classpath $conditionalJar -c -p $conditionalClass | Set-Content -LiteralPath (Join-Path $conditionalOut ($conditionalClass+'.txt')) -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Installed conditional damage API audit failed'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $conditionalJar).Hash;order='Gather before Filter; outgoing EntityEffect scaling and ArmorDamageReduction use Filter; Apply consumes filtered amount';meaning='Registration/bytecode evidence only, not connected native damage execution';connected='UNVERIFIED'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $conditionalOut 'audit.json') -Encoding utf8
