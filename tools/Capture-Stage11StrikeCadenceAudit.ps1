[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$strikeRoot=(Resolve-Path "$PSScriptRoot\..").Path
$strikeOut=Join-Path $strikeRoot 'evidence\stage-11\cohort-m\api'
$strikeJar=Join-Path $strikeRoot 'build\libs\HytaleRPG-0.0.23.jar'
$strikeServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$strikeJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $strikeOut | Out-Null
foreach($strikeClass in @('com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect',
    'com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects',
    'com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent',
    'com.hypixel.hytale.protocol.AbilityEffects',
    'com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeActionLock',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem')){
    $strikeText=(& $strikeJavap -classpath "$strikeJar;$strikeServer" -c -p $strikeClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Strike cadence audit failed: $strikeClass"}
    $strikeText | Set-Content -LiteralPath (Join-Path $strikeOut ($strikeClass+'.txt')) -Encoding utf8
}
$strikeSmoke=Get-Content -Raw -LiteralPath (Join-Path $strikeRoot 'evidence\stage-11\cohort-m\server-smoke-summary.json') | ConvertFrom-Json
if(-not $strikeSmoke.strikeLockResolved){throw 'Action lock must resolve through real native asset load'}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $strikeServer).Hash;jarSha256=(Get-FileHash -LiteralPath $strikeJar).Hash;
    nativeAsset='RPG_Strike_Action_Lock';disabledInteractions=@('Primary','Secondary','Ability1','Ability2','Ability3','Ability4');
    removal='Final .50s repeat, cancellation or first subsequent owner tick; 5s finite native expiry fallback';
    evidence='Installed API bytecode and normal-server asset resolution only. No connected action-lock, animation, geometry or damage execution proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $strikeOut 'audit.json') -Encoding utf8
