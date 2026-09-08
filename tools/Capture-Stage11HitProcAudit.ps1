[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$hitRoot=(Resolve-Path "$PSScriptRoot\..").Path
$hitOut=Join-Path $hitRoot 'evidence\stage-11\cohort-t\api'
$hitJar=Join-Path $hitRoot 'build\libs\HytaleRPG-0.0.23.jar'
$hitServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$hitJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $hitOut | Out-Null
foreach($hitClass in @('com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems',
    'com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect',
    'com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem$Port',
    'com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects$DirectDamageBreak',
    'com.inigmasgames.hytalerpg.execution.hytale.NativeHitProcAssets',
    'com.inigmasgames.hytalerpg.execution.HitProcRuntime',
    'com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime')){
    $hitText=(& $hitJavap -classpath "$hitJar;$hitServer" -c -p $hitClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Hit proc audit failed: $hitClass"}
    $hitText | Set-Content -LiteralPath (Join-Path $hitOut ($hitClass+'.txt')) -Encoding utf8
    if($hitClass.EndsWith('HytaleSkillExecutionSystem$Port') -and ($hitText -notmatch 'HitProcRuntime.observed' -or $hitText -notmatch 'HytaleDamageAdapter.applyResolved')){throw 'Missing native receipt/resolved payload boundaries'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $hitServer).Hash;jarSha256=(Get-FileHash -LiteralPath $hitJar).Hash;
    scope='Native completed damage receipt -> bounded proc decision -> existing source periodic/Fear/area authorities; resolved secondaries never recalculate offense';
    evidence='Bytecode/call-site structure only; not connected proc, AI navigation, damage or presentation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $hitOut 'audit.json') -Encoding utf8
