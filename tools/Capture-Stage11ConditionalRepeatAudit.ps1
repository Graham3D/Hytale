[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repeatRoot=(Resolve-Path "$PSScriptRoot\..").Path
$repeatOut=Join-Path $repeatRoot 'evidence\stage-11\cohort-v\api'
$repeatJar=Join-Path $repeatRoot 'build\libs\HytaleRPG-0.0.23.jar'
$repeatServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$repeatJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $repeatOut | Out-Null
foreach($repeatClass in @('com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.SkillExecutionService',
    'com.inigmasgames.hytalerpg.execution.SkillReleaseScheduler',
    'com.inigmasgames.hytalerpg.execution.ConditionalRepeatRuntime',
    'com.inigmasgames.hytalerpg.execution.ConditionalKillTargeting',
    'com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry')){
    $repeatText=(& $repeatJavap -classpath "$repeatJar;$repeatServer" -c -p $repeatClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Conditional repeat audit failed: $repeatClass"}
    $repeatText | Set-Content -LiteralPath (Join-Path $repeatOut ($repeatClass+'.txt')) -Encoding utf8
    if($repeatClass.EndsWith('HytaleDamageAdapter') -and $repeatText -notmatch 'DamageSystems.executeDamage'){throw 'Missing real native damage execution'}
    if($repeatClass.EndsWith('System$Port') -and ($repeatText -notmatch 'applyObserved' -or $repeatText -notmatch 'observedConditionalRepeat' -or $repeatText -notmatch 'registerConditionalAll' -or $repeatText -notmatch 'ConditionalKillTargeting.nearest')){throw 'Missing native receipt/target/carrier handoff'}
    if($repeatClass.EndsWith('SkillExecutionService') -and ($repeatText -notmatch 'currentConditionalPlan' -or $repeatText -notmatch 'SkillReleaseScheduler.due' -or $repeatText -notmatch 'SkillExecutionPort.validateRelease')){throw 'Missing shared queue and revalidation'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $repeatServer).Hash;jarSha256=(Get-FileHash -LiteralPath $repeatJar).Hash;
    scope='Native damage receipt -> conditional decision -> shared release queue -> fresh world/target/equipment revalidation -> existing family executor; retained projectile root accounting';
    evidence='Bytecode/call-site structure only, not connected input, hit, target selection, timing, repeat rendering or native execution proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $repeatOut 'audit.json') -Encoding utf8
