[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$orbitRoot=(Resolve-Path "$PSScriptRoot\..").Path
$orbitOut=Join-Path $orbitRoot 'evidence\stage-11\cohort-s\api'
$orbitJar=Join-Path $orbitRoot 'build\libs\HytaleRPG-0.0.23.jar'
$orbitServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$orbitJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $orbitOut | Out-Null
foreach($orbitClass in @('com.hypixel.hytale.server.core.modules.collision.CollisionModule',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAreaQueries',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem$Port$1',
    'com.inigmasgames.hytalerpg.execution.hytale.HytaleAmmoAdapter',
    'com.inigmasgames.hytalerpg.execution.connection.ConnectionRuntime',
    'com.inigmasgames.hytalerpg.execution.connection.OrbitConversionProfiles',
    'com.inigmasgames.hytalerpg.execution.RootEffectBudget')){
    $orbitText=(& $orbitJavap -classpath "$orbitJar;$orbitServer" -c -p $orbitClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw "Orbit audit failed: $orbitClass"}
    $orbitText | Set-Content -LiteralPath (Join-Path $orbitOut ($orbitClass+'.txt')) -Encoding utf8
    if($orbitClass.EndsWith('ConnectionRuntime') -and $orbitText -notmatch 'claimOrbitContact'){throw 'Missing shared contact interval'}
    if($orbitClass.EndsWith('$Port') -and ($orbitText -notmatch 'HytaleAmmoAdapter.consume' -or $orbitText -notmatch 'ConnectionRuntime.start')){throw 'Missing native conversion execution boundary'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $orbitServer).Hash;jarSha256=(Get-FileHash -LiteralPath $orbitJar).Hash;
    configSha256=(Get-FileHash -LiteralPath (Join-Path $orbitRoot 'src\main\resources\rpg\runtime\orbit-conversion.json')).Hash;
    scope='Converted immutable payload -> existing finite connection runtime -> swept native bounds/LOS -> existing native damage/status/ammunition authorities';
    evidence='Bytecode/call-site structure only; not connected collision, damage, NPC state or presentation proof';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $orbitOut 'audit.json') -Encoding utf8
