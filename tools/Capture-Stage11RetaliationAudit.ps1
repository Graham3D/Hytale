[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$retalRoot=(Resolve-Path "$PSScriptRoot\..").Path
$retalOut=Join-Path $retalRoot 'evidence\stage-11\cohort-w\api'
$retalJar=Join-Path $retalRoot 'build\libs\HytaleRPG-0.0.23.jar'
$retalServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$retalJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $retalOut | Out-Null
foreach($retalClass in @('com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$ApplyDamage',
    'com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems$BeforeApply',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleRetaliationSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.phase00.Phase00Plugin',
    'com.inigmasgames.hytalerpg.execution.SkillExecutionService',
    'com.inigmasgames.hytalerpg.execution.RetaliationLedger')){
    $retalText=(& $retalJavap -classpath "$retalJar;$retalServer" -c -p $retalClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Retaliation audit failed: $retalClass"}
    $retalText | Set-Content -LiteralPath (Join-Path $retalOut ($retalClass+'.txt')) -Encoding utf8
    if($retalClass.EndsWith('HytaleRetaliationSystem') -and ($retalText -notmatch 'SupportDamageSystems.observedHealthBefore' -or $retalText -notmatch 'HytaleDamageMetadata.noRetaliation' -or $retalText -notmatch 'onRetaliationDamage')){throw 'Missing real native loss/provenance handoff'}
    if($retalClass.EndsWith('HytaleSkillExecutionSystem') -and $retalText -notmatch 'SkillExecutionService.tickRetaliation'){throw 'Missing world-tick activation'}
    if($retalClass.EndsWith('Phase00Plugin') -and $retalText -notmatch 'HytaleRetaliationSystem'){throw 'Missing native observer registration'}
    if($retalClass.EndsWith('Phase00Plugin') -and $retalText.IndexOf('SupportDamageSystems$Reflect') -gt $retalText.IndexOf('HytaleRetaliationSystem')){throw 'Reflect must be registered before its Retaliation dependency is validated'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $retalServer).Hash;jarSha256=(Get-FileHash -LiteralPath $retalJar).Hash;
    scope='Existing pre-Apply Health capture -> native Inspect actual hostile loss/provenance -> bounded rolling history -> next world-tick normal activation/payment';
    evidence='Bytecode and call-site proof only, not connected event order, enemy damage, input, casting, native resource writes or rendering proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $retalOut 'audit.json') -Encoding utf8
