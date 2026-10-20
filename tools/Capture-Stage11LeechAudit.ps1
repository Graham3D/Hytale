[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$leechRoot=(Resolve-Path "$PSScriptRoot\..").Path
$leechOut=Join-Path $leechRoot 'evidence\stage-11\cohort-l\api'
$leechJar=Join-Path $leechRoot 'build\libs\HytaleRPG-0.0.23.jar'
$leechServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$leechJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $leechOut | Out-Null
foreach($leechClass in @('com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter',
    'com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort',
    'com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems',
    'com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage')){
    $leechText=(& $leechJavap -classpath "$leechJar;$leechServer" -c -p $leechClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Native Leech audit failed'}
    $leechText | Set-Content -LiteralPath (Join-Path $leechOut ($leechClass+'.txt')) -Encoding utf8
    if($leechClass.EndsWith('$Port') -and
        ($leechText -notmatch 'NativeResult.healthBefore' -or $leechText -notmatch 'NativeResult.healthAfter' -or $leechText -notmatch 'RpgResourceService.recoverLeech')){throw 'Missing actual native Health observation/Leech call site'}
    if($leechClass.EndsWith('SupportDamageSystems') -and $leechText -match 'recoverLeech'){throw 'Reflection must not directly leech'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $leechServer).Hash;jarSha256=(Get-FileHash -LiteralPath $leechJar).Hash;
    boundary='DamageSystems.executeDamage returns -> observed native Health loss -> shared root budget -> bounded native Mana/Stamina credit';
    evidence='Bytecode and API structure only; no connected packet, damage execution or recovery proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $leechOut 'audit.json') -Encoding utf8
