[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$componentRoot=(Resolve-Path "$PSScriptRoot\..").Path
$componentOut=Join-Path $componentRoot 'evidence\stage-11\cohort-x\api'
$componentJar=Join-Path $componentRoot 'build\libs\HytaleRPG-0.0.23.jar'
$componentServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$componentJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $componentOut | Out-Null
foreach($componentClass in @('com.inigmasgames.hytalerpg.execution.HitProcRuntime',
    'com.inigmasgames.hytalerpg.execution.CompiledProfileResolver',
    'com.inigmasgames.hytalerpg.execution.projectile.ProjectileSecondaryEffects',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter')){
    $componentText=(& $componentJavap -classpath "$componentJar;$componentServer" -c -p $componentClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Component audit failed: $componentClass"}
    $componentText | Set-Content -LiteralPath (Join-Path $componentOut ($componentClass+'.txt')) -Encoding utf8
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $componentServer).Hash;jarSha256=(Get-FileHash -LiteralPath $componentJar).Hash;
    scope='Component-scoped modifiers reuse unchanged native damage, periodic, area and collision APIs audited in cohorts P/T; no new engine API';
    evidence='Packaged bytecode and call-site structure only, not connected damage, displacement or visual proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $componentOut 'audit.json') -Encoding utf8
