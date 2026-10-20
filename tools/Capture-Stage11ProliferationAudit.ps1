[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$spreadRoot=(Resolve-Path "$PSScriptRoot\..").Path
$spreadOut=Join-Path $spreadRoot 'evidence\stage-11\cohort-u\api'
$spreadJar=Join-Path $spreadRoot 'build\libs\HytaleRPG-0.0.23.jar'
$spreadServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$spreadJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $spreadOut | Out-Null
foreach($spreadClass in @('com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems$OnDeathSystem',
    'com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent',
    'com.inigmasgames.hytalerpg.phase00.Phase00Plugin',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleStatusDeathSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaStatuses',
    'com.inigmasgames.hytalerpg.execution.ChillSourceRegistry',
    'com.inigmasgames.hytalerpg.execution.ProliferationRuntime',
    'com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime')){
    $spreadText=(& $spreadJavap -classpath "$spreadJar;$spreadServer" -c -p $spreadClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Status death audit failed: $spreadClass"}
    $spreadText | Set-Content -LiteralPath (Join-Path $spreadOut ($spreadClass+'.txt')) -Encoding utf8
    if($spreadClass.EndsWith('Phase00Plugin') -and $spreadText -notmatch 'HytaleStatusDeathSystem'){throw 'Missing native status death registration'}
    if($spreadClass.EndsWith('HytaleStatusDeathSystem') -and ($spreadText -notmatch 'DeathComponent.getDeathInfo' -or $spreadText -notmatch 'nativeStatusDeath')){throw 'Missing real native death boundary'}
    if($spreadClass.EndsWith('HytaleSkillExecutionSystem') -and ($spreadText -notmatch 'takeForDeath' -or $spreadText -notmatch 'ProliferationRuntime.death')){throw 'Missing source claim/propagation handoff'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $spreadServer).Hash;jarSha256=(Get-FileHash -LiteralPath $spreadJar).Hash;
    scope='Real native DeathComponent -> claimed source packages -> bounded nearest/LOS propagation -> existing native source-owned status authorities';
    evidence='Bytecode/call-site structure only; not connected death ordering, propagation, NPC status or presentation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $spreadOut 'audit.json') -Encoding utf8
