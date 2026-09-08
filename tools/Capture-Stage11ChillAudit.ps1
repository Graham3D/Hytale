[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$chillRoot=(Resolve-Path "$PSScriptRoot\..").Path
$chillOut=Join-Path $chillRoot 'evidence\stage-11\cohort-j\api'
$chillJar=Join-Path $chillRoot 'build\libs\HytaleRPG-0.0.23.jar'
$chillServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$chillJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $chillOut | Out-Null
$chillBytecode=@{}
foreach($chillClass in @('com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaStatuses',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjectionSystem')){
    $chillText=(& $chillJavap -classpath "$chillJar;$chillServer" -c -p $chillClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Packaged Chill entry-point audit failed'}
    $chillBytecode[$chillClass]=$chillText
    $chillText | Set-Content -LiteralPath (Join-Path $chillOut ($chillClass+'.txt')) -Encoding utf8
}
if($chillBytecode['com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaStatuses'] -notmatch 'StatusService.applyChill' -or
    $chillBytecode['com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem'] -notmatch 'HytaleAreaStatuses.applyChill' -or
    $chillBytecode['com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port'] -notmatch 'HytaleAreaStatuses.applyChill'){
    throw 'Missing shared Chill call site'
}
& $chillJavap -classpath $chillServer -c -p 'com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent' |
    Set-Content -LiteralPath (Join-Path $chillOut 'installed-EffectControllerComponent.txt') -Encoding utf8
if($LASTEXITCODE -ne 0){throw 'Installed effect controller audit failed'}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $chillServer).Hash;jarSha256=(Get-FileHash -LiteralPath $chillJar).Hash;
    boundary='Area, Aura and Projectile call the same source-root Chill service; projectile uses existing native status marker/projection';
    evidence='Compiled call-site and installed API structure only. No connected movement, casting, client rendering or native execution proof.';
    connected='UNVERIFIED'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $chillOut 'audit.json') -Encoding utf8
