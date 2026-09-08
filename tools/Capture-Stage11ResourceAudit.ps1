[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$resourceRoot=(Resolve-Path "$PSScriptRoot\..").Path
$resourceOut=Join-Path $resourceRoot 'evidence\stage-11\cohort-k\api'
$resourceJar=Join-Path $resourceRoot 'build\libs\HytaleRPG-0.0.23.jar'
$resourceServer="$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar"
$resourceJavap='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe'
New-Item -ItemType Directory -Force -Path $resourceOut | Out-Null
foreach($resourceClass in @('com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap',
    'com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue',
    'com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes',
    'com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort',
    'com.inigmasgames.hytalerpg.execution.SkillExecutionService')){
    $resourceText=(& $resourceJavap -classpath "$resourceJar;$resourceServer" -c -p $resourceClass) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Resource API audit failed'}
    $resourceText | Set-Content -LiteralPath (Join-Path $resourceOut ($resourceClass+'.txt')) -Encoding utf8
    if($resourceClass.EndsWith('EntityStatResourcePort') -and
        ($resourceText -notmatch 'DefaultEntityStatTypes.getHealth' -or $resourceText -notmatch 'RpgResourceService.nativeHealthTarget' -or $resourceText -notmatch 'EntityStatMap.setStatValue')){throw 'Missing native nonlethal Health write boundary'}
}
[ordered]@{serverSha256=(Get-FileHash -LiteralPath $resourceServer).Hash;jarSha256=(Get-FileHash -LiteralPath $resourceJar).Hash;
    boundary='Shared cast transaction -> nonlethal Health guard -> native EntityStatMap float value. No damage event, replacement resource store or HUD control.';
    evidence='Installed API and packaged call-site structure only';connected='UNVERIFIED'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $resourceOut 'audit.json') -Encoding utf8
